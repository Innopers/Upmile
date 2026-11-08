package app.upmile.core.preview3d

import app.upmile.core.model.HardSegment
import app.upmile.core.model.SegmentType
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Metres; road runs toward -Z, +X is right, +Y is up. */
data class Vec3(val x: Float, val y: Float, val z: Float)
data class Strip(val center: List<Vec3>, val width: Float, val color: Long, val dashed: Boolean = false)
/** Cue progress is the ARCLENGTH fraction (0..1) along cameraPath. */
data class TemplateScene(val strips: List<Strip>, val cameraPath: List<Vec3>, val cues: List<Pair<Float, String>>)

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

object RoadTemplates {
    private const val MAX_PIECE = 20f
    private const val TARGET_LANE = 2
    private const val LANE = 3.5f
    private const val EYE = 1.25f
    /** Open roads run on this far past the last camera point, into the fog, so the final frame never shows the road ending. */
    private const val RUNOUT = 300f
    private const val ASPHALT = 0xFF31363DL
    private const val WHITE = 0xFFE9EDF2L
    private const val YELLOW = 0xFFF2C230L
    private const val GUIDE = 0x994CC2FFL
    private const val WALL = 0xFF8A9099L

    fun build(segment: HardSegment): TemplateScene = when (segment.type) {
        SegmentType.HIGHWAY_MERGE -> merge(
            segment.params["lanes"]?.toInt() ?: 2,
            (segment.params["accelLaneM"] ?: 180.0).toFloat(),
        )
        SegmentType.MULTILANE_LEFT -> left(
            segment.params["turnLanes"]?.toInt() ?: 2,
            segment.params["fromLane"]?.toInt() ?: 2,
        )
        SegmentType.GARAGE_RAMP -> ramp(
            (segment.params["slope"] ?: 0.12).toFloat(),
            (segment.params["turnDeg"] ?: 90.0).toFloat(),
        )
        else -> generic(segment.title)
    }

