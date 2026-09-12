package com.doxigo.muchtoman.domain.shetab

/**
 * Pure Kotlin banking identity and checksum utilities for Iranian Shetab cards and SHEBA (IBAN).
 *
 * Encapsulates:
 * - ASCII / Persian / Arabic digit normalization
 * - ISO/IEC 7812 Mod-10 Luhn checksum validator
 * - ISO 7064 Mod 97-10 Iranian SHEBA (IBAN) validator
 * - Standardized 4-digit cluster formatting for cards and accounts
 *
 * Free of any Android Canvas, OpenGL, or UI framework dependencies.
 */
object ShetabKit {

    /**
     * Sanitizes any Persian or Arabic numerals to standard ASCII digits 0-9
     * for seamless compatibility when pasting into banking apps or web portals.
     */
    fun toAsciiDigits(str: String): String = str.map { char ->
        when (char) {
            '۰', '٠' -> '0'
            '۱', '١' -> '1'
            '۲', '٢' -> '2'
            '۳', '٣' -> '3'
            '۴', '٤' -> '4'
            '۵', '٥' -> '5'
            '۶', '٦' -> '6'
            '۷', '٧' -> '7'
            '۸', '٨' -> '8'
            '۹', '٩' -> '9'
            else -> char
        }
    }.joinToString("")

    /** Formats a PAN or mask into 4 digit blocks with Persian numerals. */
    fun formatCardPan(raw: String): String {
        val clean = raw.trim()
        fun convertPersian(str: String): String = str.map { char ->
            when (char) {
                '0' -> '۰'
                '1' -> '۱'
                '2' -> '۲'
                '3' -> '۳'
                '4' -> '۴'
                '5' -> '۵'
                '6' -> '۶'
                '7' -> '۷'
                '8' -> '۸'
                '9' -> '۹'
                else -> char
            }
        }.joinToString("")

        // If string is already grouped with bullet masks or double-spacing, preserve it
        if (clean.contains(" •••• ") || clean.contains("  ") || clean.contains(" - ")) {
            return convertPersian(clean)
        }

        // Strip hyphens and single spaces to uniformly segment into 4-character clusters
        val stripped = clean.replace("-", "").replace(" ", "")
        if (stripped.length >= 8) {
            val chunks = stripped.chunked(4)
            return convertPersian(chunks.joinToString("  "))
        }

        return convertPersian(clean)
    }

    /** Formats a PAN or mask into 4 digit blocks wrapped in Left-to-Right Unicode isolate markers. */
    fun formatCardPanLtr(raw: String): String {
        val formatted = formatCardPan(raw)
        return "\u2066$formatted\u2069"
    }

    /** Mod-10 Luhn checksum validator for Iranian 16-digit Shetab card numbers. */
    fun isValidLuhn(pan: String): Boolean {
        val clean = toAsciiDigits(pan).filter { it.isDigit() }
        if (clean.length != 16) return false
        var sum = 0
        for (i in 0 until 16) {
            var d = clean[i] - '0'
            if (i % 2 == 0) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return sum % 10 == 0
    }

    /** Normalizes a SHEBA string to canonical uppercase ASCII without spaces or hyphens. */
    fun cleanSheba(sheba: String): String {
        return toAsciiDigits(sheba).replace(" ", "").replace("-", "").uppercase()
    }

    /** ISO 7064 Mod 97-10 validator for Iranian SHEBA / IBAN numbers. */
    fun isValidSheba(sheba: String): Boolean {
        val clean = cleanSheba(sheba)
        if (!clean.matches(Regex("^IR[0-9]{24}$"))) return false
        val rearranged = clean.substring(4) + "1827" + clean.substring(2, 4)
        var remainder = 0
        for (c in rearranged) {
            remainder = (remainder * 10 + (c - '0')) % 97
        }
        return remainder == 1
    }

    /** Formats a 26-character SHEBA into standardized spaced blocks: IRxx  xxxx  xxxx  xxxx  xxxx  xxxx  xx. */
    fun formatSheba(sheba: String): String {
        val clean = cleanSheba(sheba)
        if (!clean.startsWith("IR")) return sheba
        val digits = clean.removePrefix("IR")
        val chunks = mutableListOf("IR" + digits.take(2))
        var remaining = digits.drop(2)
        while (remaining.isNotEmpty()) {
            chunks.add(remaining.take(4))
            remaining = remaining.drop(4)
        }
        return chunks.joinToString("  ")
    }
}
