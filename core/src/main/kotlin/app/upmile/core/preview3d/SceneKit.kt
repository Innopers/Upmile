package app.upmile.core.preview3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Driver's eye height above the road. */
const val EYE = 1.25f
/** The camera looks this far ahead along its path and this far below eye height (as the Canvas preview did). */
const val LOOK_AHEAD = 14f
const val LOOK_DROP = .25f
/** Closest any car may come to the camera or another car, centre to centre; neighbouring lanes are 3.25–3.5 m apart. */
const val MIN_GAP = 3f

/** Korean road constants: 경찰청 교통노면표시 설치·관리 매뉴얼; 도로의 구조·시설 기준에 관한 규칙 제10조; 주차장법 시행규칙. */
object Kr {
    const val LANE_HW = 3.5f
    const val LANE_CITY = 3.25f
    const val LINE = .15f
    const val HW_DASH = 10f
    const val HW_GAP = 10f
    const val CITY_DASH = 3f
    const val CITY_GAP = 5f
    const val STOP_LINE = .45f
    /** Left-turn guide line: paint and gap each 0.5–1 m. */
    const val GUIDE_DASH = .75f
    const val CROSSWALK_W = 4f
    /** shortcut: stripe width and gap are not confirmed in 별표6 (a search summary said 45–50 cm); confirm before production. */
    const val CROSSWALK_BAR = .5f
    const val RAMP_MAX_STRAIGHT = .17f
    const val RAMP_MAX_CURVE = .14f
}

data class Pose(val at: Vec3, val dir: Vec3)

operator fun Vec3.plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
operator fun Vec3.minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
operator fun Vec3.times(k: Float) = Vec3(x * k, y * k, z * k)
fun Vec3.len() = sqrt(x * x + y * y + z * z)
fun Vec3.unit(): Vec3 { val l = len(); return if (l == 0f) Vec3(0f, 0f, -1f) else this * (1f / l) }

/** Ground point; note the argument order (x, z, y). */
fun v(x: Float, z: Float, y: Float = 0f) = Vec3(x, y, z)
fun deg(d: Float) = d * PI.toFloat() / 180f

/** Travel heading of [dir]: radians, 0 = -Z, positive turns right (toward +X). */
fun heading(dir: Vec3): Float = atan2(dir.x, -dir.z)

/** Point and unit direction [s] metres along [path]; before the start or past the end it extends the end segment. */
fun pointAt(path: List<Vec3>, s: Float): Pose {
    require(path.size >= 2) { "a path needs two points" }
    var acc = 0f
    for (i in 1 until path.size) {
        val a = path[i - 1]; val d = path[i] - a; val l = d.len()
        if (l == 0f) continue
        if (s <= acc + l || i == path.lastIndex) return Pose(a + d * ((s - acc) / l), d.unit())
        acc += l
    }
    error("path has no length")
}

/** [path] shifted [d] metres to the driver's right (negative = left), horizontally. */
fun offset(path: List<Vec3>, d: Float): List<Vec3> = path.mapIndexed { i, p ->
    val a = path[maxOf(i - 1, 0)]; val b = path[minOf(i + 1, path.lastIndex)]
    val dx = b.x - a.x; val dz = b.z - a.z; val n = sqrt(dx * dx + dz * dz)
    Vec3(p.x - dz / n * d, p.y, p.z + dx / n * d)
}

/** The part of [path] from [s0] to [s1] metres, both ends included. */
fun slice(path: List<Vec3>, s0: Float, s1: Float): List<Vec3> {
    val arc = arcLengths(path)
    val out = mutableListOf(pointAt(path, s0).at)
    for (i in path.indices) if (arc[i] > s0 && arc[i] < s1) out += path[i]
    out += pointAt(path, s1).at
    return out
}

/** Paint pieces of a dashed line along [path]: [paint] metres on, [gap] off, the first piece starting at [phase]. */
fun dashes(path: List<Vec3>, paint: Float, gap: Float, phase: Float = 0f): List<List<Vec3>> {
    val total = arcLengths(path).last()
    val out = mutableListOf<List<Vec3>>()
    var s = phase
    while (s < total) { out += slice(path, s, minOf(s + paint, total)); s += paint + gap }
    return out
}

/** A Catmull–Rom curve through [pts], sampled about every [step] metres; y is interpolated too. */
fun smooth(pts: List<Vec3>, step: Float = 2f): List<Vec3> {
    if (pts.size < 3) return pts
    val out = mutableListOf(pts.first())
    for (i in 0 until pts.lastIndex) {
        val p0 = pts[maxOf(i - 1, 0)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[minOf(i + 2, pts.lastIndex)]
        val n = ceil((p2 - p1).len() / step).toInt().coerceAtLeast(1)
        for (k in 1..n) {
            val t = k / n.toFloat(); val t2 = t * t; val t3 = t2 * t
            fun c(a: Float, b: Float, c: Float, d: Float) =
                .5f * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3)
            out += if (k == n) p2 else Vec3(c(p0.x, p1.x, p2.x, p3.x), c(p0.y, p1.y, p2.y, p3.y), c(p0.z, p1.z, p2.z, p3.z))
        }
    }
    return out
}

