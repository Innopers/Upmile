package app.upmile.core.preview3d

import app.upmile.core.model.HardSegment
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random

/**
 * 카페 길 회전교차로: single-lane Korean roundabout. We come from the south, yield, circulate counter-clockwise and leave north.
 * Local frame: centre C = (0, -80); arms are addressed as (d metres out from C, l metres to the right of an incoming driver).
 * shortcut: only the straight exit (north) is built; `exitArm` is not read.
 */
object RoundaboutScene {
    private const val W = Kr.LANE_CITY
    private const val CZ = -80f
    private const val NOSE = 2.3f
    private val TAU = (2 * PI).toFloat()

    /** Outward unit vector (x, z) and the far end distance of each arm: south, east, north, west. */
    private data class Arm(val ox: Float, val oz: Float, val end: Float) {
        /** Right of a driver coming in: (oz, -ox). */
        fun at(d: Float, l: Float) = v(ox * d + oz * l, CZ + oz * d - ox * l)
    }
    private val ARMS = listOf(Arm(0f, 1f, 140f), Arm(1f, 0f, 300f), Arm(0f, -1f, 240f), Arm(-1f, 0f, 300f))

    fun build(segment: HardSegment): SceneSpec {
        val r = (segment.params["radiusM"] ?: 14.0).toFloat().coerceIn(12f, 20f)  // shortcut: clamped; the fixed 5.5 m ring and 3.25 m lanes need this range
        val rc = r - 2.75f
        val rnd = Random(segment.id.hashCode())
        val c = v(0f, CZ)
        fun ring(rad: Float, last: Int = 72) = (0..last).map { k -> val a = TAU * k / 72; v(rad * cos(a), CZ - rad * sin(a)) }

        // one step past the full turn: a ribbon's end sections are square to its end chords, which left a wedge open at a = 0
        val surfaces = mutableListOf(Ribbon(ring(rc, 73), 5.5f, Mat.ASPHALT))
        val patches = mutableListOf<Patch>(bigGround(100f, -420f))
        val props = mutableListOf<Prop>()
        fun paint(path: List<Vec3>, mat: Mat = Mat.WHITE_PAINT, w: Float = Kr.LINE) { surfaces += Ribbon(path, w, mat, .01f) }
        fun tri(vararg p: Vec3) { patches += Patch(p.toList(), Mat.WHITE_PAINT, .01f) }

        for (arm in ARMS) {
            surfaces += Ribbon(listOf(arm.at(r - 1f, 0f), arm.at(arm.end, 0f)), 2 * W, Mat.ASPHALT)
            for (l in listOf(-.15f, .15f)) paint(listOf(arm.at(r + 12f, l), arm.at(arm.end, l)), Mat.YELLOW_PAINT)
            patches += Patch(listOf(arm.at(r + .5f, -.6f), arm.at(r + .5f, .6f), arm.at(r + 12f, 0f)), Mat.CONCRETE, .12f)
            dashes(listOf(arm.at(r + .3f, .6f), arm.at(r + .3f, W)), .5f, .5f).forEach { paint(it, w = .3f) }
            tri(arm.at(r + 5f, W / 2 - .9f), arm.at(r + 5f, W / 2 + .9f), arm.at(r + 8.5f, W / 2))
            for (side in listOf(-1f, 1f)) {
                surfaces += Ribbon(listOf(arm.at(r + 2f, side * (W + 3f)), arm.at(arm.end, side * (W + 3f))), 3f, Mat.PAVING, .15f)
                // shops face the road, two per side
                for (d in listOf(40f, 75f)) {
                    val size = Vec3(16f + rnd.nextFloat() * 6f, 8f + rnd.nextFloat() * 6f, 12f + rnd.nextFloat() * 4f)
                    val towardRoad = arm.at(0f, -side) - c
                    props += Prop(PropKind.BUILDING_SHOP, arm.at(d, side * 18f), heading(Vec3(-towardRoad.x, 0f, -towardRoad.z)), size)
                }
                var d = r + 4f
                while (d < arm.end) { props += Prop(PropKind.TREE, arm.at(d, side * 5.5f), size = Vec3(1.8f + rnd.nextFloat(), 6f + 3f * rnd.nextFloat(), 0f)); d += 12f }
            }
        }
        // lamps on the south and north arms only
        var z = 40f
        while (z > CZ + r + 6f) { props += Prop(PropKind.STREET_LAMP, v(7f, z), 0f); z -= 30f }
        z = CZ - r - 30f
        while (z > -310f) { props += Prop(PropKind.STREET_LAMP, v(-7f, z), PI.toFloat()); z -= 30f }
        props += Prop(PropKind.ROUNDABOUT_SIGN, v(W + 1.8f, -50f), 0f)
        props += Prop(PropKind.YIELD_SIGN, v(W + 1.8f, CZ + r + 1.5f), 0f)

        props.dropTreesNearPosts()   // street trees skip the lamps and signs
        // island: grass inside a kerb; the ring's outer edge is a dashed white circle
        patches += Patch(ring(r - 5.5f - .1f).dropLast(1).filterIndexed { i, _ -> i % 3 == 0 }, Mat.GRASS, .15f)
        val solids = listOf(Extrusion(ring(r - 5.5f), KERB, Mat.KERB))
        for (k in 0 until 3) { val a = TAU * k / 3 + .5f; props += Prop(PropKind.TREE, v(4f * cos(a), CZ - 4f * sin(a)), size = Vec3(1.2f, 4.5f, 0f)) }
        dashes(ring(r - .2f), Kr.CITY_DASH, Kr.CITY_GAP).forEach { paint(it) }

        // camera: south entry lane, stop at the yield line, round the ring a = -60 .. +60 degrees, out north
        val stopPt = v(W / 2, CZ + r + .3f + NOSE)
        val a0 = -deg(60f); val a1 = deg(60f)
        val ringPts = arc(c, rc, a0, a1)
        val route = smooth(listOf(stopPt, v(2.6f, CZ + r - 1.5f)) + ringPts + listOf(v(2.6f, CZ - r + 1.5f), v(W / 2, CZ - r - 8f), v(W / 2, -200f)))
        val camPath = (listOf(v(W / 2, 40f)) + route).map { it.copy(y = EYE) }
        val approach = 40f - stopPt.z
        val sStop = approach
        val sExit = nearestS(camPath, ringPts.last().copy(y = EYE))
        val pace = Pace(11f).go(approach - 25f, 11f)
        val slow0 = pace.sec
        pace.go(25f, 0f)
        val stop0 = pace.sec
        pace.stop(1.8f)
        val stop1 = pace.sec
        pace.go(sExit - sStop, 6f).go(arcLengths(camPath).last() - sExit, 12f)
        val total = pace.sec

        // car B joins from the west arm, circulates counter-clockwise and leaves east; passes the south point .8 s after we stop
        val z0 = CZ + W / 2
        val aIn = PI.toFloat() + asin((W / 2) / rc)
        val aOut = TAU - asin((W / 2) / rc)
        val ringB = arc(c, rc, aIn + deg(12f), aOut - deg(12f))
        val bPath = smooth(listOf(v(-300f, z0), v(-60f, z0), v(-rc - 14f, z0), v(-rc - 6f, z0)) + ringB + listOf(v(rc + 6f, z0), v(rc + 14f, z0), v(60f, z0), v(300f, z0)))
        val bMps = 7f
        val bSouth = nearestS(bPath, v(0f, CZ + rc))
        val bS0 = bSouth - bMps * (stop0 + .8f)
        val cars = mutableListOf(Car(rnd.pickModel(), rnd.pickPaint(), bPath, listOf(Key(0f, bS0), Key(1f, bS0 + bMps * total))))
        for (arm in listOf(ARMS[2], ARMS[1])) {                          // waiting at the yield lines of the north and east arms
            val lane = listOf(arm.at(arm.end, W / 2), arm.at(r, W / 2))
            cars += rnd.parked(lane, arm.end - (r + .3f + NOSE))
        }

        fun tAt(m: Float) = pace.secAt(m) / total
        return SceneSpec(
            surfaces = surfaces, patches = patches, solids = solids, props = props, cars = cars,
            camera = Timeline(camPath, pace.keys()),
            cues = listOf(
                slow0 / total to "진입 전 감속",
                stop0 / total to "왼쪽 회전 차량 먼저",
                (stop1 + .2f) / total to "반시계 방향으로 회전",
                tAt(nearestS(camPath, v(rc * cos(deg(30f)), CZ - rc * sin(deg(30f)), EYE))) to "오른쪽 깜빡이 켜고 진출",
                tAt(pace.metres - 10f) to "완료",
            ),
            durationMs = (total * 1000).roundToLong(),
        )
    }
}
