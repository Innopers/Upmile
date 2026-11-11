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
    private fun flatRoad(y: Float) = base.copy(
        surfaces = listOf(Ribbon(listOf(v(0f, 50f, y), v(0f, -50f, y)), 6.5f, Mat.CONCRETE)), patches = emptyList(), solids = emptyList(), cars = emptyList(),
    )
    private val tower = Prop(PropKind.BUILDING_GLASS, v(0f, 0f), size = Vec3(7.2f, 38f, 20f))

    @Test fun buildingOnTheSameLevelRoadIsRejected() = rejects { SceneChecks.propsClear(flatRoad(0f).copy(props = listOf(tower))) }
    @Test fun buildingOverARoad4mBelowIsAccepted() = SceneChecks.propsClear(flatRoad(-4f).copy(props = listOf(tower)))

    @Test fun signPostInTheLaneIsRejected() = rejects {
        SceneChecks.nothingStandsInTheWay(base.copy(props = listOf(Prop(PropKind.SIGN_BLUE, v(eyeX, -40f), size = Vec3(.7f, 1f, 2.2f)))))
    }
    @Test fun signBesideTheRoadPasses() =
        SceneChecks.nothingStandsInTheWay(base.copy(props = listOf(Prop(PropKind.SIGN_BLUE, v(eyeX + 6f, -40f), size = Vec3(3.2f, 1f, 2.2f)))))
    @Test fun lampOnAnotherLevelPasses() =
        SceneChecks.nothingStandsInTheWay(base.copy(props = listOf(Prop(PropKind.STREET_LAMP, v(eyeX, -40f, 6f)))))

    @Test fun treeBesideASignIsRejected() = rejects {
        SceneChecks.propsClear(base.copy(props = listOf(Prop(PropKind.ROUNDABOUT_SIGN, v(900f, 0f)), Prop(PropKind.TREE, v(900.5f, 0f)))))
    }
    @Test fun treeBesideALampIsRejected() = rejects {
        SceneChecks.propsClear(base.copy(props = listOf(Prop(PropKind.STREET_LAMP, v(900f, 0f)), Prop(PropKind.TREE, v(901.5f, 0f)))))
    }
    @Test fun treeWellAwayFromASignPasses() =
        SceneChecks.propsClear(base.copy(props = listOf(Prop(PropKind.ROUNDABOUT_SIGN, v(900f, 0f)), Prop(PropKind.TREE, v(902.5f, 0f)), Prop(PropKind.TREE, v(902.5f, 1f)))))
}
