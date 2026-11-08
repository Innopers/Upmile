package app.upmile.drive

import app.upmile.core.record.Record
import app.upmile.core.vehicle.Gear
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveFinishedTest {
    private val saved = Record.EMPTY.copy(processedSessionIds = setOf("s1"), unseenSessionId = "s1")

    @Test fun savedArrival_inPark_finishes() = assertTrue(driveFinished(saved, "s1", Gear.P))

    @Test fun notSavedYet_keepsRunning() = assertFalse(driveFinished(Record.EMPTY, "s1", Gear.P))

    @Test fun otherSession_keepsRunning() = assertFalse(driveFinished(saved, "s2", Gear.P))

    @Test fun savedButBackInDrive_keepsRunning() = assertFalse(driveFinished(saved, "s1", Gear.D))

    @Test fun uiAlreadyClearedUnseen_stillFinishes() =
        assertTrue(driveFinished(saved.copy(unseenSessionId = null), "s1", Gear.P))
}
