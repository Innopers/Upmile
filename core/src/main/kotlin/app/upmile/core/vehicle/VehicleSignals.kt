package app.upmile.core.vehicle

import kotlinx.coroutines.flow.StateFlow

interface VehicleSignals {
    val snapshots: StateFlow<VehicleSnapshot>
    fun start()
    fun stop()
}
