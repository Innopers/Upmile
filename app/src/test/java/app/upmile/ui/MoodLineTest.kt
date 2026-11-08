package app.upmile.ui

import app.upmile.core.routine.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodLineTest {
    @Test fun aLittle_countsTodaysHardSegments() =
        assertEquals("좋아요. 오늘 어려운 구간 3곳을 미리 같이 봐요.", moodLine(Mood.A_LITTLE, 3))

    @Test fun veryNervous_namesTheLongerBreathFromThePlan() = assertTrue(moodLine(Mood.VERY_NERVOUS, 3).contains("4분"))

    @Test fun noHardSegments_neverSaysZero() {
        for (m in Mood.entries) assertFalse(m.name, moodLine(m, 0).contains("0곳"))
    }

    @Test fun noLineGradesTheDriver() {
        for (m in Mood.entries) for (n in 0..3) {
            val line = moodLine(m, n)
            assertFalse(line, line.contains("평가") || line.contains("점수"))
        }
    }
}
