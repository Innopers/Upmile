package app.upmile.core.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Replays [frames] by `snapshot.timestampMs`; the replay clock starts at [start]. Ticks every 200 ms only if [scope] is given. */
class ScenarioVehicleSignals(
    private val frames: List<ScenarioFrame>,
    private val clock: () -> Long,
    private val scope: CoroutineScope? = null,
) : VehicleSignals {
    init {
        require(frames.isNotEmpty()) { "scenario has no frames" }
        require(frames.zipWithNext().all { (a, b) -> a.snapshot.timestampMs < b.snapshot.timestampMs }) {
            "scenario timestamps must be strictly ascending"
        }
    }

    private val _frame = MutableStateFlow(frames.first())
    val frame: StateFlow<ScenarioFrame> = _frame
    private val _snapshots = MutableStateFlow(frames.first().snapshot)
    override val snapshots: StateFlow<VehicleSnapshot> = _snapshots
    @Volatile private var startMs: Long? = null // start() and tick() may run on different threads
    private var job: Job? = null

    override fun start() {
        startMs = clock()
        tick()
        job?.cancel()
        job = scope?.launch { while (true) { delay(TICK_MS); tick() } }
    }

    override fun stop() { job?.cancel(); job = null }

    internal fun tick() {
        val start = startMs ?: return
        val elapsed = clock() - start
        val f = frames.lastOrNull { it.snapshot.timestampMs <= elapsed } ?: frames.first()
        _frame.value = f
        _snapshots.value = f.snapshot
    }

    private companion object { const val TICK_MS = 200L }
}
