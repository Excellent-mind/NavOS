import React from "react";
import { CrashInfo, subscribe } from "../services/crashBus";
import { CrashScreen } from "./CrashScreen";

type State = { crash: CrashInfo | null };

/**
 * Wraps the whole app. Catches two kinds of JS-level failure that would
 * otherwise make the app just close with no explanation:
 *  1. Errors thrown during rendering (via getDerivedStateFromError /
 *     componentDidCatch - the standard React Error Boundary mechanism).
 *  2. Anything reported through crashBus - which index.js wires up to
 *     catch errors thrown at import time (before this component even
 *     exists) and to React Native's global uncaught-error handler.
 *
 * It does NOT intercept the error dialog that Android itself shows for a
 * genuinely native crash - only a JS error has a stack trace we can
 * actually display.
 */
export class CrashGate extends React.Component<{ children: React.ReactNode }, State> {
  state: State = { crash: null };
  private unsubscribe: (() => void) | null = null;

  componentDidMount() {
    this.unsubscribe = subscribe((crash) => {
      this.setState({ crash });
    });
  }

  componentWillUnmount() {
    this.unsubscribe?.();
  }

  static getDerivedStateFromError(error: Error): State {
    return { crash: { message: error.message, stack: error.stack, isFatal: true } };
  }

  render() {
    if (this.state.crash) {
      return (
        <CrashScreen
          info={this.state.crash}
          onDismiss={() => this.setState({ crash: null })}
        />
      );
    }
    return this.props.children;
  }
}
