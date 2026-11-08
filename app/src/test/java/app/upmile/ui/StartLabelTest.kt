package app.upmile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StartLabelTest {
    @Test fun realMode_guidesToTheDestination() {
        assertEquals("회사로 안내 시작", startLabel("회사", demo = false, running = false))
        assertEquals("회사로 안내 중", startLabel("회사", demo = false, running = true))
    }

    @Test fun demoMode_startsTheReplayInstead() {
        assertEquals("체험 주행 시작", startLabel("회사", demo = true, running = false))
        assertEquals("체험 주행 중", startLabel("회사", demo = true, running = true))
    }
}
