import React, { useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { GeocodeResult, LatLng } from "../types";
import { searchPlaces } from "../services/geocoding";

type Props = {
  origin: LatLng | null;
  onSelect: (result: GeocodeResult) => void;
};

let searchTimer: ReturnType<typeof setTimeout> | null = null;

export function SearchBar({ origin, onSelect }: Props) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<GeocodeResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);

  function handleChange(text: string) {
    setQuery(text);
    setOpen(true);
    if (searchTimer) clearTimeout(searchTimer);

    if (!text.trim()) {
      setResults([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    searchTimer = setTimeout(async () => {
      try {
        const r = await searchPlaces(text, origin ?? undefined);
        setResults(r);
      } catch {
        setResults([]);
      } finally {
        setLoading(false);
      }
    }, 400); // debounce so we don't hammer Nominatim's rate limit
  }

  function handleSelect(result: GeocodeResult) {
    setQuery(result.label);
    setOpen(false);
    setResults([]);
    onSelect(result);
  }

  return (
    <View style={styles.wrap}>
      <View style={styles.inputRow}>
        <TextInput
          style={styles.input}
          placeholder="Where to?"
          placeholderTextColor="#8294AC"
          value={query}
          onChangeText={handleChange}
          onFocus={() => setOpen(true)}
          returnKeyType="search"
        />
        {loading && <ActivityIndicator size="small" color="#1CB0F6" />}
      </View>

      {open && results.length > 0 && (
        <FlatList
          style={styles.results}
          data={results}
          keyExtractor={(item, i) => `${item.coordinate.latitude},${item.coordinate.longitude},${i}`}
          keyboardShouldPersistTaps="handled"
          renderItem={({ item }) => (
            <Pressable style={styles.resultRow} onPress={() => handleSelect(item)}>
              <Text style={styles.resultText} numberOfLines={2}>
                {item.label}
              </Text>
            </Pressable>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    position: "absolute",
    top: 54,
    left: 16,
    right: 16,
  },
  inputRow: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#fff",
    borderRadius: 14,
    paddingHorizontal: 14,
    height: 50,
    shadowColor: "#000",
    shadowOpacity: 0.15,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 },
    elevation: 4,
    gap: 8,
  },
  input: {
    flex: 1,
    fontSize: 16,
    color: "#14284B",
  },
  results: {
    marginTop: 8,
    backgroundColor: "#fff",
    borderRadius: 14,
    maxHeight: 260,
    shadowColor: "#000",
    shadowOpacity: 0.15,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 },
    elevation: 4,
  },
  resultRow: {
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: "#EEF2F7",
  },
  resultText: {
    fontSize: 14,
    color: "#14284B",
  },
});
