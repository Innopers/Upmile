package app.upmile.core.drive

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.Route
import app.upmile.core.model.SegmentType
import app.upmile.core.route.alongRouteM
import app.upmile.core.vehicle.Gear
import app.upmile.core.vehicle.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveTrackerTest {
    private val mPerDeg = 111_195.0
    private fun at(m: Double, offLon: Double = 0.0) = GeoPoint(37.0 + m / mPerDeg, 127.0 + offLon)
    private val route = Route("r", "r", listOf(at(0.0), at(5000.0)), emptyList(), 5000, 600)
    private val merge = HardSegment("merge", SegmentType.HIGHWAY_MERGE, "m", at(2000.0), 2000, emptyMap())
    private val ramp = HardSegment("ramp", SegmentType.GARAGE_RAMP, "g", at(4900.0), 4900, emptyMap())

    private fun snap(t: Long, speed: Float = 10f, brake: Boolean = false, steer: Float = 0f,
                     gear: Gear = Gear.D, seated: Boolean = true) =
        VehicleSnapshot(gear, speed, brake, steer, true, emptyList(), null, null, true, seated, false, false, null, t)

    @Test fun alongRouteM_onStraightRoute() {
        val r = Route("s", "s", listOf(GeoPoint(37.0, 127.0), GeoPoint(37.01, 127.0)), emptyList(), 1112, 100)
        assertEquals(1112.0, alongRouteM(r, GeoPoint(37.01, 127.0)), 5.0)
        assertEquals(556.0, alongRouteM(r, GeoPoint(37.005, 127.0001)), 5.0)
    }

    @Test fun approaching_firesOnceAt1200m() {
        val tr = DriveTracker(route, listOf(merge))
        assertTrue(tr.onSample(snap(0), at(700.0)).isEmpty())
        val ev = tr.onSample(snap(200), at(805.0)).single() as DriveEvent.Approaching
        assertEquals(1195, ev.metersAhead)
        assertTrue(tr.onSample(snap(400), at(900.0)).isEmpty())
    }

    @Test fun passed_whenAlongBeyondSegmentPlus150m_withoutHardMoment() {
        val tr = DriveTracker(route, listOf(merge))
        tr.onSample(snap(0), at(2100.0))
        assertTrue(tr.onSample(snap(200), at(2140.0)).none { it is DriveEvent.Passed })
        val ev = tr.onSample(snap(400), at(2151.0)).single { it is DriveEvent.Passed } as DriveEvent.Passed
        assertEquals(false, ev.hadHardMoment)
        assertTrue(tr.onSample(snap(600), at(2300.0)).isEmpty())
    }

    private fun brakeRun(stepMs: Long): Boolean {
        val tr = DriveTracker(route, listOf(merge))
        // 15 -> 12 m/s over 1 s, in steps of stepMs, inside the window
        tr.onSample(snap(0, speed = 15f, brake = true), at(1990.0))
        var t = 0L
        while (t < 1000) {
            t += stepMs
            tr.onSample(snap(t, speed = 15f - 3f * t / 1000f, brake = true), at(1990.0 + t / 100.0))
        }
        val ev = tr.onSample(snap(t + 200, speed = 12f), at(2200.0)).filterIsInstance<DriveEvent.Passed>().single()
        return ev.hadHardMoment
    }

    @Test fun hardBrake_isDecelAtLeast2_9mps2_whileBraking() {
        assertTrue(brakeRun(200))
    }

    @Test fun hardBrake_threshold_2_5_withBrakeIsNotHard() {
        val tr = DriveTracker(route, listOf(merge))
        tr.onSample(snap(0, speed = 15f, brake = true), at(2000.0))
        tr.onSample(snap(1000, speed = 12.5f, brake = true), at(2010.0))
        val ev = tr.onSample(snap(1200), at(2200.0)).filterIsInstance<DriveEvent.Passed>().single()
        assertEquals(false, ev.hadHardMoment)
    }

    @Test fun nearDuplicateSample_dt10ms_isNotHard() {
        val tr = DriveTracker(route, listOf(merge))
        tr.onSample(snap(0, speed = 15f, brake = true), at(2000.0))
        tr.onSample(snap(10, speed = 14.7f, brake = true), at(2000.1))
        val ev = tr.onSample(snap(200, speed = 14.7f), at(2200.0)).filterIsInstance<DriveEvent.Passed>().single()
        assertEquals(false, ev.hadHardMoment)
    }

    // spike between two samples whose along positions are [from, to] (relative to merge start 2000)
    private fun spikeVerdict(from: Double, to: Double): Boolean {
        val tr = DriveTracker(route, listOf(merge))
        val evs = tr.onSample(snap(0, speed = 15f, brake = true), at(2000.0 + from) ) +
            tr.onSample(snap(1000, speed = 10f, brake = true), at(2000.0 + to)) +
            tr.onSample(snap(1200), at(2300.0))
        return evs.filterIsInstance<DriveEvent.Passed>().single().hadHardMoment
    }

    @Test fun hardMoment_windowEdges_useTravelledInterval() {
        assertEquals(false, spikeVerdict(-300.0, -51.0))
        assertEquals(true, spikeVerdict(-300.0, -49.0))
        assertEquals(true, spikeVerdict(149.0, 200.0))
        assertEquals(false, spikeVerdict(151.0, 200.0))
        assertEquals(true, spikeVerdict(-200.0, 250.0)) // jump straddles whole window
    }

    @Test fun hardBrake_notWithoutBrakePedal() {
        val tr = DriveTracker(route, listOf(merge))
        tr.onSample(snap(0, speed = 15f), at(2000.0))
        tr.onSample(snap(1000, speed = 12f), at(2010.0))
        val ev = tr.onSample(snap(1200), at(2200.0)).filterIsInstance<DriveEvent.Passed>().single()
        assertEquals(false, ev.hadHardMoment)
    }

    @Test fun hardBrake_verdictIndependentOfSamplingRate() {
        assertEquals(brakeRun(100), brakeRun(400))
        assertTrue(brakeRun(100))
    }

    @Test fun hardSteer_isSteeringRateAbove90degPerSec() {
        val tr = DriveTracker(route, listOf(merge))
        tr.onSample(snap(0, steer = 0f), at(2000.0))
        tr.onSample(snap(200, steer = 20f), at(2010.0)) // 100 deg/s
        val ev = tr.onSample(snap(400), at(2200.0)).filterIsInstance<DriveEvent.Passed>().single()
        assertEquals(true, ev.hadHardMoment)

        val tr2 = DriveTracker(route, listOf(merge))
        tr2.onSample(snap(0, steer = 0f), at(2000.0))
        tr2.onSample(snap(200, steer = 15f), at(2010.0)) // 75 deg/s
        assertEquals(false, (tr2.onSample(snap(400), at(2200.0)).single { it is DriveEvent.Passed } as DriveEvent.Passed).hadHardMoment)
    }

    @Test fun hardMoment_outsideWindowIgnored() {
        val tr = DriveTracker(route, listOf(merge))
        tr.onSample(snap(0, speed = 15f, brake = true), at(1000.0))
        tr.onSample(snap(1000, speed = 10f, brake = true), at(1010.0))
        tr.onSample(snap(1200), at(2100.0))
        val ev = tr.onSample(snap(1400), at(2200.0)).filterIsInstance<DriveEvent.Passed>().single()
        assertEquals(false, ev.hadHardMoment)
    }

    @Test fun nonPositiveTimeDelta_isIgnored() {
        val tr = DriveTracker(route, listOf(merge))
        tr.onSample(snap(1000, speed = 15f, brake = true), at(2000.0))
        tr.onSample(snap(1000, speed = 0f, brake = true), at(2000.0))
        val ev = tr.onSample(snap(1200), at(2200.0)).filterIsInstance<DriveEvent.Passed>().single()
        assertEquals(false, ev.hadHardMoment)
    }

    private fun drive(tr: DriveTracker, seated: Boolean, endM: Double = 4950.0): List<DriveEvent> {
        var t = 0L
        var m = 0.0
        while (m < endM) { tr.onSample(snap(t), at(m)); t += 200; m += 20.0 }
        return tr.onSample(snap(t, gear = Gear.P, seated = seated), at(endM))
    }

    @Test fun arrived_requiresDtoP_driverSeated_andMoved300m() {
        val tr = DriveTracker(route, listOf(ramp))
        val ev = drive(tr, seated = true)
        assertEquals(2, ev.size)
        assertEquals("ramp", (ev[0] as DriveEvent.Passed).segment.id)
        val a = ev[1] as DriveEvent.Arrived
        assertEquals(4950.0, a.drivenM.toDouble(), 30.0)
        assertTrue(tr.onSample(snap(99_999, gear = Gear.P), at(4950.0)).isEmpty())
    }

    @Test fun arrived_notFromGpsJitterWhileStopped() {
        val tr = DriveTracker(route, listOf(ramp))
        var t = 0L
        repeat(300) { i ->
            val j = if (i % 2 == 0) 3.0 else -3.0
            tr.onSample(snap(t, speed = 0f), at(1000.0 + j)); t += 200
        }
        assertTrue(tr.onSample(snap(t, gear = Gear.P), at(1000.0)).none { it is DriveEvent.Arrived })
    }

    @Test fun ramp_notPassedByDistance() {
        val r2 = HardSegment("ramp2", SegmentType.GARAGE_RAMP, "g", at(4800.0), 4800, emptyMap())
        val tr = DriveTracker(route, listOf(r2))
        tr.onSample(snap(0), at(4700.0))
        assertTrue(tr.onSample(snap(200), at(4990.0)).none { it is DriveEvent.Passed })
    }

    @Test fun arrived_notBefore300mMoved() {
        val tr = DriveTracker(route, listOf(ramp))
        assertTrue(drive(tr, seated = true, endM = 200.0).isEmpty())
    }

    @Test fun arrived_notFromNonD() {
        val tr = DriveTracker(route, listOf(ramp))
        var t = 0L
        var m = 0.0
        while (m < 1000) { tr.onSample(snap(t, gear = if (m < 500) Gear.D else Gear.N), at(m)); t += 200; m += 20.0 }
        assertTrue(tr.onSample(snap(t, gear = Gear.P), at(1000.0)).isEmpty())
    }

    @Test fun arrived_neverFiresWhenDriverNotSeated() {
        val tr = DriveTracker(route, listOf(ramp))
        assertTrue(drive(tr, seated = false).none { it is DriveEvent.Arrived })
    }

    @Test fun offRoutePosition_beyond80m_isIgnoredForApproach() {
        val tr = DriveTracker(route, listOf(merge))
        // 0.002 deg lon ~ 177 m off the line
        assertTrue(tr.onSample(snap(0), at(1000.0, offLon = 0.002)).isEmpty())
        assertEquals(1, tr.onSample(snap(200), at(1000.0, offLon = 0.0003)).size) // ~27 m off: counts
    }
}
