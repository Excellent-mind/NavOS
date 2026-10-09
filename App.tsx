import React from "react";
import { StatusBar } from "expo-status-bar";
import { SafeAreaProvider } from "react-native-safe-area-context";
import * as Sentry from "@sentry/react-native";
import { MapScreen } from "./src/screens/MapScreen";

// TEMPORARY: crash-diagnosis build. The app was crashing instantly on open
// with no visible error, which can't be debugged without a computer/adb -
// Sentry gives a real stack trace on a website instead.
// Replace this placeholder with the real DSN from sentry.io before building.
Sentry.init({
  dsn: "__NAVOS_SENTRY_DSN__",
  debug: true,
  tracesSampleRate: 0,
});

function App() {
  return (
    <SafeAreaProvider>
      <StatusBar style="light" />
      <MapScreen />
    </SafeAreaProvider>
  );
}

export default Sentry.wrap(App);
