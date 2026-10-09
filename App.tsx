import React from "react";
import { Text, View } from "react-native";

// TEMPORARY bisection round 4: everything app-specific stripped out - no
// MapScreen, no CrashGate, no SafeAreaProvider, no expo-location, no
// routing/voice/navigationEngine imports at all. This tests the one thing
// rounds 1-3 couldn't: whether Expo SDK 55 / RN 0.83.6 / this pinned Hermes
// build simply doesn't boot on this device/OEM skin at all, independent of
// anything NavOS-specific. If this ALSO crashes identically, the cause is
// in the Expo/RN/Hermes version combination itself (or how EAS is building
// it), not in our code - and the fix is a version change, not more bisection
// of our own files. If this does NOT crash, the cause is still somewhere in
// our code/native deps, and we restore this file from git history and keep
// narrowing (crashBus/CrashGate/index.js, navigationEngine, routing, voice).
export default function App() {
  return (
    <View style={{ flex: 1, alignItems: "center", justifyContent: "center", backgroundColor: "#14284B" }}>
      <Text style={{ color: "#fff", fontSize: 18 }}>NavOS minimal test - if you can read this, it did not crash</Text>
    </View>
  );
}
