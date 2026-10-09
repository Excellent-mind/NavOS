import * as Speech from "expo-speech";

/**
 * Turn-by-turn voice announcements. Keeps a tiny "last spoken" guard so the
 * same instruction isn't repeated every location update (GPS fires several
 * times a second) — callers pass a stable key (e.g. the step index) and we
 * only speak when that key changes.
 */
let lastSpokenKey: string | null = null;

export function speakInstruction(key: string, text: string) {
  if (key === lastSpokenKey) return;
  lastSpokenKey = key;
  Speech.stop();
  Speech.speak(text, { language: "en-US", pitch: 1, rate: 0.95 });
}

export function resetVoiceGuard() {
  lastSpokenKey = null;
}

export function stopSpeaking() {
  Speech.stop();
}
