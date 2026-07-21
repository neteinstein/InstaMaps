plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

android {
    namespace = "org.neteinstein.instamaps.core.update"
    compileSdk = 36

    defaultConfig {
        minSdk = 27
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

    // androidx.core (pulled in transitively by core-ktx) provides FileProvider, used by
    // AppUpdateInstaller to hand a downloaded APK to the system Package Installer.
    implementation(libs.androidx.core.ktx)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.core)
    testImplementation(libs.turbine)
    // android.jar's org.json classes are compile-only stubs (real bodies throw/return defaults
    // under `unitTests.isReturnDefaultValues`) - GitHubUpdateRepositoryTest exercises real
    // JSONObject/JSONArray parsing, so it needs a real desktop implementation of the same org.json
    // package on the unit test runtime classpath to actually work (see core:history's build.gradle.kts).
    testImplementation(libs.json)
}
