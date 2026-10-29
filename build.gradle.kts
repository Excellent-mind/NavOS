plugins {
    id("com.android.application") version "8.7.2" apply false
    // MapLibre's android-sdk-opengl 13.6.1 was compiled with Kotlin 2.2.0
    // (its .kotlin_module metadata requires it) - 2.0.21 fails with
    // "Module was compiled with an incompatible version of Kotlin".
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    // Jetpack Compose compiler (ships with Kotlin 2.x, same version).
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
