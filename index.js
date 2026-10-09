// Custom entry point, replacing Expo's default node_modules/expo/AppEntry.js
// (the standard, documented way to customize Expo's startup - see
// https://docs.expo.dev/bare/installing-expo-modules/#custom-entry-point ).
//
// The app was closing instantly on open with no error dialog at all. Two
// of the ways that happens in JS land are:
//   1. A module throwing while it's first being imported/evaluated -
//      before React, before any error boundary, before anything.
//   2. An uncaught error reaching React Native's global error handler,
//      which by default can terminate the app without ever showing React
//      UI.
// Both are handled here, with no account, no network and no extra
// dependency - the error just gets displayed on screen instead.

import { registerRootComponent } from "expo";
import React from "react";
import { reportCrash } from "./src/services/crashBus";
import { CrashScreen } from "./src/components/CrashScreen";

// eslint-disable-next-line no-undef
const errorUtils = typeof ErrorUtils !== "undefined" ? ErrorUtils : null;
if (errorUtils) {
  errorUtils.setGlobalHandler((error, isFatal) => {
    reportCrash({
      message: error?.message ?? String(error),
      stack: error?.stack,
      isFatal,
    });
    // Deliberately not forwarding to the previous/default handler: that
    // default behavior is what was making the app disappear with no
    // explanation. Showing the error on screen instead is strictly more
    // useful while we're diagnosing this.
  });
}

let RootComponent;
try {
  RootComponent = require("./App").default;
} catch (error) {
  reportCrash({
    message: error?.message ?? String(error),
    stack: error?.stack,
    isFatal: true,
  });
  RootComponent = function CrashFallback() {
    return React.createElement(CrashScreen);
  };
}

registerRootComponent(RootComponent);