    private fun subdivide(pts: List<Vec3>): List<Vec3> {
        val out = mutableListOf(pts.first())
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            val n = ceil(arcLengths(listOf(a, b)).last() / MAX_PIECE).toInt().coerceAtLeast(1)
            for (k in 1..n) { val t = k / n.toFloat(); out += Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t) }
        }
        return out
    }

    /** Builds the scene; [cueAt] gives (arclength metres, text) and is converted to a progress fraction. */
    private fun scene(strips: List<Strip>, cam: List<Vec3>, cueAt: List<Pair<Float, String>>): TemplateScene {
        val total = arcLengths(cam).last()
        return TemplateScene(strips.map { it.copy(center = subdivide(it.center)) }, cam, cueAt.map { (d, t) -> (d / total).coerceIn(0.01f, 0.99f) to t })
    }

    private fun v(x: Float, z: Float, y: Float = 0f) = Vec3(x, y, z)
    private fun eye(p: Vec3) = p.copy(y = p.y + EYE)

    private fun merge(lanesIn: Int, accelIn: Float): TemplateScene {
        val lanes = lanesIn.coerceAtLeast(1); val accelM = accelIn.coerceAtLeast(40f)
        val mainW = lanes * LANE
        val edge = mainW / 2
        val accelX = edge + LANE / 2
        val rampEnd = 60f
        val accelEnd = rampEnd + accelM
        val taperEnd = accelEnd + 40f
        val total = taperEnd + 40f
        val ramp = listOf(v(accelX + 14f, 0f), v(accelX + 8f, -20f), v(accelX, -rampEnd))
        val accel = listOf(v(accelX, -rampEnd), v(accelX, -accelEnd))
        val taper = listOf(v(accelX, -accelEnd), v(edge - LANE / 2, -taperEnd))
        val strips = mutableListOf(
            Strip(listOf(v(0f, 0f), v(0f, -total - RUNOUT)), mainW, ASPHALT),
            Strip(ramp, LANE, ASPHALT),
            Strip(accel, LANE, ASPHALT),
            Strip(taper, LANE, ASPHALT),
            Strip(listOf(v(-edge, 0f), v(-edge, -total - RUNOUT)), 0.15f, WHITE),
            Strip(listOf(v(edge, 0f), v(edge, -accelEnd)), 0.15f, WHITE, dashed = true),
        )
        for (i in 1 until lanes) strips += Strip(listOf(v(-edge + i * LANE, 0f), v(-edge + i * LANE, -total - RUNOUT)), 0.15f, WHITE, dashed = true)
        val laneX = edge - LANE / 2
        val cam = listOf(
            v(accelX + 14f, 0f), v(accelX + 8f, -20f), v(accelX, -rampEnd), v(accelX, -rampEnd - accelM * 0.4f),
            v(accelX, -rampEnd - accelM * 0.75f), v(accelX, -accelEnd), v(accelX - (accelX - laneX) * 0.5f, -accelEnd - 20f),
            v(laneX, -taperEnd), v(laneX, -(taperEnd + total) / 2), v(laneX, -total),
        ).map(::eye)
        strips += Strip(cam.map { it.copy(y = 0.02f) }, 1.2f, GUIDE)
        val arc = arcLengths(cam)
        val cues = listOf(2f to "진입로 주행", arc[2] to "가속 차로", arc[4] to "왼쪽 깜빡이·미러 확인", arc[6] to "합류", arc[8] to "합류 완료")
        return scene(strips, cam, cues)
    }

    private fun left(turnLanesIn: Int, fromLane: Int): TemplateScene {
        val laneCount = 4
        val turnLanes = turnLanesIn.coerceIn(1, laneCount)
        val half = laneCount * LANE / 2
        val stopZ = -90f
        val crossW = 14f
        val crossZ = stopZ - crossW / 2
        val laneX = -half + (fromLane.coerceIn(1, laneCount) - 0.5f) * LANE
        val outZ = crossZ - (TARGET_LANE - 0.5f) * LANE // TARGET_LANE-th lane counted from the near edge of the cross road
        val farX = -half - 45f - RUNOUT; val nearX = half + 20f
        val strips = mutableListOf(
            Strip(listOf(v(0f, 0f), v(0f, stopZ)), laneCount * LANE, ASPHALT),
            Strip(listOf(v(farX, crossZ), v(nearX, crossZ)), crossW, ASPHALT),
            Strip(listOf(v(-half, 0f), v(-half, stopZ)), 0.15f, YELLOW),
            Strip(listOf(v(-half, stopZ), v(half, stopZ)), 0.5f, WHITE),
            Strip(listOf(v(farX, crossZ - crossW / 2 + 0.2f), v(nearX, crossZ - crossW / 2 + 0.2f)), 0.15f, WHITE), // far edge
        )
        // Cross-road markings on both arms (not inside the intersection): near edge, lane dividers, double yellow centre line.
        for ((x0, x1) in listOf(farX to -half, half to nearX)) {
            fun line(z: Float, w: Float, c: Long, dashed: Boolean = false) = Strip(listOf(v(x0, z), v(x1, z)), w, c, dashed)
            strips += line(stopZ - 0.2f, 0.15f, WHITE)
            for (k in 1 until (crossW / 2 / LANE).toInt()) { strips += line(crossZ + k * LANE, 0.15f, WHITE, true); strips += line(crossZ - k * LANE, 0.15f, WHITE, true) }
            strips += line(crossZ + 0.15f, 0.12f, YELLOW); strips += line(crossZ - 0.15f, 0.12f, YELLOW)
        }
        // Crosswalk across the approach, between the stop line and the cross traffic (Korean order: stop line, then crosswalk).
        for (i in 0 until 8) { val x = -half + (i + 0.5f) * (2 * half / 8); strips += Strip(listOf(v(x, stopZ - 0.8f), v(x, stopZ - 4.8f)), 0.5f, WHITE) }
        for (i in 1 until laneCount) strips += Strip(listOf(v(-half + i * LANE, 0f), v(-half + i * LANE, stopZ)), 0.15f, WHITE, dashed = true)
        for (i in 0 until turnLanes.coerceIn(1, laneCount)) { // turn lanes sit on the left of the approach
            val x = -half + (i + 0.5f) * LANE
            strips += Strip(listOf(v(x, -60f), v(x, stopZ + 2f)), 0.6f, WHITE)
        }
        val cam = mutableListOf(v(laneX, 0f), v(laneX, -30f), v(laneX, -60f), v(laneX, stopZ + 3f))
        val sx = laneX; val sz = stopZ + 3f
        val ex = -half - 25f
        for (k in 1..5) { // quarter-turn curve into the far-side lane
            val t = k / 5f
            val a = t * PI.toFloat() / 2
            val x = sx + (ex - sx) * (1 - cos(a))
            val z = sz + (outZ - sz) * sin(a)
            cam += v(x, z)
        }
        cam += v(ex - 20f, outZ)
        val camE = cam.map(::eye)
        strips += Strip(cam.map { it.copy(y = 0.02f) }, 1.2f, GUIDE)
        val arc = arcLengths(camE)
        val cues = listOf(
            2f to "좌회전 차로 ${turnLanes}개", arc[3] - 2f to "정지선 신호 대기", arc[3] + 3f to "유도선 따라 회전",
            arc[8] - 3f to "${TARGET_LANE}차로로 진입", arc.last() - 5f to "완료",
        )
        return scene(strips, camE, cues)
    }

    private fun ramp(slope: Float, turnDeg: Float): TemplateScene {
        val w = 5f
        val pts = mutableListOf<Vec3>()
        var x = 0f; var z = 0f; var y = 0f; var h = 0f // h: heading rad, 0 = -Z, positive = turn right
        fun step(len: Float, dh: Float) {
            h += dh
            val d = len / sqrt(1 + slope * slope)
            x += sin(h) * d; z -= cos(h) * d; y -= slope * d
            pts += v(x, z, y)
        }
        pts += v(0f, 0f, 0f)
        repeat(6) { step(5f, 0f) }
        val arcSteps = 10
        val rad = turnDeg * PI.toFloat() / 180f
        repeat(arcSteps) { step(1.2f, rad / arcSteps) }
        repeat(4) { step(5f, 0f) }
        val camCount = pts.size
        repeat(5) { step(5f, 0f) } // road continues 25 m past the last camera point
        fun offset(d: Float) = pts.mapIndexed { i, p ->
            val a = pts[maxOf(i - 1, 0)]; val b = pts[minOf(i + 1, pts.lastIndex)]
            val dx = b.x - a.x; val dz = b.z - a.z; val n = sqrt(dx * dx + dz * dz)
            Vec3(p.x - dz / n * d, p.y + 0.02f, p.z + dx / n * d)
        }
        val strips = listOf(
            Strip(pts, w, ASPHALT),
            Strip(offset(w / 2).map { it.copy(y = it.y + 1f) }, 0.3f, WALL),
            Strip(offset(-w / 2).map { it.copy(y = it.y + 1f) }, 0.3f, WALL),
            Strip(offset(0f), 0.15f, YELLOW, dashed = true),
            Strip(offset(0f), 1.2f, GUIDE),
        )
        val cam = pts.take(camCount).map(::eye)
        val arc = arcLengths(cam)
        val cues = listOf(2f to "입구 · 전조등", arc[2] to "급경사 내리막", arc[6] to "좁은 우회전", arc[16] to "차로 가운데", arc.last() - 4f to "도착")
        return scene(strips, cam, cues)
    }

    private fun generic(title: String): TemplateScene {
        val len = 200f; val road = len + RUNOUT
        val strips = listOf(
            Strip(listOf(v(0f, 0f), v(0f, -road)), 2 * LANE, ASPHALT),
            Strip(listOf(v(0f, 0f), v(0f, -road)), 0.15f, YELLOW, dashed = true),
        )
        val cam = (0..9).map { eye(v(LANE / 2, -it * len / 9f)) } // right-hand lane, not on the centre line
        return scene(strips, cam, listOf(len * 0.1f to title))
    }
}
