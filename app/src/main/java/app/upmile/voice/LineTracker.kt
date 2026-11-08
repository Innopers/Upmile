package app.upmile.voice

fun interface Scheduler {
    /** Runs [task] once after [delayMs]; the returned function cancels it. */
    fun schedule(delayMs: Long, task: () -> Unit): () -> Unit
}

/**
 * Decides when each spoken line ends. The SDK has no utterance id, so its done callbacks are attributed FIFO to the
 * utterances sent since the last stop; a line also ends by an estimated-duration timer, or by [stop]. Every line's
 * callback runs exactly once, at the earliest of those.
 */
class LineTracker(private val now: () -> Long, private val scheduler: Scheduler) {
    private class Entry(val onDone: () -> Unit) { var ended = false; var cancel: () -> Unit = {} }

    private val lock = Any()
    private val sent = ArrayDeque<Entry>()   // sent to the SDK since the last stop, oldest first (ended ones stay until a done consumes them)
    private var current: Entry? = null
    private var firstSpeak = true
    private var ignoreDoneUntil = 0L

    /** Registers a line the caller is about to send to the SDK (non-flush). */
    fun speak(text: String, onDone: () -> Unit) {
        val old = synchronized(lock) { current }
        old?.let(::finish) // serial queue never does this; don't strand the caller
        val e = Entry(onDone)
        synchronized(lock) {
            val delay = estimateMs(text) + if (firstSpeak) MODEL_LOAD_MS else 0L
            firstSpeak = false
            sent.addLast(e); current = e
            e.cancel = scheduler.schedule(delay) { finish(e) }
        }
    }

    /** An SDK onDone: belongs to the oldest unattributed utterance; ends the current line only if that is it. */
    fun sdkDone() {
        val e = synchronized(lock) {
            if (now() < ignoreDoneUntil) return
            sent.removeFirstOrNull()
        }
        if (e != null && e === synchronized(lock) { current }) finish(e)
    }

    /** Ends the current line and drops stale SDK callbacks for a short while. Also used by release. */
    fun stop() {
        val cur = synchronized(lock) { sent.clear(); ignoreDoneUntil = now() + STALE_WINDOW_MS; current }
        cur?.let(::finish)
    }

    private fun finish(e: Entry) {
        synchronized(lock) {
            if (e.ended) return
            e.ended = true; e.cancel()
            if (current === e) current = null
        }
        e.onDone()
    }

    companion object {
        const val BASE_MS = 1_200L          // synthesis start-up and trailing silence
        const val PER_CHAR_MS = 200L        // slow Korean speech, ~5 chars/s
        const val MAX_MS = 20_000L          // no single line is expected to be longer
        const val MODEL_LOAD_MS = 12_000L   // first speak loads the on-device model (~10 s measured)
        const val STALE_WINDOW_MS = 1_000L  // callbacks from the utterance cut by stop() arrive within this
        const val MAX_TOTAL_MS = MAX_MS + MODEL_LOAD_MS

        fun estimateMs(text: String): Long = (BASE_MS + PER_CHAR_MS * text.length).coerceAtMost(MAX_MS)
    }
}
