package app.upmile.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.util.Log
import app.upmile.core.location.LocationSource
import app.upmile.core.model.GeoPoint
import app.upmile.guarded
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A usable fix, or null. (0, 0) is the no-fix placeholder: as a first D position it would fake a 300 m+ drive. */
internal fun geoPoint(lat: Double, lon: Double): GeoPoint? =
    if (lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0))
        GeoPoint(lat, lon) else null

/**
 * Pleos FusedLocation into [location]. SDK callbacks (binder threads) only write the latest fix; a null/invalid fix keeps
 * the previous one while started. [stop] clears it.
 */
class PleosLocationSource(context: Context) : LocationSource {
    private val app = context.applicationContext
    private val logged: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val state = MutableStateFlow<GeoPoint?>(null)
    private var fused: FusedLocation? = null
    @Volatile private var started = false // read by the SDK callback thread

    override val location: StateFlow<GeoPoint?> = state.asStateFlow()

    private inline fun guard(name: String, onFail: (Throwable) -> Unit = {}, block: () -> Unit) =
        guarded(TAG, logged, name, onFail, block)

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (!started) return // a late fix after stop() must not refill the cleared value
            val p = location?.let { geoPoint(it.latitude, it.longitude) } ?: return
            if (logged.add("firstFix")) Log.i(TAG, "first fix received")
            state.value = p
        }
        override fun onError(exception: Exception) { if (logged.add("callback")) Log.w(TAG, "location error", exception) }
    }

    @Synchronized
    override fun start() {
        if (started) return
        started = true
        guard("start", { release() }) {
            val f = FusedLocation(app)
            fused = f
            check(f.isSupported()) { "Pleos fused location service not installed" }
            f.initialize()
            f.getLastKnownLocation(listener) // the callback gets no initial value
            f.registerFusedLocationCallback(listener)
        }
    }

    @Synchronized
    override fun stop() {
        guard("unregister") { fused?.unregisterFusedLocationCallback(listener) }
        release()
        started = false
        // An untracked fix goes stale: the next drive's first tick would place the car where the last one ended.
        state.value = null
    }

    private fun release() {
        guard("release") { fused?.release() }
        fused = null
    }

    private companion object { const val TAG = "PleosLocationSource" }
}
