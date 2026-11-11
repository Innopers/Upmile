package app.upmile.preview3d

import app.upmile.core.preview3d.Lamp
import app.upmile.core.preview3d.Prop
import app.upmile.core.preview3d.PropKind
import app.upmile.core.preview3d.Vec3
import app.upmile.core.preview3d.minus
import app.upmile.core.preview3d.unit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Prop materials; each maps to one shared Filament material (Task 9). */
internal enum class Part { POLE, DARK, BACK, LAMP_WARM, LAMP_OFF, RED_ON, YELLOW_ON, ARROW_ON, GREEN_ON, FOLIAGE, BARK,
    GLASS_FACADE, APT_FACADE, SHOP_FACADE, ROOF, MIRROR, MIRROR_RIM, HAZARD, CONCRETE, STEEL }
/** A texture drawn from text; signs with the same kind and text share one. */
internal data class SignFace(val kind: PropKind, val text: List<String>)
internal sealed interface Batch {
    data class Of(val part: Part) : Batch
    data class Face(val face: SignFace) : Batch
}
/** The lamp a lit part shows, or null. */
internal fun Part.lamp(): Lamp? = when (this) { Part.RED_ON -> Lamp.RED; Part.YELLOW_ON -> Lamp.YELLOW; Part.ARROW_ON -> Lamp.ARROW; Part.GREEN_ON -> Lamp.GREEN; else -> null }

/**
 * Builds prop meshes in a local frame (+Z faces the approaching driver, +X is his right, +Y up), then places them:
 * rotate about Y by -heading, translate by `at`. Size meaning per kind is in the table of [defaultSize].
 */
internal object PropKit {
    // Per-kind default sizes (m); the meaning of x/y/z differs per kind, see each builder.
    private val STREET_LAMP_SIZE = Vec3(2.5f, 9f, 0f)
    private val TWIN_LAMP_SIZE = Vec3(2.2f, 10f, 0f)
    private val SIGNAL_MAST_SIZE = Vec3(12f, 6f, 0f)
    private val BOARD_SIZE = Vec3(3.6f, 1.8f, 2.2f)          // width, height, height of the lower edge
    private val GANTRY_SIZE = Vec3(25f, 6f, 0f)               // span, clearance
    private val CHEVRON_SIZE = Vec3(.45f, .6f, 1f)
    private val TREE_SIZE = Vec3(2.2f, 7f, 0f)                // crown radius, height
    private val BUILDING_SIZE = Vec3(30f, 40f, 25f)
    private val PILLAR_SIZE = Vec3(.7f, 3f, 0f)
    private val CEILING_LIGHT_SIZE = Vec3(.25f, .06f, 1.2f)
    private val MIRROR_SIZE = Vec3(.8f, 2.2f, 0f)             // diameter, centre height
    private val HEIGHT_BAR_SIZE = Vec3(7.2f, 2.3f, 0f)        // span, clearance
    private val BUMP_SIZE = Vec3(6.5f, .08f, .4f)
    private val ROAD_TEXT_SIZE = Vec3(2.4f, 0f, 4f)           // width, floor drop over the length away from the driver, length (z)

    private const val SIGN_POLE_R = .04f
    private const val SIGN_POLE_Z = -.06f      // behind the back plate so the pole never pokes through the face
    private const val TRI_SIDE = .9f
    private const val TRI_BACK_SIDE = 1f
    private const val ROUND_R = .32f
    private const val ROUND_BACK_R = .36f
    private const val BUILDING_TILE_W = 12f
    private const val GLASS_TILE_H = 14.4f
    private const val APT_TILE_H = 11.2f
    private const val EQUILATERAL = 0.8660254f

