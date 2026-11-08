package app.upmile.preview3d

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.upmile.core.preview3d.RoadTemplates.WALL
import app.upmile.core.preview3d.Strip
import app.upmile.core.preview3d.TemplateScene
import app.upmile.core.preview3d.Vec3
import app.upmile.core.preview3d.arcLengths
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

private const val FOV_DEG = 60f
private const val NEAR = .1f
/** As in the mockup: look 14 m ahead along the path, 0.25 m below eye height. */
private const val LOOK_AHEAD = 14f
private const val LOOK_DROP = .25f
private const val DASH = 3f
private const val GAP = 5f
/** A wall strip's centre line sits 1 m above the road; it stands this tall from the road. */
private const val WALL_H = 2.8f
private const val TREE_TRIES = 160
/** Trees are scattered this far around the camera's route. */
private const val TREE_REACH = 100f
/** Trees keep this far from any strip's edge, so they never stand on or crowd the road. */
private const val TREE_MARGIN = 6f
private const val UPRIGHT = 3
/** Eye height above the ground; RoadTemplates' camera path already includes it. */
private const val EYE = 1.25f

/** Backdrop and fog per setting; the garage ramp is underground, so it gets a dark ceiling instead of sky and trees. */
private class Look(val top: Long, val fog: Long, val ground: Long, val fogNear: Float, val fogFar: Float)
private val Outdoor = Look(0xFF8FAFD2, 0xFFB3C9DF, 0xFF6F826D, 60f, 330f)
private val Underground = Look(0xFF20242A, 0xFF0B0D10, 0xFF15181C, 8f, 90f)
private val TreeColors = intArrayOf(0xFF2F4A3A.toInt(), 0xFF3A5642.toInt())

/** Driver's-eye camera at [eye] looking along [look] (any length); up is +Y, no roll. */
internal class Camera(val eye: Vec3, look: Vec3) {
    val fx: Float; val fy: Float; val fz: Float
    /** Right = forward × up, always horizontal. */
    val rx: Float; val rz: Float
    private val ux: Float; private val uy: Float; private val uz: Float

    init {
        val n = sqrt(look.x * look.x + look.y * look.y + look.z * look.z)
        fx = look.x / n; fy = look.y / n; fz = look.z / n
        val h = hypot(fx, fz)
        rx = -fz / h; rz = fx / h
        ux = -rz * fy; uy = rz * fx - rx * fz; uz = rx * fy // right × forward
    }

    fun depth(x: Float, y: Float, z: Float) = (x - eye.x) * fx + (y - eye.y) * fy + (z - eye.z) * fz

    /** Writes point [i] of [src] (xyz triples, world) into [dst] as (right, up, depth). */
    fun toView(src: FloatArray, i: Int, dst: FloatArray) {
        val dx = src[i * 3] - eye.x; val dy = src[i * 3 + 1] - eye.y; val dz = src[i * 3 + 2] - eye.z
        dst[i * 3] = dx * rx + dz * rz
        dst[i * 3 + 1] = dx * ux + dy * uy + dz * uz
        dst[i * 3 + 2] = dx * fx + dy * fy + dz * fz
    }

    /** Screen position of [p] on a [w]×[h] canvas, or null when it is behind the near plane. */
    fun project(p: Vec3, w: Float, h: Float): Offset? {
        val v = FloatArray(3).also { toView(floatArrayOf(p.x, p.y, p.z), 0, it) }
        return if (v[2] < NEAR) null else toScreen(v[0], v[1], v[2], w, h, focal(h))
    }

    /** Screen row of the horizon: where a level line of sight ends up. */
    fun horizonY(h: Float) = h / 2 + focal(h) * fy / hypot(fx, fz)
}

internal fun focal(h: Float) = h / 2 / tan(FOV_DEG / 2 * PI.toFloat() / 180f)

