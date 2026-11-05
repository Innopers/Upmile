package app.upmile.core.routine

import kotlin.math.ceil
import kotlin.math.roundToInt

enum class Phase { INHALE, EXHALE }

/**
 * One sample of the breathing cycle.
 * [secondsLeftInPhase] rounds up: at 3_999 ms into a 4 s inhale it reads 1, never 0.
 */
data class BreathFrame(val phase: Phase, val secondsLeftInPhase: Int, val brightness: Int, val argb: Int)

object Breathing {
    const val INHALE_S = 4
    const val EXHALE_S = 6

    private const val INHALE_MS = INHALE_S * 1_000L
    private const val CYCLE_MS = (INHALE_S + EXHALE_S) * 1_000L
    private const val MIN_BRIGHTNESS = 20
    private const val MAX_BRIGHTNESS = 80
    private const val WARM_ARGB = 0xFFFFB86B.toInt()
    private const val COOL_ARGB = 0xFF4CC2FF.toInt()

    fun frameAt(elapsedMs: Long): BreathFrame {
        val pos = Math.floorMod(elapsedMs, CYCLE_MS)
        return if (pos < INHALE_MS) {
            val f = pos.toDouble() / INHALE_MS
            BreathFrame(
                Phase.INHALE,
                secondsLeft(INHALE_MS - pos),
                lerp(MIN_BRIGHTNESS, MAX_BRIGHTNESS, f),
                WARM_ARGB,
            )
        } else {
            val into = pos - INHALE_MS
            val f = into.toDouble() / (CYCLE_MS - INHALE_MS)
            BreathFrame(
                Phase.EXHALE,
                secondsLeft(CYCLE_MS - pos),
                lerp(MAX_BRIGHTNESS, MIN_BRIGHTNESS, f),
                COOL_ARGB,
            )
        }
    }

    private fun secondsLeft(remainingMs: Long): Int = ceil(remainingMs / 1_000.0).toInt()

    private fun lerp(from: Int, to: Int, f: Double): Int = (from + (to - from) * f).roundToInt()
}
