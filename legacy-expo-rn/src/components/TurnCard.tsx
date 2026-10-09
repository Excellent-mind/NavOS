import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { NavigationState } from "../types";

type Props = {
  state: NavigationState;
};

/** Big top-of-screen "next turn" card, Waze/Google-Maps style. */
export function TurnCard({ state }: Props) {
  const step = state.route.legs[0].steps[state.currentStepIndex];

  return (
    <View style={styles.wrap}>
      <View style={styles.arrowBadge}>
        <Text style={styles.arrowGlyph}>{glyphFor(step.modifier, step.maneuver)}</Text>
      </View>
      <View style={styles.textCol}>
        <Text style={styles.distance}>{formatDistance(state.distanceToNextTurnMeters)}</Text>
        <Text style={styles.instruction} numberOfLines={2}>
          {step.instruction}
        </Text>
      </View>
    </View>
  );
}

function glyphFor(modifier?: string, maneuver?: string): string {
  if (maneuver === "arrive") return "\u{1F3C1}"; // checkered flag
  if (maneuver === "roundabout" || maneuver === "rotary") return "↻";
  switch (modifier) {
    case "left":
    case "sharp left":
      return "←";
    case "slight left":
      return "↖";
    case "right":
    case "sharp right":
      return "→";
    case "slight right":
      return "↗";
    case "uturn":
      return "↶";
    default:
      return "↑";
  }
}

export function formatDistance(meters: number): string {
  if (meters < 1000) return `${Math.round(meters / 10) * 10} m`;
  return `${(meters / 1000).toFixed(1)} km`;
}

const styles = StyleSheet.create({
  wrap: {
    position: "absolute",
    top: 54,
    left: 16,
    right: 16,
    backgroundColor: "#14284B",
    borderRadius: 18,
    padding: 16,
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    shadowColor: "#000",
    shadowOpacity: 0.25,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
    elevation: 6,
  },
  arrowBadge: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: "#1CB0F6",
    alignItems: "center",
    justifyContent: "center",
  },
  arrowGlyph: {
    fontSize: 28,
    color: "#fff",
    fontWeight: "700",
  },
  textCol: {
    flex: 1,
  },
  distance: {
    color: "#fff",
    fontSize: 22,
    fontWeight: "800",
  },
  instruction: {
    color: "#C9D6E8",
    fontSize: 15,
    fontWeight: "600",
    marginTop: 2,
  },
});
