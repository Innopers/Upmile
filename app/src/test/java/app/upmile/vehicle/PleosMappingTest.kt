package app.upmile.vehicle

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import app.upmile.core.vehicle.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PleosMappingTest {
    @Test fun gear_mapsSdkConstants() {
        assertEquals(Gear.P, mapGear(VehicleGear.GEAR_PARK))
        assertEquals(Gear.R, mapGear(VehicleGear.GEAR_REVERSE))
        assertEquals(Gear.N, mapGear(VehicleGear.GEAR_NEUTRAL))
        assertEquals(Gear.D, mapGear(VehicleGear.GEAR_DRIVE))
        assertEquals(Gear.D, mapGear(VehicleGear.GEAR_3))
        assertEquals(Gear.UNKNOWN, mapGear(VehicleGear.GEAR_UNKNOWN))
        assertEquals(Gear.UNKNOWN, mapGear(null))
    }

    @Test fun doors_closedOnlyWhenEveryPositionIsZero() {
        assertTrue(doorsClosed(listOf(0, 0, 0, 0)))
        assertFalse(doorsClosed(listOf(0, 1, 0, 0)))
        assertTrue(doorsClosed(emptyList()))
    }

    @Test fun seat_xorsWithInvertFlag() {
        assertTrue(driverSeated(occupied = true, invert = false))
        assertFalse(driverSeated(occupied = true, invert = true))
        assertTrue(driverSeated(occupied = false, invert = true))
    }

    @Test fun steering_flipsSign() = assertEquals(-90f, steeringDeg(90f), 0f)

    @Test fun range_convertsMetresToKm() = assertEquals(150, rangeKm(150_000f))

    @Test fun energy_prefersEvThenFuel() {
        assertEquals(60, energyPercent(evSoc = 60, fuel = 30))
        assertEquals(30, energyPercent(evSoc = null, fuel = 30))
        assertNull(energyPercent(null, null))
    }

    @Test fun telltales_nullMeansUnavailable_emptyMeansNoneOn() {
        assertNull(activeTelltaleIds(null))
        assertEquals(emptyList<String>(), activeTelltaleIds(listOf("SEAT_BELT" to false)))
        assertEquals(listOf("SeatBelt", "DoorOpen"), activeTelltaleIds(listOf("SEAT_BELT" to true, "ABS" to false, "DOOR_OPEN" to true)))
    }
}
