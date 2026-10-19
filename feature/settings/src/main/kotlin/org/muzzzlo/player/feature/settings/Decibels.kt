package org.muzzzlo.player.feature.settings

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * A decibel value for a settings row, without the unit: `0`, `+1.5`, `−6` (with a real minus sign). The decimal
 * separator follows [locale], so Russian shows `−6,5`.
 */
fun formatDecibels(
    db: Float,
    locale: Locale,
): String {
    val symbols = DecimalFormatSymbols.getInstance(locale).apply { minusSign = MINUS_SIGN }
    val format = DecimalFormat("+0.#;${MINUS_SIGN}0.#", symbols)
    return if (db == 0f) "0" else format.format(db.toDouble())
}

private const val MINUS_SIGN = '−'
