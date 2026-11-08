package app.upmile.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.upmile.core.voice.SpeechEngine
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Korean on-device Pleos TTS that ducks other audio (music) while speaking. Queueing, priority and timeout live in
 * QueuedVoice; this only reports done once per speak. SDK callbacks may arrive on binder threads.
 */
class PleosVoice(context: Context) : SpeechEngine {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs).build()
    private val logged: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val state = MutableStateFlow(false)
    private val main = Handler(Looper.getMainLooper())
    private val tracker = LineTracker(SystemClock::uptimeMillis) { delay, task ->
        val r = Runnable(task); main.postDelayed(r, delay); { main.removeCallbacks(r) }
    }
    @Volatile private var tts: TextToSpeech? = null

    override val ready: StateFlow<Boolean> = state.asStateFlow()

    private inline fun guard(name: String, onFail: (Throwable) -> Unit = {}, block: () -> Unit) =
        guarded(TAG, logged, name, onFail, block)

    private val listener = object : EventListener {
        override fun onReady() { Log.d(TAG, "onReady"); state.value = true }
        override fun onStart() { Log.d(TAG, "onStart") }
        override fun onDone() { Log.d(TAG, "onDone"); tracker.sdkDone() }
        override fun onStop() { Log.d(TAG, "onStop") } // stop() already ended the line
        override fun onError(errorType: ErrorType) { Log.w(TAG, "tts error $errorType"); tracker.sdkDone() }
        override fun onUpdatedRms(rms: Double) = Unit
    }

    init {
        guard("init", { state.value = false }) {
            val t = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
            tts = t
            // The SDK only accepts listeners and settings once initialize has succeeded.
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    Log.d(TAG, "initialize ok")
                    guard("configure", { state.value = false }) {
                        t.addEventListener(listener)
                        t.setAudioAttributes(attrs)
                        t.setLocale(Locale.KOREA)
                        state.value = true // onReady is not delivered after initialize on the emulator; success is ready
                    }
                }
                override fun onFailure(errorType: ErrorType) { Log.w(TAG, "initialize failed: $errorType"); state.value = false }
            })
        }
    }

    private fun abandon() = guard("abandonFocus") { audio.abandonAudioFocusRequest(focus) }

    override fun speak(text: String, onDone: () -> Unit) {
        // Focus is abandoned when the line ends by any path (SDK done, estimate timer, stop).
        tracker.speak(text) { abandon(); onDone() }
        guard("requestFocus") {
            // A denied request still speaks: a line must never be silently dropped.
            if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED && logged.add("focusDenied")) {
                Log.w(TAG, "audio focus not granted; speaking anyway")
            }
        }
        Log.d(TAG, "send to SDK: $text")
        val t = tts
        if (t == null) { tracker.stop(); return }
        guard("speak", { tracker.stop() }) { t.speak(text, false) }
    }

    override fun stop() {
        guard("stop") { tts?.stop() }
        tracker.stop()
    }

    fun release() {
        state.value = false
        tracker.stop()
        guard("removeListener") { tts?.removeEventListener(listener) }
        guard("release") { tts?.release() }
        tts = null
    }

    private companion object { const val TAG = "PleosVoice" }
}
