plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.excellentmindblow.navos"
    // Deliberately NOT the newest compile/target SDK: the previous React
    // Native build (targetSdkVersion 36) hit a real crash tied to Android
    // 15+'s mandatory edge-to-edge enforcement on this device's Android
    // skin. Targeting 34 (Android 14) avoids that enforcement entirely
    // while still comfortably covering modern devices.
    compileSdk = 34

    defaultConfig {
        applicationId = "com.excellentmindblow.navos"
        minSdk = 24
        targetSdk = 34
        versionCode = 9
        versionName = "0.9.0"

        // The unrestricted APK bundles all 4 ABIs' libmaplibre.so (arm64,
        // armv7, x86, x86_64 - including two desktop/emulator-only
        // architectures no real phone uses), which blew past the 30MB
        // chat upload limit. The test device (Snapdragon 6 Gen 3) is
        // arm64-v8a, so that's the only one needed for now - this cuts
        // the APK from ~46MB to a fraction of that. Re-widen before any
        // real release to support older 32-bit devices too.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // Release signing. The keystore and its passwords come from environment
    // variables (set by the GitHub Actions release workflow from your repo
    // secrets), so no secret is ever stored in the code.
    signingConfigs {
        create("release") {
            val storePath = System.getenv("KEYSTORE_PATH")
            if (storePath != null && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Shrink the debug APK too (Compose + MapLibre unshrunk is ~33 MB, over the 30 MB
        // chat upload limit). The app's own classes are kept as they are (proguard-rules.pro).
        debug {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3") // registerForActivityResult (permission request)

    // Jetpack Compose (the UI). BOM 2024.09 = Compose 1.7, fits compileSdk 34.
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // MapLibre's native Android SDK - the exact same engine
    // @maplibre/maplibre-react-native wrapped (confirmed from its own
    // android/gradle.properties: nativeVersion 13.6.1, nativeVariant
    // "opengl" -> artifact android-sdk-opengl), now used directly with no
    // JS bridge in between.
    implementation("org.maplibre.gl:android-sdk-opengl:13.6.1")


    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.14.2")
    // Real org.json for JVM unit tests (the Android stub throws "not mocked").
    testImplementation("org.json:json:20240303")
}
