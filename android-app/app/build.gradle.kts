plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.remotemc.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.remotemc.app"
        minSdk = 26
        targetSdk = 36
        // Release builds get the version from the git tag (-Prmc.version=1.0.0).
        val version = (project.findProperty("rmc.version") as String?) ?: "1.0.0"
        val (major, minor, patch) = (version.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 } + listOf(0, 0, 0))
        versionName = version
        versionCode = major * 10_000 + minor * 100 + patch
    }

    signingConfigs {
        // CI passes the release keystore via environment variables (see .github/workflows/release.yml).
        create("release") {
            val keystore = System.getenv("RMC_KEYSTORE_FILE")
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("RMC_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RMC_KEY_ALIAS")
                keyPassword = System.getenv("RMC_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("RMC_KEYSTORE_FILE") != null) {
                signingConfigs.getByName("release")
            } else {
                // Local builds without a keystore stay installable.
                signingConfigs.getByName("debug")
            }
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
        buildConfig = true
    }
    lint {
        // Adaptive icons must stay in mipmap-anydpi-v26 for aapt. Version update checks depend on the
        // day the build runs; Dependabot (.github/dependabot.yml) handles updates instead.
        disable += setOf("ObsoleteSdkInt", "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
        // API 36 meets Google Play's current requirement; moving to 37 needs Android Gradle Plugin 9.
        informational += "OldTargetApi"
        warningsAsErrors = true
        abortOnError = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "2g"
            // Screenshots: record with -Proborazzi.test.record=true, check with -Proborazzi.test.verify=true
            it.systemProperty("roborazzi.test.record", project.findProperty("roborazzi.test.record") ?: "false")
            it.systemProperty("roborazzi.test.compare", project.findProperty("roborazzi.test.compare") ?: "false")
            it.systemProperty("roborazzi.test.verify", project.findProperty("roborazzi.test.verify") ?: "false")
            project.findProperty("rmc.it.link")?.let { link -> it.systemProperty("rmc.it.link", link) }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0") // per-app language API on Android < 13
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // QR scanner: CameraX + ML Kit with the bundled model (works without Google Play services)
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
