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
}
