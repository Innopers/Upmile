package app.upmile.core.preview3d

import app.upmile.core.model.HardSegment
import kotlin.math.roundToLong
import kotlin.random.Random

/** Any other segment: a straight two-lane road with a warning sign and the segment title as the cue. */
object PlainScene {
    private const val RUN_M = 200f

    fun build(segment: HardSegment): SceneSpec {
        val w = Kr.LANE_CITY
        val rnd = Random(segment.id.hashCode())
        val road = listOf(v(0f, 60f), v(0f, -RUN_M - 400f))
        val pace = Pace(12f).go(RUN_M)
        return SceneSpec(
            surfaces = listOf(
                Ribbon(road, 2 * w + 1f, Mat.ASPHALT),
                Ribbon(road, Kr.LINE, Mat.YELLOW_PAINT, .01f),
                Ribbon(offset(road, w + .1f), Kr.LINE, Mat.WHITE_PAINT, .01f),
                Ribbon(offset(road, -w - .1f), Kr.LINE, Mat.WHITE_PAINT, .01f),
            ),
            patches = listOf(bigGround(300f, -900f)),
            solids = emptyList(),
            props = listOf(Prop(PropKind.WARNING_ACCIDENT, v(w + 2.2f, -40f), text = listOf(segment.title))) + rnd.trees(road, 9f, 30f),
            cars = listOf(rnd.cruise(listOf(v(-w / 2, -RUN_M - 400f), v(-w / 2, 60f)), 350f, 11f, pace.sec)),
            camera = Timeline(listOf(v(w / 2, 0f, EYE), v(w / 2, -RUN_M, EYE)), pace.keys()),
            cues = listOf(pace.secAt(20f) / pace.sec to segment.title),
            durationMs = (pace.sec * 1000).roundToLong(),
        )
    }
}
