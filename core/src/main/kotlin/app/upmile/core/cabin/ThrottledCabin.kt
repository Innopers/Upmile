package app.upmile.core.cabin

class ThrottledCabin(
    private val inner: CabinController,
    private val minIntervalMs: Long = 250,
    private val clock: () -> Long,
) : CabinController {
    @Volatile var lastError: Throwable? = null
        private set
    private var lastApplied: Long? = null

    override fun ambient(brightness: Int, argb: Int): Boolean {
        val now = clock()
        val last = lastApplied
        if (last != null && now - last < minIntervalMs) return false
        lastApplied = now
        return guard(false) { inner.ambient(brightness, argb) }
    }

    override fun quietFan(): Boolean = guard(false) { inner.quietFan() }

    override fun restore() { guard(Unit) { inner.restore() } }

    private fun <T> guard(fallback: T, block: () -> T): T =
        try { block() } catch (t: Throwable) { lastError = t; fallback }
}
