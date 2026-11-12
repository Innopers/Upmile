package app.upmile.core.preview3d

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MergeSceneTest {
    private val seg = HardSegment("commute:highway_merge:1", SegmentType.HIGHWAY_MERGE, "고속도로 합류", "양재IC 경부고속도로 방면 진입",
        GeoPoint(37.464, 127.0415), 6400, mapOf("lanes" to 5.0, "accelLaneM" to 180.0))
    private val s = MergeScene.build(seg)

    @Test fun keepsTheInvariants() = SceneChecks.all(s, listOf("진입로 주행", "가속 차로", "왼쪽 깜빡이·미러 확인", "합류", "합류 완료"))
    @Test fun isDeterministic() = SceneChecks.deterministic { MergeScene.build(seg) }

    @Test fun laneDividersUseExpresswayDashes() {
        val white = s.surfaces.filter { it.mat == Mat.WHITE_PAINT && it.width == Kr.LINE }
        val pieces = white.map { arcLengths(it.center).last() }
        assertTrue(pieces.count { abs(it - Kr.HW_DASH) < .01f } >= 20)
    }

    @Test fun fiveMainLanesPlusTheAccelerationLane() {
        // lane dividers of our carriageway: 4 lines between 5 lanes, at x = -17.5 + k*3.5
        val xs = s.surfaces.filter { it.mat == Mat.WHITE_PAINT && it.width == Kr.LINE }.map { it.center.first().x }.toSet()
        (1..4).forEach { k -> assertTrue("divider $k", xs.any { abs(it - (-17.5f + k * 3.5f)) < .01f }) }
        assertTrue(s.surfaces.any { it.mat == Mat.YELLOW_PAINT })
        assertTrue(s.solids.any { it.mat == Mat.BARRIER })
    }

    @Test fun theMergeLaneIsClearWhileWeMerge() {
        val from = s.cues[3].first; val to = s.cues[4].first
        SceneChecks.samples().filter { it in from..to }.forEach { t ->
            val eye = s.cameraAt(t).first
            s.cars.map { it.poseAt(t).at }.filter { abs(it.x - (-1.75f)) < 1f }.forEach { c ->
                assertTrue("car ${abs(c.z - eye.z)} m from us in the merge lane at t=$t", abs(c.z - eye.z) >= 15f)
            }
        }
    }

    @Test fun aCarPassesUsInTheMergeLaneOnTheAccelerationLane() {
        val car = s.cars.first { abs(it.path.first().x - (-1.75f)) < .01f }
        val tA = s.cues[1].first; val tB = s.cues[3].first
        assertTrue(car.poseAt(tA).at.z > s.cameraAt(tA).first.z)  // behind us
        assertTrue(car.poseAt(tB).at.z < s.cameraAt(tB).first.z)  // ahead of us
    }

    @Test fun signsNameTheExpresswayFromThePlace() {
        assertEquals(listOf("경부고속도로", "부산 · 대전"), s.props.first { it.kind == PropKind.SIGN_GREEN }.text)
        assertTrue(s.props.any { it.kind == PropKind.GANTRY_GREEN })
    }

    @Test fun laneCountComesFromParams() {
        val three = MergeScene.build(seg.copy(params = mapOf("lanes" to 3.0)))
        SceneChecks.all(three, listOf("진입로 주행", "가속 차로", "왼쪽 깜빡이·미러 확인", "합류", "합류 완료"))
    }

    @Test fun accelerationLaneLengthIsClamped() = listOf(1000.0, 80.0).forEach { a ->
        SceneChecks.all(MergeScene.build(seg.copy(params = mapOf("lanes" to 5.0, "accelLaneM" to a))), listOf("진입로 주행", "가속 차로", "왼쪽 깜빡이·미러 확인", "합류", "합류 완료"))
    }

    @Test fun anySegmentIdKeepsTheInvariants() = (0 until 20).forEach { i ->
        listOf("commute", "inlaws").forEach { route ->
            listOf(5.0, 3.0).forEach { lanes ->
                val id = "$route:highway_merge:$i"
                SceneChecks.all(MergeScene.build(seg.copy(id = id, params = mapOf("lanes" to lanes, "accelLaneM" to 180.0))), listOf("진입로 주행", "가속 차로", "왼쪽 깜빡이·미러 확인", "합류", "합류 완료"))
            }
        }
    }
}
