import { LatLng, Route, RouteStep } from "../types";
import { decodePolyline } from "./polyline";

type OsrmManeuver = {
  type: string;
  modifier?: string;
  location: [number, number]; // [lng, lat]
};

type OsrmStep = {
  maneuver: OsrmManeuver;
  distance: number;
  duration: number;
  name: string;
};

type OsrmLeg = {
  steps: OsrmStep[];
  distance: number;
  duration: number;
};

type OsrmRoute = {
  geometry: string;
  distance: number;
  duration: number;
  legs: OsrmLeg[];
};

/**
 * Calculates a driving route with turn-by-turn steps using the free public
 * OSRM demo server. Good for development and light use; the demo server is
 * rate-limited and not guaranteed uptime, so swap the BASE_URL for a
 * self-hosted OSRM instance (or a paid routing API) before real launch.
 */
const BASE_URL = "https://router.project-osrm.org";

export async function calculateRoute(
  origin: LatLng,
  destination: LatLng
): Promise<Route> {
  const coords = `${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}`;
  const url = `${BASE_URL}/route/v1/driving/${coords}?overview=full&geometries=polyline&steps=true&annotations=false`;

  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`Route request failed (${res.status})`);
  }
  const json: { code: string; routes: OsrmRoute[]; message?: string } =
    await res.json();

  if (json.code !== "Ok" || !json.routes?.length) {
    throw new Error(json.message || "No route found");
  }

  const r = json.routes[0];
  const geometry = decodePolyline(r.geometry);

  const legs = r.legs.map((leg) => ({
    steps: leg.steps.map(
      (step): RouteStep => ({
        instruction: buildInstruction(step),
        distanceMeters: step.distance,
        durationSeconds: step.duration,
        maneuver: step.maneuver.type,
        modifier: step.maneuver.modifier,
        roadName: step.name || undefined,
        coordinate: {
          latitude: step.maneuver.location[1],
          longitude: step.maneuver.location[0],
        },
      })
    ),
  }));

  return {
    geometry,
    distanceMeters: r.distance,
    durationSeconds: r.duration,
    legs,
  };
}

const MODIFIER_TEXT: Record<string, string> = {
  uturn: "make a U-turn",
  "sharp right": "turn sharp right",
  "sharp left": "turn sharp left",
  right: "turn right",
  left: "turn left",
  "slight right": "bear right",
  "slight left": "bear left",
  straight: "continue straight",
};

function buildInstruction(step: OsrmStep): string {
  const road = step.name ? ` onto ${step.name}` : "";
  const modifierText = step.maneuver.modifier
    ? MODIFIER_TEXT[step.maneuver.modifier] ?? `turn ${step.maneuver.modifier}`
    : undefined;

  switch (step.maneuver.type) {
    case "depart":
      return `Head out${road}`;
    case "arrive":
      return "Arrive at your destination";
    case "roundabout":
    case "rotary":
      return `Go through the roundabout${road}`;
    case "merge":
      return `Merge${road}`;
    case "on ramp":
      return `Take the ramp${road}`;
    case "off ramp":
      return `Take the exit${road}`;
    case "fork":
      return modifierText ? `Keep ${modifierText.replace("turn ", "")}${road}` : `Stay on the fork${road}`;
    case "end of road":
      return modifierText ? `${capitalize(modifierText)}${road}` : `Continue${road}`;
    case "new name":
      return `Continue${road}`;
    case "continue":
      return `Continue${road}`;
    case "turn":
    default:
      return modifierText ? `${capitalize(modifierText)}${road}` : `Continue${road}`;
  }
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
