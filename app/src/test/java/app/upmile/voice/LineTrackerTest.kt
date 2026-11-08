package app.upmile.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class LineTrackerTest {
    private class Fake : Scheduler {
        var time = 0L
        private class Task(val due: Long, val run: () -> Unit) { var cancelled = false }
        private val tasks = mutableListOf<Task>()
        override fun schedule(delayMs: Long, task: () -> Unit): () -> Unit {
            val t = Task(time + delayMs, task); tasks += t; return { t.cancelled = true }
        }
        fun advance(ms: Long) {
            val end = time + ms
            while (true) {
                val next = tasks.filter { !it.cancelled && it.due <= end }.minByOrNull { it.due } ?: break
                tasks.remove(next); time = next.due; next.run()
            }
            time = end
        }
    }

    private val fake = Fake()
    private val tracker = LineTracker({ fake.time }, fake)
    private val ended = mutableListOf<String>()
    private fun speak(name: String, text: String = "abc") = tracker.speak(text) { ended += name }

    @Test fun sdkDone_endsLineBeforeTimer() {
        speak("A"); tracker.sdkDone()
        assertEquals(listOf("A"), ended)
        fake.advance(60_000); assertEquals(listOf("A"), ended)
    }

    @Test fun timer_endsLine_lateDoneIsConsumedAndDoesNotEndNext() {
        speak("A"); fake.advance(14_000)                 // 1200+600 + 12000 first-speak allowance
        assertEquals(listOf("A"), ended)
        speak("B"); tracker.sdkDone()                     // A's late done
        assertEquals(listOf("A"), ended)
        fake.advance(1_800); assertEquals(listOf("A", "B"), ended)
        tracker.sdkDone(); assertEquals(listOf("A", "B"), ended)
    }

    @Test fun noDoneEver_linesEndByTimerInOrder() {
        speak("A"); fake.advance(13_800)
        speak("B"); fake.advance(1_800)
        speak("C"); fake.advance(1_800)
        assertEquals(listOf("A", "B", "C"), ended)
    }

    @Test fun stop_thenLateDoneDoesNotEndNextLine() {
        speak("A"); tracker.stop()
        assertEquals(listOf("A"), ended)
        speak("B"); tracker.sdkDone()                     // cut utterance's done, inside the 1 s window
        assertEquals(listOf("A"), ended)
        fake.advance(1_100); tracker.sdkDone()            // B's own done
        assertEquals(listOf("A", "B"), ended)
    }

    @Test fun firstSpeak_getsModelLoadAllowance_secondDoesNot() {
        speak("A", ""); fake.advance(13_199); assertEquals(emptyList<String>(), ended)
        fake.advance(1); assertEquals(listOf("A"), ended)
        speak("B", ""); fake.advance(1_199); assertEquals(listOf("A"), ended)
        fake.advance(1); assertEquals(listOf("A", "B"), ended)
    }

    @Test fun estimate_isCapped() {
        speak("A"); tracker.sdkDone()
        speak("B", "x".repeat(500)); fake.advance(19_999); assertEquals(listOf("A"), ended)
        fake.advance(1); assertEquals(listOf("A", "B"), ended)
    }

    @Test fun timerAndDoneRacing_endExactlyOnce() {
        speak("A"); fake.advance(13_799); tracker.sdkDone(); fake.advance(1)
        assertEquals(listOf("A"), ended)
        speak("B"); fake.advance(1_800); tracker.sdkDone() // timer first, then B's done
        assertEquals(listOf("A", "B"), ended)
    }
}
