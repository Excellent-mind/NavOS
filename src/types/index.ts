export type LatLng = {
  latitude: number;
  longitude: number;
};

/** One step of a calculated route (maps to an OSRM "step"). */
export type RouteStep = {
  instruction: string;
  distanceMeters: number;
  durationSeconds: number;
  maneuver: string; // e.g. "turn", "roundabout", "arrive"
  modifier?: string; // e.g. "left", "right", "slight left", "straight"
  roadName?: string;
  coordinate: LatLng;
};

export type RouteLeg = {
  steps: RouteStep[];
};

export type Route = {
  geometry: LatLng[]; // full polyline, for drawing on the map
  distanceMeters: number;
  durationSeconds: number;
  legs: RouteLeg[];
};

export type GeocodeResult = {
  label: string;
  coordinate: LatLng;
};

export type NavigationState = {
  route: Route;
  currentStepIndex: number;
  distanceToNextTurnMeters: number;
  distanceRemainingMeters: number;
  durationRemainingSeconds: number;
  offRoute: boolean;
};
