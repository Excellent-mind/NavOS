# NavOS: keep the app's own classes as written (engine + UI); let R8 shrink the libraries.
-keep class com.excellentmindblow.navos.** { *; }
-dontwarn org.maplibre.**
