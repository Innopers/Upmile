package app.upmile.core.preview3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.hypot

/** Invariants every built scene keeps; each scene test calls [all]. */
object SceneChecks {
    private val DRIVABLE = setOf(Mat.ASPHALT, Mat.EPOXY, Mat.CONCRETE)

    fun all(spec: SceneSpec, expectedCues: List<String>) {
        cues(spec, expectedCues); timing(spec); onRoad(spec); gaps(spec)
    }

    fun cues(spec: SceneSpec, expected: List<String>) {
        assertEquals(expected, spec.cues.map { it.second })
        assertTrue("cues inside (0,1): ${spec.cues}", spec.cues.all { it.first > 0f && it.first < 1f })
        assertTrue("cues strictly increasing: ${spec.cues}", spec.cues.zipWithNext().all { (a, b) -> a.first < b.first })
    }

    fun timing(spec: SceneSpec) {
        assertTrue("duration ${spec.durationMs}", spec.durationMs in 12_000L..40_000L)
        val k = spec.camera.keys
        assertEquals(0f, k.first().t, 1e-6f); assertEquals(1f, k.last().t, 1e-6f)
        assertTrue("camera keys monotonic", k.zipWithNext().all { (a, b) -> b.t >= a.t && b.s >= a.s - 1e-3f })
        assertEquals("camera ends at its path end", spec.pathM, k.last().s, .5f)
        spec.cars.forEach { c -> assertTrue("car keys sorted", c.keys.zipWithNext().all { (a, b) -> b.t >= a.t }) }
    }

    /** At every 1% of progress the eye stands over a drivable ribbon or patch. */
    fun onRoad(spec: SceneSpec) = samples().forEach { t ->
        val eye = spec.cameraAt(t).first
        val ok = spec.surfaces.any { it.mat in DRIVABLE && within(eye, it) } || spec.patches.any { it.mat in DRIVABLE && inside(eye, it.outline) }
        assertTrue("eye off the road at t=$t: $eye", ok)
    }

    /** At every 1% of progress no car is within MIN_GAP of the eye or of another car (XZ plane). */
    fun gaps(spec: SceneSpec) = samples().forEach { t ->
        val eye = spec.cameraAt(t).first
        val cars = spec.cars.map { it.poseAt(t).at }
        cars.forEachIndexed { i, c ->
            assertTrue("car $i is ${dist(c, eye)} m from the camera at t=$t", dist(c, eye) >= MIN_GAP)
            for (j in i + 1 until cars.size) assertTrue("cars $i and $j are ${dist(c, cars[j])} m apart at t=$t", dist(c, cars[j]) >= MIN_GAP)
        }
    }

    fun deterministic(build: () -> SceneSpec) = assertEquals(build(), build())

    fun samples() = (0..100).map { it / 100f }
    fun dist(a: Vec3, b: Vec3) = hypot(a.x - b.x, a.z - b.z)

    /** Point in a convex polygon (XZ), either winding. */
    fun inside(p: Vec3, poly: List<Vec3>): Boolean {
        var pos = false; var neg = false
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[(i + 1) % poly.size]
            val c = (b.x - a.x) * (p.z - a.z) - (b.z - a.z) * (p.x - a.x)
            if (c > 1e-4f) pos = true
            if (c < -1e-4f) neg = true
        }
        return !(pos && neg)
    }

    private fun within(p: Vec3, r: Ribbon) = r.center.zipWithNext().any { (a, b) -> segDist(p, a, b) <= r.width / 2 + .3f }

    private fun segDist(p: Vec3, a: Vec3, b: Vec3): Float {
        val dx = b.x - a.x; val dz = b.z - a.z; val l2 = dx * dx + dz * dz
        val u = if (l2 == 0f) 0f else (((p.x - a.x) * dx + (p.z - a.z) * dz) / l2).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + dx * u), p.z - (a.z + dz * u))
    }
}
