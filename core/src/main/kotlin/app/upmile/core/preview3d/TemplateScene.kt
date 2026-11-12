package app.upmile.core.preview3d

import kotlin.math.sqrt

/** Metres; road runs toward -Z, +X is right, +Y is up. */
data class Vec3(val x: Float, val y: Float, val z: Float)
data class Strip(val center: List<Vec3>, val width: Float, val color: Long, val dashed: Boolean = false)
/** What the Canvas fallback draws. Cue progress is the ARCLENGTH fraction (0..1) along cameraPath. */
data class TemplateScene(val strips: List<Strip>, val cameraPath: List<Vec3>, val cues: List<Pair<Float, String>>)

/** Wall strips; the Canvas renderer draws them upright, and a scene with walls is underground. */
const val WALL = 0xFF8A9099L

/** Cumulative 3D distance along [path]; first entry is 0. */
fun arcLengths(path: List<Vec3>): List<Float> {
    val out = ArrayList<Float>(path.size).apply { add(0f) }
    for (i in 1 until path.size) {
        val a = path[i - 1]; val b = path[i]
        val dx = b.x - a.x; val dy = b.y - a.y; val dz = b.z - a.z
        out += out.last() + sqrt(dx * dx + dy * dy + dz * dz)
    }
    return out
}
