package app.upmile.core.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadyCheckTest {
    @Test fun missingLocationPermission_blocksDrive_withKoreanWarning() {
        val s = ReadyCheck.evaluate(voiceReady = true, locationPermitted = false, speedPermitted = true, serviceRunning = true)
        assertFalse(s.canDrive); assertEquals(listOf("위치 권한이 없어 주행 중 음성 안내를 할 수 없어요."), s.warnings)
    }

    @Test fun voiceNotReady_allowsDrive_butWarns() {
        val s = ReadyCheck.evaluate(voiceReady = false, locationPermitted = true, speedPermitted = true, serviceRunning = true)
        assertTrue(s.canDrive); assertEquals(1, s.warnings.size)
    }

    @Test fun speedNotPermitted_allowsDrive_warnsWithoutScoreWords() {
        val s = ReadyCheck.evaluate(true, true, false, true)
        assertTrue(s.canDrive); assertEquals(1, s.warnings.size)
        assertTrue("급제동 감지" in s.warnings.single() && listOf("점수", "평가").none { it in s.warnings.single() })
    }

    @Test fun serviceNotRunning_blocksDrive_withWarning() {
        val s = ReadyCheck.evaluate(true, true, true, false)
        assertFalse(s.canDrive); assertEquals(1, s.warnings.size)
    }

    @Test fun demoMode_treatsPermissionsAsGranted_butStillReportsVoiceAndService() {
        val ok = ReadyCheck.evaluate(true, false, false, true, demoMode = true)
        assertTrue(ok.canDrive); assertTrue(ok.warnings.isEmpty())
        val s = ReadyCheck.evaluate(false, false, false, false, demoMode = true)
        assertFalse(s.canDrive); assertEquals(2, s.warnings.size)
    }
}
