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
    private var arrived = false

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
                is DriveEvent.Approaching -> voice.say("${distanceText(e.metersAhead)} 앞, 아까 연습한 ${withCopula(e.segment.title)}.", 10)
                is DriveEvent.Passed -> {}
                is DriveEvent.Arrived -> if (!arrived) {
                    arrived = true
                    val session = DriveSession(sessionId, events.toList(), conditions.toSet(), e.drivenM, regions.locate(e.at), clock())
                    store.save(ExperienceRules.apply(store.load(), session))
                    voice.say("도착했어요. 오늘 기록을 정리했어요.", 5)
                }
            }
        }
    }

    /** Nearest 100 m; "바로" under 50 m, "800m" below 1 km, "1km" / "1.2km" from 1 km up. */
    internal fun distanceText(m: Int): String {
        val r = (m / 100.0).roundToInt() * 100
        return when {
            m < 50 -> "바로"
            r >= 1000 -> if (r % 1000 == 0) "${r / 1000}km" else "${r / 1000}.${r % 1000 / 100}km"
            else -> "${r}m"
        }
    }
}

/** word + 예요 after a Hangul syllable without final consonant, otherwise 이에요 (also for non-Hangul endings). */
fun withCopula(word: String): String {
    val c = word.lastOrNull()
    val open = c != null && c in '가'..'힣' && (c - '가') % 28 == 0
    return word + if (open) "예요" else "이에요"
}
