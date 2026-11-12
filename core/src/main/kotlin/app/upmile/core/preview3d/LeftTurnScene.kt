package app.upmile.core.preview3d

import app.upmile.core.model.HardSegment
import kotlin.math.PI
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * 판교역 사거리: wait at the stop line on red, take the left-turn arrow, follow the guide dashes into a lane of the cross road.
 * Local frame: we drive toward -Z; the junction box spans z in [-106, -106-2H]; the cross road runs along X (west = -X).
 */
object LeftTurnScene {
    private const val W = Kr.LANE_CITY
    private const val OPP = 4                 // lanes of the opposite carriageway
    private const val START_Z = 40f           // camera and the turners' paths begin here
    private const val NEAR_Z = 60f
    private const val FAR_Z = -400f
    private const val JZ = -106f              // junction's near edge (south side of the cross road)
    private const val STOP_Z = -100f
    private const val CROSS_MPS = 13f
    private const val HALF_X = 400f
    private const val NOSE = 2.3f             // driver's seat to the front bumper
    private const val BEHIND = 6.2f           // camera sits this far behind the waiting car ahead (4.6 m car + 1.6 m gap)
    private const val SIDEWALK = 120f
    /** Example place names, not read from a real sign; shortcut: replace with the destination tags of the junction's signs. */
    private val SIGN_TEXT = listOf("← 분당 · 서현", "↑ 판교IC", "→ 백현동")

