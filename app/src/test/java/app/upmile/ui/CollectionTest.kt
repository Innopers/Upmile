package app.upmile.ui

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.SegmentType
import app.upmile.core.record.ExperienceCard
import app.upmile.core.record.ExperienceType
import app.upmile.core.record.Record
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionTest {
    private fun seg(id: String, type: SegmentType) = HardSegment(id, type, type.name, "곳", GeoPoint(0.0, 0.0), 0, emptyMap())
    private fun card(type: ExperienceType) = ExperienceCard(type, 1, 0L, "곳", emptySet())

    @Test fun everyType_hasItsOwnKoreanNameAndIcon() {
        val looks = ExperienceType.entries.map { it.look }
        assertEquals(11, looks.map { it.first }.toSet().size)
        assertTrue(looks.all { (name, icon) -> name.isNotBlank() && icon.isNotBlank() })
        assertEquals("첫 고속도로 합류", ExperienceType.HIGHWAY_MERGE.look.first)
        assertEquals("처음 가는 도", ExperienceType.NEW_PROVINCE.look.first)
    }

    @Test fun conquered_countsEachTypeOnce() {
        val r = Record.EMPTY.copy(cards = listOf(card(ExperienceType.FIRST_DRIVE), card(ExperienceType.NIGHT), card(ExperienceType.NIGHT)))
        assertEquals(setOf(ExperienceType.FIRST_DRIVE, ExperienceType.NIGHT), conquered(r))
    }

    @Test fun todayChallenges_areTodaysSegmentTypesNotYetEarned() {
        val today = listOf(
            seg("c:highway_merge:1", SegmentType.HIGHWAY_MERGE), seg("c:multilane_left:2", SegmentType.MULTILANE_LEFT),
            seg("c:garage_ramp:end", SegmentType.GARAGE_RAMP),
        )
        assertEquals(setOf(ExperienceType.MULTILANE_LEFT, ExperienceType.GARAGE_RAMP), todayChallenges(today, setOf(ExperienceType.HIGHWAY_MERGE)))
    }

    @Test fun todayChallenges_skipTypesWithNoCard() =
        assertEquals(emptySet<ExperienceType>(), todayChallenges(listOf(seg("i:sharp_curve:4", SegmentType.SHARP_CURVE)), emptySet()))

    @Test fun particle_followsTheLastSyllable() {
        assertEquals("회사로", withRo("회사"))
        assertEquals("처음 가는 카페로", withRo("처음 가는 카페"))
        assertEquals("서울역으로", withRo("서울역"))
        assertEquals("병원으로", withRo("병원"))
        assertEquals("서울로", withRo("서울")) // ㄹ takes 로
        assertEquals("Upmile로", withRo("Upmile")) // not Hangul: plain 로
    }
}
