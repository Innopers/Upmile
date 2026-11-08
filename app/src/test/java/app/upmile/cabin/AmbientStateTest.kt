package app.upmile.cabin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientStateTest {
    private val all = Known(switch = true, brightness = true, color = true)

    @Test fun failedSwitch_isRetriedOnNextApply() {
        val s = AmbientState(); val log = mutableListOf<AmbientWrite>()
        var failSwitch = true
        val run = { w: AmbientWrite -> log += w; if (w == AmbientWrite.SwitchOn && failSwitch) s.failed(w) } // failure may come back at once
        s.apply(20, 1, { all }, run)
        failSwitch = false
        s.apply(20, 1, { all }, run)
        assertEquals(2, log.count { it == AmbientWrite.SwitchOn })
        assertTrue(s.on)
    }

    @Test fun missingOriginalSwitch_writesNothing_andTriesAgain() {
        val s = AmbientState(); val log = mutableListOf<AmbientWrite>()
        assertFalse(s.apply(20, 1, { Known(false, true, true) }, { log += it }))
        assertTrue(log.isEmpty())
        assertTrue(s.apply(20, 1, { all }, { log += it }))
        assertEquals(AmbientWrite.SwitchOn, log.first())
    }

    @Test fun missingOriginalBrightness_neverWritesBrightness() {
        val s = AmbientState(); val log = mutableListOf<AmbientWrite>()
        s.apply(20, 1, { Known(true, false, true) }, { log += it })
        s.apply(30, 2, { all }, { log += it })
        assertTrue(log.none { it is AmbientWrite.Brightness })
        assertEquals(listOf(1, 2), log.filterIsInstance<AmbientWrite.Color>().map { it.value })
    }

    @Test fun unchangedValues_areNotRewritten_failedOnesAre() {
        val s = AmbientState(); val log = mutableListOf<AmbientWrite>()
        s.apply(20, 1, { all }, { log += it }); s.apply(20, 1, { all }, { log += it })
        assertEquals(3, log.size)
        s.failed(AmbientWrite.Brightness(20)); s.apply(20, 1, { all }, { log += it })
        assertEquals(AmbientWrite.Brightness(20), log.last())
    }
}
