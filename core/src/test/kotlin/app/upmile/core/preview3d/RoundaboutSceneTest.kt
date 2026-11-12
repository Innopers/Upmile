package app.upmile.core.preview3d

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class RoundaboutSceneTest {
    private val seg = HardSegment("cafe:roundabout:1", SegmentType.ROUNDABOUT, "회전교차로", "회전교차로 진입",
        GeoPoint(37.52, 127.045), 3000, mapOf("radiusM" to 14.0, "exitArm" to 2.0))
    private val s = RoundaboutScene.build(seg)
    private val cues = listOf("진입 전 감속", "왼쪽 회전 차량 먼저", "반시계 방향으로 회전", "오른쪽 깜빡이 켜고 진출", "완료")

    @Test fun keepsTheInvariants() = SceneChecks.all(s, cues)
    @Test fun isDeterministic() = SceneChecks.deterministic { RoundaboutScene.build(seg) }
    @Test fun builderRoutesHere() = assertEquals(s, SceneBuilder.build(seg))

    @Test fun anySegmentIdKeepsTheInvariants() = (0 until 20).forEach { i ->
        listOf("cafe", "commute").forEach { route -> SceneChecks.all(RoundaboutScene.build(seg.copy(id = "$route:roundabout:$i")), cues) }
    }

    @Test fun biggerRingKeepsTheInvariants() = SceneChecks.all(RoundaboutScene.build(seg.copy(params = mapOf("radiusM" to 18.0, "exitArm" to 2.0))), cues)

    @Test fun weGiveWayToTheCarAlreadyCirculating() {
        val stopT = s.cues[1].first; val goT = s.cues[2].first
        assertEquals(s.distanceAt(stopT), s.distanceAt(goT - .01f), .01f)  // standing at the yield line
        // the circulating car crosses in front of us while we wait, and has passed our entry when we go
        val moving = s.cars.filter { it.keys.first().s != it.keys.last().s }
        val b = moving.first { c -> SceneChecks.samples().any { t -> c.poseAt(t).at.let { it.z < -60f && it.z > -100f && it.x < -5f } } }
        assertTrue(SceneChecks.samples().filter { it in stopT..goT }.any { t -> b.poseAt(t).at.x in -2f..2f })
        assertTrue(b.poseAt(goT).at.x > 2f)
    }

    /** On the ring the angle around the island (from +X toward -Z, as in `arc`) only grows: counter-clockwise seen from above. */
    @Test fun drivesCounterClockwiseAroundTheIslandAndLeavesNorth() {
        val ring = s.camera.path.filter { abs(hypot(it.x, it.z + 80f) - 11.25f) < 1.5f }
        val angles = ring.map { atan2(-(it.z + 80f), it.x) }
        assertTrue("camera reaches the ring", ring.size >= 5)
        assertTrue("angles grow: $angles", angles.zipWithNext().all { (a, b) -> b > a - 1e-3f })
        assertTrue(s.camera.path.last().z < -150f)
    }

    @Test fun signsAndIsland() {
        assertTrue(s.props.any { it.kind == PropKind.ROUNDABOUT_SIGN } && s.props.any { it.kind == PropKind.YIELD_SIGN })
        assertTrue(s.patches.any { it.mat == Mat.GRASS && it.lift > 0f })
    }

    /** Two cars wait, nose at the yield line, one at the north entry lane (x = -W/2) and one at the east entry lane (z = -80 - W/2). */
    @Test fun twoCarsWaitAtTheNorthAndEastYieldLines() {
        val w = 3.25f
        val waiting = s.cars.filter { it.keys.first().s == it.keys.last().s }
        assertEquals(2, waiting.size)
        val n = waiting.single { abs(it.poseAt(0f).at.x + w / 2) < .01f }.poseAt(0f)
        val e = waiting.single { abs(it.poseAt(0f).at.z + 80f + w / 2) < .01f }.poseAt(0f)
        assertEquals(-80f - 14f - .3f - 2.3f, n.at.z, .5f); assertEquals(1f, n.dir.z, .01f)   // facing south, toward the ring
        assertEquals(14f + .3f + 2.3f, e.at.x, .5f); assertEquals(-1f, e.dir.x, .01f)         // facing west
    }

    /** The ring's asphalt starts and ends at a = 0, where its end sections are square to the first and last chords: no gap may open there. */
    @Test fun ringAsphaltHasNoSeamAtItsStart() {
        val rc = 14f - 2.75f
        val ribbon = s.surfaces.single { it.mat == Mat.ASPHALT && it.center.all { p -> abs(hypot(p.x, p.z + 80f) - rc) < .01f } }
        val l = offset(ribbon.center, -ribbon.width / 2); val r = offset(ribbon.center, ribbon.width / 2)
        for (deg in -10..10) for (d in listOf(-2.7f, -1.5f, 0f, 1.5f, 2.7f)) {
            val a = deg / 10f * PI.toFloat() / 180f
            val p = Vec3((rc + d) * cos(a), 0f, -80f - (rc + d) * sin(a))
            assertTrue("asphalt at a = ${deg / 10f} deg, ${rc + d} m", (1 until l.size).any { i -> SceneChecks.inside(p, listOf(l[i - 1], r[i - 1], r[i], l[i])) })
        }
    }

    @Test fun endsInTheNorthArmsRightLane() = assertEquals(w2, s.camera.path.last().x, .01f)
    private val w2 = 3.25f / 2
}
