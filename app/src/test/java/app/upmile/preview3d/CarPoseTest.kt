package app.upmile.preview3d

import app.upmile.core.preview3d.Vec3
import app.upmile.core.preview3d.minus
import app.upmile.core.preview3d.unit
import dev.romainguy.kotlin.math.Float3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.tan

class CarPoseTest {
    @Test fun aCarNosesAlongItsTravelAndPitchesWithTheSlopeWithoutRolling() {
        // north, south, east, a diagonal, up the garage ramp (12%) toward -Z, down a slope toward +X+Z
        val dirs = listOf(Vec3(0f, 0f, -1f), Vec3(0f, 0f, 1f), Vec3(1f, 0f, 0f), Vec3(-.6f, 0f, .8f), Vec3(0f, .12f, -1f).unit(), Vec3(.5f, -.1f, .5f).unit())
        for (d in dirs) {
            val q = carRotation(d)
            val nose = q * Float3(0f, 0f, 1f) // Kenney cars face +Z
            assertEquals("$d nose x", d.x, nose.x, 1e-4f)
            assertEquals("$d nose y (climbing lifts the nose)", d.y, nose.y, 1e-4f)
            assertEquals("$d nose z", d.z, nose.z, 1e-4f)
            assertEquals("$d axle level", 0f, (q * Float3(1f, 0f, 0f)).y, 1e-5f)
            assertTrue("$d upright", (q * Float3(0f, 1f, 0f)).y > .99f)
        }
    }

    @Test fun theHoodFacesTheEyeAboveIt() {
        val m = hoodMesh()
        fun p(i: Int) = Vec3(m.positions[3 * i], m.positions[3 * i + 1], m.positions[3 * i + 2])
        assertEquals(7 * 4 + 7, m.vertexCount) // the grid and its lip
        for (i in 0 until m.indexCount step 3) {
            val a = p(m.indices[i]); val b = p(m.indices[i + 1]); val c = p(m.indices[i + 2])
            assertTrue("triangle $i winds up", cross(b - a, c - a).y > 0f) // front faces wind counter-clockwise
        }
        for (i in 0 until m.vertexCount) assertTrue("normal $i up", m.normals[3 * i + 1] > .95f)
        val near = p(3); val far = p(3 + 3 * 7) // the middle column
        assertEquals(-.9f, near.z, 1e-5f); assertEquals(-.38f + .03f, near.y, 1e-5f)
        assertEquals(-2.6f, far.z, 1e-5f); assertEquals(-.55f + .03f, far.y, 1e-5f)
        assertEquals(-.38f, p(0).y, 1e-5f); assertEquals(-.95f, p(0).x, 1e-5f) // corners: no crown
        for (i in 28 until 35) assertTrue("lip $i below the view's bottom edge", p(i).y / -p(i).z < -tan(PI / 6).toFloat())
    }
}