    private fun defaultSize(k: PropKind): Vec3 = when (k) {
        PropKind.STREET_LAMP -> STREET_LAMP_SIZE
        PropKind.TWIN_LAMP -> TWIN_LAMP_SIZE
        PropKind.SIGNAL_MAST -> SIGNAL_MAST_SIZE
        PropKind.SIGN_GREEN, PropKind.SIGN_BLUE -> BOARD_SIZE
        PropKind.GANTRY_GREEN -> GANTRY_SIZE
        PropKind.CHEVRON_LEFT, PropKind.CHEVRON_RIGHT -> CHEVRON_SIZE
        PropKind.TREE -> TREE_SIZE
        PropKind.BUILDING_GLASS, PropKind.BUILDING_APT, PropKind.BUILDING_SHOP -> BUILDING_SIZE
        PropKind.PILLAR -> PILLAR_SIZE
        PropKind.CEILING_LIGHT -> CEILING_LIGHT_SIZE
        PropKind.CONVEX_MIRROR -> MIRROR_SIZE
        PropKind.HEIGHT_BAR -> HEIGHT_BAR_SIZE
        PropKind.SPEED_BUMP -> BUMP_SIZE
        PropKind.ROAD_TEXT -> ROAD_TEXT_SIZE
        else -> Vec3(0f, 0f, 0f)
    }

    fun build(props: List<Prop>): Map<Batch, MeshData> {
        val out = LinkedHashMap<Batch, MeshData>()
        props.forEach { Shapes(it.at, it.heading, out).prop(it) }
        return out
    }

    private fun Shapes.prop(p: Prop) {
        val s = p.size ?: defaultSize(p.kind)
        val face = Batch.Face(SignFace(p.kind, p.text))
        when (p.kind) {
            PropKind.POST -> box(Vec3(0f, .375f, 0f), Vec3(.12f, .75f, .12f), Part.STEEL)
            PropKind.STREET_LAMP -> streetLamp(s.x, s.y, listOf(-1f))
            PropKind.TWIN_LAMP -> streetLamp(s.x, s.y, listOf(-1f, 1f))
            PropKind.SIGNAL_MAST -> signalMast(s.x, s.y)
            PropKind.SIGN_GREEN, PropKind.SIGN_BLUE -> board(face, s.x, s.y, s.z)
            PropKind.GANTRY_GREEN -> gantry(face, s.x, s.y)
            PropKind.WARNING_CURVE_LEFT, PropKind.WARNING_CURVE_RIGHT, PropKind.WARNING_ACCIDENT -> triSign(face, 2.35f, 2.6f, false)
            PropKind.YIELD_SIGN -> triSign(face, 2.3f, 2.6f, true)
            PropKind.SPEED_LIMIT, PropKind.ROUNDABOUT_SIGN -> roundSign(face)
            PropKind.CHEVRON_LEFT, PropKind.CHEVRON_RIGHT -> {
                cylinder(Vec3(0f, 0f, -.05f), SIGN_POLE_R, s.z + s.y, Part.POLE)
                face(Vec3(-s.x / 2, s.z + s.y, 0f), Vec3(s.x / 2, s.z + s.y, 0f), Vec3(s.x / 2, s.z, 0f), Vec3(-s.x / 2, s.z, 0f), face)
            }
            PropKind.TREE -> {
                cylinder(Vec3(0f, 0f, 0f), .14f, .5f * s.y, Part.BARK)
                ellipsoid(Vec3(0f, .6f * s.y, 0f), Vec3(s.x, .34f * s.y, s.x), Part.FOLIAGE)
                ellipsoid(Vec3(0f, .82f * s.y, 0f), Vec3(.65f * s.x, .18f * s.y, .65f * s.x), Part.FOLIAGE)
            }
            PropKind.BUILDING_GLASS -> building(s, Part.GLASS_FACADE, GLASS_TILE_H)
            PropKind.BUILDING_APT -> building(s, Part.APT_FACADE, APT_TILE_H)
            PropKind.BUILDING_SHOP -> building(s, Part.SHOP_FACADE, s.y)
            PropKind.PILLAR -> pillar(face, s.x, s.y)
            PropKind.CEILING_LIGHT -> box(Vec3(0f, 0f, 0f), s, Part.LAMP_WARM)
            PropKind.CONVEX_MIRROR -> {
                cylinder(Vec3(0f, 0f, -.06f), .05f, s.y, Part.POLE)
                disc(Vec3(0f, s.y, -.01f), s.x / 2 + .05f, Batch.Of(Part.MIRROR_RIM))
                disc(Vec3(0f, s.y, 0f), s.x / 2, Batch.Of(Part.MIRROR))
            }
            PropKind.HEIGHT_BAR -> {
                listOf(-1f, 1f).forEach { box(Vec3(it * s.x / 2, (s.y + .6f) / 2, 0f), Vec3(.2f, s.y + .6f, .2f), Part.HAZARD) }
                box(Vec3(0f, s.y + .12f, 0f), Vec3(s.x, .24f, .14f), Part.HAZARD)
                face(Vec3(-.8f, s.y + .75f, .08f), Vec3(.8f, s.y + .75f, .08f), Vec3(.8f, s.y + .3f, .08f), Vec3(-.8f, s.y + .3f, .08f), face)
            }
            PropKind.SPEED_BUMP -> speedBump(s.x, s.y, s.z)
            PropKind.ROAD_TEXT -> { // [at] is the floor under the centre; on a ramp the far edge (-z) lies s.y lower than the near one
                val far = .02f - s.y / 2; val near = .02f + s.y / 2
                face(Vec3(-s.x / 2, far, -s.z / 2), Vec3(s.x / 2, far, -s.z / 2), Vec3(s.x / 2, near, s.z / 2), Vec3(-s.x / 2, near, s.z / 2), face)
            }
        }
    }

