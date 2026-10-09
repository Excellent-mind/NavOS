import { useEffect, useRef, useState } from "react";
import { LatLng, NavigationState, Route } from "../types";
import { distanceMeters, distanceToPolyline } from "./geo";
import { speakInstruction, resetVoiceGuard } from "./voice";

const ADVANCE_STEP_METERS = 25; // close enough to a maneuver to call it "done"
const OFF_ROUTE_METERS = 40; // drifted this far from the line -> re-route
const ANNOUNCE_FAR_METERS = 300;
const ANNOUNCE_NEAR_METERS = 80;

/**
 * Drives turn-by-turn state from a calculated Route plus a stream of live
 * locations: which step you're on, distance to the next turn / destination,
 * off-route detection, and voice prompts at sensible distances.
 *
 * Steps[0] is "depart" and the last step is "arrive" (OSRM convention), so
 * currentStepIndex starts at 1 - the first real turn to look out for.
 */
export function useNavigationEngine(
  route: Route | null,
  location: LatLng | null
) {
  const [state, setState] = useState<NavigationState | null>(null);
  const stepIndexRef = useRef(1);

  useEffect(() => {
    stepIndexRef.current = 1;
    resetVoiceGuard();
    setState(null);
  }, [route]);

  useEffect(() => {
    if (!route || !location) return;
    const steps = route.legs[0]?.steps ?? [];
    if (steps.length < 2) return;

    let idx = Math.min(stepIndexRef.current, steps.length - 1);
    let distToTurn = distanceMeters(location, steps[idx].coordinate);

    // Advance past any steps we've effectively already reached (handles a
    // GPS jump skipping a short step, not just the normal one-at-a-time case).
    while (idx < steps.length - 1 && distToTurn < ADVANCE_STEP_METERS) {
      idx += 1;
      distToTurn = distanceMeters(location, steps[idx].coordinate);
    }
    stepIndexRef.current = idx;

    const remainingSteps = steps.slice(idx);
    const distanceRemainingMeters =
      distToTurn +
      remainingSteps.slice(1).reduce((sum, s) => sum + s.distanceMeters, 0);
    const durationRemainingSeconds = remainingSteps.reduce(
      (sum, s) => sum + s.durationSeconds,
      0
    );

    const { distance: offLineDistance } = distanceToPolyline(
      location,
      route.geometry
    );
    const offRoute = offLineDistance > OFF_ROUTE_METERS;

    setState({
      route,
      currentStepIndex: idx,
      distanceToNextTurnMeters: distToTurn,
      distanceRemainingMeters,
      durationRemainingSeconds,
      offRoute,
    });

    if (!offRoute) {
      announceIfNeeded(idx, distToTurn, steps[idx].instruction, steps.length);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [route, location]);

  return state;
}

function announceIfNeeded(
  stepIndex: number,
  distanceToTurn: number,
  instruction: string,
  totalSteps: number
) {
  const isArrival = stepIndex === totalSteps - 1;
  if (distanceToTurn <= ANNOUNCE_NEAR_METERS) {
    speakInstruction(`${stepIndex}-near`, instruction);
  } else if (distanceToTurn <= ANNOUNCE_FAR_METERS) {
    speakInstruction(
      `${stepIndex}-far`,
      `In ${Math.round(distanceToTurn / 10) * 10} meters, ${lowerFirst(instruction)}`
    );
  }
  if (isArrival && distanceToTurn <= ANNOUNCE_NEAR_METERS) {
    speakInstruction(`${stepIndex}-arrive`, "You have arrived at your destination");
  }
}

function lowerFirst(s: string): string {
  return s.charAt(0).toLowerCase() + s.slice(1);
}
