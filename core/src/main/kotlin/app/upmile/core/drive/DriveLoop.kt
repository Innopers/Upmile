package app.upmile.core.drive

import app.upmile.core.model.GeoPoint
import app.upmile.core.record.Condition
import app.upmile.core.record.DriveSession
import app.upmile.core.record.ExperienceRules
import app.upmile.core.record.RecordStore
import app.upmile.core.record.RegionLocator
import app.upmile.core.vehicle.Gear
import app.upmile.core.vehicle.VehicleSnapshot
import app.upmile.core.voice.Voice
import kotlin.math.roundToInt

/** Pure drive loop, ticked at 5 Hz by the service or the demo. Ticks are serialized by [lock]. */
class DriveLoop(
    private val tracker: DriveTracker,
    private val voice: Voice,
    private val store: RecordStore,
    private val regions: RegionLocator,
    private val sessionId: String,
    private val clock: () -> Long,
) {
    private val lock = Any()
    private val events = mutableListOf<DriveEvent>()
    private val conditions = mutableSetOf<Condition>()

    fun tick(s: VehicleSnapshot, p: GeoPoint?) = synchronized(lock) {
        if (s.gear == Gear.D) {
            if (s.wiperOn) conditions += Condition.RAIN
            if (s.headlightsOn) conditions += Condition.NIGHT
            if (s.outsideTempC?.let { it < 0 } == true) conditions += Condition.FREEZING
        }
        if (p == null) return@synchronized
        for (e in tracker.onSample(s, p)) {
            events += e
            when (e) {
                is DriveEvent.Approaching -> voice.say("${distanceText(e.metersAhead)} 앞, 아까 연습한 ${e.segment.title}이에요.", 10)
                is DriveEvent.Passed -> {}
                is DriveEvent.Arrived -> {
                    val session = DriveSession(sessionId, events.toList(), conditions.toSet(), e.drivenM, regions.locate(e.at), clock())
                    store.save(ExperienceRules.apply(store.load(), session))
                    voice.say("도착했어요. 오늘 기록을 정리했어요.", 5)
                }
            }
        }
    }

    /** Nearest 100 m; "800m" below 1 km, "1.2km" from 1 km up. */
    internal fun distanceText(m: Int): String {
        val r = (m / 100.0).roundToInt() * 100
        return if (r >= 1000) "${r / 1000}.${r % 1000 / 100}km" else "${r}m"
    }
}