    private fun Shapes.streetLamp(len: Float, h: Float, sides: List<Float>) {
        cylinder(Vec3(0f, 0f, 0f), .09f, h, Part.POLE)
        for (sd in sides) {
            val hx = sd * len
            box(Vec3(hx / 2, h - .1f, 0f), Vec3(len, .1f, .1f), Part.POLE)
            box(Vec3(hx, h - .2f, 0f), Vec3(.7f, .14f, .32f), Part.DARK)
            val y = h - .28f
            quad(Batch.Of(Part.LAMP_WARM), Vec3(hx - .3f, y, .13f), Vec3(hx + .3f, y, .13f), Vec3(hx + .3f, y, -.13f), Vec3(hx - .3f, y, -.13f), Vec3(0f, -1f, 0f))
        }
    }

    private fun Shapes.signalMast(len: Float, h: Float) {
        cylinder(Vec3(0f, 0f, 0f), .14f, h + .6f, Part.POLE)
        box(Vec3(-len / 2, h + .2f, 0f), Vec3(len, .18f, .18f), Part.POLE)
        for (hx in listOf(-.45f * len, -.92f * len)) {
            val cy = h - .15f
            box(Vec3(hx, cy, 0f), Vec3(1.36f, .42f, .30f), Part.DARK)
            val xs = listOf(-.48f, -.16f, .16f, .48f).map { hx + it }
            xs.forEach { disc(Vec3(it, cy, .16f), .14f, Batch.Of(Part.LAMP_OFF)) }
            disc(Vec3(xs[0], cy, .165f), .14f, Batch.Of(Part.RED_ON))
            disc(Vec3(xs[1], cy, .165f), .14f, Batch.Of(Part.YELLOW_ON))
            disc(Vec3(xs[3], cy, .165f), .14f, Batch.Of(Part.GREEN_ON))
            val a = Batch.Of(Part.ARROW_ON); val cx = xs[2]; val z = .165f // left arrow: shaft .18 x .06 and a head
            face(Vec3(cx - .06f, cy + .03f, z), Vec3(cx + .12f, cy + .03f, z), Vec3(cx + .12f, cy - .03f, z), Vec3(cx - .06f, cy - .03f, z), a)
            tri(a, Vec3(cx - .13f, cy, z), Vec3(cx - .06f, cy - .08f, z), Vec3(cx - .06f, cy + .08f, z), Vec3(0f, 0f, 1f), UV_ZERO)
        }
    }

    private fun Shapes.board(face: Batch, w: Float, h: Float, low: Float) {
        for (sd in listOf(-1f, 1f)) cylinder(Vec3(sd * (w / 2 - .35f), 0f, -.12f), .06f, low + h, Part.POLE)
        box(Vec3(0f, low + h / 2, -.05f), Vec3(w, h, .08f), Part.BACK)
        face(Vec3(-w / 2, low + h, 0f), Vec3(w / 2, low + h, 0f), Vec3(w / 2, low, 0f), Vec3(-w / 2, low, 0f), face)
    }

