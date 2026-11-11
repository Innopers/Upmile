package app.upmile.preview3d

import app.upmile.core.preview3d.Lamp
import app.upmile.core.preview3d.Prop
import app.upmile.core.preview3d.PropKind
import app.upmile.core.preview3d.Vec3
import app.upmile.core.preview3d.deg
import app.upmile.core.preview3d.minus
import app.upmile.core.preview3d.v
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PropKitTest {
    private fun MeshData.xs() = (0 until vertexCount).map { positions[3 * it] }
    private fun MeshData.ys() = (0 until vertexCount).map { positions[3 * it + 1] }
    private fun MeshData.zs() = (0 until vertexCount).map { positions[3 * it + 2] }
    private fun all(m: Map<Batch, MeshData>) = m.values.flatMap { mesh -> (0 until mesh.vertexCount).map { Vec3(mesh.positions[3 * it], mesh.positions[3 * it + 1], mesh.positions[3 * it + 2]) } }
    private fun MeshData.p(i: Int) = Vec3(positions[3 * i], positions[3 * i + 1], positions[3 * i + 2])
    private fun MeshData.n(i: Int) = Vec3(normals[3 * i], normals[3 * i + 1], normals[3 * i + 2])

    @Test fun lampArmReachesOverTheRoadForAnyHeading() {
        val north = PropKit.build(listOf(Prop(PropKind.STREET_LAMP, v(10f, 0f))))
        assertEquals(10f - 2.5f - .35f, all(north).minOf { it.x }, .05f)        // arm toward -X (the driver's left)
        val east = PropKit.build(listOf(Prop(PropKind.STREET_LAMP, v(0f, 10f), heading = deg(90f))))
        assertEquals(10f - 2.5f - .35f, all(east).minOf { it.z }, .05f)          // driving +X, the road is toward -Z
        assertTrue(north.keys.contains(Batch.Of(Part.LAMP_WARM)))
    }

    @Test fun signalHeadsHaveEveryLampOnAndOff() {
        val m = PropKit.build(listOf(Prop(PropKind.SIGNAL_MAST, v(17f, -127f), size = Vec3(12f, 6f, 0f))))
        Lamp.values().forEach { lamp -> assertTrue("$lamp", m.keys.any { it is Batch.Of && it.part.lamp() == lamp }) }
        assertTrue(m.getValue(Batch.Of(Part.LAMP_OFF)).vertexCount > 0)
        assertEquals(6f + .6f, all(m).maxOf { it.y }, .05f)
    }

    @Test fun signFaceIsKeyedByKindAndTextAndFacesTheDriver() {
        val text = listOf("경부고속도로", "부산 · 대전")
        val m = PropKit.build(listOf(Prop(PropKind.SIGN_GREEN, v(5f, -10f), size = Vec3(3.6f, 1.8f, 2.2f), text = text)))
        val face = m.getValue(Batch.Face(SignFace(PropKind.SIGN_GREEN, text)))
        assertEquals(4, face.vertexCount)
        assertEquals(1f, face.normals[2], 1e-5f)                              // +Z: toward a driver coming from +Z
        val tl = (0 until 4).first { face.uvs[2 * it] == 0f && face.uvs[2 * it + 1] == 0f }
        assertEquals(2.2f + 1.8f, face.positions[3 * tl + 1], 1e-4f)            // uv (0,0) is the top left
        assertEquals(5f - 1.8f, face.positions[3 * tl], 1e-4f)
    }

    @Test fun treesAndBuildingsHaveTheirSize() {
        val tree = PropKit.build(listOf(Prop(PropKind.TREE, v(0f, 0f), size = Vec3(2f, 8f, 0f))))
        assertEquals(8f, all(tree).maxOf { it.y }, .2f)
        val b = PropKit.build(listOf(Prop(PropKind.BUILDING_APT, v(50f, -100f), size = Vec3(12f, 60f, 55f))))
        assertEquals(60f, all(b).maxOf { it.y }, 1e-3f)
        assertEquals(44f, all(b).minOf { it.x }, 1e-3f); assertEquals(-127.5f, all(b).minOf { it.z }, 1e-3f)
    }

    @Test fun ceilingLightStaysAtItsHeight() {
        val m = PropKit.build(listOf(Prop(PropKind.CEILING_LIGHT, Vec3(0f, -2f, -10f))))
        assertEquals(-2f, m.getValue(Batch.Of(Part.LAMP_WARM)).ys().average().toFloat(), .05f)
    }

    @Test fun everyKindBuildsWithDefaults() = PropKind.values().forEach { k ->
        val m = PropKit.build(listOf(Prop(k, v(0f, 0f), text = listOf("가"))))
        assertTrue("$k", m.values.sumOf { it.indexCount } > 0)
    }

    @Test fun everyTriangleWindsTowardItsNormalAndNormalsAreUnit() = PropKind.values().forEach { k ->
        val m = PropKit.build(listOf(Prop(k, v(3f, -4f), heading = deg(30f), text = listOf("가"))))
        m.values.forEach { mesh ->
            for (t in 0 until mesh.indexCount / 3) {
                val a = mesh.indices[3 * t]; val b = mesh.indices[3 * t + 1]; val c = mesh.indices[3 * t + 2]
                val area = cross(mesh.p(b) - mesh.p(a), mesh.p(c) - mesh.p(a))
                assertTrue("$k winding", dot(area, mesh.n(a)) > 0f)
            }
            (0 until mesh.vertexCount).forEach { val n = mesh.n(it); assertEquals("$k", 1f, dot(n, n), 1e-3f) }
        }
    }

    @Test fun boxFacesPointAwayFromItsCentre() {
        val m = PropKit.build(listOf(Prop(PropKind.POST, v(0f, 0f), heading = deg(30f)))).getValue(Batch.Of(Part.STEEL))
        val centre = Vec3(0f, .375f, 0f)
        (0 until m.vertexCount).forEach { assertTrue(dot(m.n(it), m.p(it) - centre) > 0f) }
    }

    @Test fun headingTurnsTheFaceTowardTheDriver() {
        // theta = -90deg: local +Z -> (z sin t, z cos t) = (-1, 0): the sign faces -X; local x -> z' = -x sin t = x.
        val text = listOf("가")
        val m = PropKit.build(listOf(Prop(PropKind.SIGN_GREEN, v(5f, -10f), heading = deg(90f), size = Vec3(3.6f, 1.8f, 2.2f), text = text)))
        val face = m.getValue(Batch.Face(SignFace(PropKind.SIGN_GREEN, text)))
        assertEquals(-1f, face.n(0).x, 1e-5f); assertEquals(0f, face.n(0).z, 1e-5f)
        val tl = (0 until 4).first { face.uvs[2 * it] == 0f && face.uvs[2 * it + 1] == 0f }
        assertEquals(5f, face.p(tl).x, 1e-4f)
        assertEquals(-10f - 1.8f, face.p(tl).z, 1e-4f)                          // tl local x = -1.8 -> z' = -1.8
    }

    @Test fun roadTextTiltsByTheDropItIsGivenAndIsFlatWithout() {
        val m = PropKit.build(listOf(Prop(PropKind.ROAD_TEXT, v(0f, 0f), size = Vec3(2.4f, .48f, 4f), text = listOf("천천히")))).values.single()
        (0 until m.vertexCount).forEach { assertEquals(if (m.p(it).z < 0f) .02f - .24f else .02f + .24f, m.p(it).y, 1e-4f) } // lower away from the driver
        val n = m.n(0)
        assertTrue("normal $n leans toward the downhill side", n.y > .99f && n.z < 0f)
        val flat = PropKit.build(listOf(Prop(PropKind.ROAD_TEXT, v(0f, 0f), text = listOf("천천히")))).values.single()
        (0 until flat.vertexCount).forEach { assertEquals(.02f, flat.p(it).y, 1e-6f) }
    }
}
