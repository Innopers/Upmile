package app.upmile.core.routine

enum class Mood { VERY_NERVOUS, A_LITTLE, FINE }

enum class RoutineStep { BREATHE, CHECK, SEAT_MIRROR, PREVIEW_PROMPT }

data class RoutinePlan(val steps: List<RoutineStep>, val breatheSeconds: Int, val playMusic: Boolean)

private val ALL_STEPS = RoutineStep.entries.toList()

fun planFor(mood: Mood): RoutinePlan = when (mood) {
    Mood.VERY_NERVOUS -> RoutinePlan(ALL_STEPS, breatheSeconds = 240, playMusic = true)
    Mood.A_LITTLE -> RoutinePlan(ALL_STEPS, breatheSeconds = 40, playMusic = false)
    Mood.FINE -> RoutinePlan(
        listOf(RoutineStep.BREATHE, RoutineStep.CHECK, RoutineStep.PREVIEW_PROMPT),
        breatheSeconds = 20,
        playMusic = false,
    )
}
