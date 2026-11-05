package app.upmile.core.record

import app.upmile.core.drive.DriveEvent
import app.upmile.core.model.SegmentType

object ExperienceRules {
    private val segmentCard = mapOf(
        SegmentType.HIGHWAY_MERGE to ExperienceType.HIGHWAY_MERGE,
        SegmentType.MULTILANE_LEFT to ExperienceType.MULTILANE_LEFT,
        SegmentType.ROUNDABOUT to ExperienceType.ROUNDABOUT,
        SegmentType.GARAGE_RAMP to ExperienceType.GARAGE_RAMP,
    )

    fun apply(record: Record, session: DriveSession): Record {
        if (session.id in record.processedSessionIds) return record
        val arrived = session.events.any { it is DriveEvent.Arrived }
        var cards = record.cards
        var stamps = record.stamps
        var regions = record.regions
        var revisit = record.revisitSegments
        val place = session.arrivalRegion?.name ?: ""

        fun earn(type: ExperienceType, placeName: String = place, newStar: Boolean = false) {
            val old = cards.firstOrNull { it.type == type }
            cards = when {
                old == null -> cards + ExperienceCard(type, 1, session.endedAtMs, placeName, session.conditions)
                newStar -> cards.map {
                    if (it.type == type) it.copy(stars = minOf(3, it.stars + 1), conditions = it.conditions + session.conditions) else it
                }
                else -> cards
            }
        }

        if (arrived) earn(ExperienceType.FIRST_DRIVE)
        for (e in session.events) {
            if (e !is DriveEvent.Passed) continue
            val seg = e.segment
            val newCombo = stamps.none { it.type == seg.type && it.conditions == session.conditions }
            val hadType = stamps.any { it.type == seg.type }
            segmentCard[seg.type]?.let { earn(it, seg.title, newStar = hadType && newCombo) }
            stamps = stamps + SegmentStamp(seg.title, seg.type, session.conditions, session.endedAtMs)
            if (e.hadHardMoment && seg.title !in revisit) revisit = revisit + seg.title
        }
        session.conditions.forEach {
            earn(when (it) { Condition.RAIN -> ExperienceType.RAIN; Condition.NIGHT -> ExperienceType.NIGHT; Condition.FREEZING -> ExperienceType.FREEZING })
        }
        if (session.drivenM >= 100_000) earn(ExperienceType.KM_100_TRIP)
        val region = session.arrivalRegion
        if (arrived && region != null) {
            if (regions.none { it.code == region.code }) earn(ExperienceType.NEW_CITY)
            if (regions.none { it.code.take(2) == region.code.take(2) }) earn(ExperienceType.NEW_PROVINCE)
            if (regions.none { it.code == region.code }) regions = regions + region
        }
        return record.copy(
            cards = cards, stamps = stamps, regions = regions, revisitSegments = revisit,
            processedSessionIds = record.processedSessionIds + session.id,
            unseenSessionId = if (arrived) session.id else record.unseenSessionId,
        )
    }
}
