package app.upmile.core.preview3d

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class RampSceneTest {
    private val seg = HardSegment("commute:garage_ramp:end", SegmentType.GARAGE_RAMP, "주차장 경사로", "회사",
        GeoPoint(37.3941, 127.1129), 19000, mapOf("slope" to 0.12, "turnDeg" to 90.0))
    private val s = RampScene.build(seg)
    private val cues = listOf("입구 · 전조등", "급경사 내리막", "좁은 우회전", "차로 가운데", "도착")

    @Test fun keepsTheInvariants() = SceneChecks.all(s, cues)
    @Test fun isDeterministic() = SceneChecks.deterministic { RampScene.build(seg) }
    @Test fun builderRoutesHere() = assertEquals(s, SceneBuilder.build(seg))

    @Test fun anySegmentIdKeepsTheInvariants() = (0 until 20).forEach { i ->
        listOf("commute", "inlaws").forEach { route -> SceneChecks.all(RampScene.build(seg.copy(id = "$route:garage_ramp:$i")), cues) }
    }

    @Test fun leftTurnVariantKeepsTheInvariants() {
        val left = RampScene.build(seg.copy(params = mapOf("slope" to 0.12, "turnDeg" to -90.0)))
        SceneChecks.all(left, cues); checkSlopes(left)
        assertTrue("turns left", left.camera.path.last().x < -20f)
    }

    @Test fun gentlerVariantKeepsTheInvariants() {
        val gentle = RampScene.build(seg.copy(params = mapOf("slope" to 0.08, "turnDeg" to 90.0)))
        SceneChecks.all(gentle, cues); checkSlopes(gentle, -2f)
    }

    @Test fun slopesStayWithinTheParkingRules() = checkSlopes(s)

    private fun checkSlopes(spec: SceneSpec, endBelow: Float = -3.5f) {
        val p = spec.camera.path
        p.zipWithNext().forEach { (a, b) ->
            val run = hypot(b.x - a.x, b.z - a.z)
            if (run > .2f) assertTrue("slope ${(a.y - b.y) / run} at $a", (a.y - b.y) / run <= Kr.RAMP_MAX_STRAIGHT + 1e-3f)
        }
        // on the turn (heading changing) the camera lane stays within the curved-ramp limit
        p.windowed(3).filter { (a, b, c) -> abs(heading(b - a) - heading(c - b)) > deg(1f) }.forEach { (a, b, _) ->
            val run = hypot(b.x - a.x, b.z - a.z)
            assertTrue("curve slope ${(a.y - b.y) / run} at $a", (a.y - b.y) / run <= Kr.RAMP_MAX_CURVE + 1e-3f)
        }
        assertTrue("ends a storey down", p.last().y < endBelow)
    }

    @Test fun indoorFromTheEntranceWithWallsAndLights() {
        assertEquals(25f, s.indoorFromM, .5f)
        assertTrue(s.solids.count { it.mat == Mat.WALL } >= 2)
        assertTrue(s.solids.any { it.mat == Mat.CEILING })
        assertTrue(s.props.count { it.kind == PropKind.CEILING_LIGHT } >= 8)
        assertEquals(listOf("높이 2.3m"), s.props.first { it.kind == PropKind.HEIGHT_BAR }.text)
        assertTrue(s.props.any { it.kind == PropKind.CONVEX_MIRROR })
        assertTrue(s.flat().strips.any { it.color == WALL }) // Canvas fallback still knows it is underground
    }

    @Test fun noGrassUnderTheBuilding() {
        s.patches.filter { it.mat == Mat.GRASS }.forEach { g ->
            listOf(v(0f, -10f), v(6.5f, -34.5f), v(30f, -34.5f)).forEach { p -> assertTrue("grass over $p", !SceneChecks.inside(p, g.outline)) }
        }
    }

    private fun lateralOfCamera(spec: SceneSpec, p: Vec3): Float {
        val pose = pointAt(spec.camera.path, nearestS(spec.camera.path, p))
        val n = hypot(pose.dir.x, pose.dir.z)
        return ((p.x - pose.at.x) * -pose.dir.z + (p.z - pose.at.z) * pose.dir.x) / n
    }

    @Test fun roadTextSitsBetweenTheTwoArrows() {
        val cam = s.camera.path
        val text = s.props.first { it.kind == PropKind.ROAD_TEXT }
        val sText = nearestS(cam, text.at)
        val half = text.size!!.z / 2
        s.patches.filter { it.mat == Mat.WHITE_PAINT }.forEach { a ->
            val ss = a.outline.map { nearestS(cam, it) }
            assertTrue("text ${sText - half}..${sText + half} overlaps arrow ${ss.min()}..${ss.max()}", sText + half <= ss.min() || sText - half >= ss.max())
        }
    }

    /** PropKit's rule: ROAD_TEXT's far edge (local -z) at .02 - size.y / 2, its near edge at .02 + size.y / 2. */
    @Test fun roadTextLiesOnTheSlopingFloor() = listOf(
        s, RampScene.build(seg.copy(params = mapOf("slope" to 0.12, "turnDeg" to -90.0))), RampScene.build(seg.copy(params = mapOf("slope" to 0.08, "turnDeg" to 90.0))),
    ).forEach { spec ->
        val text = spec.props.first { it.kind == PropKind.ROAD_TEXT }
        val size = text.size!!; val c = cos(-text.heading); val sn = sin(-text.heading)
        for ((lx, lz) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
            val q = Vec3(lx * size.x / 2, .02f + lz * size.y / 2, lz * size.z / 2)
            val p = Vec3(q.x * c + q.z * sn + text.at.x, q.y + text.at.y, -q.x * sn + q.z * c + text.at.z)
            val floor = pointAt(spec.camera.path, nearestS(spec.camera.path, p)).at.y - EYE
            assertTrue("corner (${p.x}, ${p.z}) is ${p.y - floor} m above the floor", p.y - floor in 0f..0.05f)
        }
    }

    @Test fun entranceSignStandsOnTheSidewalkClearOfTheLanes() {
        val sign = s.props.first { it.kind == PropKind.SIGN_BLUE }
        assertEquals(5.25f, sign.at.x, .01f); assertEquals(4f, sign.at.z, .01f)
        assertTrue(sign.at.x - (sign.size!!.x / 2 - .35f) >= 3.25f + .5f)
    }

    @Test fun hallHasPerimeterWallsWithAnOpeningForThePassage() {
        val hall = s.solids.first { it.mat == Mat.WALL && it.path.first().z < -20f }
        assertTrue(hall.path.all { it.x in 21f..71f && it.z in -49f..-21f })
        assertTrue("opening", hall.path.first().z > -32f && hall.path.last().z < -37f)
    }

    @Test fun leftVariantHasTheMirrorOnTheOuterRightWallAndWallsEndAtMinus21() {
        val left = RampScene.build(seg.copy(params = mapOf("slope" to 0.12, "turnDeg" to -90.0)))
        assertTrue("left mirror lateral", lateralOfCamera(left, left.props.first { it.kind == PropKind.CONVEX_MIRROR }.at) > 0f)
        assertTrue("right mirror lateral", lateralOfCamera(s, s.props.first { it.kind == PropKind.CONVEX_MIRROR }.at) < 0f)
        val tunnel = left.solids.filter { it.mat == Mat.WALL && it.path.first().z == 0f }
        assertEquals(2, tunnel.size)
        tunnel.forEach { assertEquals(-21.5f, it.path.last().x, .01f) }
    }

    @Test fun oncomingCarPassesBesideTheCameraJustAfterTheTurn() {
        val (t, d) = (0..1000).map { it / 1000f }.map { t -> t to SceneChecks.dist(s.cars[0].poseAt(t).at, s.cameraAt(t).first) }.minBy { it.second }
        assertTrue("closest ${d} m", d <= 6f)
        assertEquals(3.25f, Math.abs(lateralOfCamera(s, s.cars[0].poseAt(t).at)), .3f)
    }
}
