package app.upmile.core.cabin

import org.junit.Assert.*
import org.junit.Test

private const val RED = 0xFFFF0000.toInt()

private class RecordingCabin : CabinController {
    val brightnessCalls = mutableListOf<Int>()
    override fun ambient(brightness: Int, argb: Int): Boolean { brightnessCalls += brightness; return true }
    override fun quietFan() = true
    override fun restore() {}
}

private class ThrowingCabin(val e: Throwable) : CabinController {
    override fun ambient(brightness: Int, argb: Int): Boolean = throw e
    override fun quietFan(): Boolean = throw e
    override fun restore() { throw e }
}

class ThrottledCabinTest {
    @Test fun throttle_dropsCallsWithin250ms_keepsLatestNext() {
        val fake = RecordingCabin(); var now = 0L; val c = ThrottledCabin(fake, clock = { now })
        c.ambient(20, RED); now = 100; c.ambient(30, RED); now = 260; c.ambient(40, RED)
        assertEquals(listOf(20, 40), fake.brightnessCalls)
    }

    @Test fun rejectedControl_returnsFalse_neverThrows() {
        val c = ThrottledCabin(ThrowingCabin(SecurityException("CONTROL_CAR_LIGHTS")), clock = { 0 })
        assertFalse(c.ambient(50, RED))
        assertTrue(c.lastError is SecurityException)
    }

    @Test fun quietFanAndRestore_onThrowingInner_doNotThrow() {
        val c = ThrottledCabin(ThrowingCabin(IllegalStateException("x")), clock = { 0 })
        assertFalse(c.quietFan())
        c.restore()
        assertTrue(c.lastError is IllegalStateException)
    }
}

class ThrottleWindowOnErrorTest {
    @Test fun throwingAmbient_stillStartsWindow() {
        var now = 0L
        val inner = object : CabinController {
            var calls = 0
            override fun ambient(brightness: Int, argb: Int): Boolean { calls++; throw IllegalStateException() }
            override fun quietFan() = true
            override fun restore() {}
        }
        val c = ThrottledCabin(inner, clock = { now })
        c.ambient(1, 0); now = 100; c.ambient(2, 0)
        assertEquals(1, inner.calls)
    }
}
