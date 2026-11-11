package app.upmile.core.preview3d

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LeftTurnSceneTest {
    private val seg = HardSegment("commute:multilane_left:2", SegmentType.MULTILANE_LEFT, "다차선 좌회전", "판교역 사거리에서 좌회전",
        GeoPoint(37.3953, 127.1098), 15000, mapOf("turnLanes" to 2.0, "fromLane" to 2.0, "approachLanes" to 5.0, "crossLanes" to 3.0))
    private val s = LeftTurnScene.build(seg)
    private val cues = listOf("좌회전 차로 2개", "정지선 신호 대기", "유도선 따라 회전", "2차로로 진입", "완료")

    @Test fun keepsTheInvariants() = SceneChecks.all(s, cues)
    @Test fun isDeterministic() = SceneChecks.deterministic { LeftTurnScene.build(seg) }
    @Test fun builderRoutesHere() = assertEquals(s, SceneBuilder.build(seg))

    @Test fun anySegmentIdKeepsTheInvariants() = (0 until 20).forEach { i ->
        listOf("commute", "inlaws").forEach { route ->
            SceneChecks.all(LeftTurnScene.build(seg.copy(id = "$route:multilane_left:$i")), cues)
        }
    }

    @Test fun redThenRedAndLeftArrow() {
        assertEquals(setOf(Lamp.RED), s.signalAt(0f))
        assertEquals(setOf(Lamp.RED, Lamp.ARROW), s.signalAt(1f))
    }

    @Test fun cameraWaitsAtTheLineAndMovesOnlyAfterTheArrow() {
        val tArrow = s.signal[1].t
        val waitT = s.cues[1].first
        assertEquals(s.distanceAt(waitT), s.distanceAt(tArrow), .01f)          // standing still until the arrow
        assertTrue(s.cameraAt(waitT).first.z > -100f)                          // behind the stop line
        assertTrue(s.distanceAt(1f) > s.distanceAt(tArrow) + 50f)
    }

    @Test fun endsInLaneTwoOfTheCrossRoad() {
        val end = s.camera.path.last()
        assertEquals(-121.225f, end.z, .05f); assertTrue(end.x < -60f)
    }

    @Test fun koreanMarkings() {
        val white = s.surfaces.filter { it.mat == Mat.WHITE_PAINT }
        assertTrue("stop line", white.any { it.width == Kr.STOP_LINE })
        assertTrue("guide dashes", white.count { abs(arcLengths(it.center).last() - Kr.GUIDE_DASH) < .05f } >= 10)
        assertTrue("crosswalk bars", white.count { it.width == Kr.CROSSWALK_BAR } >= 20)
        assertTrue("arrows", s.patches.count { it.mat == Mat.WHITE_PAINT } >= 10)
    }

    @Test fun noCrossTrafficInTheJunctionAfterTheArrow() {
        val tArrow = s.signal[1].t
        fun inBox(p: Pose) = p.at.x in -14f..17f && p.at.z in -126.1f..-106f
        val crossing = s.cars.filter { it.path.first().z != 40f }       // our turners (A, C) start their path at z=+40
        fun crossersInBox(t: Float) = crossing.map { it.poseAt(t) }.filter { abs(it.dir.x) > .9f && inBox(it) }
        assertTrue("some cross car crosses before the arrow", SceneChecks.samples().filter { it < tArrow }.any { crossersInBox(it).isNotEmpty() })
        SceneChecks.samples().filter { it >= tArrow }.forEach { t -> assertTrue("cross car in the junction at t=$t: ${crossersInBox(t)}", crossersInBox(t).isEmpty()) }
    }

    @Test fun nearSideOpposingExitHasNoSolidDividers() {
        val solid = s.surfaces.filter { r -> r.mat == Mat.WHITE_PAINT && r.width == Kr.LINE && r.center.map { it.x }.distinct().size == 1 &&
            r.center.all { it.z in -100f..-70f } && arcLengths(r.center).last() > Kr.CITY_DASH + .1f }
        assertTrue("approach side keeps its solid dividers", solid.any { it.center.first().x > 0f })
        assertTrue("opposing exit is dashed: $solid", solid.none { it.center.first().x < 0f })
    }

    @Test fun clampedVariantsKeepTheInvariants() = listOf(
        mapOf("approachLanes" to 4.0, "turnLanes" to 1.0, "fromLane" to 1.0, "crossLanes" to 4.0),
        mapOf("approachLanes" to 6.0, "turnLanes" to 1.0, "fromLane" to 1.0, "crossLanes" to 3.0),
        mapOf("approachLanes" to 4.0, "turnLanes" to 2.0, "fromLane" to 2.0, "crossLanes" to 4.0),
        mapOf("approachLanes" to 6.0, "turnLanes" to 2.0, "fromLane" to 1.0, "crossLanes" to 4.0),
        mapOf("approachLanes" to 9.0, "turnLanes" to 5.0, "fromLane" to 4.0, "crossLanes" to 9.0),   // out of range: clamped to 6 / 2 / 2 / 4
    ).forEach { p ->
        val turn = minOf(p["turnLanes"]!!.toInt(), 2); val from = minOf(p["fromLane"]!!.toInt(), turn)
        SceneChecks.all(LeftTurnScene.build(seg.copy(params = p)), listOf("좌회전 차로 ${turn}개", "정지선 신호 대기", "유도선 따라 회전", "${from}차로로 진입", "완료"))
    }

    @Test fun signalMastAndBlueSign() {
        assertTrue(s.props.any { it.kind == PropKind.SIGNAL_MAST })
        assertEquals(3, s.props.first { it.kind == PropKind.SIGN_BLUE }.text.size)
    }
}
