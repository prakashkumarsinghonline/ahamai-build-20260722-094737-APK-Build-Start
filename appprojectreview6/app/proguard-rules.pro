# ==========================================================
# AhamAI - R8 / ProGuard keep rules (release minified build)
# ==========================================================

# --- Keep source file + line numbers for readable crash stack traces ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Kotlin metadata / coroutines ---
-keepattributes *Annotation*, Signature, Exceptions, InnerClasses, EnclosingMethod
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- OkHttp + Okio (HTTP client + SSE streaming) ---
# OkHttp ships consumer rules; these silence optional platform integrations
# (Conscrypt / BouncyCastle / OpenJSSE / GraalVM) that are not bundled.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okio.**
-keep class okhttp3.internal.publicsuffix.** { *; }

# --- jLaTeXMath (native LaTeX rendering) ---
# Atom/formula classes are resolved dynamically; keep the package intact.
-keep class ru.noties.jlatexmath.** { *; }
-keep class org.scilab.forge.jlatexmath.** { *; }
-dontwarn org.scilab.forge.jlatexmath.**

# --- Coil (image loading) ---
-dontwarn coil.**

# --- JetBrains Markdown parser ---
-dontwarn org.intellij.markdown.**

# --- App: keep Activities/Services referenced from the manifest ---
-keep class com.ahamai.app.MainActivity { *; }
-keep class com.ahamai.app.service.AhamAIService { *; }

# --- iText7 (PDF creation & editing) ---
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**
-dontwarn org.slf4j.**
-dontwarn javax.xml.**


# ==========================================================
# Hardening additions (obfuscation + shrinking, R8 full mode)
# ==========================================================

# --- Aggressive obfuscation: flatten everything into the default package and
# --- widen access so more inlining/merging is possible. Makes decompiled
# --- output (a.a, b.c, ...) much harder to navigate.
-repackageclasses ''
-allowaccessmodification

# --- Strip all android.util.Log calls from release builds (no debug leakage).
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

# --- Honor @Keep (androidx annotation) wherever used ---
-keep class androidx.annotation.Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * { @androidx.annotation.Keep <methods>; }
-keepclasseswithmembers class * { @androidx.annotation.Keep <fields>; }
-keepclasseswithmembers class * { @androidx.annotation.Keep <init>(...); }

# --- Firebase / Google Play services ---
# All Firebase artifacts ship consumer ProGuard rules; app code talks to
# Firestore/RemoteConfig via Map<String,Object> and string keys only (no POJO
# .toObject()/set(dataclass) reflection - verified), so no model keeps needed.
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-dontnote com.google.firebase.**
-dontnote com.google.android.gms.**

# --- kotlinx-coroutines (safety: internal fields looked up reflectively) ---
-keepclassmembers class kotlin.coroutines.SafeContinuation { volatile <fields>; }
-dontwarn java.lang.instrument.**
-dontwarn sun.misc.SignalHandler

# --- Jetpack Compose: rules are consumer-provided by the libraries.
# Safety net only for the runtime's reflective invalidation hooks.
-dontwarn androidx.compose.**

# --- Lottie / Accompanist / Coil ship consumer rules; silence optional deps ---
-dontwarn com.airbnb.lottie.**
-dontwarn com.google.accompanist.**

# --- Google Mobile Ads (AdMob) — ships consumer rules; keep the public ads API
# --- as a safety net for the native-ad view binding under R8 full mode.
-keep public class com.google.android.gms.ads.** { public *; }
-keep public class com.google.android.gms.ads.nativead.** { public *; }

# --- org.json is part of the Android platform (no rules needed); app JSON
# --- (de)serialization uses string-literal keys, so field renaming is safe.

# --- Keep enum machinery (R8 full mode can strip values()/valueOf which the
# --- Kotlin runtime and serialized enum handling may still need).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

