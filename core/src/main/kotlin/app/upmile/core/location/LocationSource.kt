package app.upmile.core.location

import app.upmile.core.model.GeoPoint
import app.upmile.core.vehicle.ScenarioVehicleSignals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

interface LocationSource {
    val location: StateFlow<GeoPoint?>
    fun start()
    fun stop()
}

/** Reads the position from the scenario replay's current frame; needs no permission, no scope. */
@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
class ScenarioLocationSource(private val signals: ScenarioVehicleSignals) : LocationSource {
    override val location: StateFlow<GeoPoint?> = object : StateFlow<GeoPoint?> {
        override val value: GeoPoint? get() = signals.frame.value.let { GeoPoint(it.lat, it.lon) }
        override val replayCache: List<GeoPoint?> get() = listOf(value)
        override suspend fun collect(collector: FlowCollector<GeoPoint?>): Nothing =
            signals.frame.collect { collector.emit(GeoPoint(it.lat, it.lon)) }
    }

    override fun start() {} // the scenario's own start() drives the frames
    override fun stop() {}
}
