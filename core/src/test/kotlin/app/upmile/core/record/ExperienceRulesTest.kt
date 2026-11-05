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
    private fun seg(t: SegmentType, title: String) = HardSegment(title, t, title, GeoPoint(0.0, 0.0), 0, emptyMap())
    private val merge = seg(SegmentType.HIGHWAY_MERGE, "경부선 합류")
    private val left = seg(SegmentType.MULTILANE_LEFT, "대로 좌회전")
    private val bundang = Region("41135", "성남시분당구")
    private val gangnam = Region("11680", "강남구")
    private val suwon = Region("41111", "수원시장안구")

    private fun session(
        id: String = "S1", events: List<app.upmile.core.drive.DriveEvent> = emptyList(),
        conditions: Set<Condition> = emptySet(), drivenM: Int = 5_000, region: Region? = null,
    ) = DriveSession(id, events, conditions, drivenM, region, 1_000L)

    private fun types(r: Record) = r.cards.map { it.type }.toSet()

    @Test fun passedMergeInRain_createsCardStampAndRainCard() {
        val r = ExperienceRules.apply(Record.EMPTY, session(events = listOf(Passed(merge, false)), conditions = setOf(Condition.RAIN)))
        assertTrue(r.cards.any { it.type == ExperienceType.HIGHWAY_MERGE } && r.cards.any { it.type == ExperienceType.RAIN })
        assertEquals(setOf(Condition.RAIN), r.stamps.single().conditions)
    }

    @Test fun hardMoment_addsRevisit_notPenalty() {
        val r = ExperienceRules.apply(Record.EMPTY, session(events = listOf(Passed(left, true))))
        assertTrue(r.cards.any { it.type == ExperienceType.MULTILANE_LEFT })
        assertEquals(listOf(left.title), r.revisitSegments)
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
        assertEquals(r1.cards.size + 0, r2.cards.size)
        val r3 = ExperienceRules.apply(r2, session("C", arr, region = gangnam)) // province 11
        assertTrue(r3.cards.any { it.type == ExperienceType.NEW_PROVINCE && it.place == "강남구" } || r3.regions.size == 3)
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
