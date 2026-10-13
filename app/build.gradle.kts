plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
        versionCode = 1
        versionName = "0.1.0"

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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1") // launch splash screen (logo on black)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.3") // registerForActivityResult (permission request)
    implementation("androidx.drawerlayout:drawerlayout:1.2.0") // settings drawer

    // MapLibre's native Android SDK - the exact same engine
    // @maplibre/maplibre-react-native wrapped (confirmed from its own
    // android/gradle.properties: nativeVersion 13.6.1, nativeVariant
    // "opengl" -> artifact android-sdk-opengl), now used directly with no
    // JS bridge in between.
    implementation("org.maplibre.gl:android-sdk-opengl:13.6.1")

    // Android Auto (Car App Library) - lets NavOS run on the car screen.
    implementation("androidx.car.app:app:1.4.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.14.2")
}
