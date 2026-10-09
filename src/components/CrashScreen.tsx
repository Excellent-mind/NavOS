import React from "react";
import { View, Text, ScrollView, Pressable, StyleSheet } from "react-native";
import { CrashInfo } from "../services/crashBus";

export function CrashScreen({
  info,
  onDismiss,
}: {
  info?: CrashInfo | null;
  onDismiss?: () => void;
}) {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>NavOS hit an error</Text>
      <Text style={styles.subtitle}>
        Nothing was sent anywhere. Just read what's below (or screenshot
        this screen) and share it.
      </Text>
      <ScrollView style={styles.scroll}>
        <Text selectable style={styles.mono}>
          {info?.message ??
            "(no JS error was captured - this means the crash happened before any app code could run, which usually points to a native/startup crash rather than a bug in the app's own code)"}
          {info?.stack ? `\n\n${info.stack}` : ""}
        </Text>
      </ScrollView>
      {onDismiss && (
        <Pressable style={styles.button} onPress={onDismiss}>
          <Text style={styles.buttonText}>Dismiss and continue</Text>
        </Pressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#1a0000", paddingTop: 56, paddingHorizontal: 16 },
  title: { color: "#fff", fontSize: 20, fontWeight: "700", marginBottom: 6 },
  subtitle: { color: "#ffb4b4", fontSize: 13, marginBottom: 16, lineHeight: 18 },
  scroll: { flex: 1 },
  mono: { color: "#ffd9d9", fontFamily: "monospace", fontSize: 12, lineHeight: 18 },
  button: {
    backgroundColor: "#E02B2B",
    borderRadius: 10,
    padding: 14,
    alignItems: "center",
    marginVertical: 16,
  },
  buttonText: { color: "#fff", fontWeight: "700" },
});
