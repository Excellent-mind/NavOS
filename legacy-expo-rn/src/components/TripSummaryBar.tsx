import React from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { NavigationState } from "../types";
import { formatDistance } from "./TurnCard";

type Props = {
  state: NavigationState;
  onStop: () => void;
};

/** Bottom bar: ETA, remaining time/distance, and a Stop button. */
export function TripSummaryBar({ state, onStop }: Props) {
  const eta = new Date(Date.now() + state.durationRemainingSeconds * 1000);
  const etaLabel = eta.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

  return (
    <View style={styles.wrap}>
      <View>
        <Text style={styles.eta}>{etaLabel}</Text>
        <Text style={styles.sub}>
          {formatMinutes(state.durationRemainingSeconds)} · {formatDistance(state.distanceRemainingMeters)}
        </Text>
      </View>
      <Pressable style={styles.stopBtn} onPress={onStop}>
        <Text style={styles.stopText}>Stop</Text>
      </Pressable>
    </View>
  );
}

function formatMinutes(seconds: number): string {
  const mins = Math.max(1, Math.round(seconds / 60));
  if (mins < 60) return `${mins} min`;
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return `${h} hr ${m} min`;
}

const styles = StyleSheet.create({
  wrap: {
    position: "absolute",
    left: 16,
    right: 16,
    bottom: 32,
    backgroundColor: "#fff",
    borderRadius: 18,
    paddingVertical: 14,
    paddingHorizontal: 18,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    shadowColor: "#000",
    shadowOpacity: 0.18,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
    elevation: 6,
  },
  eta: {
    fontSize: 20,
    fontWeight: "800",
    color: "#14284B",
  },
  sub: {
    fontSize: 13,
    fontWeight: "600",
    color: "#5B6B82",
    marginTop: 2,
  },
  stopBtn: {
    backgroundColor: "#FFECEC",
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 99,
  },
  stopText: {
    color: "#E02B2B",
    fontWeight: "700",
    fontSize: 14,
  },
});
