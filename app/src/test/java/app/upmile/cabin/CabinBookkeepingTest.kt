package app.upmile.cabin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CabinBookkeepingTest {
    @Test fun slot_newestWins_onlyFirstOfferSchedules() {
        val s = LatestSlot<Int>()
        assertTrue(s.offer(1))
        assertFalse(s.offer(2))
        assertFalse(s.offer(3))
        assertEquals(3, s.take())
        assertNull(s.take())
        assertTrue(s.offer(4)) // empty again: schedule again
    }

    @Test fun slot_clearDropsPending() {
        val s = LatestSlot<Int>().also { it.offer(1) }
        s.clear()
        assertNull(s.take())
    }

    @Test fun remembered_keepsFirstValue_restoresOnce() {
        val r = Remembered<String, Int>()
        r.remember("brightness", 100)
        r.remember("brightness", 40) // already changed by us: not an original
        r.remember("color", 7)
        assertEquals(mapOf("brightness" to 100, "color" to 7), r.take())
        assertTrue(r.take().isEmpty()) // second restore is a no-op
        r.remember("brightness", 55) // a new session records afresh
        assertEquals(mapOf("brightness" to 55), r.take())
    }
}
