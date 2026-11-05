package app.upmile.core.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface Voice {
    val ready: StateFlow<Boolean>
    fun say(text: String, priority: Int = 0)
    fun stop()
}

/** TTS SDK has no utterance id: [onDone] fires once per speak, possibly never. */
interface SpeechEngine {
    val ready: StateFlow<Boolean>
    fun speak(text: String, onDone: () -> Unit)
    /** Cuts the current utterance (e.g. QUEUE_FLUSH); a late onDone after this is ignored by [QueuedVoice]. */
    fun stop()
}

/**
 * Serial priority queue over [engine]; a line that never reports done is released after [timeoutMs].
 * A line whose speak throws counts as done (the error goes to [lastError]) so the caller's tick keeps running.
 */
class QueuedVoice(
    private val engine: SpeechEngine,
    private val timeoutMs: Long = 10_000,
    private val scope: CoroutineScope,
) : Voice {
    @Volatile var lastError: Throwable? = null
        private set

    private class Line(val text: String, val priority: Int, val order: Long)

    private val lock = Any()
    private val pending = mutableListOf<Line>()
    private var order = 0L
    private var token = 0L        // identifies the in-flight line; stale onDone/timeouts don't match
    private var inFlight = false
    private var timeout: Job? = null

    override val ready: StateFlow<Boolean> get() = engine.ready

    init { scope.launch { engine.ready.collect { if (it) pump() } } }

    override fun say(text: String, priority: Int) {
        synchronized(lock) { pending += Line(text, priority, order++) }
        pump()
    }

    override fun stop() {
        synchronized(lock) { pending.clear(); token++; inFlight = false; timeout?.cancel() }
        try { engine.stop() } catch (e: Exception) { lastError = e }
    }

    private fun pump() {
        val line: Line; val my: Long
        synchronized(lock) {
            if (inFlight || !engine.ready.value) return
            line = pending.minWithOrNull(compareBy<Line>({ -it.priority }, { it.order })) ?: return
            pending.remove(line); inFlight = true; my = ++token
            timeout = scope.launch { delay(timeoutMs); finish(my) }
        }
        try { engine.speak(line.text) { finish(my) } } catch (e: Exception) { lastError = e; finish(my) }
    }

    private fun finish(my: Long) {
        synchronized(lock) {
            if (my != token || !inFlight) return
            inFlight = false; timeout?.cancel()
        }
        pump()
    }
}
