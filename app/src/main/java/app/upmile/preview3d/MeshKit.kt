package app.upmile.preview3d

import app.upmile.core.preview3d.Extrusion
import app.upmile.core.preview3d.Mat
import app.upmile.core.preview3d.Patch
import app.upmile.core.preview3d.Ribbon
import app.upmile.core.preview3d.SceneSpec
import app.upmile.core.preview3d.Vec3
import app.upmile.core.preview3d.arcLengths
import app.upmile.core.preview3d.minus
import app.upmile.core.preview3d.offset
import app.upmile.core.preview3d.plus
import app.upmile.core.preview3d.times
import app.upmile.core.preview3d.unit
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/** Turns a scene's ribbons, patches and extrusions into one mesh per material. */
internal object MeshKit {
    /**
     * Metres per texture repeat. Ground uses world XZ (so overlapping asphalt pieces line up); solids run along their path.
     * Most materials are flat colours now and never read their UVs; only the hazard stripes (and any future textured surface) use this.
     */
    private val TILE = mapOf(
        Mat.ASPHALT to 6f, Mat.GRASS to 5f, Mat.PAVING to 4f, Mat.CONCRETE to 3f, Mat.EPOXY to 4f,
        Mat.BARRIER to 2f, Mat.WALL to 3f, Mat.CEILING to 3f, Mat.HAZARD to 1f, Mat.NOISE_WALL to 4f,
    )
    fun tile(m: Mat) = TILE[m] ?: 1f

    fun build(spec: SceneSpec): Map<Mat, MeshData> {
        val out = LinkedHashMap<Mat, MeshData>()
        fun mesh(m: Mat) = out.getOrPut(m) { MeshData() }
        spec.surfaces.forEach { ribbon(mesh(it.mat), it, tile(it.mat)) }
        spec.patches.forEach { patch(mesh(it.mat), it, tile(it.mat)) }
        spec.solids.forEach { extrusion(mesh(it.mat), it, tile(it.mat)) }
        return out
    }

    private fun Vec3.raised(by: Float) = copy(y = y + by)
    private fun planar(tile: Float, vararg p: Vec3) = FloatArray(2 * p.size) { i -> if (i % 2 == 0) p[i / 2].x / tile else p[i / 2].z / tile }

    fun ribbon(out: MeshData, r: Ribbon, tile: Float) {
        if (r.center.size < 2) return
        val left = offset(r.center, -r.width / 2).map { it.raised(r.lift) }
        val right = offset(r.center, r.width / 2).map { it.raised(r.lift) }
        for (i in 1 until r.center.size) {
            val a = left[i - 1]; val b = right[i - 1]; val c = right[i]; val d = left[i]
            val n = cross(b - a, d - a).unit().let { if (it.y < 0f) it * -1f else it }
            out.quad(a, b, c, d, n, planar(tile, a, b, c, d))
        }
    }

    fun patch(out: MeshData, p: Patch, tile: Float) {
        val pts = p.outline.map { it.raised(p.lift) }
        val n = Vec3(0f, if (p.faceDown) -1f else 1f, 0f)
        val idx = pts.map { out.vertex(it, n, it.x / tile, it.z / tile) }
        for (k in 1 until pts.size - 1) {
            if (dot(cross(pts[k] - pts[0], pts[k + 1] - pts[0]), n) >= 0f) out.triangle(idx[0], idx[k], idx[k + 1])
            else out.triangle(idx[0], idx[k + 1], idx[k])
        }
    }

    /** Sweeps the counter-clockwise (right, up) profile along the path; each profile edge is a flat-shaded band. */
    fun extrusion(out: MeshData, e: Extrusion, tile: Float) {
        val path = e.path
        if (path.size < 2) return
        val arc = arcLengths(path)
        // Same right-hand convention as ribbons; degenerate points are handled by offset().
        val right = offset(path, 1f).mapIndexed { i, o -> o - path[i] }.toMutableList()
        val closed = path.size > 2 && abs(path.first().x - path.last().x) < 1e-4f && abs(path.first().z - path.last().z) < 1e-4f
        if (closed) { // both seam vertices share the direction across the seam (last segment before, first after)
            val a = path[path.size - 2]; val b = path[1]
            val dx = b.x - a.x; val dz = b.z - a.z; val l = sqrt(dx * dx + dz * dz)
            if (l > 0f) { val s = Vec3(-dz / l, 0f, dx / l); right[0] = s; right[path.lastIndex] = s }
        }
        var perim = 0f
        for (k in e.profile.indices) {
            val (r0, u0) = e.profile[k]; val (r1, u1) = e.profile[(k + 1) % e.profile.size]
            val len = hypot(r1 - r0, u1 - u0)
            if (len == 0f) continue
            val nr = (u1 - u0) / len; val nu = -(r1 - r0) / len // outward normal of a counter-clockwise edge
            fun at(j: Int, r: Float, u: Float) = path[j] + right[j] * r + Vec3(0f, u, 0f)
            for (i in 1 until path.size) {
                val n = (right[i] * nr + Vec3(0f, nu, 0f)).unit()
                val uv = floatArrayOf(
                    arc[i - 1] / tile, perim / tile, arc[i] / tile, perim / tile,
                    arc[i] / tile, (perim + len) / tile, arc[i - 1] / tile, (perim + len) / tile,
                )
                out.quad(at(i - 1, r0, u0), at(i, r0, u0), at(i, r1, u1), at(i - 1, r1, u1), n, uv)
            }
            perim += len
        }
    }
}
