package app.upmile.core.demo

import app.upmile.core.record.LevelCalculator
import app.upmile.core.record.RecordStore
import app.upmile.core.voice.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DemoModeTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun readAsset(path: String) = javaClass.classLoader.getResource(path)!!.readText()

    @Test fun demoScenario_producesArrivalAndAtLeastTwoCards() {
        val record = runDemoHeadless(::readAsset, store = RecordStore(tmp.newFile("record_demo.json")))
        assertTrue(record.cards.size >= 2)
        assertTrue(record.regions.any { it.code == "41135" })
        assertEquals("demo-commute", record.unseenSessionId)
        assertTrue(LevelCalculator.level(record) >= 2)
        assertEquals(listOf("commute:multilane_left:2"), record.revisitSegments)
        assertEquals(3, record.stamps.size) // all three commute segments were rehearsed
        assertTrue(record.cards.all { it.firstAtMs > 1_000_000_000_000 }) // wall-clock dates, not replay time
    }

    @Test fun runningTwiceOnSameStore_isIdempotent() {
        val store = RecordStore(tmp.newFile("record_demo.json"))
        val first = runDemoHeadless(::readAsset, store)
        assertEquals(first, runDemoHeadless(::readAsset, store))
    }

    @Test fun givenVoice_getsEveryLine() {
        val voice = object : Voice {
            val said = mutableListOf<String>()
            override val ready: StateFlow<Boolean> = MutableStateFlow(true)
            override fun say(text: String, priority: Int) { said += text }
            override fun stop() {}
        }
        runBlocking { runDemo(::readAsset, RecordStore(tmp.newFile("record_demo.json")), voice = voice) }
        assertEquals(3, voice.said.count { "아까 연습한" in it }) // one approach line per commute segment
        assertEquals("도착했어요. 오늘 기록을 정리했어요.", voice.said.last())
    }

    @Test fun coarseAppPacing_400msSteps_stillKeepsTheHardBrake() {
        val paced = mutableListOf<Long>()
        val record = runBlocking {
            runDemo(::readAsset, RecordStore(tmp.newFile("record_demo.json")), stepMs = 400, pace = { paced += it })
        }
        assertEquals(listOf("commute:multilane_left:2"), record.revisitSegments)
        assertTrue(paced.isNotEmpty() && paced.all { it == 400L })
    }
}
