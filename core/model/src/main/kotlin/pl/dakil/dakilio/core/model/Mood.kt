package pl.dakil.dakilio.core.model

/**
 * One grade of a mood scale. [tier] runs 1 (worst) .. scale size (best); [value] is the same grade
 * normalized to 0.0–1.0 so analytics stay comparable if the scale ever changes size.
 */
data class Mood(
    val id: Long,
    val moodSetId: Long,
    val tier: Int,
    val value: Float,
    val label: String,
    val iconKey: String,
    val colorArgb: Int,
) {
    companion object {
        fun normalize(tier: Int, scaleSize: Int): Float =
            if (scaleSize <= 1) 1f else (tier - 1).toFloat() / (scaleSize - 1)
    }
}
