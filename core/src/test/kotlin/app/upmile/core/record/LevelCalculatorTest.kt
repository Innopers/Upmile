package app.upmile.core.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelCalculatorTest {
    private fun record(cards: Int) = Record.EMPTY.copy(
        cards = ExperienceType.entries.take(cards).map { ExperienceCard(it, 1, 0L, "", emptySet()) },
    )

    private fun level(cards: Int) = LevelCalculator.level(record(cards))

    @Test fun level_countsCardsNotDistance() {
        // distance is not an input to level(); a 5 km and a 500 km record with equal cards are equal
        val a = ExperienceCard(ExperienceType.KM_100_TRIP, 1, 0L, "", emptySet())
        assertEquals(LevelCalculator.level(record(3)), LevelCalculator.level(record(3).copy(processedSessionIds = setOf("500km"))))
        assertEquals(1, LevelCalculator.level(Record.EMPTY.copy(cards = listOf(a))))
    }

    @Test fun level_thresholds() {
        assertEquals(1, level(0)); assertEquals(1, level(1)); assertEquals(2, level(2)); assertEquals(2, level(4))
        assertEquals(3, level(5)); assertEquals(4, level(8)); assertEquals(4, level(10)); assertEquals(5, level(11))
    }

    @Test fun graduation_whenAllMvpTypesCollected() {
        assertFalse(LevelCalculator.graduated(record(10)))
        assertTrue(LevelCalculator.graduated(record(11)))
    }
}