    fun build(segment: HardSegment): SceneSpec {
        fun param(k: String, d: Int, r: IntRange) = (segment.params[k] ?: d.toDouble()).toInt().coerceIn(r)
        val lanes = param("approachLanes", 5, 4..6)
        val turn = param("turnLanes", 2, 1..2)
        val cross = param("crossLanes", 3, 3..4)
        val from = param("fromLane", 2, 1..turn)
        val rnd = Random(segment.id.hashCode())
        val xR = .3f + lanes * W
        val xL = -.3f - OPP * W
        val mid = (xR + xL) / 2
        val h = .3f + cross * W
        val zc = JZ - h
        val zN = JZ - 2 * h
        val farStop = zN - 5.5f
        fun laneX(k: Int) = .3f + (k - .5f) * W
        fun zW(j: Int) = zc - .3f - (j - .5f) * W
        fun zE(j: Int) = zc + .3f + (j - .5f) * W
        fun oppX(k: Int) = -.3f - (k - .5f) * W
        fun curve(k: Int) = arc(v(-h, JZ), laneX(k) + h, 0f, deg(90f))      // lane k's left turn, 0 deg = on the approach lane
        fun turnPath(k: Int, endX: Float) = listOf(v(laneX(k), START_Z)) + curve(k) + v(endX, JZ - laneX(k) - h) // straight, arc, then west in cross lane k
        fun cam(path: List<Vec3>) = path.map { it.copy(y = EYE) }

        // camera: straight to the junction, the arc, then west along cross lane `from`
        val camPath = cam(turnPath(from, -75f))
        val sTurn0 = START_Z - JZ
        val arcM = arcLengths(curve(from)).last()
        val sStop = START_Z - (STOP_Z + NOSE + BEHIND)
        val pace = Pace(12f).go(sStop - 31.5f, 12f).go(31.5f, 0f)
        val stop0 = pace.sec
        pace.stop(3f)
        val stop1 = pace.sec
        pace.go(sTurn0 - sStop, 6f).go(arcM, 8f).go(arcLengths(camPath).last() - sTurn0 - arcM, 11f)
        val total = pace.sec
        val arrowSec = stop0 + 1.5f

        // paint
        val surfaces = mutableListOf(
            Ribbon(listOf(v(mid, NEAR_Z), v(mid, JZ)), xR - xL, Mat.ASPHALT),
            Ribbon(listOf(v(-HALF_X, zc), v(HALF_X, zc)), 2 * h, Mat.ASPHALT),
            Ribbon(listOf(v(mid, zN), v(mid, FAR_Z)), xR - xL, Mat.ASPHALT),
        )
        fun paint(path: List<Vec3>, mat: Mat = Mat.WHITE_PAINT, w: Float = Kr.LINE) { surfaces += Ribbon(path, w, mat, .01f) }
        fun dashed(path: List<Vec3>) = dashes(path, Kr.CITY_DASH, Kr.CITY_GAP).forEach { paint(it) }
        fun alongZ(x: Float, z0: Float, z1: Float) = listOf(v(x, z0), v(x, z1))
        fun alongX(z: Float, x0: Float, x1: Float) = listOf(v(x0, z), v(x1, z))
        // solid only on an approach, for the 30 m before its stop line; exits and the other side's departure stay dashed
        for (x in (1 until lanes).map { .3f + it * W }) {
            dashed(alongZ(x, NEAR_Z, -70f)); paint(alongZ(x, -70f, STOP_Z)); dashed(alongZ(x, farStop, FAR_Z))
        }
        for (x in (1 until OPP).map { -.3f - it * W }) {
            dashed(alongZ(x, NEAR_Z, STOP_Z)); paint(alongZ(x, farStop, farStop - 30f)); dashed(alongZ(x, farStop - 30f, FAR_Z))
        }
        for (x in listOf(-.15f, .15f)) { paint(alongZ(x, NEAR_Z, STOP_Z), Mat.YELLOW_PAINT); paint(alongZ(x, farStop, FAR_Z), Mat.YELLOW_PAINT) }
        paint(alongX(STOP_Z, .3f, xR), w = Kr.STOP_LINE); paint(alongX(farStop, xL, -.3f), w = Kr.STOP_LINE)
        fun zebraZ(x0: Float, x1: Float, za: Float, zb: Float) { var x = x0 + Kr.CROSSWALK_BAR / 2; while (x <= x1) { paint(alongZ(x, za, zb), w = Kr.CROSSWALK_BAR); x += 2 * Kr.CROSSWALK_BAR } }
        fun zebraX(xa: Float, xb: Float, z0: Float, z1: Float) { var z = z0 + Kr.CROSSWALK_BAR / 2; while (z <= z1) { paint(alongX(z, xa, xb), w = Kr.CROSSWALK_BAR); z += 2 * Kr.CROSSWALK_BAR } }
        zebraZ(xL, xR, -101f, -105f); zebraZ(xL, xR, zN - 1f, zN - 5f)
        zebraX(xL - 5f, xL - 1f, zN, JZ); zebraX(xR + 1f, xR + 5f, zN, JZ)
        for ((xa, xb) in listOf(-HALF_X to xL - 5.5f, xR + 5.5f to HALF_X)) {      // cross-road markings stop short of the junction and its crosswalks
            for (d in listOf(-.15f, .15f)) paint(alongX(zc + d, xa, xb), Mat.YELLOW_PAINT)
            for (j in 1 until cross) for (sgn in listOf(-1f, 1f)) dashed(alongX(zc + sgn * (.3f + j * W), xa, xb))
        }
        // guide dashes from the inner edge of lane `from` along its turn (centre (-H, JZ) for every lane)
        val gx = .3f + (from - 1) * W
        dashes(arc(v(-h, JZ), gx + h, 0f, deg(90f)), Kr.GUIDE_DASH, Kr.GUIDE_DASH).forEach { paint(it) }

        // arrows, 5 m long, tail at zb pointing -Z
        val patches = mutableListOf(bigGround(NEAR_Z + 60f, FAR_Z - 100f))
        fun poly(vararg p: Pair<Float, Float>) = patches.add(Patch(p.map { v(it.first, it.second) }, Mat.WHITE_PAINT, .01f))
        fun straightArrow(x: Float, zb: Float) {
            poly(x - .15f to zb, x + .15f to zb, x + .15f to zb - 3.4f, x - .15f to zb - 3.4f)
            poly(x - .5f to zb - 3.4f, x + .5f to zb - 3.4f, x to zb - 5f)
        }
        fun leftArrow(x: Float, zb: Float) {
            poly(x - .15f to zb, x + .15f to zb, x + .15f to zb - 3.5f, x - .15f to zb - 3.5f)          // body
            poly(x - .6f to zb - 3.2f, x + .15f to zb - 3.2f, x + .15f to zb - 3.5f, x - .6f to zb - 3.5f) // short bar bending left
            poly(x - .6f to zb - 2.85f, x - .6f to zb - 3.85f, x - 1.4f to zb - 3.35f)                   // head, left-pointing
        }
        for (zb in listOf(-60f, -85f)) for (k in 1..lanes) if (k <= turn) leftArrow(laneX(k), zb) else straightArrow(laneX(k), zb)
        val paving = { x0: Float, x1: Float, z0: Float, z1: Float -> Patch(listOf(v(x0, z0), v(x1, z0), v(x1, z1), v(x0, z1)), Mat.PAVING, .15f) }
        patches += listOf(paving(xR + .1f, SIDEWALK, JZ + .1f, NEAR_Z), paving(-SIDEWALK, xL - .1f, JZ + .1f, NEAR_Z), paving(xR + .1f, SIDEWALK, FAR_Z, zN - .1f), paving(-SIDEWALK, xL - .1f, FAR_Z, zN - .1f)) // start past the kerb's outer edge
        val solids = listOf(
            Extrusion(listOf(v(xR, NEAR_Z), v(xR, JZ), v(SIDEWALK, JZ)), KERB, Mat.KERB),
            Extrusion(listOf(v(xL, NEAR_Z), v(xL, JZ), v(-SIDEWALK, JZ)), KERB, Mat.KERB),
            Extrusion(listOf(v(xR, FAR_Z), v(xR, zN), v(SIDEWALK, zN)), KERB, Mat.KERB),
            Extrusion(listOf(v(xL, FAR_Z), v(xL, zN), v(-SIDEWALK, zN)), KERB, Mat.KERB),
        )

        // props
        val props = mutableListOf<Prop>()
        fun tree(x: Float, z: Float) = Prop(PropKind.TREE, v(x, z), size = Vec3(1.8f + rnd.nextFloat(), 6f + 3f * rnd.nextFloat(), 0f))
        fun column(x: Float, z0: Float, z1: Float) = generateSequence(z0) { it - 9f }.takeWhile { it > z1 }.map { tree(x, it) }
        fun row(z: Float, x0: Float, x1: Float) = generateSequence(x0) { it + 9f }.takeWhile { it <= x1 }.map { tree(it, z) }
        for (x in listOf(xR + 2.75f, xL - 2.7f)) { props += column(x, NEAR_Z - 3f, STOP_Z + 5f); props += column(x, zN - 12f, FAR_Z + 10f) }   // 6 m clear of the crosswalks
        for (z in listOf(JZ + 2.5f, zN - 2.5f)) { props += row(z, xR + 12f, SIDEWALK - 2f); props += row(z, -SIDEWALK + 2f, xL - 11f) }
        for (z in generateSequence(45f) { it - 30f }.takeWhile { it > STOP_Z - 5f }) { props += Prop(PropKind.STREET_LAMP, v(xR + 1.1f, z), 0f); props += Prop(PropKind.STREET_LAMP, v(xL - .9f, z - 15f), PI.toFloat()) }
        for (z in generateSequence(zN - 24f) { it - 30f }.takeWhile { it > FAR_Z + 10f }) { props += Prop(PropKind.STREET_LAMP, v(xR + 1.1f, z), 0f); props += Prop(PropKind.STREET_LAMP, v(xL - .9f, z - 15f), PI.toFloat()) }
        props += Prop(PropKind.SIGNAL_MAST, v(xR + 1.05f, zN - 1.5f), 0f, Vec3(12f, 6f, 0f))
        props += Prop(PropKind.SIGN_BLUE, v(xR + 1.65f, -55f), 0f, Vec3(4.2f, 2.4f, 2.5f), SIGN_TEXT)
        for ((x, z, size) in listOf(
            Triple(48f, -20f, Vec3(30f, 55f, 26f)), Triple(-46f, -30f, Vec3(26f, 42f, 24f)), Triple(48f, -175f, Vec3(32f, 70f, 26f)),
            Triple(-50f, -180f, Vec3(28f, 62f, 30f)), Triple(90f, -60f, Vec3(30f, 40f, 30f)), Triple(-90f, -240f, Vec3(30f, 50f, 30f)),
        )) props += Prop(PropKind.BUILDING_GLASS, v(x, z), size = size)

        props.dropTreesNearPosts()

        // traffic. Paths are long enough that no car runs off its road before the scene ends.
        val cars = mutableListOf<Car>()
        val sLine = START_Z - (STOP_Z + NOSE)                         // a waiting car's s on a turn path
        fun lanePath(k: Int) = alongZ(laneX(k), NEAR_Z, JZ)
        fun leaver(k: Int, wait: Float): Car {
            val p = Pace(0f).stop(wait).go(sTurn0 - sLine, 7f).go(arcLengths(curve(k)).last(), 8f).go(80f, 11f)
            val keys = p.keys(total).map { Key(it.t, it.s + sLine) } + Key(1f, sLine + p.metres + 11f * (total - p.sec))
            return Car(rnd.pickModel(), rnd.pickPaint(), turnPath(k, -300f), keys)
        }
        cars += leaver(from, arrowSec + .3f)
        if (from > 1) cars += leaver(from - 1, arrowSec + .7f)
        val lineS = NEAR_Z - (STOP_Z + NOSE)
        cars += rnd.parked(lanePath(turn + 1), lineS); cars += rnd.parked(lanePath(turn + 1), lineS - 7f)
        cars += rnd.parked(lanePath(turn + 2), lineS)
        val clearBy = arrowSec - 1f                                    // cross traffic has left the junction box by now
        fun crossing(z: Float, east: Boolean, lead: Float): Car {
            val x0 = if (east) xR + .45f + lead - CROSS_MPS * clearBy else xL - .7f - lead + CROSS_MPS * clearBy
            return rnd.cruise(if (east) alongX(z, -HALF_X, HALF_X) else alongX(z, HALF_X, -HALF_X), if (east) x0 + HALF_X else HALF_X - x0, CROSS_MPS, total)
        }
        cars += crossing(zE(2), true, 10f); cars += crossing(zE(2), true, 60f); cars += crossing(zW(3), false, 10f)
        for (k in 1..2) cars += rnd.parked(listOf(v(oppX(k), FAR_Z), v(oppX(k), zN)), farStop - NOSE - FAR_Z)

        return SceneSpec(
            surfaces = surfaces, patches = patches, solids = solids, props = props, cars = cars,
            camera = Timeline(camPath, pace.keys()),
            cues = listOf(
                pace.secAt(2f) / total to "좌회전 차로 ${turn}개",
                stop0 / total to "정지선 신호 대기",
                (stop1 + .2f) / total to "유도선 따라 회전",
                pace.secAt(sTurn0 + arcM - 4f) / total to "${from}차로로 진입",
                pace.secAt(pace.metres - 6f) / total to "완료",
            ),
            durationMs = (total * 1000).roundToLong(),
            signal = listOf(SignalPhase(0f, setOf(Lamp.RED)), SignalPhase(arrowSec / total, setOf(Lamp.RED, Lamp.ARROW))),
        )
    }
}
