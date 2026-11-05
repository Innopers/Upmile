package app.upmile.core.check

import app.upmile.core.vehicle.Gear
import app.upmile.core.vehicle.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCheckTest {
    private fun snap(
        doorsClosed: Boolean = true,
        activeTelltales: List<String>? = emptyList(),
        energyPercent: Int? = 62,
        rangeKm: Int? = 412,
        beltBuckled: Boolean? = true,
        gear: Gear = Gear.P,
        driverSeated: Boolean = true,
        timestampMs: Long = 0,
    ) = VehicleSnapshot(
        gear = gear, speedMps = 0f, brakePressed = false, steeringDeg = 0f,
        doorsClosed = doorsClosed, activeTelltales = activeTelltales, energyPercent = energyPercent,
        rangeKm = rangeKm, beltBuckled = beltBuckled, driverSeated = driverSeated, wiperOn = false,
        headlightsOn = false, outsideTempC = null, timestampMs = timestampMs,
    )

    private fun CheckItem.of(r: List<CheckResult>) = r.first { it.item == this }

    @Test fun allGood() {
        val r = VehicleCheck.run(snap(doorsClosed = true, activeTelltales = emptyList(), energyPercent = 62, rangeKm = 412, beltBuckled = true), routeKm = 18.2)
        assertTrue(r.filter { it.item != CheckItem.SEAT_MIRROR }.all { it.state == CheckState.OK })
    }

    @Test fun openDoor_and_seatbeltTelltale_warn() {
        val r = VehicleCheck.run(snap(doorsClosed = false, activeTelltales = listOf("SeatBelt"), beltBuckled = null), 18.2)
        assertEquals(CheckState.WARN, CheckItem.DOORS.of(r).state)
        assertEquals(CheckState.WARN, CheckItem.BELT.of(r).state) // belt falls back to telltale
    }

    @Test fun belt_listAvailable_ignoresGetter() {
        // emulator getter always reads false; with the telltale list available, only SeatBelt matters
        assertEquals(CheckState.OK, CheckItem.BELT.of(VehicleCheck.run(snap(beltBuckled = false, activeTelltales = emptyList()), 18.2)).state)
    }

    @Test fun belt_listAvailable_seatBeltTelltaleWarns() {
        assertEquals(CheckState.WARN, CheckItem.BELT.of(VehicleCheck.run(snap(beltBuckled = true, activeTelltales = listOf("SeatBelt")), 18.2)).state)
    }

    @Test fun belt_listNull_getterFalseWarns() {
        assertEquals(CheckState.WARN, CheckItem.BELT.of(VehicleCheck.run(snap(beltBuckled = false, activeTelltales = null), 18.2)).state)
    }

    @Test fun belt_listNull_getterTrueOk() {
        assertEquals(CheckState.OK, CheckItem.BELT.of(VehicleCheck.run(snap(beltBuckled = true, activeTelltales = null), 18.2)).state)
    }

    @Test fun belt_listNull_getterNullIsInfo() {
        assertEquals(CheckState.INFO, CheckItem.BELT.of(VehicleCheck.run(snap(beltBuckled = null, activeTelltales = null), 18.2)).state)
    }

    @Test fun telltales_listNull_isInfo() {
        assertEquals(CheckState.INFO, CheckItem.TELLTALES.of(VehicleCheck.run(snap(activeTelltales = null), 18.2)).state)
    }

    @Test fun energy_boundaryRangeEqualsTwiceRoute_isOk() {
        assertEquals(CheckState.OK, CheckItem.ENERGY.of(VehicleCheck.run(snap(rangeKm = 36), routeKm = 18.0)).state)
    }

    @Test fun otherTelltale_warnsTelltalesItem() {
        val r = VehicleCheck.run(snap(activeTelltales = listOf("TirePressure")), 18.2)
        assertEquals(CheckState.WARN, CheckItem.TELLTALES.of(r).state)
    }

    @Test fun energy_warnsWhenRangeBelowTwiceRoute() {
        assertEquals(CheckState.WARN, CheckItem.ENERGY.of(VehicleCheck.run(snap(rangeKm = 30), routeKm = 18.2)).state)
    }

    @Test fun unknownEnergy_isInfo_notWarn() {
        assertEquals(CheckState.INFO, CheckItem.ENERGY.of(VehicleCheck.run(snap(energyPercent = null, rangeKm = null), 18.2)).state)
    }

    @Test fun seatMirror_isAlwaysInfoWithExactMessage() {
        val m = CheckItem.SEAT_MIRROR.of(VehicleCheck.run(snap(), 18.2))
        assertEquals(CheckState.INFO, m.state)
        assertEquals("사이드미러에 내 차 옆면이 1/4쯤 보이게 맞춰 주세요.", m.message)
    }

    @Test fun messages_avoidScoreAndEvaluationWords() {
        val all = VehicleCheck.run(snap(doorsClosed = false, activeTelltales = listOf("TirePressure"), rangeKm = 30, beltBuckled = false), 18.2)
        all.forEach { r -> assertFalse(r.message, r.message.contains("점수") || r.message.contains("평가")) }
    }
}
