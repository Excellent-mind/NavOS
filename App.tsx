import React from "react";
import { Text, View } from "react-native";
import { StatusBar } from "expo-status-bar";
import { SafeAreaProvider } from "react-native-safe-area-context";

// TEMPORARY bisection round 7: round 6 (CrashGate + SafeAreaProvider +
// StatusBar together) crashed instantly, narrowing the culprit to one of
// those three. CrashGate is plain JS (a React error boundary + a pub/sub
// subscription, no native calls at all) so it's the least likely of the
// three to cause a NATIVE crash - this round removes it and keeps
// SafeAreaProvider + StatusBar, which both touch real native code (screen
// insets / system UI). If this still crashes, the culprit is one of these
// two (split again next). If it doesn't crash, the culprit was CrashGate
// itself (its componentDidMount/subscribe logic), which would be
// surprising but testable on its own next.
export default function App() {
  return (
    <SafeAreaProvider>
      <StatusBar style="light" />
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center", backgroundColor: "#14284B" }}>
        <Text style={{ color: "#fff", fontSize: 18 }}>
          NavOS round 7 test - SafeAreaProvider + StatusBar only, no CrashGate.
          If you can read this, it did not crash.
        </Text>
      </View>
    </SafeAreaProvider>
  );
}
