package com.ryuuflores2006.inventorysystem.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One palette, shared with the PC dashboard so both platforms look like the
 * same product: slate greys, a cyan accent, and semantic status colours.
 */

// Surfaces
val Ink900 = Color(0xFF060A13) // app background
val Ink800 = Color(0xFF0B111E) // bars
val Ink700 = Color(0xFF121A2A) // cards
val Ink600 = Color(0xFF1B2537) // raised surfaces, inputs
val Ink500 = Color(0xFF27334A) // borders, dividers

// Text
val Chalk = Color(0xFFF8FAFC)
val Ash = Color(0xFF94A3B8)
val Slate = Color(0xFF64748B)

// Brand
val Cyan = Color(0xFF06B6D4)
val CyanDeep = Color(0xFF0891B2)
val CyanSoft = Color(0xFF67E8F9)

// Status
val Emerald = Color(0xFF10B981)
val Amber = Color(0xFFF59E0B)
val Rose = Color(0xFFEF4444)
val Azure = Color(0xFF3B82F6)
val Violet = Color(0xFF8B5CF6)

/** Chip colour for a device status (`gadget_status_type`). */
fun gadgetStatusColor(status: String): Color = when (status) {
    "In Stock" -> Emerald
    "Reserved" -> Amber
    "In Transit" -> Azure
    "Sold" -> Slate
    "Returned" -> Rose
    else -> Ash
}

/** Chip colour for a repair status (`ticket_status_type`). */
fun ticketStatusColor(status: String): Color = when (status) {
    "Pending" -> Amber
    "Diagnosing" -> Azure
    "Waiting for Parts" -> Rose
    "Repairing" -> Violet
    "Ready" -> Emerald
    "Completed" -> Slate
    else -> Ash
}
