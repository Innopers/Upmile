package app.upmile.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.upmile.guarded
import java.util.concurrent.ConcurrentHashMap

/** Looping routine background music. Everything runs on the main looper, so callers may use any thread. */
class MusicPlayer(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val logged: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attrs).setOnAudioFocusChangeListener(::onFocus, main).build()

    private var player: MediaPlayer? = null
    private var duck = 1f          // 1 normally, DUCKED while the voice speaks over us
    private var fade = 1f          // 1 -> 0 during fadeOutAndStop
    private var resumeOnGain = false
    private var fadeStep: Runnable? = null

    private inline fun guard(name: String, onFail: (Throwable) -> Unit = {}, block: () -> Unit) =
        guarded(TAG, logged, name, onFail, block)

    private fun applyVolume() = guard("volume") { (duck * fade).let { player?.setVolume(it, it) } }

    fun play(assetPath: String) = main.post {
        halt()
        guard("play", { Log.w(TAG, "could not play $assetPath"); halt() }) {
            if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG, "audio focus not granted for music"); return@post
            }
            val p = MediaPlayer()
            player = p
            app.assets.openFd(assetPath).use { p.setDataSource(it) }
            p.setAudioAttributes(attrs)
            p.isLooping = true
            p.setOnErrorListener { _, what, extra -> Log.w(TAG, "MediaPlayer error what=$what extra=$extra"); halt(); true }
            p.prepare()
            applyVolume()
            p.start()
            Log.d(TAG, "started $assetPath")
        }
    }

    fun fadeOutAndStop(ms: Long = 1500) = main.post {
        if (player == null || fadeStep != null) return@post
        val t0 = SystemClock.uptimeMillis()
        val step = object : Runnable {
            override fun run() {
                val k = ((SystemClock.uptimeMillis() - t0).toFloat() / ms).coerceIn(0f, 1f)
                fade = 1f - k
                applyVolume()
                if (k < 1f) main.postDelayed(this, FADE_TICK_MS) else { Log.d(TAG, "faded out"); halt() }
            }
        }
        fadeStep = step
        step.run()
    }

    private fun onFocus(change: Int) {
        val p = player ?: return
        Log.d(TAG, "focus change $change")
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { duck = DUCKED; applyVolume() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> guard("pause") { if (p.isPlaying) { p.pause(); resumeOnGain = true } }
            AudioManager.AUDIOFOCUS_GAIN -> {
                duck = 1f; applyVolume()
                if (resumeOnGain) { resumeOnGain = false; guard("resume") { p.start() } }
            }
            AudioManager.AUDIOFOCUS_LOSS -> halt()
        }
    }

    /** Stops, releases and abandons focus; safe when idle. */
    private fun halt() {
        fadeStep?.let(main::removeCallbacks)
        fadeStep = null
        player?.let { p -> guard("stop") { p.stop() }; guard("release") { p.release() } }
        player = null
        duck = 1f; fade = 1f; resumeOnGain = false
        guard("abandonFocus") { audio.abandonAudioFocusRequest(focus) }
    }

    fun release() { main.post { halt() } }

    private companion object { const val TAG = "MusicPlayer"; const val DUCKED = 0.3f; const val FADE_TICK_MS = 50L }
}
