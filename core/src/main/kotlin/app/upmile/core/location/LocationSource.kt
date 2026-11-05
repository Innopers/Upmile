package app.upmile.core.location

import app.upmile.core.model.GeoPoint
import app.upmile.core.vehicle.ScenarioVehicleSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

interface LocationSource {
    val location: StateFlow<GeoPoint?>
    fun start()
    fun stop()
}

/** Follows the scenario replay's current frame; needs no permission. start/stop are no-ops: the scenario owns the clock. */
class ScenarioLocationSource(signals: ScenarioVehicleSignals, scope: CoroutineScope) : LocationSource {
    override val location: StateFlow<GeoPoint?> =
        signals.frame.map { GeoPoint(it.lat, it.lon) }.stateIn(scope, SharingStarted.Eagerly, null)

    override fun start() {}
    override fun stop() {}
}
