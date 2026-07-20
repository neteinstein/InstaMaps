plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    // No Kover here: :app is a pure composition root (Application + one trampoline Activity)
    // with no domain/data logic of its own - see the root build.gradle.kts comment for why it's
    // intentionally left out of the merged coverage report.
}

android {
    namespace = "org.neteinstein.instamaps"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.neteinstein.instamaps"
        minSdk = 27
        targetSdk = 36
        // Overridable by .github/workflows/release.yml (APP_VERSION_CODE/APP_VERSION_NAME env
        // vars, derived from the GitHub Actions run number) so a release build gets a unique,
        // monotonically increasing versionCode without editing this file on every release. Local/
        // debug builds fall back to these defaults.
        versionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("APP_VERSION_NAME") ?: "1.0"
    }

    buildFeatures {
        compose = true
    }

    // Populated by .github/workflows/release.yml from Action secrets (KEYSTORE_BASE64 decoded to
    // a file + KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD) so the release workflow can produce a
    // signed APK. Left unset for local builds - see the release buildType below for the fallback.
    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Sign with the real release key when CI provides one; otherwise fall back to debug
            // signing so `./gradlew assembleRelease` still works on a developer machine without
            // the signing secrets configured.
            signingConfig =
                if (System.getenv("KEYSTORE_FILE") != null) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:settings"))
    implementation(project(":core:instagramauth"))
    implementation(project(":feature:maps"))
    implementation(project(":feature:geocoding"))
    implementation(project(":feature:videoprocessing"))
    implementation(project(":feature:share"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:instagramauth"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.koin.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
