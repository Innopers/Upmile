package app.upmile.core.preview3d

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

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
}