    private fun Shapes.gantry(face: Batch, span: Float, clear: Float) {
        listOf(0f, -span).forEach { cylinder(Vec3(it, 0f, 0f), .2f, clear + 2f, Part.POLE) }
        box(Vec3(-span / 2, clear + 1.7f, 0f), Vec3(span, .35f, .35f), Part.POLE)
        val cx = -.3f * span; val w = 6.5f; val h = 2.6f // hung in front of the beam so the beam does not hide the text
        box(Vec3(cx, clear + h / 2, .215f), Vec3(w, h, .08f), Part.BACK)
        face(Vec3(cx - w / 2, clear + h, .26f), Vec3(cx + w / 2, clear + h, .26f), Vec3(cx + w / 2, clear, .26f), Vec3(cx - w / 2, clear, .26f), face)
    }

    private fun Shapes.triSign(face: Batch, cy: Float, poleH: Float, inverted: Boolean) {
        cylinder(Vec3(0f, 0f, SIGN_POLE_Z), SIGN_POLE_R, poleH, Part.POLE)
        fun corners(side: Float, z: Float): List<Vec3> {
            val hh = side * EQUILATERAL / 2
            return if (inverted) listOf(Vec3(0f, cy - hh, z), Vec3(-side / 2, cy + hh, z), Vec3(side / 2, cy + hh, z))
            else listOf(Vec3(0f, cy + hh, z), Vec3(side / 2, cy - hh, z), Vec3(-side / 2, cy - hh, z))
        }
        val (a, b, c) = corners(TRI_BACK_SIDE, -.01f)
        tri(Batch.Of(Part.BACK), a, b, c, Vec3(0f, 0f, 1f), UV_ZERO)
        val (d, e, f) = corners(TRI_SIDE, 0f)
        tri(face, d, e, f, Vec3(0f, 0f, 1f), if (inverted) floatArrayOf(.5f, 1f, 0f, 0f, 1f, 0f) else floatArrayOf(.5f, 0f, 1f, 1f, 0f, 1f))
    }

    private fun Shapes.roundSign(face: Batch) {
        cylinder(Vec3(0f, 0f, SIGN_POLE_Z), SIGN_POLE_R, 2.3f, Part.POLE)
        disc(Vec3(0f, 2.3f, -.01f), ROUND_BACK_R, Batch.Of(Part.BACK))
        disc(Vec3(0f, 2.3f, 0f), ROUND_R, face)
    }

    /** [pos] is the bottom centre. v runs down from the roof edge: 0 at the top. */
    private fun Shapes.building(s: Vec3, wall: Part, tileH: Float) {
        val hx = s.x / 2; val hz = s.z / 2; val h = s.y; val b = Batch.Of(wall)
        fun side(tl: Vec3, tr: Vec3, len: Float) =
            face(tl, tr, Vec3(tr.x, 0f, tr.z), Vec3(tl.x, 0f, tl.z), b, floatArrayOf(0f, 0f, len / BUILDING_TILE_W, 0f, len / BUILDING_TILE_W, h / tileH, 0f, h / tileH))
        side(Vec3(-hx, h, hz), Vec3(hx, h, hz), s.x)
        side(Vec3(hx, h, hz), Vec3(hx, h, -hz), s.z)
        side(Vec3(hx, h, -hz), Vec3(-hx, h, -hz), s.x)
        side(Vec3(-hx, h, -hz), Vec3(-hx, h, hz), s.z)
        face(Vec3(-hx, h, -hz), Vec3(hx, h, -hz), Vec3(hx, h, hz), Vec3(-hx, h, hz), Batch.Of(Part.ROOF))
    }

    private fun Shapes.pillar(face: Batch, w: Float, h: Float) {
        box(Vec3(0f, h / 2, 0f), Vec3(w, h, w), Part.CONCRETE)
        box(Vec3(0f, .5f, 0f), Vec3(w + .02f, 1f, w + .02f), Part.HAZARD)
        val z = w / 2 + .005f; val y0 = 1.4f; val y1 = 1.9f; val k = .25f
        face(Vec3(-k, y1, z), Vec3(k, y1, z), Vec3(k, y0, z), Vec3(-k, y0, z), face)
        face(Vec3(k, y1, -z), Vec3(-k, y1, -z), Vec3(-k, y0, -z), Vec3(k, y0, -z), face)
    }

