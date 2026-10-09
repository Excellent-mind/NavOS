import React from "react";
import { StatusBar } from "expo-status-bar";
import { CrashGate } from "./src/components/CrashGate";
import { MapScreen } from "./src/screens/MapScreen";

// react-native-safe-area-context's <SafeAreaProvider> was confirmed, via
// device bisection, to crash this app instantly and natively on launch
// (rounds 6-8: SafeAreaProvider alone was sufficient to reproduce it). The
// package is removed from package.json entirely - MapScreen and its
// children now handle their own top/bottom spacing with fixed padding
// instead of real safe-area insets.
export default function App() {
  return (
    <CrashGate>
      <StatusBar style="light" />
      <MapScreen />
    </CrashGate>
  );
}
