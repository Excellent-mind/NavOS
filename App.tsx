import React from "react";
import { Text, View } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";

// TEMPORARY bisection round 8: round 7 (SafeAreaProvider + StatusBar, no
// CrashGate) still crashed instantly, narrowing the culprit to one of
// those two. react-native-safe-area-context has documented real crashes
// around Android 15+'s mandatory edge-to-edge enforcement (WindowInsets
// handling) on some OEM Android skins - and this build's
// targetSdkVersion=36 makes edge-to-edge mandatory, which is a concrete,
// version-matched reason to suspect it over expo-status-bar. This round
// drops StatusBar and keeps SafeAreaProvider alone. If it still crashes,
// SafeAreaProvider is confirmed as sufficient on its own. If it doesn't,
// StatusBar was needed too (either alone, or only in combination).
export default function App() {
  return (
    <SafeAreaProvider>
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center", backgroundColor: "#14284B" }}>
        <Text style={{ color: "#fff", fontSize: 18 }}>
          NavOS round 8 test - SafeAreaProvider only, no StatusBar, no CrashGate.
          If you can read this, it did not crash.
        </Text>
      </View>
    </SafeAreaProvider>
  );
}
