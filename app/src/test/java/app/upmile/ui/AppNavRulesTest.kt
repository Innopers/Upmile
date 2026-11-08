package app.upmile.ui

import app.upmile.core.vehicle.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavRulesTest {
    @Test fun parkedAndUnrestricted_noCover() = assertFalse(coverShown(restricted = false, gear = Gear.P))

    @Test fun restricted_coversEvenInPark() = assertTrue(coverShown(restricted = true, gear = Gear.P))

    @Test fun anyGearButPark_covers() {
        for (g in listOf(Gear.D, Gear.R, Gear.N)) assertTrue(g.name, coverShown(restricted = false, gear = g))
    }

    @Test fun unknownGear_coversFailSafe() = assertTrue(coverShown(restricted = false, gear = Gear.UNKNOWN))

    @Test fun unseenDriveInPark_opensRecord() = assertTrue(opensRecord(Gear.P, "s1"))

    @Test fun unseenDriveNotInPark_waits() = assertFalse(opensRecord(Gear.D, "s1"))

    @Test fun nothingUnseen_staysPut() = assertFalse(opensRecord(Gear.P, null))

    @Test fun firstVisit_onlyCurrentIsActive() {
        assertEquals(TabState.ACTIVE, tabState(step = 1, current = 1, reached = 1))
        assertEquals(TabState.UPCOMING, tabState(step = 2, current = 1, reached = 1))
    }

    @Test fun jumpingAhead_marksEverythingBeforeDone() {
        assertEquals(listOf(TabState.DONE, TabState.DONE, TabState.DONE, TabState.ACTIVE), (1..4).map { tabState(it, current = 4, reached = 4) })
    }

    @Test fun goingBack_keepsEarlierStepsDone() {
        // reached 3, back on 1: step 2 stays done, step 3 (never finished) is upcoming again
        assertEquals(listOf(TabState.ACTIVE, TabState.DONE, TabState.UPCOMING, TabState.UPCOMING), (1..4).map { tabState(it, current = 1, reached = 3) })
    }

    @Test fun onRecord_noTabIsActive() {
        assertEquals(listOf(TabState.DONE, TabState.UPCOMING, TabState.UPCOMING, TabState.UPCOMING), (1..4).map { tabState(it, current = null, reached = 2) })
    }
}
