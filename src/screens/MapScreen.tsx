import React, { useCallback, useMemo, useState } from "react";
import { ActivityIndicator, StyleSheet, Text, View } from "react-native";
import MapLibreGL from "@maplibre/maplibre-react-native";
import { useLiveLocation } from "../services/useLiveLocation";
import { calculateRoute } from "../services/routing";
import { useNavigationEngine } from "../services/navigationEngine";
import { stopSpeaking } from "../services/voice";
import { GeocodeResult, LatLng, Route } from "../types";
import { SearchBar } from "../components/SearchBar";
import { TurnCard } from "../components/TurnCard";
import { TripSummaryBar } from "../components/TripSummaryBar";

// OpenFreeMap's hosted "liberty" style: free, no API key, no rate limit -
// unlike OSRM/Nominatim above this is explicitly meant for production use.
// https://openfreemap.org
const MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";

function toLngLat(p: LatLng): [number, number] {
  return [p.longitude, p.latitude];
}

export function MapScreen() {
  const { location, permissionDenied } = useLiveLocation();
  const [route, setRoute] = useState<Route | null>(null);
  const [destinationLabel, setDestinationLabel] = useState<string | null>(null);
  const [routing, setRouting] = useState(false);
  const [routeError, setRouteError] = useState<string | null>(null);

  const navState = useNavigationEngine(route, location);

  const handleSelectDestination = useCallback(
    async (result: GeocodeResult) => {
      if (!location) return;
      setRouting(true);
      setRouteError(null);
      try {
        const r = await calculateRoute(location, result.coordinate);
        setRoute(r);
        setDestinationLabel(result.label);
      } catch (e) {
        setRouteError(e instanceof Error ? e.message : "Couldn't calculate a route");
      } finally {
        setRouting(false);
      }
    },
    [location]
  );

  const handleStop = useCallback(() => {
    stopSpeaking();
    setRoute(null);
    setDestinationLabel(null);
    setRouteError(null);
  }, []);

  const routeGeoJson = useMemo(() => {
    if (!route) return null;
    return {
      type: "Feature" as const,
      properties: {},
      geometry: {
        type: "LineString" as const,
        coordinates: route.geometry.map(toLngLat),
      },
    };
  }, [route]);

  if (permissionDenied) {
    return (
      <View style={styles.center}>
        <Text style={styles.permissionText}>
          NavOS needs location access to show your position and give directions.
          Please enable location permission in your device settings.
        </Text>
      </View>
    );
  }

  if (!location) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#1CB0F6" />
        <Text style={styles.loadingText}>Finding your location…</Text>
      </View>
    );
  }

  const isNavigating = !!(route && navState);
  const destination = route?.geometry[route.geometry.length - 1] ?? null;

  return (
    <View style={styles.flex}>
      <MapLibreGL.MapView style={styles.flex} styleURL={MAP_STYLE_URL} logoEnabled={false}>
        <MapLibreGL.Camera
          zoomLevel={15}
          centerCoordinate={toLngLat(location)}
          followUserLocation={isNavigating}
          followUserMode="course"
          followZoomLevel={17}
          animationMode="flyTo"
          animationDuration={600}
        />
        <MapLibreGL.UserLocation visible showsUserHeadingIndicator />

        {routeGeoJson && (
          <MapLibreGL.ShapeSource id="routeSource" shape={routeGeoJson}>
            <MapLibreGL.LineLayer
              id="routeLine"
              style={{
                lineColor: "#1CB0F6",
                lineWidth: 6,
                lineCap: "round",
                lineJoin: "round",
              }}
            />
          </MapLibreGL.ShapeSource>
        )}

        {destination && (
          <MapLibreGL.PointAnnotation id="destinationMarker" coordinate={toLngLat(destination)}>
            <View style={styles.destinationPin} />
          </MapLibreGL.PointAnnotation>
        )}
      </MapLibreGL.MapView>

      {!isNavigating && <SearchBar origin={location} onSelect={handleSelectDestination} />}

      {routing && (
        <View style={styles.routingBanner}>
          <ActivityIndicator size="small" color="#fff" />
          <Text style={styles.routingText}>Calculating route…</Text>
        </View>
      )}

      {routeError && (
        <View style={[styles.routingBanner, styles.errorBanner]}>
          <Text style={styles.routingText}>{routeError}</Text>
        </View>
      )}

      {navState && (
        <>
          <TurnCard state={navState} />
          <TripSummaryBar state={navState} onStop={handleStop} />
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  center: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    padding: 32,
    backgroundColor: "#14284B",
  },
  loadingText: {
    color: "#fff",
    marginTop: 12,
    fontSize: 15,
  },
  permissionText: {
    color: "#fff",
    fontSize: 16,
    textAlign: "center",
    lineHeight: 22,
  },
  routingBanner: {
    position: "absolute",
    top: 54,
    left: 16,
    right: 16,
    backgroundColor: "#14284B",
    borderRadius: 14,
    paddingVertical: 12,
    paddingHorizontal: 16,
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  errorBanner: {
    backgroundColor: "#E02B2B",
  },
  routingText: {
    color: "#fff",
    fontWeight: "600",
  },
  destinationPin: {
    width: 20,
    height: 20,
    borderRadius: 10,
    backgroundColor: "#FF9600",
    borderWidth: 3,
    borderColor: "#fff",
  },
});
