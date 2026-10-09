import React from "react";
import { Text, View } from "react-native";
import { StatusBar } from "expo-status-bar";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { CrashGate } from "./src/components/CrashGate";

// TEMPORARY bisection round 6: round 4 proved that a bare View+Text screen
// does NOT crash, even with expo-location/expo-speech/expo-dev-client/
// safe-area-context still linked (just unused) - so autolinking those
// packages is not the cause. The crash only happens somewhere in the real
// render tree. This round adds back CrashGate, SafeAreaProvider and
// StatusBar (the three wrappers every screen renders through) around the
// same simple text, WITHOUT MapScreen's own content (navigationEngine,
// routing, voice, SearchBar, TurnCard, TripSummaryBar). If this crashes,
// the culprit is CrashGate or SafeAreaProvider. If it doesn't, the culprit
// is specifically inside MapScreen's own subtree.
export default function App() {
  return (
    <CrashGate>
      <SafeAreaProvider>
        <StatusBar style="light" />
        <View style={{ flex: 1, alignItems: "center", justifyContent: "center", backgroundColor: "#14284B" }}>
          <Text style={{ color: "#fff", fontSize: 18 }}>
            NavOS round 6 test - CrashGate + SafeAreaProvider + StatusBar only.
            If you can read this, it did not crash.
          </Text>
        </View>
      </SafeAreaProvider>
    </CrashGate>
  );
}
