package app.upmile.preview3d

import app.upmile.core.preview3d.Vec3
import app.upmile.core.preview3d.arcLengths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasPreviewTest {
    private val path = listOf(Vec3(0f, 1.25f, 0f), Vec3(0f, 1.25f, -50f), Vec3(0f, 1.25f, -100f))
    private val arc = arcLengths(path)
    private val w = 1100f
    private val h = 1000f

    private fun assertForwardDownTheRoad(c: Camera) {
        assertEquals(0f, c.fx, 1e-5f)
        assertTrue("looks down the road (-Z)", c.fz < -.99f)
        assertTrue("tilted slightly down, as in the mockup", c.fy < 0f && c.fy > -.05f)
    }

    @Test fun camera_atStart_middle_andEnd_followsThePathAndLooksAhead() {
        listOf(0f to 0f, .5f to -50f, 1f to -100f).forEach { (p, z) ->
            val c = cameraAt(path, arc, p)
            assertEquals(Vec3(0f, 1.25f, z), c.eye)
            assertForwardDownTheRoad(c)
        }
    }

    @Test fun camera_clampsProgress() = assertEquals(Vec3(0f, 1.25f, -100f), cameraAt(path, arc, 1.7f).eye)

    @Test fun project_pointStraightAhead_isScreenCentre() {
        val c = cameraAt(path, arc, .5f)
        val o = c.project(Vec3(c.eye.x + c.fx * 10, c.eye.y + c.fy * 10, c.eye.z + c.fz * 10), w, h)!!
        assertEquals(w / 2, o.x, 1e-3f)
        assertEquals(h / 2, o.y, 1e-3f)
    }

    @Test fun project_pointBehind_isNull() = assertNull(cameraAt(path, arc, .5f).project(Vec3(0f, 1.25f, -40f), w, h))

    @Test fun project_pointOnTheRight_isRightOfCentre_andGroundIsBelow() {
        val o = cameraAt(path, arc, 0f).project(Vec3(3f, 0f, -20f), w, h)!!
        assertTrue(o.x > w / 2)
        assertTrue(o.y > h / 2)
    }

    @Test fun project_onAPathHeadingMinusX_pointOnItsRight_isRightOfCentre() {
        val west = listOf(Vec3(0f, 1.25f, 0f), Vec3(-100f, 1.25f, 0f))
        val c = cameraAt(west, arcLengths(west), 0f)
        assertTrue("right of heading -X is -Z", c.project(Vec3(-20f, 0f, -3f), w, h)!!.x > w / 2)
        assertTrue(c.project(Vec3(-20f, 0f, 3f), w, h)!!.x < w / 2)
    }

    @Test fun clipNear_quadWithTwoCornersBehind_keepsFourPointsInFront() {
        val quad = floatArrayOf(-1f, 0f, -1f, 1f, 0f, -1f, 1f, 0f, 5f, -1f, 0f, 5f) // view space: (right, up, depth)
        val out = FloatArray(18)
        val m = clipNear(quad, 4, out)
        assertEquals(4, m)
        (0 until m).forEach { assertTrue(out[it * 3 + 2] >= .1f - 1e-6f) }
    }

    @Test fun dashes_areThreeMetresOnFiveOff_fullDashesOnly() =
        assertEquals(listOf(0f to 3f, 8f to 11f, 16f to 19f), dashSpans(20f))
}