    /** A trapezoid cross-section (bottom +-z/2, top +-z/4) stretched along x; u is metres. */
    private fun Shapes.speedBump(w: Float, h: Float, d: Float) {
        val b = Batch.Of(Part.HAZARD); val hx = w / 2
        val uv = floatArrayOf(0f, 0f, w, 0f, w, 1f, 0f, 1f)
        quad(b, Vec3(-hx, h, -d / 4), Vec3(hx, h, -d / 4), Vec3(hx, h, d / 4), Vec3(-hx, h, d / 4), Vec3(0f, 1f, 0f), uv)
        for (sd in listOf(-1f, 1f)) {
            quad(b, Vec3(-hx, 0f, sd * d / 2), Vec3(hx, 0f, sd * d / 2), Vec3(hx, h, sd * d / 4), Vec3(-hx, h, sd * d / 4), Vec3(0f, d / 4, sd * h).unit(), uv)
            quad(b, Vec3(sd * hx, 0f, -d / 2), Vec3(sd * hx, 0f, d / 2), Vec3(sd * hx, h, d / 4), Vec3(sd * hx, h, -d / 4), Vec3(sd, 0f, 0f))
        }
    }

    private val UV_ZERO = FloatArray(6)

    /** Local-frame shape tools that write transformed geometry straight into the batches of [out]. */
    private class Shapes(private val at: Vec3, heading: Float, private val out: MutableMap<Batch, MeshData>) {
        private val c = cos(-heading); private val s = sin(-heading)
        private fun p(q: Vec3) = Vec3(q.x * c + q.z * s + at.x, q.y + at.y, -q.x * s + q.z * c + at.z)
        private fun n(q: Vec3) = Vec3(q.x * c + q.z * s, q.y, -q.x * s + q.z * c)
        private fun mesh(b: Batch) = out.getOrPut(b) { MeshData() }

        fun quad(b: Batch, a: Vec3, bb: Vec3, cc: Vec3, d: Vec3, nrm: Vec3, uv: FloatArray = MeshData.UNIT_UV) =
            mesh(b).quad(p(a), p(bb), p(cc), p(d), n(nrm), uv)

        fun tri(b: Batch, a: Vec3, bb: Vec3, cc: Vec3, nrm: Vec3, uv: FloatArray) {
            val m = mesh(b); val pa = p(a); val pb = p(bb); val pc = p(cc); val nn = n(nrm)
            val i0 = m.vertex(pa, nn, uv[0], uv[1]); val i1 = m.vertex(pb, nn, uv[2], uv[3]); val i2 = m.vertex(pc, nn, uv[4], uv[5])
            if (dot(cross(pb - pa, pc - pa), nn) >= 0f) m.triangle(i0, i1, i2) else m.triangle(i0, i2, i1)
        }

        /** A +Z-facing quad given its corners as seen from the front; uv (0,0) is the top left. */
        fun face(tl: Vec3, tr: Vec3, br: Vec3, bl: Vec3, b: Batch, uv: FloatArray = MeshData.UNIT_UV) =
            quad(b, tl, tr, br, bl, cross(bl - tl, tr - tl).unit(), uv)

        fun box(center: Vec3, size: Vec3, part: Part) {
            val b = Batch.Of(part)
            val x0 = center.x - size.x / 2; val x1 = center.x + size.x / 2
            val y0 = center.y - size.y / 2; val y1 = center.y + size.y / 2
            val z0 = center.z - size.z / 2; val z1 = center.z + size.z / 2
            quad(b, Vec3(x0, y1, z1), Vec3(x1, y1, z1), Vec3(x1, y0, z1), Vec3(x0, y0, z1), Vec3(0f, 0f, 1f))
            quad(b, Vec3(x1, y1, z0), Vec3(x0, y1, z0), Vec3(x0, y0, z0), Vec3(x1, y0, z0), Vec3(0f, 0f, -1f))
            quad(b, Vec3(x1, y1, z1), Vec3(x1, y1, z0), Vec3(x1, y0, z0), Vec3(x1, y0, z1), Vec3(1f, 0f, 0f))
            quad(b, Vec3(x0, y1, z0), Vec3(x0, y1, z1), Vec3(x0, y0, z1), Vec3(x0, y0, z0), Vec3(-1f, 0f, 0f))
            quad(b, Vec3(x0, y1, z0), Vec3(x1, y1, z0), Vec3(x1, y1, z1), Vec3(x0, y1, z1), Vec3(0f, 1f, 0f))
            quad(b, Vec3(x0, y0, z1), Vec3(x1, y0, z1), Vec3(x1, y0, z0), Vec3(x0, y0, z0), Vec3(0f, -1f, 0f))
        }

