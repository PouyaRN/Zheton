package com.doxigo.muchtoman

import kotlin.math.roundToInt

/**
 * A gold amount typed the way the bazaar says it: whole kernels (حبه) plus a weighed fraction
 * of a gram. حبه = ۱۲ سوت = ۰٫۱۲ گرم exactly.
 *
 * This only *composes* the number she entered — the arithmetic lives here, in one tested
 * place, and never in a model's output. Null when either field fails to parse.
 */
object GoldInput {
    /** Grams in one kernel of 18k gold: ۱۲ سوت at ۵ سوت to the gram. */
    const val GRAMS_PER_KERNEL = 0.12
    const val SOOT_PER_KERNEL = 12.0

    /** Grams + kernels × ۰٫۱۲, rounded to the milligram the market scale can claim. */
    fun totalGrams(gramsText: String?, kernelsText: String?): Double? {
        val g = gramsText?.takeIf { it.isNotBlank() }?.let { parseAmount(it) }
        val k = kernelsText?.takeIf { it.isNotBlank() }?.let { parseAmount(it) }
        // A field that will not parse is rejected outright — never read as zero — because
        // this is the money path and a silent misread as zero invents gold.
        if (gramsText?.isNotBlank() == true && g == null) return null
        if (kernelsText?.isNotBlank() == true && k == null) return null
        return round3((g ?: 0.0) + (k ?: 0.0) * GRAMS_PER_KERNEL).takeIf { it >= 0 }
    }

    /** Splits a gram total back into whole kernels and the weighed remainder. */
    fun split(totalGrams: Double): Pair<Int, Double> {
        val kernels = (totalGrams / GRAMS_PER_KERNEL + 1e-9).toInt().coerceAtLeast(0)
        return kernels to round3(totalGrams - kernels * GRAMS_PER_KERNEL)
    }

    /** A gram total as the words she'd say: «۳ حبه و ۰٫۰۴ گرم». */
    fun splitFa(totalGrams: Double): String {
        val (kernels, grams) = split(totalGrams)
        return buildString {
            if (kernels > 0) append("${faNumber(kernels.toDouble())} حبه")
            if (grams > 0.0005) {
                if (isNotEmpty()) append(" و ")
                append("${faDecimal(grams, 2, pad = true)} گرم")
            }
            if (isEmpty()) append("۰ گرم")
        }
    }

    private fun round3(v: Double): Double = (v * 1000.0).roundToInt() / 1000.0
}
