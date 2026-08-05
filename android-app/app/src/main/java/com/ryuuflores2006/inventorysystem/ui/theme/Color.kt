package com.ryuuflores2006.inventorysystem.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * Premium, modern dark aesthetic with glassmorphism and vibrant accents.
 */

// Surfaces (Deep blue/slate for glassmorphism)
val DeepSpace = Color(0xFF070B14)      // App background (Dark)
val GlassBase = Color(0xFF131A2A)      // Bars (Dark)
val GlassSurface = Color(0x661E293B)   // Translucent cards (Dark)
val GlassSurfaceRaised = Color(0x992B3A55) // Inputs (Dark)
val GlassBorder = Color(0x33FFFFFF)    // Borders (Dark)

// Light Mode Surfaces
val LightBackground = Color(0xFFF8FAFC)      // App background (Light)
val LightBase = Color(0xFFF1F5F9)      // Bars (Light)
val LightSurface = Color(0xCCFFFFFF)   // Translucent cards (Light)
val LightSurfaceRaised = Color(0xFFFFFFFF) // Inputs (Light)
val LightBorder = Color(0x33000000)    // Borders (Light)

// Text
val Chalk = Color(0xFFF8FAFC) // Light text for dark mode
val Ink = Color(0xFF0F172A)   // Dark text for light mode
val Ash = Color(0xFF94A3B8)
val Slate = Color(0xFF64748B)

// Accents (Vibrant)
val Cyan = Color(0xFF00F0FF)
val CyanDeep = Color(0xFF00B7C4)
val CyanSoft = Color(0xFF80F8FF)

// Status
val Emerald = Color(0xFF00FF9D)
val EmeraldDark = Color(0xFF00C77B)
val Amber = Color(0xFFFFB800)
val AmberDark = Color(0xFFD99B00)
val Rose = Color(0xFFFF2A55)
val RoseDark = Color(0xFFD61A40)
val Azure = Color(0xFF0084FF)
val AzureDark = Color(0xFF0066CC)
val Violet = Color(0xFFB147FF)
val VioletDark = Color(0xFF8A30D6)

/** Chip colour for a device status (`gadget_status_type`). */
@Composable
fun gadgetStatusColor(status: String): Color {
    val isDark = isSystemInDarkTheme()
    return when (status) {
        "In Stock" -> if (isDark) Emerald else EmeraldDark
        "Reserved" -> if (isDark) Amber else AmberDark
        "In Transit" -> if (isDark) Azure else AzureDark
        "Sold" -> Slate
        "Returned" -> if (isDark) Rose else RoseDark
        else -> Ash
    }
}

/** Chip colour for a repair status (`ticket_status_type`). */
@Composable
fun ticketStatusColor(status: String): Color {
    val isDark = isSystemInDarkTheme()
    return when (status) {
        "Pending" -> if (isDark) Amber else AmberDark
        "Diagnosing" -> if (isDark) Azure else AzureDark
        "Waiting for Parts" -> if (isDark) Rose else RoseDark
        "Repairing" -> if (isDark) Violet else VioletDark
        "Ready" -> if (isDark) Emerald else EmeraldDark
        "Completed" -> Slate
        else -> Ash
    }
}
