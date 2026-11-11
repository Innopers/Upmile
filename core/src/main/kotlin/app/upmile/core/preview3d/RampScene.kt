package app.upmile.core.preview3d

import app.upmile.core.model.HardSegment
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * 회사 지하주차장 경사로: concrete apron, tunnel under an office building, 12% ramp, a narrow 90 degree turn, the B1 hall.
 * Built for a right turn; a left turn (turnDeg < 0) mirrors every x. shortcut: only the sign of turnDeg is read, the angle is always 90.
 */
object RampScene {
    private const val W = 6.5f                // ramp width, two 3.25 m lanes
    private const val LANE = W / 4            // centre of the lane to the right of the centreline
    private const val APRON = 25f
    private const val FIRST = 28f
    private const val R = 6.5f
    private const val SECOND = 10f
    private const val HALL_EDGE_X = 21.5f
    private const val FLAT_END_X = 34.5f
    private const val CEIL = 3f
    private const val UP_MPS = 4f

    fun build(segment: HardSegment): SceneSpec {
        val slope = (segment.params["slope"] ?: .12).toFloat().coerceIn(.06f, .13f) // curve slope is 1.07x this on the inner lane; keep it under Kr.RAMP_MAX_CURVE
        val sgn = if ((segment.params["turnDeg"] ?: 90.0) < 0.0) -1f else 1f
        val rnd = Random(segment.id.hashCode())
        fun mx(x: Float, z: Float, y: Float = 0f) = v(x * sgn, z, y)

        // centreline (y = floor), built as a right turn then mirrored
        val y1 = -slope * FIRST
        val bend = arc(v(R, -FIRST), R, PI.toFloat(), PI.toFloat() / 2).drop(1)
        var yy = y1
        val curve = bend.mapIndexed { i, p -> val prev = if (i == 0) v(0f, -FIRST) else bend[i - 1]; yy -= slope * .8f * hypot(p.x - prev.x, p.z - prev.z); p.copy(y = yy) }
        val y2 = yy - slope * SECOND
        val raw = listOf(v(0f, APRON), v(0f, 0f), v(0f, -FIRST, y1)) + curve +
            listOf(v(R + SECOND, -FIRST - R, y2), v(HALL_EDGE_X, -FIRST - R, y2), v(FLAT_END_X, -FIRST - R, y2))
        val center = raw.map { it.copy(x = it.x * sgn) }
        val iArc0 = 2; val iArc1 = 2 + curve.size; val iSecond = iArc1 + 1; val iHall = iArc1 + 2
        val sC = arcLengths(center)
        val hallY = y2

        // camera on the right lane
        val cam = offset(center, LANE).map { it.copy(y = it.y + EYE) }
        val sK = arcLengths(cam)
        val pace = Pace(5f).go(sK[1], 5f).go(sK[iArc0] - sK[1], 6f).go(sK[iArc1] - sK[iArc0], 4f).go(sK.last() - sK[iArc1], 5f)
        val total = pace.sec
        fun tAt(m: Float) = pace.secAt(m) / total

        // horizontal point [lat] metres to the right of the centreline at centre-metre [s]
        fun side(s: Float, lat: Float): Vec3 {
            val p = pointAt(center, s); val n = hypot(p.dir.x, p.dir.z)
            return Vec3(p.at.x - p.dir.z / n * lat, p.at.y, p.at.z + p.dir.x / n * lat)
        }

        val surfaces = mutableListOf(
            Ribbon(center.subList(0, 2), W, Mat.CONCRETE),
            Ribbon(center.subList(1, center.size), W, Mat.EPOXY),
        )
        dashes(slice(center, sC[1], sC.last()), Kr.CITY_DASH, Kr.CITY_GAP).forEach { surfaces += Ribbon(it, Kr.LINE, Mat.YELLOW_PAINT, .01f) }

        val patches = mutableListOf<Patch>()
        fun rect(x0: Float, x1: Float, z0: Float, z1: Float, mat: Mat, y: Float = 0f, lift: Float = 0f, faceDown: Boolean = false) =
            patches.add(Patch(listOf(mx(x0, z0, y), mx(x1, z0, y), mx(x1, z1, y), mx(x0, z1, y)), mat, lift, faceDown))
        for (tail in listOf(APRON + 8f, APRON + 20f)) {   // straight arrows in the right lane, tail at [tail], 5 m long
            fun pt(along: Float, lat: Float) = side(tail + along, LANE + lat)
            patches += Patch(listOf(pt(0f, -.15f), pt(0f, .15f), pt(3.4f, .15f), pt(3.4f, -.15f)), Mat.WHITE_PAINT, .01f)
            patches += Patch(listOf(pt(3.4f, -.5f), pt(3.4f, .5f), pt(5f, 0f)), Mat.WHITE_PAINT, .01f)
        }
        rect(HALL_EDGE_X, 70f, -22f, -48f, Mat.EPOXY, hallY)
        rect(HALL_EDGE_X, 70f, -22f, -48f, Mat.CEILING, hallY + CEIL, faceDown = true)
        // grass keeps off the building footprint x in [-40, 40], z in [-50, 0]; below the paving and apron so nothing is coplanar
        for ((x0, x1, z0, z1) in listOf(listOf(-400f, 400f, 0f, 300f), listOf(-400f, -40f, -300f, 0f), listOf(40f, 400f, -300f, 0f)))
            patches += Patch(listOf(v(x0, z0), v(x1, z0), v(x1, z1), v(x0, z1)), Mat.GRASS, lift = -.03f)
        for ((x0, x1) in listOf(-40f to -3.25f, 3.25f to 40f)) patches += Patch(listOf(v(x0, 0f), v(x1, 0f), v(x1, 8f), v(x0, 8f)), Mat.PAVING)

        // hall walls: 3 m high, 0.3 m thick, outside the hall rectangle, open where the passage (z -37.75..-31.25) enters
        val e = .15f
        val hallWall = listOf(mx(HALL_EDGE_X - e, -31.25f, hallY), mx(HALL_EDGE_X - e, -22f + e, hallY), mx(70f + e, -22f + e, hallY),
            mx(70f + e, -48f - e, hallY), mx(HALL_EDGE_X - e, -48f - e, hallY), mx(HALL_EDGE_X - e, -37.75f, hallY))

        // walls and ceiling from the entrance to where the centreline reaches the hall
        val tunnel = center.subList(1, iHall + 1)
        val entranceS = sC[1]
        val hazard = slice(tunnel, sC[iArc0] - 3f - entranceS, sC[iArc1] + 3f - entranceS)
        val solids = listOf(
            Extrusion(tunnel, listOf(-3.55f to 0f, -3.25f to 0f, -3.25f to 3f, -3.55f to 3f), Mat.WALL),
            Extrusion(tunnel, listOf(3.25f to 0f, 3.55f to 0f, 3.55f to 3f, 3.25f to 3f), Mat.WALL),
            Extrusion(tunnel, listOf(-3.55f to 3f, 3.55f to 3f, 3.55f to 3.3f, -3.55f to 3.3f), Mat.CEILING),
            Extrusion(hazard, listOf(-3.25f to 0f, -3.22f to 0f, -3.22f to 1f, -3.25f to 1f), Mat.HAZARD),
            Extrusion(hazard, listOf(3.22f to 0f, 3.25f to 0f, 3.25f to 1f, 3.22f to 1f), Mat.HAZARD),
        )

        val solidsAll = solids + Extrusion(hallWall, listOf(-e to 0f, e to 0f, e to CEIL, -e to CEIL), Mat.WALL)
        val props = mutableListOf<Prop>()
        props += Prop(PropKind.BUILDING_GLASS, mx(-21.8f, -25f), size = Vec3(36.4f, 42f, 50f))
        props += Prop(PropKind.BUILDING_GLASS, mx(21.8f, -25f), size = Vec3(36.4f, 38f, 50f))
        props += Prop(PropKind.BUILDING_GLASS, v(0f, -25f, 3.4f), size = Vec3(7.2f, 38f, 50f))
        props += Prop(PropKind.SIGN_BLUE, v(5.25f, 4f), size = Vec3(3.2f, 1.0f, 2.2f), text = listOf("P  지하주차장", "입구"))
        props += Prop(PropKind.HEIGHT_BAR, v(0f, 1.5f), size = Vec3(7.2f, 2.3f, 0f), text = listOf("높이 2.3m"))
        for (z in listOf(-39.5f, -29.5f)) for (x in listOf(26f, 34f, 42f)) props += Prop(PropKind.PILLAR, mx(x, z, hallY), size = Vec3(.7f, 3f, .7f), text = listOf("B1"))
        generateSequence(sC[1] + 2f) { it + 5f }.takeWhile { it <= sC.last() }.forEach { s ->
            val p = pointAt(center, s); props += Prop(PropKind.CEILING_LIGHT, p.at.copy(y = p.at.y + 2.95f), heading(p.dir), Vec3(.25f, .06f, 1.2f))
        }
        pointAt(center, sC[iArc0] + 3f).let { props += Prop(PropKind.CONVEX_MIRROR, side(sC[iArc0] + 3f, -3f * sgn), heading(it.dir) + PI.toFloat(), Vec3(.8f, 2.2f, 0f)) }
        pointAt(center, sC[1] + 16.5f).let { props += Prop(PropKind.ROAD_TEXT, side(sC[1] + 16.5f, LANE), heading(it.dir), Vec3(2.4f, 0f, 4f), listOf("천천히")) }
        pointAt(center, sC[iSecond]).let { props += Prop(PropKind.SPEED_BUMP, it.at, heading(it.dir), Vec3(W, .08f, .4f)) }

        // the oncoming car climbs the left lane at 4 m/s and passes the camera 3 m after its turn ends; it starts in the hall, behind the path's first point
        val carPath = offset(center, -LANE).reversed()
        val passM = sK[iArc1] + 3f
        val s0Car = nearestS(carPath, pointAt(cam, passM).at) - UP_MPS * pace.secAt(passM)
        val car = rnd.cruise(carPath, s0Car, UP_MPS, total)

        return SceneSpec(
            surfaces = surfaces, patches = patches, solids = solidsAll, props = props, cars = listOf(car),
            camera = Timeline(cam, pace.keys()),
            cues = listOf(
                tAt(2f) to "입구 · 전조등",
                tAt(sK[1] + 1f) to "급경사 내리막",
                tAt(sK[iArc0] - 3f) to "좁은 우회전",
                tAt(sK[iArc1] + 2f) to "차로 가운데",
                tAt(sK.last() - 4f) to "도착",
            ),
            durationMs = (total * 1000).roundToLong(),
            indoorFromM = sK[1],
        )
    }
}
