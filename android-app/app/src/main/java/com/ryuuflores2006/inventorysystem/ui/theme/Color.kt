package com.ryuuflores2006.inventorysystem.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Premium, modern dark aesthetic with glassmorphism and vibrant accents.
 */

// Surfaces (Deep blue/slate for glassmorphism)
val DeepSpace = Color(0xFF070B14)      // App background
val GlassBase = Color(0xFF131A2A)      // Bars
val GlassSurface = Color(0x661E293B)   // Translucent cards
val GlassSurfaceRaised = Color(0x992B3A55) // Inputs
val GlassBorder = Color(0x33FFFFFF)    // Borders

// Text
val Chalk = Color(0xFFF8FAFC)
val Ash = Color(0xFF94A3B8)
val Slate = Color(0xFF64748B)

// Accents (Vibrant)
val Cyan = Color(0xFF00F0FF)
val CyanDeep = Color(0xFF00B7C4)
val CyanSoft = Color(0xFF80F8FF)

// Status
val Emerald = Color(0xFF00FF9D)
val Amber = Color(0xFFFFB800)
val Rose = Color(0xFFFF2A55)
val Azure = Color(0xFF0084FF)
val Violet = Color(0xFFB147FF)

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
