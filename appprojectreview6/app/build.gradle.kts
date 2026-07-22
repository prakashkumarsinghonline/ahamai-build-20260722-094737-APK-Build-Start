import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

// Load release signing credentials from keystore.properties (kept out of version control).
// Template: keystore.properties.example
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}
val hasReleaseKeystore = keystorePropertiesFile.exists()

android {
    namespace = "com.ahamai.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ahamai.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Only attach the release signing config when keystore.properties is present,
            // so CI/clean checkouts without secrets still configure successfully.
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    // Custom Compose splash (logo + "Bihar, India" watermark), no Android system splash
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // Firebase: Remote Config (Brave/Sarvam API keys, endpoints) + Analytics + Auth + Firestore
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // Coroutine await() extensions for Firebase Tasks
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // JetBrains Markdown AST parser (GFM flavour)
    implementation("org.jetbrains:markdown:0.7.3")

    // Native LaTeX math rendering (no WebView)
    implementation("ru.noties:jlatexmath-android:0.2.0")

    // Async image loading (markdown images / webpage screenshots)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // FileProvider for sharing code files
    implementation("androidx.core:core:1.13.1")

    // iText7 for PDF creation, editing, merging, splitting
    implementation("com.itextpdf:itext7-core:7.2.5")

    // Lottie animations (animated suggestion accents)
    implementation("com.airbnb.android:lottie-compose:6.4.0")

    // Accompanist Pager for onboarding slides
    implementation("com.google.accompanist:accompanist-pager:0.32.0")
    implementation("com.google.accompanist:accompanist-pager-indicators:0.32.0")

    // Tier 2 perf: ProfileInstaller enables baseline/startup profiles at install time,
    // pre-compiling hot code paths (startup, scrolling, streaming) for smoother frames.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    // Google AdMob — native "Sponsored" ad cards shown in CHAT mode only (ChatGPT-style)
    implementation("com.google.android.gms:play-services-ads:23.6.0")
}
