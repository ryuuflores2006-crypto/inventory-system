package com.ryuuflores2006.inventorysystem.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * The app is dark-only on purpose: it is used on a shop counter under
 * fluorescent light where a bright screen washes out, and a fixed scheme lets
 * the design rely on exact contrast values.
 */
private val AppColorScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Ink900,
    primaryContainer = CyanDeep,
    onPrimaryContainer = Chalk,

    secondary = CyanSoft,
    onSecondary = Ink900,
    secondaryContainer = Ink600,
    onSecondaryContainer = Chalk,

    tertiary = Violet,
    onTertiary = Chalk,

    background = Ink900,
    onBackground = Chalk,

    surface = Ink700,
    onSurface = Chalk,
    surfaceVariant = Ink600,
    onSurfaceVariant = Ash,
    surfaceContainer = Ink700,
    surfaceContainerHigh = Ink600,
    surfaceContainerHighest = Ink600,

    error = Rose,
    onError = Chalk,
    errorContainer = Ink600,
    onErrorContainer = Rose,

    outline = Ink500,
    outlineVariant = Ink600,
    scrim = Ink900
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun InventorySystemTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Ink900.toArgb()
            window.navigationBarColor = Ink800.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
