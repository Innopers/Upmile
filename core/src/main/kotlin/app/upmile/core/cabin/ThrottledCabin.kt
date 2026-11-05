package app.upmile.core.cabin

/** Call from the UI thread only (throttle state is not synchronized). */
class ThrottledCabin(
    private val inner: CabinController,
    private val minIntervalMs: Long = 250,
    private val clock: () -> Long,
) : CabinController {
    @Volatile var lastError: Throwable? = null
        private set
    private var lastApplied: Long? = null
    private var lastResult = false

    /** Returns whether ambient control currently works: a throttled (dropped) call repeats the last real result. */
    override fun ambient(brightness: Int, argb: Int): Boolean {
        val now = clock()
        val last = lastApplied
        if (last != null && now - last < minIntervalMs) return lastResult
        lastApplied = now
        return guard(false) { inner.ambient(brightness, argb) }.also { lastResult = it }
    }

    override fun quietFan(): Boolean = guard(false) { inner.quietFan() }

    override fun restore() { guard(Unit) { inner.restore() } }

    private fun <T> guard(fallback: T, block: () -> T): T =
        try { block() } catch (t: Throwable) { lastError = t; fallback }
}
