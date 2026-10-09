package com.excellentmindblow.navos.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Native port of legacy-expo-rn/src/services/voice.ts - same "only speak
 * when the key changes" guard (NavigationEngine's onLocation fires several
 * times a second from GPS updates; without this every update would
 * re-speak the same instruction), backed by Android's built-in
 * TextToSpeech instead of expo-speech.
 */
class VoiceNarrator(context: Context) {
    private var ready = false
    private var lastSpokenKey: String? = null

    private val engine: TextToSpeech =
        TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
            }
        }

    fun speak(
        key: String,
        text: String,
    ) {
        if (key == lastSpokenKey) return
        lastSpokenKey = key
        if (!ready) return
        engine.language = Locale.US
        engine.setSpeechRate(0.95f)
        engine.setPitch(1.0f)
        engine.stop()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, key)
    }

    fun resetGuard() {
        lastSpokenKey = null
    }

    fun shutdown() {
        engine.stop()
        engine.shutdown()
    }
}