/** View-space point (right, up, depth ≥ NEAR) on a [w]×[h] canvas with focal length [f]. */
private fun toScreen(vx: Float, vy: Float, vz: Float, w: Float, h: Float, f: Float) = Offset(w / 2 + vx / vz * f, h / 2 - vy / vz * f)

/** The camera at arclength fraction [progress] of [path] ([arc] = its [arcLengths]). */
internal fun cameraAt(path: List<Vec3>, arc: List<Float>, progress: Float): Camera {
    if (path.size < 2) return Camera(path.first(), Vec3(0f, -LOOK_DROP, -LOOK_AHEAD))
    val s = progress.coerceIn(0f, 1f) * arc.last()
    val eye = pointAt(path, arc, s)
    val ahead = pointAt(path, arc, s + LOOK_AHEAD)
    return Camera(eye, Vec3(ahead.x - eye.x, ahead.y - LOOK_DROP - eye.y, ahead.z - eye.z))
}

/** The point [s] metres along [path] (2+ points); past the end it carries on along the last segment, so the view never snaps. */
internal fun pointAt(path: List<Vec3>, arc: List<Float>, s: Float): Vec3 {
    var i = 1
    while (i < path.lastIndex && arc[i] < s) i++
    val a = path[i - 1]; val b = path[i]
    val len = arc[i] - arc[i - 1]
    val t = if (len > 0f) (s - arc[i - 1]) / len else 0f
    return Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)
}

/** Painted spans (from, to) in metres along a dashed line [length] long. */
internal fun dashSpans(length: Float): List<Pair<Float, Float>> =
    generateSequence(0f) { it + DASH + GAP }.takeWhile { it + DASH <= length }.map { it to it + DASH }.toList()

/** One world polygon (xyz triples), or a cone tree (x, y, z, radius, height) turned to face the camera each frame. */
private class Item(val pts: FloatArray, val color: Int, val layer: Int, val tree: Boolean = false) {
    val cx: Float; val cy: Float; val cz: Float

    init {
        val n = if (tree) 1 else pts.size / 3
        cx = (0 until n).sumOf { pts[it * 3].toDouble() }.toFloat() / n
        cy = (0 until n).sumOf { pts[it * 3 + 1].toDouble() }.toFloat() / n
        cz = (0 until n).sumOf { pts[it * 3 + 2].toDouble() }.toFloat() / n
    }
}

/** A scene's polygons, built once; each frame only transforms, sorts and projects them. */
private class World(scene: TemplateScene) {
    private val path = scene.cameraPath
    private val arc = arcLengths(path)
    private val look = if (scene.strips.any { it.color == WALL }) Underground else Outdoor
    private val items = buildList {
        scene.strips.forEach { addStrip(it) }
        if (look === Outdoor) addAll(trees(scene.strips, path))
    }
    private val depth = FloatArray(items.size)
    private val key = FloatArray(items.size)
    private val order = IntArray(items.size) { it } // kept across frames: nearly sorted, so the insertion sort is ~linear
    private val tri = FloatArray(9)
    private val view = FloatArray(15)
    private val clipped = FloatArray(18)
    private val poly = Path()
    // Gradients run over a unit-high rect that draw() scales to size: brushes span the whole canvas otherwise, and this way none is made per frame.
    private val sky = Brush.verticalGradient(listOf(Color(look.top), Color(look.fog)), startY = 0f, endY = 1f)
    private val fog = Color(look.fog)
    /** Outdoors, ground fog fades in per pixel just below the horizon (as distance 60 m → ∞), so flat pieces need no stepped fog. */
    private val haze = Brush.verticalGradient(0f to fog, .2f to fog.copy(alpha = .9f), .5f to fog.copy(alpha = .22f), 1f to fog.copy(alpha = 0f), startY = 0f, endY = 1f)
    private val hardEdged = Paint().apply { isAntiAlias = false }

