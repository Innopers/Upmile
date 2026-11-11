package app.upmile.core.preview3d

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainSceneTest {
    private val seg = HardSegment("r:accident_spot:3", SegmentType.ACCIDENT_SPOT, "사고 다발 구간", "어딘가", GeoPoint(37.0, 127.0), 0, emptyMap())

    @Test fun keepsTheInvariants() = SceneChecks.all(PlainScene.build(seg), listOf("사고 다발 구간"))
    @Test fun isDeterministic() = SceneChecks.deterministic { PlainScene.build(seg) }

    @Test fun hasAWarningSignAndAnOncomingCarThatPasses() {
        val s = PlainScene.build(seg)
        assertEquals(1, s.props.count { it.kind == PropKind.WARNING_ACCIDENT })
        val car = s.cars.single()
        assertTrue(car.poseAt(0f).at.z < s.cameraAt(0f).first.z && car.poseAt(1f).at.z > s.cameraAt(1f).first.z)
    }

    @Test fun everyTypeBuilds() = SegmentType.values().forEach { t ->
        assertTrue(SceneBuilder.build(seg.copy(type = t)).camera.path.size >= 2)
    }
}
