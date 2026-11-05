package app.upmile.core.demo

import app.upmile.core.record.LevelCalculator
import app.upmile.core.record.RecordStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DemoModeTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun readAsset(path: String) = javaClass.classLoader.getResource(path)!!.readText()

    @Test fun demoScenario_producesArrivalAndAtLeastTwoCards() {
        val record = runDemoHeadless(::readAsset, timeScale = 50f, store = RecordStore(tmp.newFile("record_demo.json")))
        assertTrue(record.cards.size >= 2)
        assertTrue(record.regions.any { it.code == "41135" })
        assertEquals("demo-commute", record.unseenSessionId)
        assertTrue(LevelCalculator.level(record) >= 2)
    }

    @Test fun runningTwiceOnSameStore_isIdempotent() {
        val store = RecordStore(tmp.newFile("record_demo.json"))
        val first = runDemoHeadless(::readAsset, 50f, store).cards.size
        assertEquals(first, runDemoHeadless(::readAsset, 50f, store).cards.size)
    }
}