/** Points on a circle around [c] (radius [r]); angles from +X toward -Z, i.e. counter-clockwise seen from above. */
fun arc(c: Vec3, r: Float, from: Float, to: Float, step: Float = 2f): List<Vec3> {
    val n = ceil(abs(to - from) * r / step).toInt().coerceAtLeast(1)
    return (0..n).map { k -> val a = from + (to - from) * k / n; Vec3(c.x + r * cos(a), c.y, c.z - r * sin(a)) }
}

/** [pts] with extra points so no piece is longer than [maxPiece] metres (the Canvas projection needs short pieces). */
fun subdivide(pts: List<Vec3>, maxPiece: Float = 20f): List<Vec3> {
    val out = mutableListOf(pts.first())
    for (i in 1 until pts.size) {
        val a = pts[i - 1]; val b = pts[i]
        val n = ceil((b - a).len() / maxPiece).toInt().coerceAtLeast(1)
        for (k in 1..n) out += a + (b - a) * (k / n.toFloat())
    }
    return out
}

/** A mover's (seconds, metres) trace, built leg by leg; speeds in m/s. */
class Pace(private var mps: Float) {
    private val trace = mutableListOf(0f to 0f)
    val sec get() = trace.last().first
    val metres get() = trace.last().second

    /** Drives [len] metres while the speed changes evenly (in time) to [to]. */
    fun go(len: Float, to: Float = mps): Pace {
        require(len > 0f && mps + to > 0f) { "a leg needs length and some speed" }
        val dur = 2 * len / (mps + to); val acc = (to - mps) / dur; val t0 = sec; val s0 = metres
        for (k in 1..LEG_SAMPLES) { val dt = dur * k / LEG_SAMPLES; trace += (t0 + dt) to (s0 + mps * dt + acc * dt * dt / 2) }
        mps = to
        return this
    }

    /** Stands still for [seconds]; only after slowing to 0 (or starting at 0). */
    fun stop(seconds: Float): Pace {
        require(mps == 0f) { "slow to 0 before stopping" }
        trace += (sec + seconds) to metres
        return this
    }

    /** Keys over a scene lasting [totalSec] seconds (default: this trace's own length); past its end the mover stays put. */
    fun keys(totalSec: Float = sec): List<Key> = trace.map { (t, s) -> Key(t / totalSec, s) }

    /** Seconds when the trace first reaches [m] metres. */
    fun secAt(m: Float): Float {
        if (m <= 0f) return 0f
        for (i in 1 until trace.size) {
            val (t0, s0) = trace[i - 1]; val (t1, s1) = trace[i]
            if (s1 >= m && s1 > s0) return t0 + (t1 - t0) * (m - s0) / (s1 - s0)
        }
        return sec
    }

    /** Metres covered after [seconds]. */
    fun metresAt(seconds: Float): Float {
        if (seconds <= 0f) return 0f
        for (i in 1 until trace.size) {
            val (t0, s0) = trace[i - 1]; val (t1, s1) = trace[i]
            if (t1 >= seconds) return if (t1 == t0) s1 else s0 + (s1 - s0) * (seconds - t0) / (t1 - t0)
        }
        return metres
    }

    private companion object { const val LEG_SAMPLES = 8 }
}

/** Metres along [path] to the point nearest [p] (XZ plane). */
fun nearestS(path: List<Vec3>, p: Vec3): Float {
    val arc = arcLengths(path)
    var best = Float.MAX_VALUE; var bestS = 0f
    for (i in 1 until path.size) {
        val a = path[i - 1]; val b = path[i]
        val dx = b.x - a.x; val dz = b.z - a.z; val l2 = dx * dx + dz * dz
        val u = if (l2 == 0f) 0f else (((p.x - a.x) * dx + (p.z - a.z) * dz) / l2).coerceIn(0f, 1f)
        val ex = a.x + dx * u - p.x; val ez = a.z + dz * u - p.z; val d = ex * ex + ez * ez
        if (d < best) { best = d; bestS = arc[i - 1] + (arc[i] - arc[i - 1]) * u }
    }
    return bestS
}

/** Metres along the path at progress [t]; [keys] sorted by t; clamps outside. */
fun at(keys: List<Key>, t: Float): Float {
    if (t <= keys.first().t) return keys.first().s
    if (t >= keys.last().t) return keys.last().s
    val i = keys.indexOfFirst { it.t >= t }
    val a = keys[i - 1]; val b = keys[i]
    return if (b.t == a.t) b.s else a.s + (b.s - a.s) * (t - a.t) / (b.t - a.t)
}
