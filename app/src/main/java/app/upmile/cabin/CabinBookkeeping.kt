package app.upmile.cabin

import java.util.concurrent.atomic.AtomicReference

/** One pending request, newest wins: the UI offers every frame, the cabin thread applies only the latest. */
internal class LatestSlot<T : Any> {
    private val ref = AtomicReference<T?>(null)

    /** True when the slot was empty, so the caller must schedule a drain. */
    fun offer(v: T): Boolean = ref.getAndSet(v) == null
    fun take(): T? = ref.getAndSet(null)
    fun clear() { ref.set(null) }
}

/** The values to put back: the first one seen per key is kept, [take] hands them out once. Thread-safe. */
internal class Remembered<K : Any, V : Any> {
    private val values = LinkedHashMap<K, V>()
    @Synchronized fun remember(key: K, value: V) { values.putIfAbsent(key, value) }
    @Synchronized fun take(): Map<K, V> = LinkedHashMap(values).also { values.clear() }
}