    fun DrawScope.draw(progress: Float) {
        val cam = cameraAt(path, arc, progress)
        val w = size.width; val h = size.height; val f = focal(h)
        val hy = cam.horizonY(h).coerceIn(0f, h)
        scale(1f, hy, Offset.Zero) { drawRect(sky, size = Size(w, 1f)) }
        drawRect(Color(look.ground), Offset(0f, hy), Size(w, h - hy))

        for (i in items.indices) {
            val it = items[i]
            depth[i] = cam.depth(it.cx, it.cy, it.cz)
            key[i] = it.layer * 1e5f - depth[i] // by layer, then far to near
        }
        for (i in 1 until order.size) {
            val k = order[i]; var j = i - 1
            while (j >= 0 && key[order[j]] > key[k]) { order[j + 1] = order[j]; j-- }
            order[j + 1] = k
        }
        for (k in order) drawItem(items[k], depth[k], cam, w, h, f)
        if (look === Outdoor) translate(top = hy) { scale(1f, f * EYE / look.fogNear, Offset.Zero) { drawRect(haze, size = Size(w, 1f)) } }
    }

    private fun DrawScope.drawItem(item: Item, d: Float, cam: Camera, w: Float, h: Float, f: Float) {
        val src: FloatArray
        if (item.tree) {
            val p = item.pts
            tri[0] = p[0] - cam.rx * p[3]; tri[1] = p[1]; tri[2] = p[2] - cam.rz * p[3]
            tri[3] = p[0] + cam.rx * p[3]; tri[4] = p[1]; tri[5] = p[2] + cam.rz * p[3]
            tri[6] = p[0]; tri[7] = p[1] + p[4]; tri[8] = p[2]
            src = tri
        } else src = item.pts
        val n = if (item.tree) 3 else src.size / 3
        for (i in 0 until n) cam.toView(src, i, view)
        val m = clipNear(view, n, clipped)
        if (m < 3) return
        poly.reset()
        for (i in 0 until m) {
            val o = toScreen(clipped[i * 3], clipped[i * 3 + 1], clipped[i * 3 + 2], w, h, f)
            if (i == 0) poly.moveTo(o.x, o.y) else poly.lineTo(o.x, o.y)
        }
        poly.close()
        val flatOutdoors = item.layer < UPRIGHT && look === Outdoor
        val c = Color(if (flatOutdoors) item.color else fogged(item.color, look.fog.toInt(), (d - look.fogNear) / (look.fogFar - look.fogNear)))
        if (item.layer < 2) {
            // Road and guide: a hard-edged pass first fills the hairline seams AA leaves between pieces;
            // each pass gets the alpha that makes the two together the strip's own.
            val two = c.copy(alpha = 1 - sqrt(1 - c.alpha))
            hardEdged.color = two
            drawContext.canvas.drawPath(poly, hardEdged)
            drawPath(poly, two)
        } else drawPath(poly, c)
    }
}

/** Keeps the part of polygon [src] ([n] view-space points) in front of the near plane; returns its point count. */
internal fun clipNear(src: FloatArray, n: Int, dst: FloatArray): Int {
    var m = 0
    for (i in 0 until n) {
        val j = (i + 1) % n
        val az = src[i * 3 + 2]; val bz = src[j * 3 + 2]
        if (az >= NEAR) { for (c in 0..2) dst[m * 3 + c] = src[i * 3 + c]; m++ }
        if ((az >= NEAR) != (bz >= NEAR)) {
            val t = (NEAR - az) / (bz - az)
            for (c in 0..2) dst[m * 3 + c] = src[i * 3 + c] + (src[j * 3 + c] - src[i * 3 + c]) * t
            m++
        }
    }
    return m
}

