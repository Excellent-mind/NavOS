Compile-only stand-ins for the OsmAnd and Material classes src/ calls. They
declare just the members used, with the app's real signatures (read from the
decoded smali), so javac can type-check. They are never packaged: only
classes under src/ are dexed into the APK, where the real classes exist.
