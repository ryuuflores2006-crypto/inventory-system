package com.ryuuflores2006.inventorysystem.ui.components

import java.util.Locale

/** Peso amounts are shown the same way on every screen and on the dashboard. */
fun peso(amount: Double): String {
    val formatted = String.format(Locale.US, "%,.2f", amount)
    return "₱" + if (formatted.endsWith(".00")) formatted.dropLast(3) else formatted
}

/** `2026-08-04T08:50:23.123456+00:00` → `2026-08-04 08:50`. Falls back to the raw string. */
fun shortStamp(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    val date = iso.substringBefore('T')
    val time = iso.substringAfter('T', "").take(5)
    return if (time.isBlank()) date else "$date $time"
}
