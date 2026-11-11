package app.upmile.core.preview3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneKitTest {
    private val straight = listOf(v(0f, 0f), v(0f, -100f))

    @Test fun pointAt_walksAndExtendsPastTheEnds() {
        assertEquals(-30f, pointAt(straight, 30f).at.z, 1e-4f) // float: 100 * 0.3f is -30.000002, so no exact Vec3 equality
        assertEquals(-110f, pointAt(straight, 110f).at.z, 1e-4f)
        assertEquals(5f, pointAt(straight, -5f).at.z, 1e-4f)
        assertEquals(v(0f, -1f), pointAt(straight, 50f).dir)
    }

    @Test fun offset_positiveIsTheDriversRight() {
        assertEquals(2f, offset(straight, 2f)[0].x, 1e-5f)
        val west = listOf(v(0f, 0f), v(-10f, 0f)) // heading -X: right is -Z
        assertEquals(-2f, offset(west, 2f)[0].z, 1e-5f)
    }

    @Test fun heading_zeroIsMinusZ_positiveTurnsRight() {
        assertEquals(0f, heading(v(0f, -1f)), 1e-6f)
        assertEquals(deg(90f), heading(v(1f, 0f)), 1e-6f)
        assertEquals(deg(-90f), heading(v(-1f, 0f)), 1e-6f)
    }

    @Test fun dashes_expresswayPattern() {
        val d = dashes(straight, Kr.HW_DASH, Kr.HW_GAP)
        assertEquals(5, d.size) // 0-10, 20-30, 40-50, 60-70, 80-90
        assertEquals(10f, arcLengths(d[0]).last(), 1e-4f)
        assertEquals(-20f, d[1].first().z, 1e-4f)
    }

    @Test fun arc_counterClockwiseFromPlusX() {
        val a = arc(v(0f, 0f), 10f, 0f, deg(90f))
        assertEquals(10f, a.first().x, 1e-4f)
        assertEquals(-10f, a.last().z, 1e-4f) // 90° is -Z (north)
    }

    @Test fun smooth_passesThroughItsPoints() {
        val pts = listOf(v(0f, 0f), v(5f, -20f), v(0f, -40f))
        val s = smooth(pts)
        assertEquals(pts.first(), s.first()); assertEquals(pts.last(), s.last())
        assertTrue(s.any { kotlin.math.abs(it.x - 5f) < 1e-3f && kotlin.math.abs(it.z + 20f) < 1e-3f })
    }

    @Test fun pace_constantSpeed_stop_andSecAt() {
        val p = Pace(10f).go(100f).go(20f, 0f).stop(3f).go(10f, 5f)
        assertEquals(10f, p.secAt(100f), 1e-4f)
        assertEquals(10f + 4f, p.secAt(120f), 1e-3f) // 20 m at an average of 5 m/s
        assertEquals(130f, p.metres, 1e-3f)
        assertEquals(10f + 4f + 3f + 4f, p.sec, 1e-3f)
        val k = p.keys()
        assertEquals(0f, k.first().t); assertEquals(1f, k.last().t, 1e-6f)
        assertEquals(120f, at(k, (14f + 1.5f) / p.sec), 1e-3f) // standing still mid-stop
        assertEquals(50f, p.metresAt(5f), 1e-3f); assertEquals(120f, p.metresAt(15f), 1e-3f)
    }

    @Test fun nearestS_projectsOntoThePath() {
        assertEquals(30f, nearestS(straight, v(4f, -30f)), 1e-4f)
        assertEquals(0f, nearestS(straight, v(0f, 50f)), 1e-4f)
    }

    @Test fun at_clampsAndInterpolates() {
        val k = listOf(Key(0f, 0f), Key(.5f, 10f), Key(1f, 10f))
        assertEquals(5f, at(k, .25f), 1e-6f); assertEquals(0f, at(k, -1f)); assertEquals(10f, at(k, 2f))
    }
}
