import { GeocodeResult, LatLng } from "../types";

/**
 * Destination search, using OpenStreetMap's free Nominatim geocoder.
 *
 * Nominatim's public instance is rate-limited (~1 req/sec) and asks for a
 * descriptive User-Agent — fine for development and small-scale use. For
 * production traffic, self-host Nominatim or switch to a paid geocoder
 * (Mapbox Geocoding API, Google Places) by swapping this one function.
 */
export async function searchPlaces(
  query: string,
  biasCenter?: LatLng
): Promise<GeocodeResult[]> {
  if (!query.trim()) return [];

  const params = new URLSearchParams({
    q: query,
    format: "jsonv2",
    addressdetails: "0",
    limit: "6",
    countrycodes: "my", // bias to Malaysia, matching the app's focus
  });
  if (biasCenter) {
    // Nominatim viewbox: left,top,right,bottom - use a rough +-1 degree box
    const { latitude, longitude } = biasCenter;
    params.set(
      "viewbox",
      `${longitude - 1},${latitude + 1},${longitude + 1},${latitude - 1}`
    );
    params.set("bounded", "0");
  }

  const res = await fetch(
    `https://nominatim.openstreetmap.org/search?${params.toString()}`,
    {
      headers: {
        "User-Agent": "NavOS/0.1 (navigation app; contact: excellentmindblow@gmail.com)",
      },
    }
  );
  if (!res.ok) {
    throw new Error(`Place search failed (${res.status})`);
  }
  const json: Array<{ display_name: string; lat: string; lon: string }> =
    await res.json();

  return json.map((r) => ({
    label: r.display_name,
    coordinate: { latitude: parseFloat(r.lat), longitude: parseFloat(r.lon) },
  }));
}
