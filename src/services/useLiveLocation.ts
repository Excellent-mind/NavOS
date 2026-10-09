import { useEffect, useRef, useState } from "react";
import * as Location from "expo-location";
import { LatLng } from "../types";

export type LiveLocation = LatLng & {
  heading: number | null;
  speedMetersPerSecond: number | null;
};

// TEMPORARY bisection round 9: the real build (MapLibre + real GPS, no
// SafeAreaProvider) still crashed. That build changed several things at
// once - this isolates one: fake the location so expo-location's real
// native permission/GPS calls never run, while the real MapLibre <Map>
// view still renders. If this build does NOT crash, the real GPS call is
// the (additional) culprit. If it STILL crashes, MapLibre's real map view
// is the (additional) culprit.
const DEBUG_FAKE_LOCATION = true;
const FAKE_LOCATION: LiveLocation = {
  latitude: 3.139,
  longitude: 101.6869,
  heading: 0,
  speedMetersPerSecond: 0,
};

/**
 * Requests foreground location permission and streams the device's live
 * position. Returns null until permission is granted and a first fix
 * arrives, plus a permissionDenied flag the UI can use to show guidance.
 */
export function useLiveLocation() {
  const [location, setLocation] = useState<LiveLocation | null>(
    DEBUG_FAKE_LOCATION ? FAKE_LOCATION : null
  );
  const [permissionDenied, setPermissionDenied] = useState(false);
  const subscriptionRef = useRef<Location.LocationSubscription | null>(null);

  useEffect(() => {
    if (DEBUG_FAKE_LOCATION) return;
    let cancelled = false;

    (async () => {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== "granted") {
        if (!cancelled) setPermissionDenied(true);
        return;
      }

      const sub = await Location.watchPositionAsync(
        {
          accuracy: Location.Accuracy.BestForNavigation,
          timeInterval: 1000,
          distanceInterval: 3,
        },
        (update) => {
          if (cancelled) return;
          setLocation({
            latitude: update.coords.latitude,
            longitude: update.coords.longitude,
            heading: update.coords.heading,
            speedMetersPerSecond: update.coords.speed,
          });
        }
      );
      subscriptionRef.current = sub;
    })();

    return () => {
      cancelled = true;
      subscriptionRef.current?.remove();
    };
  }, []);

  return { location, permissionDenied };
}
