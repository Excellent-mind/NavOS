package com.excellentmindblow.navos.navigation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Native port of legacy-expo-rn/src/services/voice.ts - same "only speak
 * when the key changes" guard (NavigationEngine's onLocation fires several
 * times a second from GPS updates; without this every update would
 * re-speak the same instruction), backed by Android's built-in
 * TextToSpeech instead of expo-speech.
 *
 * Speaks as navigation guidance and asks for transient "may duck" audio
 * focus, so music or a podcast is turned down while an instruction plays
 * and comes back afterwards - the way Waze and Google Maps behave.
 */
class VoiceNarrator(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var ready = false
    private var lastSpokenKey: String? = null
    private var listenerSet = false
    private var focusRequest: AudioFocusRequest? = null

    private val guidanceAttributes: AudioAttributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    /** Settings-drawer "Voice guidance" toggle reads/writes this. */
    var enabled = true

    private val engine: TextToSpeech =
        TextToSpeech(appContext) { status ->
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
        if (!ready || !enabled) return
        if (!listenerSet) {
            listenerSet = true
            engine.setAudioAttributes(guidanceAttributes)
            engine.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        abandonFocus()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        abandonFocus()
                    }
                },
            )
        }
        engine.language = Locale.US
        engine.setSpeechRate(0.95f)
        engine.setPitch(1.0f)
        engine.stop()
        requestFocus()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, key)
    }

    fun resetGuard() {
        lastSpokenKey = null
    }

    fun shutdown() {
        engine.stop()
        engine.shutdown()
        abandonFocus()
    }

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request =
                focusRequest
                    ?: AudioFocusRequest
                        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(guidanceAttributes)
                        .build()
                        .also { focusRequest = it }
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}
