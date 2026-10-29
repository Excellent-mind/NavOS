package com.excellentmindblow.navos.navigation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.excellentmindblow.navos.Lang
import java.util.Locale

/**
 * Native port of the original React Native app's voice.ts - same "only speak
 * when the key changes" guard (NavigationEngine's onLocation fires several
 * times a second from GPS updates; without this every update would
 * re-speak the same instruction), backed by Android's built-in
 * TextToSpeech instead of expo-speech.
 *
 * Speaks as navigation guidance and asks for transient "may duck" audio
 * focus, so music or a podcast is turned down while an instruction plays
 * and comes back afterwards - the way Waze and Google Maps behave.
 *
 * All engine and audio-focus calls run on a private background thread.
 * They are synchronous calls into other processes (setLanguage alone pulls
 * and deserialises the full installed-voice list), and speak() is called
 * from the GPS callback on the main thread - doing them there froze the
 * map and drive bar for seconds at a time on a slow device.
 */
class VoiceNarrator(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    @Volatile private var ready = false
    private var lastSpokenKey: String? = null
    private var focusRequest: AudioFocusRequest? = null

    private val voiceThread = HandlerThread("navos-voice").apply { start() }
    private val voiceHandler = Handler(voiceThread.looper)

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
                // Voice, rate and listener are set once here rather than
                // before every instruction.
                voiceHandler.post {
                    applyLanguage()
                    engine.setSpeechRate(0.95f)
                    engine.setPitch(1.0f)
                    engine.setAudioAttributes(guidanceAttributes)
                    engine.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}

                            override fun onDone(utteranceId: String?) {
                                voiceHandler.post { abandonFocus() }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                voiceHandler.post { abandonFocus() }
                            }
                        },
                    )
                    ready = true
                }
            }
        }

    /** Speaks Malay with a Malay voice, else the close Indonesian one, else English. */
    fun applyLanguage() {
        voiceHandler.post {
            val wanted = if (Lang.malay) listOf(Locale("ms", "MY"), Locale("id", "ID")) else emptyList()
            val voice = wanted.firstOrNull { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE } ?: Locale.US
            engine.language = voice
        }
    }

    fun speak(
        key: String,
        text: String,
    ) {
        if (key == lastSpokenKey) return
        lastSpokenKey = key
        if (!ready || !enabled) return
        voiceHandler.post {
            engine.stop()
            requestFocus()
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, key)
        }
    }

    fun resetGuard() {
        lastSpokenKey = null
    }

    fun shutdown() {
        voiceHandler.post {
            engine.stop()
            engine.shutdown()
            abandonFocus()
            voiceThread.quitSafely()
        }
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
