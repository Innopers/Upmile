package app.upmile.core.record

import app.upmile.core.drive.DriveEvent
import app.upmile.core.model.SegmentType
import kotlinx.serialization.Serializable

@Serializable enum class Condition { RAIN, NIGHT, FREEZING }

@Serializable
enum class ExperienceType {
    FIRST_DRIVE, HIGHWAY_MERGE, MULTILANE_LEFT, ROUNDABOUT, GARAGE_RAMP, RAIN, NIGHT, FREEZING,
    KM_100_TRIP, NEW_CITY, NEW_PROVINCE,
}

@Serializable
data class ExperienceCard(val type: ExperienceType, val stars: Int, val firstAtMs: Long, val place: String, val conditions: Set<Condition>)

@Serializable
data class SegmentStamp(val segmentTitle: String, val type: SegmentType, val conditions: Set<Condition>, val atMs: Long)

@Serializable
data class Region(val code: String, val name: String)

@Serializable
data class Record(
    val cards: List<ExperienceCard>,
    val stamps: List<SegmentStamp>,
    val regions: List<Region>,
    val revisitSegments: List<String>,
    val processedSessionIds: Set<String>,
    val unseenSessionId: String? = null,
) {
    companion object {
        val EMPTY = Record(emptyList(), emptyList(), emptyList(), emptyList(), emptySet())
    }
}

data class DriveSession(
    val id: String,
    val events: List<DriveEvent>,
    val conditions: Set<Condition>,
    val drivenM: Int,
    val arrivalRegion: Region?,
    val endedAtMs: Long,
)
