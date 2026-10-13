package com.example.drinksync

import kotlin.math.roundToInt

object DrinkEvent {
    const val GRAMS_PER_OZ = 29.5735

    /** Longest line accepted from the coaster; anything longer is not a drink event. */
    const val MAX_LINE_LENGTH = 256

    /**
     * Sanity bound for one event (10 kg). A coaster cannot report more, and rejecting
     * larger values keeps a malformed or hostile line from overflowing the Int totals.
     */
    const val MAX_EVENT_GRAMS = 10_000.0

    private val message = Regex(
        """^(?:Weight difference|Average weight|Weight Differnce):\s*(-?\d+(?:\.\d+)?)\s*grams$""",
        RegexOption.IGNORE_CASE,
    )

    fun parseGrams(raw: String): Double? {
        if (raw.length > MAX_LINE_LENGTH) return null
        val match = message.matchEntire(raw.trim()) ?: return null
        val grams = match.groupValues[1].toDoubleOrNull() ?: return null
        return if (grams.isFinite() && grams > 0 && grams <= MAX_EVENT_GRAMS) grams else null
    }

    fun gramsToOz(grams: Double): Int = (grams / GRAMS_PER_OZ).roundToInt()

    fun leftoverAfterAdding(existingLeftoverGrams: Double, addedGrams: Double): Pair<Int, Double> {
        val total = existingLeftoverGrams + addedGrams
        val ounces = gramsToOz(total)
        if (ounces <= 0) return 0 to total
        return ounces to (total - ounces * GRAMS_PER_OZ)
    }
}
