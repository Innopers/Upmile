package app.upmile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewPlaybackTest {
    private val cues = listOf(.1f to "진입로 주행", .5f to "가속 차로", .9f to "합류 완료")
    private val run = 20_000L

    @Test fun advance_movesByElapsedOverRunTime() = assertEquals(.25f, Playback().step(5_000, run, cues).playback.progress, 1e-6f)

    @Test fun slow_movesAtHalfSpeed() =
        assertEquals(.125f, Playback().slowToggled().step(5_000, run, cues).playback.progress, 1e-6f)

    @Test fun paused_staysPut() {
        val paused = Playback(progress = .3f).toggled()
        assertFalse(paused.playing)
        assertEquals(paused, paused.step(5_000, run, cues).playback)
    }

    @Test fun reachingTheEnd_stopsAtOne() {
        val p = Playback(progress = .95f).step(5_000, run, cues).playback
        assertEquals(1f, p.progress)
        assertFalse(p.playing)
        assertTrue(p.finished)
    }

    @Test fun rehearsed_onlyWhenTheRunReachesTheEnd() {
        assertFalse(Playback(progress = .5f).step(9_000, run, cues).rehearsed) // .95
        assertTrue(Playback(progress = .95f).step(1_000, run, cues).rehearsed)
        // pausing, restarting or sitting at the end never counts again
        assertFalse(Playback(progress = .5f).toggled().step(60_000, run, cues).rehearsed)
        assertFalse(Playback(progress = .99f).restarted().step(1_000, run, cues).rehearsed)
        assertFalse(Playback(progress = 1f, playing = false).step(1_000, run, cues).rehearsed)
    }

    @Test fun eachCue_isSpokenOnceWhenReached() {
        var p = Playback()
        val said = mutableListOf<String>()
        repeat(40) { p = p.step(500, run, cues).also { said += it.say }.playback }
        assertEquals(cues.map { it.second }, said)
    }

    @Test fun aBigStep_speaksEveryCuePassed() =
        assertEquals(listOf("진입로 주행", "가속 차로"), Playback().step(12_000, run, cues).say)

    @Test fun restart_speaksTheCuesAgain() {
        val end = Playback().step(run, run, cues).playback
        assertEquals(2, end.cue)
        assertEquals(listOf("진입로 주행"), end.restarted().step(2_500, run, cues).say)
    }

    @Test fun playAtTheEnd_restartsFromTheTop() {
        val again = Playback(progress = 1f, playing = false, slow = true, cue = 2).toggled()
        assertEquals(Playback(slow = true), again)
    }

    @Test fun restart_keepsTheSpeed() = assertTrue(Playback(progress = .4f, slow = true).restarted().slow)

    @Test fun next_walksTheListThenStops() {
        assertEquals(1, nextSegment(0, 3))
        assertEquals(2, nextSegment(1, 3))
        assertNull(nextSegment(2, 3))
    }
}
