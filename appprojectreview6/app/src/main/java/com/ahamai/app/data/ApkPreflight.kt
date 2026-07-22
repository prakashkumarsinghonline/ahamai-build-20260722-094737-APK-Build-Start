package com.ahamai.app.data

import java.io.File

/**
 * INSTANT static pre-flight for Android APK builds.
 *
 * The real build runs in the cloud via GitHub Actions (which already fast-fails on
 * `compileDebugKotlin`), but waiting ~4-6 min only to fail on a trivial, predictable mistake is a
 * terrible loop. Running a real Gradle compile locally isn't possible (the app is on a phone) and
 * isn't reliable in the E2B sandbox (JRE-only, no javac, ~1GB RAM, empty Java trust store). So
 * instead we do a fast, dependency-free STATIC scan that catches the highest-frequency reasons an
 * Android build fails — the exact classes of mistakes the agent (or a user's uploaded source)
 * tends to ship — so they get fixed BEFORE a CI run is spent:
 *
 *   1. Missing/incomplete Gradle wrapper (jar/properties) — CI can't even start.
 *   2. Missing app module manifest / build script.
 *   3. Kotlin `package` that doesn't match the file's directory (a top compile breaker).
 *   4. Unbalanced braces/parens/brackets in a .kt file (truncated/half-written files).
 *   5. Manifest activity classes that don't exist in sources.
 *   6. @Composable used without `buildFeatures { compose = true }`.
 *
 * This is purely static (no network, no Gradle) so it returns in milliseconds. It cannot GUARANTEE
 * a green build (only a compiler can), but it eliminates the common, embarrassing failures and
 * gives an instant, high-signal go/no-go before the cloud build.
 */
object ApkPreflight {

    data class Report(val blocking: List<String>, val warnings: List<String>, val checked: Int) {
        val ok: Boolean get() = blocking.isEmpty()
        fun format(): String = buildString {
            if (blocking.isEmpty()) {
                append("BUILD PRE-FLIGHT: PASSED ✓  ($checked Kotlin file(s) scanned). ")
                append("No blocking issues found — safe to start the cloud build.")
            } else {
                append("BUILD PRE-FLIGHT: ${blocking.size} BLOCKING issue(s) — FIX THESE before building (they would fail the cloud build):\n")
                blocking.forEachIndexed { i, m -> append("  ${i + 1}. $m\n") }
            }
            if (warnings.isNotEmpty()) {
                append("\nWarnings (likely but not certain to break the build):\n")
                warnings.forEach { append("  - $it\n") }
            }
        }.trim()
    }

