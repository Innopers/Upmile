package app.upmile.core.record

object LevelCalculator {
    /** Cards needed for each level, from Lv.1 up. */
    val thresholds = listOf(0, 2, 5, 8, 11)

    fun level(record: Record): Int = thresholds.count { record.cards.size >= it }.coerceAtLeast(1)

    fun graduated(record: Record): Boolean = record.cards.map { it.type }.toSet().containsAll(ExperienceType.entries)
}
