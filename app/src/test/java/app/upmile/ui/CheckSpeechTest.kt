package app.upmile.ui

import app.upmile.core.check.CheckItem
import app.upmile.core.check.CheckResult
import app.upmile.core.check.CheckState
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckSpeechTest {
    private val doorsOk = CheckResult(CheckItem.DOORS, CheckState.OK, "문이 모두 닫혀 있어요.")
    private val doorsOpen = CheckResult(CheckItem.DOORS, CheckState.WARN, "문이 열려 있어요. 출발 전에 닫아 주세요.")
    private val beltWarn = CheckResult(CheckItem.BELT, CheckState.WARN, "안전벨트를 매 주세요.")
    private val beltOk = CheckResult(CheckItem.BELT, CheckState.OK, "안전벨트 경고등이 없어요.")
    private val mirror = CheckResult(CheckItem.SEAT_MIRROR, CheckState.INFO, "사이드미러에 내 차 옆면이 1/4쯤 보이게 맞춰 주세요.")
    private val noTelltales = CheckResult(CheckItem.TELLTALES, CheckState.INFO, "경고등 정보를 읽을 수 없어요.")
    private val silent = emptyList<CheckResult>()

    private fun CheckMemory.seen(vararg shown: CheckResult) = hear(shown.toList()).second

    @Test fun warnAppearing_isSpoken() = assertEquals(listOf(beltWarn), CheckMemory().seen(doorsOk).hear(listOf(doorsOk, beltWarn)).first)

    @Test fun okAndPlainInfo_areSilent() = assertEquals(silent, CheckMemory().hear(listOf(doorsOk, noTelltales, beltOk)).first)

    @Test fun unchangedWarn_isNotRepeated() = assertEquals(silent, CheckMemory().seen(beltWarn).hear(listOf(beltWarn)).first)

    @Test fun warnWithNewWording_isNotRepeated() {
        val r1 = CheckResult(CheckItem.ENERGY, CheckState.WARN, "주행 가능 거리 30km로 왕복 37km보다 짧아요. 충전 후 출발을 권해요.")
        assertEquals(silent, CheckMemory().seen(r1).hear(listOf(r1.copy(message = r1.message.replace("30", "29")))).first)
    }

    @Test fun okTurningWarn_isSpoken() = assertEquals(listOf(doorsOpen), CheckMemory().seen(doorsOk, beltOk).hear(listOf(doorsOpen, beltOk)).first)

    @Test fun warnAgainAfterOk_isSpokenAgain() = assertEquals(listOf(beltWarn), CheckMemory().seen(beltWarn).seen(beltOk).hear(listOf(beltWarn)).first)

    @Test fun seatMirrorTip_isSpokenOnce() {
        val afterTip = CheckMemory().seen(doorsOk, mirror)
        assertEquals("사이드미러에 내 차 옆면이 1/4쯤 보이게 맞춰 주세요.", afterTip.lastLine)
        assertEquals(silent, afterTip.hear(listOf(doorsOk, mirror)).first)
    }

    @Test fun reEntry_revealingAgainSaysNothingAlreadySaid() {
        // left CHECK and came back: the reveal starts empty and grows, nothing was cleared
        var memory = CheckMemory().seen(doorsOpen, beltOk, mirror)
        for (step in listOf(silent, listOf(doorsOpen), listOf(doorsOpen, beltOk), listOf(doorsOpen, beltOk, mirror))) {
            val (say, next) = memory.hear(step)
            assertEquals(silent, say)
            memory = next
        }
        assertEquals(mirror.message, memory.lastLine)
    }

    @Test fun lastLine_isTheLatestSpoken() = assertEquals(beltWarn.message, CheckMemory().seen(doorsOpen, beltWarn).lastLine)

    @Test fun coverComingOn_startsANewDeparture() {
        val cleared = CheckMemory().seen(doorsOpen, mirror).afterCover(coverOn = true)
        assertEquals(CheckMemory(), cleared)
        assertEquals(listOf(doorsOpen), cleared.hear(listOf(doorsOpen)).first)
    }

    @Test fun coverGoingOff_keepsMemory() {
        val memory = CheckMemory().seen(doorsOpen)
        assertEquals(memory, memory.afterCover(coverOn = false))
    }
}
