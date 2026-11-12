package app.upmile.core.preview3d

import app.upmile.core.model.HardSegment
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random

/**
 * 처가 길 급커브: two-lane rural road, a sharp curve with chevrons, guardrail and warning signs, an oncoming car.
 * Built as a left turn and mirrored for a right turn. shortcut: radius is clamped to 30..60 m and the angle to 60..120 degrees.
 * The road runs 70 m before the camera's start and 130 m after the curve so the oncoming car stays on asphalt for the whole scene.
 */
object CurveScene {
    private const val W = Kr.LANE_CITY
    private const val ROAD_W = 7.5f
    private const val BEFORE = 70f   // road laid before the camera's start (0, 40)
    private const val AFTER = 130f   // straight after the curve
    private const val ONCOMING_MPS = 11f

    fun build(segment: HardSegment): SceneSpec {
        val radius = (segment.params["radiusM"] ?: 45.0).toFloat().coerceIn(30f, 60f)
        val turn = (segment.params["turnDeg"] ?: -90.0)
        val left = turn <= 0.0
        val theta = deg(abs(turn).toFloat().coerceIn(60f, 120f))
        val sgn = if (left) 1f else -1f
        val out = sgn                                      // side of the outside of the curve, as an offset (+ = right)
        val rnd = Random(segment.id.hashCode())
        fun mx(p: Vec3) = p.copy(x = p.x * sgn)

        // centreline, built as a left turn then mirrored; s = 0 is BEFORE metres ahead of the camera's start
        val c = v(-radius, -60f)
        val arcPts = arc(c, radius, 0f, theta)
        val endDir = Vec3(-sin(theta), 0f, -cos(theta))
        val road = (listOf(v(0f, 40f + BEFORE)) + arcPts + (arcPts.last() + endDir * AFTER)).map(::mx)
        val sArc0 = 100f + BEFORE
        val arcLen = arcLengths(road)[arcPts.size] - sArc0

        val surfaces = mutableListOf(Ribbon(road, ROAD_W, Mat.ASPHALT))
        for (d in listOf(-.15f, .15f)) surfaces += Ribbon(offset(road, d), Kr.LINE, Mat.YELLOW_PAINT, .01f)
        for (d in listOf(-3.2f, 3.2f)) surfaces += Ribbon(offset(road, d), Kr.LINE, Mat.WHITE_PAINT, .01f)

        // guardrail on the outside, 15 m beyond the curve both ways, with posts
        val rail = offset(slice(road, sArc0 - 15f, sArc0 + arcLen + 15f), 3.9f * out)
        val solids = listOf(Extrusion(rail, GUARDRAIL, Mat.STEEL))
        val props = mutableListOf<Prop>()
        props += posts(rail)
        var s = 0f
        while (s <= arcLen + .01f) {
            val p = pointAt(road, sArc0 + s)
            props += Prop((if (left) PropKind.CHEVRON_LEFT else PropKind.CHEVRON_RIGHT), offset(listOf(p.at, p.at + p.dir), 4.6f * out)[0], heading(p.dir), Vec3(.45f, .6f, 1.0f))
            s += 10f
        }
        for ((kind, at) in listOf((if (left) PropKind.WARNING_CURVE_LEFT else PropKind.WARNING_CURVE_RIGHT) to 50f, PropKind.SPEED_LIMIT to 58f)) {
            val p = pointAt(road, BEFORE + at)
            props += Prop(kind, offset(listOf(p.at, p.at + p.dir), 5f)[0], heading(p.dir), text = if (kind == PropKind.SPEED_LIMIT) listOf("30") else emptyList())
        }
        props += rnd.trees(road, 9f, 40f, every = 7f).filter { t -> road.zipWithNext().minOf { (a, b) -> distTo(t.at, a, b) } >= 8f }

        // camera on the right lane from the start (0, 40)
        val cam = offset(slice(road, BEFORE, arcLengths(road).last()), W / 2).map { it.copy(y = EYE) }
        val arcCam = arcLengths(offset(arcPts.map(::mx), W / 2)).last()
        val pace = Pace(12f).go(70f, 12f).go(30f, 8f).go(arcCam, 8f).go(30f, 12f)
        val camM = arcLengths(cam).last()
        pace.go(camM - 100f - arcCam - 30f, 12f)
        val total = pace.sec
        val tMid = pace.secAt(100f + arcCam / 2)

        // oncoming car: the left lane reversed, timed to be at the middle of the curve when we are
        val oncoming = offset(road, -W / 2).reversed().map { it.copy(y = 0f) }
        val midA = theta / 2; val rl = radius - W / 2
        val midPt = mx(v(c.x + rl * cos(midA), c.z - rl * sin(midA)))
        val sMid = nearestS(oncoming, midPt)
        val s0 = sMid - ONCOMING_MPS * tMid
        val car = Car(rnd.pickModel(), rnd.pickPaint(), oncoming, listOf(Key(0f, s0), Key(1f, s0 + ONCOMING_MPS * total)))

        fun tAt(m: Float) = pace.secAt(m) / total
        return SceneSpec(
            surfaces = surfaces, patches = listOf(bigGround(40f + BEFORE + 20f, -330f)), solids = solids, props = props, cars = listOf(car),
            camera = Timeline(cam, pace.keys()),
            cues = listOf(
                tAt(70f) to "커브 전 감속",
                tAt(105f) to "내 차로 안쪽 유지",
                (tMid - 1f) / total to "맞은편 차 주의",
                tAt(100f + arcCam) to "커브 끝에서 가속",
                tAt(pace.metres - 8f) to "완료",
            ),
            durationMs = (total * 1000).roundToLong(),
        )
    }

    private fun distTo(p: Vec3, a: Vec3, b: Vec3): Float {
        val dx = b.x - a.x; val dz = b.z - a.z; val l2 = dx * dx + dz * dz
        val u = if (l2 == 0f) 0f else (((p.x - a.x) * dx + (p.z - a.z) * dz) / l2).coerceIn(0f, 1f)
        return kotlin.math.hypot(p.x - (a.x + dx * u), p.z - (a.z + dz * u))
    }
}
