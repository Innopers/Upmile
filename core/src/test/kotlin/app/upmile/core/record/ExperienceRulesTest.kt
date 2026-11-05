package app.upmile.core.record

import app.upmile.core.drive.DriveEvent.Arrived
import app.upmile.core.drive.DriveEvent.Passed
import app.upmile.core.model.GeoPoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperienceRulesTest {
    private fun seg(id: String, t: SegmentType, place: String) = HardSegment(id, t, t.name, place, GeoPoint(0.0, 0.0), 0, emptyMap())
    private val merge = seg("r:highway_merge:1", SegmentType.HIGHWAY_MERGE, "양재IC 진입")
    private val left = seg("r:multilane_left:2", SegmentType.MULTILANE_LEFT, "판교역 사거리")
    private val otherLeft = seg("r:multilane_left:5", SegmentType.MULTILANE_LEFT, "서현역 사거리")
    private val bundang = Region("41135", "경기 성남시 분당구")
    private val gangnam = Region("11680", "서울 강남구")
    private val suwon = Region("41111", "경기 수원시 장안구")

    private fun session(
        id: String = "S1", events: List<app.upmile.core.drive.DriveEvent> = emptyList(),
        conditions: Set<Condition> = emptySet(), drivenM: Int = 5_000, region: Region? = null,
        rehearsed: Set<String> = setOf(merge.id, left.id, otherLeft.id),
    ) = DriveSession(id, events, conditions, drivenM, region, 1_000L, rehearsed)

    private fun types(r: Record) = r.cards.map { it.type }.toSet()

    @Test fun passedMergeInRain_createsCardStampAndRainCard() {
        val r = ExperienceRules.apply(Record.EMPTY, session(events = listOf(Passed(merge, false)), conditions = setOf(Condition.RAIN)))
        assertTrue(r.cards.any { it.type == ExperienceType.HIGHWAY_MERGE } && r.cards.any { it.type == ExperienceType.RAIN })
        assertEquals(setOf(Condition.RAIN), r.stamps.single().conditions)
        assertEquals(merge.id to "양재IC 진입", r.stamps.single().let { it.segmentId to it.place })
    }

    @Test fun unrehearsedPass_earnsCard_butNoStamp() {
        val r = ExperienceRules.apply(Record.EMPTY, session(events = listOf(Passed(merge, false), Passed(left, true)), rehearsed = setOf(left.id)))
        assertTrue(r.cards.any { it.type == ExperienceType.HIGHWAY_MERGE && it.place == "양재IC 진입" })
        assertEquals(listOf(left.id), r.stamps.map { it.segmentId })
    }

    @Test fun twoLeftTurns_keepSeparateStampsAndRevisits() {
        val r = ExperienceRules.apply(Record.EMPTY, session(events = listOf(Passed(left, true), Passed(otherLeft, true))))
        assertEquals(listOf(left.id, otherLeft.id), r.revisitSegments)
        assertEquals(listOf("판교역 사거리", "서현역 사거리"), r.stamps.map { it.place })
    }

    @Test fun hardMoment_addsRevisit_notPenalty() {
        val r = ExperienceRules.apply(Record.EMPTY, session(events = listOf(Passed(left, true))))
        assertTrue(r.cards.any { it.type == ExperienceType.MULTILANE_LEFT })
        assertEquals(listOf(left.id), r.revisitSegments)
    }

    @Test fun sameExperienceUnderNewCondition_upgradesStars_max3() {
        val ev = listOf(Passed(merge, false))
        var r = ExperienceRules.apply(Record.EMPTY, session("a", ev))
        fun stars() = r.cards.single { it.type == ExperienceType.HIGHWAY_MERGE }.stars
        assertEquals(1, stars())
        r = ExperienceRules.apply(r, session("b", ev)) // same (empty) condition set
        assertEquals(1, stars())
        r = ExperienceRules.apply(r, session("c", ev, setOf(Condition.RAIN)))
        assertEquals(2, stars())
        r = ExperienceRules.apply(r, session("d", ev, setOf(Condition.NIGHT)))
        assertEquals(3, stars())
        r = ExperienceRules.apply(r, session("e", ev, setOf(Condition.FREEZING)))
        assertEquals(3, stars())
    }

    @Test fun starUpgrade_needsANewCondition_evenWithoutRehearsal() {
        val ev = listOf(Passed(merge, false))
        var r = ExperienceRules.apply(Record.EMPTY, session("a", ev, setOf(Condition.RAIN), rehearsed = emptySet()))
        r = ExperienceRules.apply(r, session("b", ev, rehearsed = emptySet())) // easier: no rain
        assertEquals(1, r.cards.single { it.type == ExperienceType.HIGHWAY_MERGE }.stars)
        r = ExperienceRules.apply(r, session("c", ev, setOf(Condition.NIGHT), rehearsed = emptySet()))
        assertEquals(2, r.cards.single { it.type == ExperienceType.HIGHWAY_MERGE }.stars)
        assertTrue(r.stamps.isEmpty())
    }

    @Test fun sameSessionTwice_isIdempotent() {
        val s = session("S1", listOf(Passed(merge, false), Arrived(GeoPoint(0.0, 0.0), 5000)), region = bundang)
        val once = ExperienceRules.apply(Record.EMPTY, s)
        assertEquals(once, ExperienceRules.apply(once, s))
    }

    @Test fun arrival_firstDriveCityProvince_andUnseenSession() {
        val arr = listOf(Arrived(GeoPoint(0.0, 0.0), 5000))
        val r1 = ExperienceRules.apply(Record.EMPTY, session("A", arr, region = bundang))
        assertEquals(setOf(ExperienceType.FIRST_DRIVE, ExperienceType.NEW_CITY, ExperienceType.NEW_PROVINCE), types(r1))
        assertEquals("A", r1.unseenSessionId)
        val r2 = ExperienceRules.apply(r1, session("B", arr, region = suwon)) // same province 41, new city
        assertEquals(r1.cards.size, r2.cards.size)
        val r3 = ExperienceRules.apply(r2, session("C", arr, region = gangnam)) // province 11
        assertEquals(1, r3.cards.count { it.type == ExperienceType.NEW_PROVINCE })
        assertEquals(3, r3.regions.size)
    }

    @Test fun newCityOnlyWhenCodeUnseen() {
        val arr = listOf(Arrived(GeoPoint(0.0, 0.0), 5000))
        val r1 = ExperienceRules.apply(Record.EMPTY, session("A", arr, region = bundang))
        val r2 = ExperienceRules.apply(r1, session("B", arr, region = suwon))
        assertEquals(2, r2.regions.size)
        assertEquals(1, r2.cards.count { it.type == ExperienceType.NEW_CITY })
    }

    @Test fun km100Trip_andNoArrivalMeansNoUnseen() {
        val r = ExperienceRules.apply(Record.EMPTY, session(drivenM = 100_000))
        assertEquals(setOf(ExperienceType.KM_100_TRIP), types(r))
        assertEquals(null, r.unseenSessionId)
        assertEquals(emptySet<ExperienceType>(), types(ExperienceRules.apply(Record.EMPTY, session(drivenM = 99_999))))
    }
}
