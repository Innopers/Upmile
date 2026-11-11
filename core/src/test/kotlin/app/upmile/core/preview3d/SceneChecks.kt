package app.upmile.core.preview3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Invariants every built scene keeps; each scene test calls [all]. */
object SceneChecks {
    private val DRIVABLE = setOf(Mat.ASPHALT, Mat.EPOXY, Mat.CONCRETE)

    fun all(spec: SceneSpec, expectedCues: List<String>) {
        cues(spec, expectedCues); timing(spec); onRoad(spec); gaps(spec); propsClear(spec)
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

    /** At every 1% of progress the eye and every car stand over a drivable surface, and keep CLEARANCE from every extruded solid. */
    fun onRoad(spec: SceneSpec) = samples().forEach { t ->
        val eye = spec.cameraAt(t).first
        val cars = spec.cars.map { it.poseAt(t).at }
        assertTrue("eye off the road at t=$t: $eye", drivable(spec, eye))
        cars.forEachIndexed { i, c -> assertTrue("car $i off the road at t=$t: $c", drivable(spec, c)) }
        for ((who, p) in listOf("eye" to eye) + cars.mapIndexed { i, c -> "car $i" to c }) for (x in spec.solids) {
            val d = x.profile.map { it.first }.distinct().minOf { o -> offset(x.path, o).zipWithNext().minOf { (a, b) -> segDist(p, a, b) } }
            assertTrue("$who is $d m from a ${x.mat} solid at t=$t", d >= CLEARANCE)
        }
    }

    /** At every 1% of progress no car is within MIN_GAP of the eye or another car, nor in the same lane closer than SAME_LANE_LONG. */
    fun gaps(spec: SceneSpec) = samples().forEach { t ->
        val (eye, look) = spec.cameraAt(t)
        val cars = spec.cars.map { it.poseAt(t) }
        cars.forEachIndexed { i, c ->
            assertTrue("car $i is ${dist(c.at, eye)} m from the camera at t=$t", dist(c.at, eye) >= MIN_GAP)
            sameLane("car $i vs the camera at t=$t", eye, Vec3(look.x - eye.x, 0f, look.z - eye.z), c.at)
            for (j in i + 1 until cars.size) {
                assertTrue("cars $i and $j are ${dist(c.at, cars[j].at)} m apart at t=$t", dist(c.at, cars[j].at) >= MIN_GAP)
                sameLane("cars $i and $j at t=$t", c.at, c.dir, cars[j].at)
            }
        }
    }

    private fun sameLane(what: String, a: Vec3, dir: Vec3, b: Vec3) {
        val n = hypot(dir.x, dir.z); if (n < 1e-4f) return
        val fx = dir.x / n; val fz = dir.z / n
        val dx = b.x - a.x; val dz = b.z - a.z
        val long = abs(dx * fx + dz * fz); val lat = abs(dx * -fz + dz * fx)
        assertTrue("$what: ${lat} m beside, only $long m along the same lane", lat >= SAME_LANE_LAT || long >= SAME_LANE_LONG)
    }

    /** Trees stand off the road and out of buildings; buildings do not sit on the road. */
    fun propsClear(spec: SceneSpec) {
        val boxes = spec.props.filter { it.kind in BUILDINGS && it.size != null }
        for (p in spec.props.filter { it.kind == PropKind.TREE }) {
            assertTrue("tree on or beside the road at ${p.at}", !onSurface(spec, p.at, 0.5f))
            boxes.forEach { b -> assertTrue("tree ${p.at} inside ${b.kind} at ${b.at}", !inside(p.at, footprint(b, 1f))) }
        }
        for (b in boxes) (footprint(b, 0f) + b.at).forEach { c -> assertTrue("${b.kind} at ${b.at} covers road at $c", !onSurface(spec, c, 0f)) }
    }

    private val BUILDINGS = setOf(PropKind.BUILDING_GLASS, PropKind.BUILDING_APT, PropKind.BUILDING_SHOP)

    private fun footprint(p: Prop, grow: Float): List<Vec3> {
        val h = p.heading; val sz = p.size!!
        val rx = cos(h); val rz = sin(h); val lx = -sin(h); val lz = cos(h)
        val w = sz.x / 2 + grow; val d = sz.z / 2 + grow
        return listOf(1f to 1f, 1f to -1f, -1f to -1f, -1f to 1f).map { (a, b) -> Vec3(p.at.x + rx * w * a + lx * d * b, 0f, p.at.z + rz * w * a + lz * d * b) }
    }

    private fun drivable(spec: SceneSpec, p: Vec3) = spec.surfaces.any { it.mat in DRIVABLE && within(p, it) } || spec.patches.any { it.mat in DRIVABLE && inside(p, it.outline) }

    /** On a drivable ribbon (within half width + [margin]) or inside a drivable patch. */
    private fun onSurface(spec: SceneSpec, p: Vec3, margin: Float) =
        spec.surfaces.any { it.mat in DRIVABLE && it.center.zipWithNext().any { (a, b) -> segDist(p, a, b) <= it.width / 2 + margin } } ||
            spec.patches.any { it.mat in DRIVABLE && inside(p, it.outline) }

    private const val CLEARANCE = 0.9f      // car body half-width plus a hand of margin from a barrier or rail
    private const val SAME_LANE_LAT = 2.0f  // closer sideways than this is the same lane (lane width 3.5 m)
    private const val SAME_LANE_LONG = 5.0f // a car is ~4.6 m long, so nose-to-tail needs at least this

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
