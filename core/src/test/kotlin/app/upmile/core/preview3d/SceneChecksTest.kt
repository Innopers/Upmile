package app.upmile.core.preview3d

import app.upmile.core.model.GeoPoint
import app.upmile.core.model.HardSegment
import app.upmile.core.model.SegmentType
import org.junit.Assert.assertThrows
import org.junit.Test

/** The shared checks must reject scenes that break them. */
class SceneChecksTest {
    private val base = PlainScene.build(HardSegment("r:accident_spot:3", SegmentType.ACCIDENT_SPOT, "사고 다발 구간", "어딘가", GeoPoint(37.0, 127.0), 0, emptyMap()))
    private val eyeX = base.cameraAt(0f).first.x
    private val car = base.cars.single()
    private fun rejects(f: () -> Unit) { assertThrows(AssertionError::class.java) { f() } }

    @Test fun carOffTheRoad() = rejects { SceneChecks.onRoad(base.copy(cars = listOf(car.copy(path = car.path.map { it.copy(x = 500f) })))) }
    @Test fun eyeBesideASolid() = rejects {
        SceneChecks.onRoad(base.copy(solids = listOf(Extrusion(listOf(v(eyeX + .3f, 200f), v(eyeX + .3f, -2000f)), GUARDRAIL, Mat.STEEL))))
    }
    @Test fun carNoseToTailInTheSameLane() = rejects {
        val ahead = base.cameraAt(0f).first
        val tail = car.copy(path = listOf(v(ahead.x, ahead.z - 3.5f), v(ahead.x, ahead.z - 4000f)), keys = listOf(Key(0f, 0f), Key(1f, 0f)))
        SceneChecks.gaps(base.copy(cars = listOf(tail)))
    }
    @Test fun treeOnTheRoad() = rejects { SceneChecks.propsClear(base.copy(props = listOf(Prop(PropKind.TREE, v(eyeX, 0f))))) }
    @Test fun treeInsideABuilding() = rejects {
        SceneChecks.propsClear(base.copy(props = listOf(Prop(PropKind.BUILDING_APT, v(900f, 0f), size = Vec3(12f, 30f, 20f)), Prop(PropKind.TREE, v(905f, 5f)))))
    }
}
