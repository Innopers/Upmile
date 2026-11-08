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

    @Test fun warnAppearing_isSpoken() = assertEquals(listOf(beltWarn), toSpeak(listOf(doorsOk), listOf(doorsOk, beltWarn)))

    @Test fun okAndPlainInfo_areSilent() = assertEquals(emptyList<CheckResult>(), toSpeak(emptyList(), listOf(doorsOk, noTelltales, beltOk)))

    @Test fun unchangedWarn_isNotRepeated() = assertEquals(emptyList<CheckResult>(), toSpeak(listOf(beltWarn), listOf(beltWarn)))

    @Test fun warnWithNewWording_isNotRepeated() {
        val r1 = CheckResult(CheckItem.ENERGY, CheckState.WARN, "주행 가능 거리 30km로 왕복 37km보다 짧아요. 충전 후 출발을 권해요.")
        assertEquals(emptyList<CheckResult>(), toSpeak(listOf(r1), listOf(r1.copy(message = r1.message.replace("30", "29")))))
    }

    @Test fun okTurningWarn_isSpoken() = assertEquals(listOf(doorsOpen), toSpeak(listOf(doorsOk, beltOk), listOf(doorsOpen, beltOk)))

    @Test fun warnAgainAfterOk_isSpokenAgain() {
        val afterFix = toSpeak(listOf(beltWarn), listOf(beltOk))
        val again = toSpeak(listOf(beltOk), listOf(beltWarn))
        assertEquals(emptyList<CheckResult>() to listOf(beltWarn), afterFix to again)
    }

    @Test fun seatMirrorTip_isSpokenOnceWhenShown() {
        assertEquals(listOf(mirror), toSpeak(listOf(doorsOk), listOf(doorsOk, mirror)))
        assertEquals(emptyList<CheckResult>(), toSpeak(listOf(doorsOk, mirror), listOf(doorsOk, mirror)))
    }
}