/** [c] faded toward [fog] by [t] (0..1), keeping its alpha. */
private fun fogged(c: Int, fog: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    fun ch(shift: Int) = ((c shr shift and 0xFF) * (1 - k) + (fog shr shift and 0xFF) * k).toInt()
    return (c and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

private fun quad(color: Int, layer: Int, vararg xyz: Float) = Item(xyz, color, layer)

/** Road surface first, then the translucent guide, then markings; walls stand upright. */
private fun MutableList<Item>.addStrip(s: Strip) {
    val c = s.center
    val color = s.color.toInt()
    val layer = when { s.color ushr 24 < 0xFF -> 1; s.width >= 1f -> 0; else -> 2 }
    val hw = s.width / 2
    when {
        s.color == WALL -> for (i in 1 until c.size) {
            val a = c[i - 1]; val b = c[i]
            add(quad(color, UPRIGHT, a.x, a.y - 1, a.z, b.x, b.y - 1, b.z, b.x, b.y - 1 + WALL_H, b.z, a.x, a.y - 1 + WALL_H, a.z))
        }
        s.dashed -> {
            val arc = arcLengths(c)
            dashSpans(arc.last()).forEach { (from, to) ->
                val a = pointAt(c, arc, from); val b = pointAt(c, arc, to)
                val len = hypot(b.x - a.x, b.z - a.z).coerceAtLeast(1e-6f)
                val nx = -(b.z - a.z) / len * hw; val nz = (b.x - a.x) / len * hw
                add(quad(color, layer, a.x - nx, a.y, a.z - nz, a.x + nx, a.y, a.z + nz, b.x + nx, b.y, b.z + nz, b.x - nx, b.y, b.z - nz))
            }
        }
        else -> {
            // per-vertex normals from both neighbours, so pieces share edges and curves have no gaps
            val nx = FloatArray(c.size); val nz = FloatArray(c.size)
            for (i in c.indices) {
                val a = c[max(i - 1, 0)]; val b = c[min(i + 1, c.lastIndex)]
                val len = hypot(b.x - a.x, b.z - a.z).coerceAtLeast(1e-6f)
                nx[i] = -(b.z - a.z) / len * hw; nz[i] = (b.x - a.x) / len * hw
            }
            for (i in 1 until c.size) {
                val a = c[i - 1]; val b = c[i]; val p = i - 1
                add(quad(color, layer, a.x - nx[p], a.y, a.z - nz[p], a.x + nx[p], a.y, a.z + nz[p], b.x + nx[i], b.y, b.z + nz[i], b.x - nx[i], b.y, b.z - nz[i]))
            }
        }
    }
}

/** Cone trees on both sides, placed by the mockup's seeded LCG so every run looks the same. */
private fun trees(strips: List<Strip>, route: List<Vec3>): List<Item> {
    val x0 = route.minOf { it.x } - TREE_REACH; val x1 = route.maxOf { it.x } + TREE_REACH
    val z0 = route.minOf { it.z } - TREE_REACH; val z1 = route.maxOf { it.z } + TREE_REACH
    var r = 7L
    fun rnd(): Float { r = (r * 9301 + 49297) % 233280; return r / 233280f }
    return (0 until TREE_TRIES).mapNotNull {
        val x = x0 + rnd() * (x1 - x0); val z = z0 + rnd() * (z1 - z0)
        val height = 3 + rnd() * 4; val rad = 1.2f + rnd(); val color = TreeColors[if (rnd() > .5f) 0 else 1]
        val onRoad = strips.any { s -> s.center.zipWithNext().any { (a, b) -> distXZ(x, z, a, b) < s.width / 2 + rad + TREE_MARGIN } }
        if (onRoad) null else Item(floatArrayOf(x, 0f, z, rad, height), color, UPRIGHT, tree = true)
    }
}

private fun distXZ(x: Float, z: Float, a: Vec3, b: Vec3): Float {
    val dx = b.x - a.x; val dz = b.z - a.z
    val t = (((x - a.x) * dx + (z - a.z) * dz) / (dx * dx + dz * dz).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
    return hypot(x - a.x - dx * t, z - a.z - dz * t)
}

/** The road seen from the driver's seat at [progress] (arclength fraction 0..1 along [TemplateScene.cameraPath]). */
@Composable
fun CanvasPreview(scene: TemplateScene, progress: Float, modifier: Modifier) {
    val world = remember(scene) { World(scene) }
    Canvas(modifier) { with(world) { draw(progress) } }
}
