import { LatLng } from "../types";

const EARTH_RADIUS_M = 6371000;

function toRad(deg: number): number {
  return (deg * Math.PI) / 180;
}

/** Great-circle distance between two points, in meters. */
export function distanceMeters(a: LatLng, b: LatLng): number {
  const dLat = toRad(b.latitude - a.latitude);
  const dLng = toRad(b.longitude - a.longitude);
  const lat1 = toRad(a.latitude);
  const lat2 = toRad(b.latitude);

  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** Compass bearing from a to b, in degrees (0 = north). */
export function bearingDegrees(a: LatLng, b: LatLng): number {
  const lat1 = toRad(a.latitude);
  const lat2 = toRad(b.latitude);
  const dLng = toRad(b.longitude - a.longitude);
  const y = Math.sin(dLng) * Math.cos(lat2);
  const x =
    Math.cos(lat1) * Math.sin(lat2) -
    Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
  const deg = (Math.atan2(y, x) * 180) / Math.PI;
  return ((deg % 360) + 360) % 360;
}

/** Distance (meters) from a point to the nearest point on a polyline, and
 * the index of that nearest segment's starting vertex. Used to detect
 * when the driver has drifted off the planned route. */
export function distanceToPolyline(
  point: LatLng,
  line: LatLng[]
): { distance: number; segmentIndex: number } {
  let best = Infinity;
  let bestIndex = 0;
  for (let i = 0; i < line.length - 1; i++) {
    const d = distanceToSegment(point, line[i], line[i + 1]);
    if (d < best) {
      best = d;
      bestIndex = i;
    }
  }
  return { distance: best, segmentIndex: bestIndex };
}

function distanceToSegment(p: LatLng, a: LatLng, b: LatLng): number {
  // Approximate by projecting in a local flat-earth frame; good enough at
  // city-block scale for off-route detection.
  const toXY = (pt: LatLng) => ({
    x: pt.longitude * Math.cos(toRad(a.latitude)),
    y: pt.latitude,
  });
  const pXY = toXY(p);
  const aXY = toXY(a);
  const bXY = toXY(b);

  const dx = bXY.x - aXY.x;
  const dy = bXY.y - aXY.y;
  const lengthSq = dx * dx + dy * dy;

  let t = lengthSq === 0 ? 0 : ((pXY.x - aXY.x) * dx + (pXY.y - aXY.y) * dy) / lengthSq;
  t = Math.max(0, Math.min(1, t));

  const closest: LatLng = {
    latitude: a.latitude + t * (b.latitude - a.latitude),
    longitude: a.longitude + t * (b.longitude - a.longitude),
  };
  return distanceMeters(p, closest);
}
