package app.upmile.ui

import androidx.compose.ui.graphics.Color
import app.upmile.core.routine.Breathing
import org.junit.Assert.assertEquals
import org.junit.Test

class BreathColorTest {
    private val warm = Color(Breathing.frameAt(0).argb)      // inhale
    private val cool = Color(Breathing.frameAt(4_000).argb)  // exhale

    private fun assertClose(expected: Color, actual: Color) {
        for ((e, a) in listOf(expected.red to actual.red, expected.green to actual.green, expected.blue to actual.blue, expected.alpha to actual.alpha)) {
            assertEquals(e, a, 1f / 255)
        }
    }

    @Test fun atEachFlip_stillTheOldColour() {
        assertClose(warm, breathColor(4_000))   // inhale -> exhale
        assertClose(cool, breathColor(10_000))  // exhale -> next inhale
    }

    @Test fun afterTheFade_theNewColour() {
        assertEquals(cool, breathColor(4_000 + 1_200))
        assertEquals(warm, breathColor(10_000 + 1_200))
    }
}
