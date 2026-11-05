package app.upmile.core.preview3d

import app.upmile.core.model.HardSegment
import app.upmile.core.model.SegmentType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Metres; road runs toward -Z, +X is right, +Y is up. */
data class Vec3(val x: Float, val y: Float, val z: Float)
data class Strip(val center: List<Vec3>, val width: Float, val color: Long, val dashed: Boolean = false)
/** Cue progress is 0..1 along the camera path. */
data class TemplateScene(val strips: List<Strip>, val cameraPath: List<Vec3>, val cues: List<Pair<Float, String>>)

object RoadTemplates {
    private const val LANE = 3.5f
    private const val EYE = 1.25f
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

    private fun v(x: Float, z: Float, y: Float = 0f) = Vec3(x, y, z)
    private fun eye(p: Vec3) = p.copy(y = p.y + EYE)

    private fun merge(lanes: Int, accelM: Float): TemplateScene {
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
            Strip(listOf(v(0f, 0f), v(0f, -total)), mainW, ASPHALT),
            Strip(ramp, LANE, ASPHALT),
            Strip(accel, LANE, ASPHALT),
            Strip(taper, LANE, ASPHALT),
            Strip(listOf(v(-edge, 0f), v(-edge, -total)), 0.15f, WHITE),
            Strip(listOf(v(edge, 0f), v(edge, -accelEnd)), 0.15f, WHITE, dashed = true),
        )
        for (i in 1 until lanes) strips += Strip(listOf(v(-edge + i * LANE, 0f), v(-edge + i * LANE, -total)), 0.15f, WHITE, dashed = true)
        val laneX = edge - LANE / 2
        val cam = listOf(
            v(accelX + 14f, 0f), v(accelX + 8f, -20f), v(accelX, -rampEnd), v(accelX, -rampEnd - accelM * 0.4f),
            v(accelX, -rampEnd - accelM * 0.75f), v(accelX, -accelEnd), v(accelX - (accelX - laneX) * 0.5f, -accelEnd - 20f),
            v(laneX, -taperEnd), v(laneX, -(taperEnd + total) / 2), v(laneX, -total),
        ).map(::eye)
        strips += Strip(cam.map { it.copy(y = 0.02f) }, 1.2f, GUIDE)
        val cues = listOf(0.05f to "진입로 주행", 0.22f to "가속 차로", 0.45f to "왼쪽 깜빡이·미러 확인", 0.65f to "합류", 0.85f to "합류 완료")
        return TemplateScene(strips, cam, cues)
    }

    private fun left(turnLanes: Int, fromLane: Int): TemplateScene {
        val laneCount = 4
        val half = laneCount * LANE / 2
        val stopZ = -90f
        val crossW = 14f
        val crossZ = stopZ - crossW / 2
        val laneX = -half + (fromLane.coerceIn(1, laneCount) - 0.5f) * LANE
        val outZ = crossZ - LANE * 0.5f // far-side 2nd lane of the exit road
        val strips = mutableListOf(
            Strip(listOf(v(0f, 0f), v(0f, stopZ)), laneCount * LANE, ASPHALT),
            Strip(listOf(v(-half - 60f, crossZ), v(half + 20f, crossZ)), crossW, ASPHALT),
            Strip(listOf(v(-half, 0f), v(-half, stopZ)), 0.15f, YELLOW),
            Strip(listOf(v(-half, stopZ), v(half, stopZ)), 0.5f, WHITE),
        )
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
        val cues = listOf(0.04f to "좌회전 차로 ${turnLanes}개", 0.30f to "정지선 신호 대기", 0.45f to "유도선 따라 회전", 0.7f to "${2}차로로 진입", 0.92f to "완료")
        return TemplateScene(strips, camE, cues)
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
        val cues = listOf(0.04f to "입구 · 전조등", 0.2f to "급경사 내리막", 0.45f to "좁은 우회전", 0.72f to "차로 가운데", 0.9f to "도착")
        return TemplateScene(strips, pts.map(::eye), cues)
    }

    private fun generic(title: String): TemplateScene {
        val len = 200f
        val strips = listOf(
            Strip(listOf(v(0f, 0f), v(0f, -len)), 2 * LANE, ASPHALT),
            Strip(listOf(v(0f, 0f), v(0f, -len)), 0.15f, YELLOW, dashed = true),
        )
        val cam = (0..9).map { eye(v(0f, -it * len / 9f)) }
        return TemplateScene(strips, cam, listOf(0.1f to title))
    }
}
