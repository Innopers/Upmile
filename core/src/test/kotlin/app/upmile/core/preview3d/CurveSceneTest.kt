package app.upmile.core.preview3d

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class CurveSceneTest {
    private val seg = HardSegment("inlaws:sharp_curve:2", SegmentType.SHARP_CURVE, "급커브", "급커브 구간",
        GeoPoint(37.378, 127.107), 17000, mapOf("radiusM" to 45.0, "turnDeg" to -90.0))
    private val s = CurveScene.build(seg)
    private val cues = listOf("커브 전 감속", "내 차로 안쪽 유지", "맞은편 차 주의", "커브 끝에서 가속", "완료")

    @Test fun keepsTheInvariants() = SceneChecks.all(s, cues)
    @Test fun isDeterministic() = SceneChecks.deterministic { CurveScene.build(seg) }
    @Test fun builderRoutesHere() = assertEquals(s, SceneBuilder.build(seg))

    @Test fun anySegmentIdKeepsTheInvariants() = (0 until 20).forEach { i ->
        listOf("inlaws", "commute").forEach { route -> SceneChecks.all(CurveScene.build(seg.copy(id = "$route:sharp_curve:$i")), cues) }
    }

    @Test fun tighterCurveKeepsTheInvariants() = SceneChecks.all(CurveScene.build(seg.copy(params = mapOf("radiusM" to 30.0, "turnDeg" to -90.0))), cues)

    @Test fun rightCurveKeepsTheInvariants() {
        val right = CurveScene.build(seg.copy(params = mapOf("radiusM" to 45.0, "turnDeg" to 90.0)))
        SceneChecks.all(right, cues)
        val p = right.camera.path
        assertEquals(deg(90f), heading(p.last() - p[p.lastIndex - 1]) - heading(p[1] - p[0]), deg(2f))
        assertTrue("guardrail outside the curve", right.solids.single { it.mat == Mat.STEEL }.path.all { hypot(it.x - 45f, it.z + 60f) >= 48f })
    }

    @Test fun turnsLeftNinetyDegrees() {
        val p = s.camera.path
        val h0 = heading(p[1] - p[0]); val h1 = heading(p.last() - p[p.lastIndex - 1])
        assertEquals(deg(-90f), h1 - h0, deg(2f))
    }

    @Test fun slowsForTheCurve() {
        val a = s.cues[1].first; val b = s.cues[3].first
        val speed = (s.distanceAt(b) - s.distanceAt(a)) / ((b - a) * s.durationMs / 1000f)
        assertTrue("curve speed $speed", speed <= 8.5f)
    }

    @Test fun chevronsGuardrailAndWarnings() {
        assertTrue(s.props.count { it.kind == PropKind.CHEVRON_LEFT } >= 7)
        assertTrue(s.solids.any { it.mat == Mat.STEEL })
        assertTrue(s.props.any { it.kind == PropKind.WARNING_CURVE_LEFT })
        assertEquals(listOf("30"), s.props.first { it.kind == PropKind.SPEED_LIMIT }.text)
    }

    @Test fun anOncomingCarPassesInTheCurve() {
        val car = s.cars.single()
        val mid = (s.cues[1].first + s.cues[3].first) / 2
        assertTrue(SceneChecks.dist(car.poseAt(mid).at, s.cameraAt(mid).first) < 25f)
        assertTrue(abs(SceneChecks.dist(car.poseAt(mid).at, s.cameraAt(mid).first)) >= MIN_GAP)
    }

    @Test fun noTreeOnTheRoad() = s.props.filter { it.kind == PropKind.TREE }.forEach { t ->
        val road = s.camera.path
        assertTrue(road.minOf { SceneChecks.dist(it, t.at) } >= 6f)
    }

    private val rightSeg = seg.copy(params = mapOf("radiusM" to 45.0, "turnDeg" to 90.0))
    private val LEFT_KINDS = setOf(PropKind.CHEVRON_LEFT, PropKind.WARNING_CURVE_LEFT)
    private val RIGHT_KINDS = setOf(PropKind.CHEVRON_RIGHT, PropKind.WARNING_CURVE_RIGHT)

    @Test fun rightCurveUsesRightHandSigns() {
        val r = CurveScene.build(rightSeg)
        assertTrue(r.props.none { it.kind in LEFT_KINDS })
        assertTrue(r.props.count { it.kind == PropKind.CHEVRON_RIGHT } >= 7)
        assertEquals(1, r.props.count { it.kind == PropKind.WARNING_CURVE_RIGHT })
        assertTrue(s.props.none { it.kind in RIGHT_KINDS })
    }

    /** Chevrons stand on the outside of the bend and face the driver: heading equals the road's heading there. */
    @Test fun chevronsStandOutsideAndFaceTheDriver() {
        for ((spec, kind, outerSign) in listOf(Triple(s, PropKind.CHEVRON_LEFT, 1f), Triple(CurveScene.build(rightSeg), PropKind.CHEVRON_RIGHT, -1f))) {
            val road = offset(spec.camera.path, -1.625f)   // camera is on the right lane: back to the centreline
            spec.props.filter { it.kind == kind }.forEach { c ->
                val p = pointAt(road, nearestS(road, c.at))
                val side = (c.at.x - p.at.x) * -p.dir.z + (c.at.z - p.at.z) * p.dir.x      // + = right of travel
                assertEquals("outside", outerSign * 4.6f, side, .3f)
                assertEquals("faces the driver", heading(p.dir), c.heading, deg(3f))
            }
        }
    }
}
