package app.upmile.core.check

import app.upmile.core.vehicle.VehicleSnapshot

enum class CheckItem { DOORS, TELLTALES, ENERGY, BELT, SEAT_MIRROR }
enum class CheckState { OK, WARN, INFO }
data class CheckResult(val item: CheckItem, val state: CheckState, val message: String)

object VehicleCheck {
    private const val SEAT_BELT = "SeatBelt"

    fun run(s: VehicleSnapshot, routeKm: Double): List<CheckResult> = listOf(
        doors(s),
        telltales(s),
        energy(s, routeKm),
        belt(s),
        CheckResult(CheckItem.SEAT_MIRROR, CheckState.INFO, "사이드미러에 내 차 옆면이 1/4쯤 보이게 맞춰 주세요."),
    )

    private fun doors(s: VehicleSnapshot) =
        if (s.doorsClosed) CheckResult(CheckItem.DOORS, CheckState.OK, "문이 모두 닫혀 있어요.")
        else CheckResult(CheckItem.DOORS, CheckState.WARN, "문이 열려 있어요. 출발 전에 닫아 주세요.")

    private fun telltales(s: VehicleSnapshot): CheckResult {
        val others = s.activeTelltales.filter { it != SEAT_BELT } // seat belt is reported under BELT
        return if (others.isEmpty()) CheckResult(CheckItem.TELLTALES, CheckState.OK, "켜진 경고등이 없어요.")
        else CheckResult(CheckItem.TELLTALES, CheckState.WARN, "경고등이 켜져 있어요: ${others.joinToString()}. 확인해 주세요.")
    }

    private fun energy(s: VehicleSnapshot, routeKm: Double): CheckResult {
        val range = s.rangeKm ?: return CheckResult(CheckItem.ENERGY, CheckState.INFO, "주행 가능 거리를 확인할 수 없어요.")
        return if (range >= routeKm * 2) CheckResult(CheckItem.ENERGY, CheckState.OK, "주행 가능 거리 ${range}km로 왕복이 충분해요.")
        else CheckResult(CheckItem.ENERGY, CheckState.WARN, "주행 가능 거리 ${range}km로 왕복 ${"%.0f".format(routeKm * 2)}km보다 짧아요. 충전 후 출발을 권해요.")
    }

    // Telltale is checked first: the emulator's belt getter always reads false.
    private fun belt(s: VehicleSnapshot): CheckResult = when {
        SEAT_BELT in s.activeTelltales -> CheckResult(CheckItem.BELT, CheckState.WARN, "안전벨트를 매 주세요.")
        s.beltBuckled == false -> CheckResult(CheckItem.BELT, CheckState.WARN, "안전벨트를 매 주세요.")
        else -> CheckResult(CheckItem.BELT, CheckState.OK, "안전벨트를 맸어요.")
    }
}
