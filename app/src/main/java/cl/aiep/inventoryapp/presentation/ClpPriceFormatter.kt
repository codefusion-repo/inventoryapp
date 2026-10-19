package cl.aiep.inventoryapp.presentation

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Formato visual de pesos chilenos: sin decimales y con punto para miles. */
fun formatClp(value: Long): String {
    val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    val formatter = DecimalFormat("#,##0", symbols).apply {
        isGroupingUsed = true
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }
    return "\$" + formatter.format(value)
}
