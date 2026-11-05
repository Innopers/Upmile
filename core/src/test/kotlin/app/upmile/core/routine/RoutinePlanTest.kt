package app.upmile.core.routine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutinePlanTest {
    @Test
    fun plan_veryNervous_isFiveMinutesWithMusic() {
        val p = planFor(Mood.VERY_NERVOUS)
        assertEquals(300, p.breatheSeconds + 60)
        assertTrue(p.playMusic)
    }

    @Test
    fun plan_default_isShortWithoutMusic() {
        val p = planFor(Mood.A_LITTLE)
        assertTrue(p.breatheSeconds in 30..60)
        assertFalse(p.playMusic)
    }
}
