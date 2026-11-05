package app.upmile.core.preview3d

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class RoadTemplatesTest {
    private fun seg(t: SegmentType, p: Map<String, Double>) =
        HardSegment("s", t, "구간", GeoPoint(37.0, 127.0), 0, p)

    @Test fun merge_hasMainLanesRampAndCuesInOrder() {
        val s = RoadTemplates.build(seg(SegmentType.HIGHWAY_MERGE, mapOf("lanes" to 2.0, "accelLaneM" to 180.0)))
        assertTrue(s.strips.size >= 3); assertTrue(s.cameraPath.size >= 8)
        assertEquals(listOf("진입로 주행", "가속 차로", "왼쪽 깜빡이·미러 확인", "합류", "합류 완료"), s.cues.map { it.second })
        assertTrue(s.cues.zipWithNext().all { (a, b) -> a.first < b.first })
        assertTrue(s.cues.all { it.first > 0f && it.first < 1f })
    }

    @Test fun everySegmentType_hasATemplate_orGenericFallback() {
        SegmentType.values().forEach { assertTrue(RoadTemplates.build(seg(it, emptyMap())).cameraPath.isNotEmpty()) }
    }

    @Test fun cameraPath_neverBacktracksAndCuesAreNonEmpty() {
        SegmentType.values().forEach { t ->
            val s = RoadTemplates.build(seg(t, emptyMap()))
            assertTrue(t.name, s.cameraPath.size >= 8)
            assertTrue(t.name, s.cameraPath.zipWithNext().all { (a, b) -> hypot(b.x - a.x, b.z - a.z) > 0f })
            assertTrue(t.name, s.cues.isNotEmpty() && s.cues.all { it.second.isNotBlank() })
        }
    }

    @Test fun merge_moreLanesMakesWiderRoad() {
        fun w(l: Double) = RoadTemplates.build(seg(SegmentType.HIGHWAY_MERGE, mapOf("lanes" to l))).strips.maxOf { it.width }
        assertTrue(w(3.0) > w(2.0))
    }

    private fun build(t: SegmentType) = RoadTemplates.build(seg(t, emptyMap()))
    private fun cueArc(s: TemplateScene, i: Int) = s.cues[i].first * arcLengths(s.cameraPath).last()

    @Test fun cues_strictlyIncreasingInOpenUnitInterval_forEveryTemplate() {
        SegmentType.values().forEach { t ->
            val c = build(t).cues
            assertTrue(t.name, c.all { it.first > 0f && it.first < 1f })
            assertTrue(t.name, c.zipWithNext().all { (a, b) -> a.first < b.first })
        }
    }

    @Test fun strips_areSubdividedAndExtendBeyondCamera() {
        SegmentType.values().forEach { t ->
            val s = build(t)
            s.strips.forEach { st ->
                assertTrue(t.name, st.center.zipWithNext().all { (a, b) -> arcLengths(listOf(a, b)).last() <= 20.01f })
            }
            val end = s.cameraPath.last()
            val road = s.strips.first()
            val tail = road.center.last()
            assertTrue(t.name, hypot(tail.x - end.x, tail.z - end.z) >= 20f)
        }
    }

    @Test fun merge_andLeft_cameraZNonIncreasing_rampHeadingsForward() {
        listOf(SegmentType.HIGHWAY_MERGE, SegmentType.MULTILANE_LEFT).forEach {
            assertTrue(build(it).cameraPath.zipWithNext().all { (a, b) -> b.z <= a.z })
        }
        val p = build(SegmentType.GARAGE_RAMP).cameraPath
        val h = p.zipWithNext().map { (a, b) -> (b.x - a.x) to (b.z - a.z) }
        assertTrue(h.zipWithNext().all { (a, b) -> a.first * b.first + a.second * b.second >= 0f })
    }

    @Test fun merge_cuesLandInTheirSections() {
        val s = build(SegmentType.HIGHWAY_MERGE); val arc = arcLengths(s.cameraPath)
        assertEquals(arc[2], cueArc(s, 1), 5f)                       // accel lane starts where the ramp ends
        assertTrue(cueArc(s, 2) in arc[3]..arc[5])                   // mirror check while on the accel lane
        assertTrue(cueArc(s, 3) in arc[5]..arc[7])                   // merge during taper
        assertTrue(cueArc(s, 4) >= arc[7])
    }

    @Test fun left_exitsIntoSecondLane_andCuesLandInTheirSections() {
        val s = build(SegmentType.MULTILANE_LEFT); val arc = arcLengths(s.cameraPath)
        val crossZ = -97f
        assertEquals(crossZ - 1.5f * 3.5f, s.cameraPath.last().z, 0.2f)
        assertEquals(90f, cueArc(s, 1), 5f)                          // stop line at z = -90
        assertTrue(cueArc(s, 2) in 90f..100f)                        // turn starts after the stop line
        assertTrue(cueArc(s, 3) in (arc[8] - 5f)..arc[8])            // end of turn
        assertEquals("2차로로 진입", s.cues[3].second)
    }

    @Test fun ramp_cuesLandInTheirSections() {
        val s = build(SegmentType.GARAGE_RAMP); val arc = arcLengths(s.cameraPath)
        assertTrue(cueArc(s, 1) in arc[1]..arc[4])
        assertTrue(cueArc(s, 2) in arc[5]..arc[8])                   // turn begins after the 30 m straight
        assertTrue(cueArc(s, 3) in arc[15]..arc[17])
        assertTrue(cueArc(s, 4) >= arc.last() - 5f)
    }

    @Test fun badParams_doNotBreakGeometry() {
        val s = RoadTemplates.build(seg(SegmentType.HIGHWAY_MERGE, mapOf("lanes" to 0.0, "accelLaneM" to -5.0)))
        assertTrue(s.strips.all { it.width > 0f } && s.cameraPath.size >= 8)
    }
}
