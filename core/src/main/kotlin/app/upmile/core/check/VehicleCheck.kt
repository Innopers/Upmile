package app.upmile.core.check

import app.upmile.core.vehicle.VehicleSnapshot
import java.util.Locale

enum class CheckItem { DOORS, TELLTALES, ENERGY, BELT, SEAT_MIRROR }
enum class CheckState { OK, WARN, INFO }
data class CheckResult(val item: CheckItem, val state: CheckState, val message: String)

object VehicleCheck {
    private const val SEAT_BELT = "SeatBelt"

    // Real warning lamps only (ids = CamelCase of the Vehicle SDK TelltaleType); indicators are ignored.
    private val WARNING_NAMES = mapOf(
        "Engine" to "엔진", "EngineOilPressure" to "엔진 오일 압력", "EngineTemperature" to "냉각수 온도",
        "Abs" to "브레이크 잠김 방지", "Esc" to "차체 자세 제어", "Mdps" to "조향 보조", "Tpms" to "타이어 공기압",
        "Airbag" to "에어백", "LowVoltBattery" to "보조 배터리", "MasterWarning" to "종합 경고",
        "EvSystem" to "전기차 시스템", "HevSystem" to "하이브리드 시스템", "LowEvBattery" to "구동 배터리 부족",
        "LowFuel" to "연료 부족", "DoorOpen" to "문 열림", "TrunkOpen" to "트렁크 열림",
    )

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
        val all = s.activeTelltales
            ?: return CheckResult(CheckItem.TELLTALES, CheckState.INFO, "경고등 정보를 읽을 수 없어요.")
        // seat belt is reported under BELT; ids outside the whitelist are indicators
        val names = all.filter { it != SEAT_BELT }.mapNotNull { WARNING_NAMES[it] }.distinct()
        return if (names.isEmpty()) CheckResult(CheckItem.TELLTALES, CheckState.OK, "켜진 경고등이 없어요.")
        else CheckResult(CheckItem.TELLTALES, CheckState.WARN, "경고등이 켜져 있어요: ${names.joinToString(", ")}. 확인해 주세요.")
    }

    private fun energy(s: VehicleSnapshot, routeKm: Double): CheckResult {
        val range = s.rangeKm ?: return CheckResult(CheckItem.ENERGY, CheckState.INFO, "주행 가능 거리를 확인할 수 없어요.")
        return if (range >= routeKm * 2) CheckResult(CheckItem.ENERGY, CheckState.OK, "주행 가능 거리 ${range}km로 왕복이 충분해요.")
        else CheckResult(CheckItem.ENERGY, CheckState.WARN, "주행 가능 거리 ${range}km로 왕복 ${String.format(Locale.US, "%.0f", routeKm * 2)}km보다 짧아요. 충전 후 출발을 권해요.")
    }

    // Telltale list is authoritative when available: the emulator's belt getter always reads false.
    // beltBuckled is used only when the telltale list is unavailable (null).
    private fun belt(s: VehicleSnapshot): CheckResult {
        val tt = s.activeTelltales
        return when {
            tt != null && SEAT_BELT in tt -> CheckResult(CheckItem.BELT, CheckState.WARN, "안전벨트를 매 주세요.")
            tt != null -> CheckResult(CheckItem.BELT, CheckState.OK, "안전벨트 경고등이 없어요.")
            s.beltBuckled == false -> CheckResult(CheckItem.BELT, CheckState.WARN, "안전벨트를 매 주세요.")
            s.beltBuckled == true -> CheckResult(CheckItem.BELT, CheckState.OK, "안전벨트를 맸어요.")
            else -> CheckResult(CheckItem.BELT, CheckState.INFO, "안전벨트 상태를 확인할 수 없어요.")
        }
    }
}
