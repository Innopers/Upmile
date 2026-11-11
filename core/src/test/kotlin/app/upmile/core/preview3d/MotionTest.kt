package app.upmile.core.preview3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    // 100 m at 10 m/s, then 10 m to a stop, a 5 s stop, then 10 m at an average of 5 m/s.
    private val pace = Pace(10f).go(100f).go(10f, 0f).stop(5f).go(10f, 10f)
    private val path = listOf(v(0f, 0f, EYE), v(0f, -120f, EYE))
    private val spec = SceneSpec(
        surfaces = listOf(
            Ribbon(listOf(v(0f, 10f), v(0f, -200f)), 7f, Mat.ASPHALT),
            Ribbon(listOf(v(0f, 10f), v(0f, -200f)), Kr.LINE, Mat.YELLOW_PAINT, lift = .01f),
            Ribbon(listOf(v(0f, 10f), v(0f, -200f)), 30f, Mat.GRASS, lift = -.03f),
        ),
        patches = emptyList(),
        solids = listOf(Extrusion(listOf(v(0f, 0f), v(0f, -50f)), listOf(3.25f to 0f, 3.55f to 0f, 3.55f to 3f, 3.25f to 3f), Mat.WALL)),
        props = emptyList(),
        cars = listOf(Car(CarModel.SEDAN, Paint.WHITE, listOf(v(3f, 0f), v(3f, -500f)), listOf(Key(0f, 20f), Key(1f, 220f)))),
        camera = Timeline(path, pace.keys()),
        cues = listOf(.1f to "가", .9f to "나"),
        durationMs = (pace.sec * 1000).toLong(),
        signal = listOf(SignalPhase(0f, setOf(Lamp.RED)), SignalPhase(.6f, setOf(Lamp.RED, Lamp.ARROW))),
        indoorFromM = 100f,
    )
    private fun tOf(sec: Float) = sec / pace.sec

    @Test fun distanceAndRemaining_followTheKeysNotTheClock() {
        assertEquals(50f, spec.distanceAt(tOf(5f)), 1e-3f)
        assertEquals(110f, spec.distanceAt(tOf(14f)), 1e-3f) // standing still
        assertEquals(10f, spec.remainingM(tOf(14f)), 1e-3f)
        assertEquals(110f / 120f, spec.arcFraction(tOf(14f)), 1e-4f)
    }

    @Test fun cameraLooksAheadAndSlightlyDown() {
        val (eye, look) = spec.cameraAt(tOf(5f))
        assertEquals(v(0f, -50f, EYE), eye)
        assertEquals(-50f - LOOK_AHEAD, look.z, 1e-3f)
        assertEquals(EYE - LOOK_DROP, look.y, 1e-4f)
    }

    @Test fun cameraLooksAheadPastTheEndOfItsPath() {
        val (_, look) = spec.cameraAt(1f)
        assertEquals(-120f - LOOK_AHEAD, look.z, 1e-3f)
    }

    @Test fun carPose_isOnItsPathFacingItsWay() {
        val p = spec.cars[0].poseAt(.5f)
        assertEquals(v(3f, -120f), p.at); assertEquals(v(0f, -1f), p.dir)
    }

    @Test fun signalAt_lastPhaseNotAfterT() {
        assertEquals(setOf(Lamp.RED), spec.signalAt(.3f))
        assertEquals(setOf(Lamp.RED, Lamp.ARROW), spec.signalAt(.6f))
        assertEquals(emptySet<Lamp>(), spec.copy(signal = emptyList()).signalAt(.5f))
    }

    @Test fun indoorRampsOverTenMetresFromTheRoofLine() {
        assertEquals(0f, spec.indoorAt(tOf(5f)), 0f)     // 50 m
        assertEquals(1f, spec.indoorAt(tOf(14f)), 0f)    // 110 m
        assertEquals(.5f, spec.indoorAt(pace.secAt(105f) / pace.sec), 1e-3f) // 105 m
    }

    @Test fun flat_keepsGroundStripsAndWalls_dropsGrass_usesArcFractionCues() {
        val f = spec.flat()
        assertEquals(listOf(0xFF31363DL, 0xFFF2C230L, WALL), f.strips.map { it.color })
        assertEquals(spec.camera.path, f.cameraPath)
        assertEquals(spec.arcFraction(.1f), f.cues[0].first, 1e-6f)
        assertEquals(3.4f, f.strips.last().center.first().x, 1e-4f) // wall line runs along the profile's middle
        assertTrue(f.strips.all { s -> s.center.zipWithNext().all { (a, b) -> (b - a).len() <= 20.001f } })
    }
}