    /** Run the static pre-flight. [buildRoot] is the project-relative folder holding the Gradle
     *  build (from [ProjectManager.detectBuildRoot]); "" means the project dir itself. */
    fun check(projectDir: String, buildRoot: String = ""): Report {
        val blocking = ArrayList<String>()
        val warnings = ArrayList<String>()
        val root = if (buildRoot.isBlank()) File(projectDir) else File(projectDir, buildRoot)
        if (!root.isDirectory) {
            return Report(listOf("Build root '$buildRoot' not found in the project."), emptyList(), 0)
        }

        // 1. Gradle wrapper — CI checks these out and runs ./gradlew; a missing jar/properties is a
        //    frequent hard failure (the workflow's `gradle wrapper` fallback often can't run).
        val hasGradlew = File(root, "gradlew").exists()
        val hasWrapperJar = File(root, "gradle/wrapper/gradle-wrapper.jar").exists()
        val hasWrapperProps = File(root, "gradle/wrapper/gradle-wrapper.properties").exists()
        if (!hasGradlew) warnings.add("No gradlew script at build root — CI will try to generate the wrapper.")
        if (hasGradlew && !hasWrapperJar)
            blocking.add("gradle/wrapper/gradle-wrapper.jar is missing — ./gradlew cannot start. Restore the wrapper jar.")
        if (hasGradlew && !hasWrapperProps)
            blocking.add("gradle/wrapper/gradle-wrapper.properties is missing — ./gradlew has no distribution URL.")

        // 2. App module: a build script + a manifest must exist somewhere.
        val gradleFiles = root.walkTopDown().filter {
            it.isFile && (it.name == "build.gradle" || it.name == "build.gradle.kts")
        }.toList()
        if (gradleFiles.isEmpty())
            blocking.add("No build.gradle(.kts) found — this is not a Gradle project.")
        val manifests = root.walkTopDown().filter { it.isFile && it.name == "AndroidManifest.xml" }.toList()
        if (gradleFiles.isNotEmpty() && manifests.isEmpty())
            blocking.add("No AndroidManifest.xml found — an Android app module needs one.")

        // 3+4. Per-Kotlin-file checks: package/path match + brace balance.
        val ktFiles = root.walkTopDown().filter {
            it.isFile && it.extension == "kt" &&
                !it.path.contains("/build/") && !it.path.contains("/.gradle/") &&
                !it.path.contains("\\build\\") && !it.path.contains("\\.gradle\\")
        }.toList()
        var anyCompose = false
        val srcRoots = listOf(
            "src/main/java/", "src/main/kotlin/",
            "src/test/java/", "src/test/kotlin/",
            "src/androidTest/java/", "src/androidTest/kotlin/",
            // KMP / multiplatform common paths — package still must match
            "src/commonMain/kotlin/", "src/androidMain/kotlin/",
            "src/jvmMain/kotlin/", "src/iosMain/kotlin/"
        )
        for (f in ktFiles) {
            val text = runCatching { f.readText() }.getOrNull() ?: continue
            if (text.contains("@Composable")) anyCompose = true

            // package vs directory
            val pkg = Regex("(?m)^\\s*package\\s+([A-Za-z0-9_.]+)").find(text)?.groupValues?.get(1)
            if (pkg != null) {
                val rel = f.path.substringAfter(root.path).trimStart('/', '\\').replace('\\', '/')
                val underSrc = srcRoots.firstOrNull { rel.contains(it) }
                if (underSrc != null) {
                    val dirPath = rel.substringAfter(underSrc).substringBeforeLast('/', "")
                    val expectedPkgPath = pkg.replace('.', '/')
                    // Only flag when the folder is clearly under a src root AND differs.
                    // Empty dirPath (file sitting directly in java/) with a multi-segment package
                    // is still wrong — flag it. Single-segment edge cases keep the old rule.
                    if (dirPath != expectedPkgPath && (dirPath.isNotEmpty() || expectedPkgPath.contains('/'))) {
                        // Cap noise: one message per file name is enough
                        blocking.add("${f.name}: package '$pkg' does not match its folder '${dirPath.ifEmpty { "(src root)" }}' " +
                            "(expected '$expectedPkgPath'). Move it under '$underSrc$expectedPkgPath/' or fix the package.")
                    }
                }
            }

            // brace / paren / bracket balance (string/template/comment-aware)
            val imbalance = delimiterImbalance(text)
            if (imbalance != null)
                blocking.add("${f.name}: unbalanced $imbalance — the file looks truncated or malformed and won't compile.")
        }

        // 5. Manifest's OWN component classes exist. Only check components declared with a
        //    relative name (android:name=".Foo") inside <activity>/<service>/<receiver>/<provider>
        //    — those are the app's own classes. Fully-qualified names (androidx.*, com.google.*)
        //    come from dependencies, and permissions/intent-actions/categories also use
        //    android:name but aren't classes — so we skip all of those to avoid noise.
        val compRe = Regex("<(activity|service|receiver|provider)\\b[^>]*?android:name\\s*=\\s*\"(\\.[^\"]+)\"",
            RegexOption.DOT_MATCHES_ALL)
        for (mf in manifests) {
            val mtext = runCatching { mf.readText() }.getOrNull() ?: continue
            for (m in compRe.findAll(mtext)) {
                val simple = m.groupValues[2].removePrefix(".").substringAfterLast('.')
                if (simple.isBlank()) continue
                val declared = ktFiles.any { kf ->
                    runCatching { kf.readText() }.getOrNull()?.let {
                        Regex("\\b(class|object)\\s+$simple\\b").containsMatchIn(it)
                    } ?: false
                }
                if (!declared)
                    warnings.add("Manifest declares component '${m.groupValues[2]}' but no `class $simple` exists in the Kotlin sources.")
            }
        }

        // 6. Compose feature flag.
        if (anyCompose) {
            val enablesCompose = gradleFiles.any { g ->
                runCatching { g.readText() }.getOrNull()?.let {
                    it.contains("compose = true") || it.contains("compose true") ||
                        it.contains("buildFeatures") && it.contains("compose")
                } ?: false
            }
            if (!enablesCompose)
                warnings.add("@Composable is used but no module enables `buildFeatures { compose = true }` — Compose won't compile.")
        }

        return Report(blocking, warnings.distinct().take(8), ktFiles.size)
    }

