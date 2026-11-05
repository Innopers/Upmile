package app.upmile.core.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeEngine(ready: Boolean = true) : SpeechEngine {
    private val _ready = MutableStateFlow(ready)
    override val ready: StateFlow<Boolean> = _ready
    val spoken = mutableListOf<String>()
    var stops = 0
    var failOn: String? = null
    private val dones = mutableListOf<() -> Unit>()
    fun setReady(v: Boolean) { _ready.value = v }
    fun done(i: Int) = dones[i]()
    override fun speak(text: String, onDone: () -> Unit) {
        if (text == failOn) throw IllegalStateException("tts died")
        spoken += text; dones += onDone
    }
    override fun stop() { stops++ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class QueuedVoiceTest {
    private fun TestScope.voice(e: SpeechEngine) = QueuedVoice(e, 10_000, backgroundScope)

    @Test fun say_whileNotReady_isQueued_thenSpokenInPriorityOrder() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine(ready = false); val v = voice(engine)
        v.say("점검 완료", 0); v.say("1.2km 앞 합류", 10)
        assertTrue(engine.spoken.isEmpty())
        engine.setReady(true)
        assertEquals(listOf("1.2km 앞 합류"), engine.spoken)
        engine.done(0)
        assertEquals(listOf("1.2km 앞 합류", "점검 완료"), engine.spoken)
    }

    @Test fun serial_secondWaitsForFirstDone() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine(); val v = voice(engine)
        v.say("a"); v.say("b")
        assertEquals(listOf("a"), engine.spoken)
        engine.done(0)
        assertEquals(listOf("a", "b"), engine.spoken)
    }

    @Test fun hungEngine_nextSpokenAfterTimeout() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine(); val v = voice(engine)
        v.say("a"); v.say("b")
        advanceTimeBy(9_999)
        assertEquals(listOf("a"), engine.spoken)
        advanceTimeBy(2)
        assertEquals(listOf("a", "b"), engine.spoken)
    }

    @Test fun lateDoneFromTimedOutLine_doesNotSkipNextTurn() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine(); val v = voice(engine)
        v.say("a"); v.say("b"); v.say("c")
        advanceTimeBy(10_001)
        assertEquals(listOf("a", "b"), engine.spoken)
        engine.done(0) // late, stale
        assertEquals(listOf("a", "b"), engine.spoken)
        engine.done(1)
        assertEquals(listOf("a", "b", "c"), engine.spoken)
    }

    @Test fun equalPriority_isFifo() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine(ready = false); val v = voice(engine)
        listOf("1", "2", "3").forEach { v.say(it, 5) }
        engine.setReady(true)
        engine.done(0); engine.done(1)
        assertEquals(listOf("1", "2", "3"), engine.spoken)
    }

    @Test fun throwingSpeak_countsAsDone_andTheQueueGoesOn() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine().apply { failOn = "a" }; val v = voice(engine)
        v.say("a") // throws inside the engine; say() itself must return normally
        assertTrue(v.lastError is IllegalStateException)
        v.say("b")
        assertEquals(listOf("b"), engine.spoken)
    }

    @Test fun stop_clearsQueue_andIgnoresInFlightDone() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine(); val v = voice(engine)
        v.say("a"); v.say("b")
        v.stop()
        assertEquals(1, engine.stops)
        engine.done(0)
        advanceTimeBy(20_000)
        assertEquals(listOf("a"), engine.spoken)
        v.say("c")
        assertEquals(listOf("a", "c"), engine.spoken)
    }
}
