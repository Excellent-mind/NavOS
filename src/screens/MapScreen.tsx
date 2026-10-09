import React, { useCallback, useState } from "react";
import { ActivityIndicator, StyleSheet, Text, View } from "react-native";
import MapView, { Marker, Polyline, PROVIDER_GOOGLE } from "react-native-maps";
import { useLiveLocation } from "../services/useLiveLocation";
import { calculateRoute } from "../services/routing";
import { useNavigationEngine } from "../services/navigationEngine";
import { stopSpeaking } from "../services/voice";
import { GeocodeResult, Route } from "../types";
import { SearchBar } from "../components/SearchBar";
import { TurnCard } from "../components/TurnCard";
import { TripSummaryBar } from "../components/TripSummaryBar";

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

  return (
    <View style={styles.flex}>
      <MapView
        style={styles.flex}
        provider={PROVIDER_GOOGLE}
        showsUserLocation
        showsMyLocationButton
        followsUserLocation={isNavigating}
        initialRegion={{
          latitude: location.latitude,
          longitude: location.longitude,
          latitudeDelta: 0.02,
          longitudeDelta: 0.02,
        }}
      >
        {route && (
          <Polyline
            coordinates={route.geometry}
            strokeColor="#1CB0F6"
            strokeWidth={6}
          />
        )}
        {route && (
          <Marker
            coordinate={route.geometry[route.geometry.length - 1]}
            title={destinationLabel ?? "Destination"}
            pinColor="#FF9600"
          />
        )}
      </MapView>

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
});
