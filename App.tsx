import React from "react";
import { StatusBar } from "expo-status-bar";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { CrashGate } from "./src/components/CrashGate";
import { MapScreen } from "./src/screens/MapScreen";

export default function App() {
  return (
    <CrashGate>
      <SafeAreaProvider>
        <StatusBar style="light" />
        <MapScreen />
      </SafeAreaProvider>
    </CrashGate>
  );
}