        /** Side walls and a top cap (no bottom). */
        fun cylinder(base: Vec3, r: Float, h: Float, part: Part, seg: Int = 8) {
            val b = Batch.Of(part); val top = Vec3(base.x, base.y + h, base.z)
            for (i in 0 until seg) {
                val a0 = 2 * PI.toFloat() * i / seg; val a1 = 2 * PI.toFloat() * (i + 1) / seg; val m = (a0 + a1) / 2
                val p0 = Vec3(base.x + r * cos(a0), base.y, base.z + r * sin(a0)); val p1 = Vec3(base.x + r * cos(a1), base.y, base.z + r * sin(a1))
                val u0 = i.toFloat() / seg; val u1 = (i + 1f) / seg
                quad(b, p0, p1, p1.copy(y = top.y), p0.copy(y = top.y), Vec3(cos(m), 0f, sin(m)), floatArrayOf(u0, 1f, u1, 1f, u1, 0f, u0, 0f))
                tri(b, top, p0.copy(y = top.y), p1.copy(y = top.y), Vec3(0f, 1f, 0f), floatArrayOf(.5f, .5f, .5f + .5f * cos(a0), .5f - .5f * sin(a0), .5f + .5f * cos(a1), .5f - .5f * sin(a1)))
            }
        }

        /** A +Z-facing fan; uv = (0.5 + 0.5cos, 0.5 - 0.5sin). */
        fun disc(center: Vec3, r: Float, b: Batch, seg: Int = 16) {
            for (i in 0 until seg) {
                val a0 = 2 * PI.toFloat() * i / seg; val a1 = 2 * PI.toFloat() * (i + 1) / seg
                tri(b, center, Vec3(center.x + r * cos(a0), center.y + r * sin(a0), center.z), Vec3(center.x + r * cos(a1), center.y + r * sin(a1), center.z), Vec3(0f, 0f, 1f),
                    floatArrayOf(.5f, .5f, .5f + .5f * cos(a0), .5f - .5f * sin(a0), .5f + .5f * cos(a1), .5f - .5f * sin(a1)))
            }
        }

        /** 8 x 6 flat-shaded facets; pole to pole, the top is exactly center.y + radii.y. */
        fun ellipsoid(center: Vec3, radii: Vec3, part: Part) {
            val b = Batch.Of(part); val cols = 8; val rows = 6
            fun pt(i: Int, j: Int): Vec3 {
                val phi = PI.toFloat() * i / rows; val th = 2 * PI.toFloat() * j / cols
                return Vec3(center.x + radii.x * sin(phi) * cos(th), center.y + radii.y * cos(phi), center.z + radii.z * sin(phi) * sin(th))
            }
            fun normal(vararg q: Vec3): Vec3 {
                val m = Vec3(q.sumOf { it.x.toDouble() }.toFloat() / q.size, q.sumOf { it.y.toDouble() }.toFloat() / q.size, q.sumOf { it.z.toDouble() }.toFloat() / q.size) - center
                return Vec3(m.x / (radii.x * radii.x), m.y / (radii.y * radii.y), m.z / (radii.z * radii.z)).unit()
            }
            for (i in 0 until rows) for (j in 0 until cols) {
                val u0 = j.toFloat() / cols; val u1 = (j + 1f) / cols; val v0 = i.toFloat() / rows; val v1 = (i + 1f) / rows
                val a = pt(i, j); val bb = pt(i, j + 1); val cc = pt(i + 1, j + 1); val d = pt(i + 1, j)
                when (i) {
                    0 -> tri(b, a, cc, d, normal(a, cc, d), floatArrayOf(u0, v0, u1, v1, u0, v1))
                    rows - 1 -> tri(b, a, bb, d, normal(a, bb, d), floatArrayOf(u0, v0, u1, v0, u0, v1))
                    else -> quad(b, a, bb, cc, d, normal(a, bb, cc, d), floatArrayOf(u0, v0, u1, v0, u1, v1, u0, v1))
                }
            }
        }
    }
}
