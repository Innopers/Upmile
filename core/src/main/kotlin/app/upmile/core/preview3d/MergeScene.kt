package app.upmile.core.preview3d

import app.upmile.core.model.HardSegment
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * 양재IC → 경부고속도로: on-ramp from the right, acceleration lane, merge into the rightmost main lane.
 * Our carriageway spans x in [-lanes*3.5, 0]; the acceleration lane x in [0, 3.5]; the opposite carriageway is left of the median.
 */
object MergeScene {
    private const val W = Kr.LANE_HW
    private const val JOIN_Z = -60f
    private const val END_Z = -340f
    private const val NEAR_Z = 420f   // behind the start so traffic spawning behind us still stands on the road
    private const val FAR_Z = -900f

    fun build(segment: HardSegment): SceneSpec {
        val lanes = (segment.params["lanes"] ?: 5.0).toInt().coerceIn(2, 6)
        val accel = (segment.params["accelLaneM"] ?: 180.0).toFloat().coerceIn(80f, 220f) // above 220 the last cue (change end + 15 m) nears END_Z
        val rnd = Random(segment.id.hashCode())
        val left = -lanes * W
        val medianX = left - 1.3f
        val oppInner = medianX - 1.3f
        val farLeft = oppInner - lanes * W
        val accelEnd = JOIN_Z - accel
        val taperEnd = accelEnd - 40f
        val changeStart = JOIN_Z - accel * .75f
        val changeEnd = changeStart - 45f
        fun laneX(k: Int) = left + (k - .5f) * W
        fun oppX(k: Int) = oppInner - (k - .5f) * W
        fun along(x: Float) = listOf(v(x, NEAR_Z), v(x, FAR_Z))
        fun against(x: Float) = listOf(v(x, FAR_Z), v(x, NEAR_Z))

        val ramp = smooth(listOf(v(26f, 40f), v(17f, 5f), v(8f, -35f), v(W / 2, JOIN_Z)))
        val rampLeft = offset(ramp, -W / 2)
        val goreX = rampLeft.minBy { abs(it.z) }.x

        // camera: ramp, acceleration lane, smoothstep lane change, merge lane
        val change = (1..8).map { k -> val u = k / 8f; v(W / 2 + (laneX(lanes) - W / 2) * u * u * (3 - 2 * u), changeStart - 45f * u) }
        val cam = (ramp + v(W / 2, changeStart) + change + v(laneX(lanes), END_Z)).map { it.copy(y = EYE) }
        val rampM = arcLengths(ramp).last()
        val pace = Pace(11f).go(rampM, 14f).go(arcLengths(cam).last() - rampM, 16f)
        fun tAt(p: Vec3) = pace.secAt(nearestS(cam, p)) / pace.sec

        val (rampSign, gantry) = signText(segment.place)
        val surfaces = mutableListOf(
            Ribbon(along((farLeft + 0f) / 2), -farLeft, Mat.ASPHALT),
            Ribbon(ramp, W, Mat.ASPHALT),
            Ribbon(listOf(v(W / 2, JOIN_Z), v(W / 2, accelEnd)), W, Mat.ASPHALT),
            Ribbon(listOf(v(W + 1.5f, JOIN_Z), v(W + 1.5f, accelEnd)), 3f, Mat.ASPHALT),
            Ribbon(listOf(v(1.5f, taperEnd), v(1.5f, FAR_Z)), 3f, Mat.ASPHALT),
        )
        fun paint(path: List<Vec3>, mat: Mat = Mat.WHITE_PAINT, w: Float = Kr.LINE) { surfaces += Ribbon(path, w, mat, .01f) }
        fun dashed(path: List<Vec3>, w: Float = Kr.LINE) = dashes(path, Kr.HW_DASH, Kr.HW_GAP).forEach { paint(it, w = w) }
        for (k in 1 until lanes) { dashed(along(left + k * W)); dashed(against(oppInner - k * W)) }
        paint(along(left - .1f), Mat.YELLOW_PAINT); paint(along(oppInner + .1f), Mat.YELLOW_PAINT); paint(along(farLeft + .1f))
        paint(listOf(v(.1f, NEAR_Z), v(.1f, JOIN_Z)))                                    // main edge beside the gore
        dashes(listOf(v(0f, JOIN_Z), v(0f, accelEnd)), Kr.HW_DASH, Kr.HW_GAP).forEach { paint(it, w = .2f) } // shortcut: Korean accel-lane dash pattern unverified; expressway dashes, wider
        paint(listOf(v(W - .1f, JOIN_Z), v(W - .1f, accelEnd), v(.1f, taperEnd), v(.1f, FAR_Z)))
        paint(offset(ramp, -W / 2 + .1f)); paint(offset(ramp, W / 2 - .1f))
        for (zk in listOf(-14f, -26f, -38f)) {                                              // gore hatching (도류화)
            val xr = rampLeft.minBy { abs(it.z - (zk + 4f)) }.x - .4f
            if (xr > 1.2f) surfaces += Ribbon(listOf(v(.4f, zk), v(xr, zk + 4f)), .45f, Mat.WHITE_PAINT, .01f)
        }

        val patches = listOf(
            bigGround(300f, FAR_Z - 100f),
            Patch(listOf(v(0f, accelEnd), v(W, accelEnd), v(0f, taperEnd)), Mat.ASPHALT),
            Patch(listOf(v(W, accelEnd), v(W + 3f, accelEnd), v(3f, taperEnd), v(0f, taperEnd)), Mat.ASPHALT),
            Patch(listOf(v(0f, 0f), v(goreX, 0f), v(0f, JOIN_Z)), Mat.ASPHALT),
        )
        val rail = listOf(v(W + 3.3f, JOIN_Z), v(W + 3.3f, accelEnd), v(3.3f, taperEnd), v(3.3f, -560f))
        val rampRail = offset(ramp, W / 2 + .5f)
        val solids = listOf(
            Extrusion(along(medianX), NEW_JERSEY, Mat.BARRIER),
            Extrusion(rail, GUARDRAIL, Mat.STEEL),
            Extrusion(rampRail, GUARDRAIL, Mat.STEEL),
            Extrusion(listOf(v(9f, -120f), v(9f, FAR_Z)), listOf(-.15f to 0f, .15f to 0f, .15f to 4f, -.15f to 4f), Mat.NOISE_WALL),
        )

        val props = mutableListOf<Prop>()
        props += posts(rail) + posts(rampRail)
        for (z in generateSequence(NEAR_Z - 20f) { it - 45f }.takeWhile { it > -620f }) props += Prop(PropKind.TWIN_LAMP, v(medianX, z))
        val lampLine = offset(ramp, W / 2 + 1.2f)
        for (sl in listOf(10f, 40f, 70f, 100f)) pointAt(lampLine, sl).let { props += Prop(PropKind.STREET_LAMP, it.at, heading(it.dir)) }
        pointAt(offset(ramp, W / 2 + 2.2f), 18f).let { props += Prop(PropKind.SIGN_GREEN, it.at, heading(it.dir), Vec3(3.6f, 1.8f, 2.2f), rampSign) }
        props += Prop(PropKind.GANTRY_GREEN, v(W + 3.8f, -300f), size = Vec3(W + 3.8f - medianX, 6f, 0f), text = gantry)
        for (z in listOf(-200f, -300f, -400f)) props += Prop(PropKind.BUILDING_APT, v(55f, z), size = Vec3(12f, 60f + rnd.nextFloat() * 12f, 55f))
        for (z in listOf(-150f, -260f, -370f)) props += Prop(PropKind.BUILDING_APT, v(farLeft - 38f, z), size = Vec3(12f, 55f + rnd.nextFloat() * 15f, 55f))
        // trees fill the gaps between road and apartments; the symmetric helper also drops a mirror band that is filtered away
        props += rnd.trees(along(0f), 33f, 46f, every = 16f).filter { it.at.x > 0f }       // right: clear of the ramp start (x<=28), short of the apartments (x>=49)
        props += rnd.trees(along(farLeft), 8f, 28f, every = 16f).filter { it.at.x < farLeft } // left: short of the apartments at farLeft-38

        // traffic: each lane one speed, so cars in a lane never close up
        val total = pace.sec
        val cars = mutableListOf<Car>()
        for (k in 1 until lanes) {
            var s0 = 30f + (NEAR_Z - 80f) + rnd.nextFloat() * 40f
            repeat(2) { cars += rnd.cruise(along(laneX(k)), s0, 22f - (k - 1), total); s0 += 70f + rnd.nextFloat() * 40f }
        }
        val passSec = pace.secAt(nearestS(cam, v(W / 2, JOIN_Z - 50f, EYE)))
        val passS = NEAR_Z - (JOIN_Z - 50f)
        cars += Car(rnd.pickModel(), rnd.pickPaint(), along(laneX(lanes)), listOf(Key(0f, passS - 19f * passSec), Key(1f, passS + 19f * (total - passSec))))
        val changeSec = pace.secAt(nearestS(cam, v(W / 2, changeStart, EYE)))
        val behindS = NEAR_Z - changeStart - 60f
        cars += Car(rnd.pickModel(), rnd.pickPaint(), along(laneX(lanes)), listOf(Key(0f, behindS - 17f * changeSec), Key(1f, behindS + 17f * (total - changeSec))))
        for (k in 1..lanes) cars += rnd.cruise(against(oppX(k)), 300f + rnd.nextFloat() * 400f, 20f + k, total)

        return SceneSpec(
            surfaces = surfaces, patches = patches, solids = solids, props = props, cars = cars,
            camera = Timeline(cam, pace.keys()),
            cues = listOf(
                pace.secAt(2f) / pace.sec to "진입로 주행",
                tAt(v(W / 2, JOIN_Z)) to "가속 차로",
                tAt(v(W / 2, JOIN_Z - accel * .45f)) to "왼쪽 깜빡이·미러 확인",
                tAt(v(W / 2, changeStart)) to "합류",
                tAt(v(laneX(lanes), changeEnd - 15f)) to "합류 완료",
            ),
            durationMs = (pace.sec * 1000).roundToLong(),
        )
    }

    /**
     * Ramp sign and gantry text. shortcut: a small place table; in production the map's destination tags
     * (OSM `destination=부산;대전` on the 양재IC ramp) fill these.
     */
    private fun signText(place: String): Pair<List<String>, List<String>> {
        val road = Regex("(\\S+고속도로)").find(place)?.value ?: "고속도로"
        return if ("경부" in road) listOf(road, "부산 · 대전") to listOf("판교  Pangyo", "7 km")
        else listOf(road, "본선 진입") to listOf("다음 나들목", "5 km")
    }
}
