plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.secrets)
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
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
        // Required for the secrets-gradle-plugin below: it writes the Places API key via
        // Variant.buildConfigFields, which AGP rejects at configuration time unless this is on
        // (AGP 8 defaults buildConfig generation to off).
        buildConfig = true
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

// Reads the Places API key from `secrets.properties` (gitignored, developer-local) with
// `local.defaults.properties` (committed placeholder) as the fallback so a clean checkout still
// compiles - see app/local.defaults.properties and the README for setup instructions. Generates
// `BuildConfig.PLACES_API_KEY`.
secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "local.defaults.properties"
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
    implementation(project(":feature:maps"))
    implementation(project(":feature:geocoding"))
    implementation(project(":feature:videoprocessing"))
    implementation(project(":feature:share"))

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
