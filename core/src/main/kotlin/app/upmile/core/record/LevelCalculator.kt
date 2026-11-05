package app.upmile.core.record

object LevelCalculator {
    private val thresholds = listOf(0, 2, 5, 8, 11)

    fun level(record: Record): Int = thresholds.count { record.cards.size >= it }.coerceAtLeast(1)

    fun graduated(record: Record): Boolean = record.cards.map { it.type }.toSet().containsAll(ExperienceType.entries)
}
