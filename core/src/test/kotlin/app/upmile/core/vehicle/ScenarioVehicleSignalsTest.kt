package app.upmile.core.vehicle

import app.upmile.core.drive.DriveEvent
import app.upmile.core.drive.DriveTracker
import app.upmile.core.model.GeoPoint
import app.upmile.core.model.SegmentType
import app.upmile.core.route.SegmentFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class ScenarioVehicleSignalsTest {
    private fun readAsset(path: String) = javaClass.classLoader.getResource(path)!!.readText()
    private fun frames() = ScenarioJson.decode(readAsset("scenarios/commute_drive.json"))

    private fun frame(t: Long, gear: Gear, lat: Double = 0.0, lon: Double = 0.0) = ScenarioFrame(
        VehicleSnapshot(gear, 0f, false, 0f, true, emptyList(), null, null, true, true, false, false, null, t), lat, lon)

    @Test fun replay_emitsFrameMatchingClock() {
        var now = 1_000L
        val s = ScenarioVehicleSignals(listOf(frame(0, Gear.P), frame(5_000, Gear.D, 1.0, 2.0)), clock = { now })
        s.start(); assertEquals(Gear.P, s.snapshots.value.gear)
        now = 6_000; s.tick(); assertEquals(Gear.D, s.snapshots.value.gear)
        assertEquals(2.0, s.frame.value.lon, 0.0)
        now = 99_000; s.tick(); assertEquals(Gear.D, s.snapshots.value.gear) // stays on the last frame
    }

    @Test fun scenarioJson_roundTrips() {
        val frames = frames()
        assertTrue(frames.first().snapshot.gear == Gear.P && frames.any { it.snapshot.gear == Gear.D } && frames.last().snapshot.gear == Gear.P)
        assertEquals(frames, ScenarioJson.decode(ScenarioJson.encode(frames)))
        assertTrue(frames.last().snapshot.timestampMs <= 150_000)
    }

    @Test fun timestamps_mustAscend() {
        assertThrows(IllegalArgumentException::class.java) {
            ScenarioVehicleSignals(listOf(frame(200, Gear.P), frame(200, Gear.D)), clock = { 0L })
        }
    }

    @Test fun scenario_hasOneHardBrakeRun_ofAtLeast3BrakedSteps() {
        val d = frames().map { it.snapshot }.filter { it.gear == Gear.D }
        val steps = d.zipWithNext()
        val decel = steps.map { (a, b) -> (a.speedMps - b.speedMps) / ((b.timestampMs - a.timestampMs) / 1000f) }
        val hard = decel.indices.filter { decel[it] >= 2.9f }
        assertTrue(hard.toString(), hard.size >= 3 && hard.last() - hard.first() == hard.size - 1) // one consecutive run
        assertTrue(hard.all { steps[it].second.brakePressed })
        assertTrue(decel.max() >= 3.5f)
        assertTrue(steps.all { (a, b) -> abs(b.steeringDeg - a.steeringDeg) / 0.2f < 90f })
        assertTrue(d.all { it.speedMps <= 22f })
    }

    @Test fun scenario_agreesWithDriveTracker() {
        val route = ScenarioGenerator.load(::readAsset)
        val segs = SegmentFinder.find(route, true)
        val tracker = DriveTracker(route, segs)
        val events = frames().flatMap { tracker.onSample(it.snapshot, GeoPoint(it.lat, it.lon)) }
        fun id(t: SegmentType) = segs.first { it.type == t }.id
        val merge = id(SegmentType.HIGHWAY_MERGE); val left = id(SegmentType.MULTILANE_LEFT); val garage = id(SegmentType.GARAGE_RAMP)
        val approaching = events.filterIsInstance<DriveEvent.Approaching>().map { it.segment.id }
        assertTrue(merge in approaching && left in approaching)
        val passed = events.filterIsInstance<DriveEvent.Passed>().associate { it.segment.id to it.hadHardMoment }
        assertEquals(true, passed[left]); assertEquals(false, passed[merge]); assertTrue(garage in passed)
        assertEquals(1, events.count { it is DriveEvent.Arrived })
    }

    @Test fun hardBrake_survivesDroppingEveryOtherFrame_inBothPhases() {
        val route = ScenarioGenerator.load(::readAsset)
        val segs = SegmentFinder.find(route, true)
        val left = segs.first { it.type == SegmentType.MULTILANE_LEFT }.id
        for (phase in 0..1) {
            val tracker = DriveTracker(route, segs)
            val events = frames().filterIndexed { i, _ -> i % 2 == phase }.flatMap { tracker.onSample(it.snapshot, GeoPoint(it.lat, it.lon)) }
            val pass = events.filterIsInstance<DriveEvent.Passed>().single { it.segment.id == left }
            assertEquals("phase $phase", true, pass.hadHardMoment)
        }
    }

    /** Regenerates the committed asset: GEN_SCENARIO=1 ./gradlew :core:test --tests '*regenerate*' */
    @Test fun regenerate_asset_whenRequested() {
        if (System.getenv("GEN_SCENARIO") != "1") return
        val route = ScenarioGenerator.load(::readAsset)
        File("../assets/scenarios").mkdirs()
        File("../assets/scenarios/commute_drive.json").writeText(ScenarioJson.encode(ScenarioGenerator.generate(route)))
    }
}