    /**
     * Returns a short description of the first unbalanced delimiter type, or null if balanced.
     * Skips characters inside line/block comments, "strings", 'chars', and """triple""" strings.
     * Kotlin string templates `${…}` are scanned so nested braces/quotes don't false-positive
     * (e.g. `"Hello ${foo("x")}"` used to report phantom unbalanced braces).
     */
    private fun delimiterImbalance(src: String): String? {
        var curly = 0; var paren = 0; var square = 0
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            when {
                c == '/' && i + 1 < n && src[i + 1] == '/' -> { // line comment
                    while (i < n && src[i] != '\n') i++
                }
                c == '/' && i + 1 < n && src[i + 1] == '*' -> { // block comment
                    i += 2
                    while (i + 1 < n && !(src[i] == '*' && src[i + 1] == '/')) i++
                    i += 2; continue
                }
                c == '"' && i + 2 < n && src[i + 1] == '"' && src[i + 2] == '"' -> { // triple-quoted
                    i += 3
                    while (i + 2 < n && !(src[i] == '"' && src[i + 1] == '"' && src[i + 2] == '"')) i++
                    i += 3; continue
                }
                c == '"' -> { // normal string + ${} templates
                    i++
                    while (i < n && src[i] != '"') {
                        if (src[i] == '\\' && i + 1 < n) { i += 2; continue }
                        if (src[i] == '$' && i + 1 < n && src[i + 1] == '{') {
                            i += 2
                            var depth = 1
                            while (i < n && depth > 0) {
                                when (src[i]) {
                                    '{' -> { depth++; i++ }
                                    '}' -> { depth--; i++ }
                                    '"' -> {
                                        i++
                                        while (i < n && src[i] != '"') {
                                            if (src[i] == '\\' && i + 1 < n) { i += 2; continue }
                                            i++
                                        }
                                        if (i < n && src[i] == '"') i++
                                    }
                                    '\'' -> {
                                        i++
                                        while (i < n && src[i] != '\'') {
                                            if (src[i] == '\\' && i + 1 < n) { i += 2; continue }
                                            i++
                                        }
                                        if (i < n && src[i] == '\'') i++
                                    }
                                    else -> i++
                                }
                            }
                            continue
                        }
                        i++
                    }
                }
                c == '\'' -> { // char literal
                    i++
                    while (i < n && src[i] != '\'') {
                        if (src[i] == '\\' && i + 1 < n) { i += 2; continue }
                        i++
                    }
                }
                // Kotlin escaped identifiers `foo` — not delimiters
                c == '`' -> {
                    i++
                    while (i < n && src[i] != '`' && src[i] != '\n') i++
                }
                c == '{' -> curly++
                c == '}' -> curly--
                c == '(' -> paren++
                c == ')' -> paren--
                c == '[' -> square++
                c == ']' -> square--
            }
            if (curly < 0) return "braces {}"
            if (paren < 0) return "parentheses ()"
            if (square < 0) return "brackets []"
            i++
        }
        return when {
            curly != 0 -> "braces {} (${if (curly > 0) "$curly unclosed" else "extra"})"
            paren != 0 -> "parentheses ()"
            square != 0 -> "brackets []"
            else -> null
        }
    }
}
