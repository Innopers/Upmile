package app.upmile.core.routine

import org.junit.Assert.assertEquals
import org.junit.Test

class BreathingTest {
    @Test
    fun frame_inhaleRampsBrightness20to80_exhaleBack() {
        assertEquals(Phase.INHALE, Breathing.frameAt(0).phase)
        assertEquals(20, Breathing.frameAt(0).brightness)
        assertEquals(80, Breathing.frameAt(4_000).brightness) // end of inhale
        assertEquals(Phase.EXHALE, Breathing.frameAt(4_001).phase)
        assertEquals(20, Breathing.frameAt(10_000).brightness) // cycle restarts at 10s
    }

    @Test
    fun frame_colorIsWarmOnInhale_coolOnExhale() {
        assertEquals(0xFFFFB86B.toInt(), Breathing.frameAt(3_999).argb)
        assertEquals(0xFF4CC2FF.toInt(), Breathing.frameAt(9_999).argb)
    }
}
