import { useEffect, useRef, useState } from "react";
import * as Location from "expo-location";
import { LatLng } from "../types";

export type LiveLocation = LatLng & {
  heading: number | null;
  speedMetersPerSecond: number | null;
};

/**
 * Requests foreground location permission and streams the device's live
 * position. Returns null until permission is granted and a first fix
 * arrives, plus a permissionDenied flag the UI can use to show guidance.
 */
export function useLiveLocation() {
  const [location, setLocation] = useState<LiveLocation | null>(null);
  const [permissionDenied, setPermissionDenied] = useState(false);
  const subscriptionRef = useRef<Location.LocationSubscription | null>(null);

  useEffect(() => {
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
