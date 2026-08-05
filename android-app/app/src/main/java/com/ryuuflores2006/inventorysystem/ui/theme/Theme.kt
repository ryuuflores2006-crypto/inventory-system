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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.lightColorScheme

/**
 * Premium, modern aesthetic with glassmorphism and vibrant accents.
 * Now supports both Light and Dark mode.
 */
private val AppDarkColorScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = DeepSpace,
    primaryContainer = CyanDeep,
    onPrimaryContainer = Chalk,

    secondary = CyanSoft,
    onSecondary = DeepSpace,
    secondaryContainer = GlassSurfaceRaised,
    onSecondaryContainer = Chalk,

    tertiary = Violet,
    onTertiary = Chalk,

    background = DeepSpace,
    onBackground = Chalk,

    surface = GlassSurface,
    onSurface = Chalk,
    surfaceVariant = GlassSurfaceRaised,
    onSurfaceVariant = Ash,
    surfaceContainer = GlassSurface,
    surfaceContainerHigh = GlassSurfaceRaised,
    surfaceContainerHighest = GlassSurfaceRaised,

    error = Rose,
    onError = Chalk,
    errorContainer = GlassSurfaceRaised,
    onErrorContainer = Rose,

    outline = GlassBorder,
    outlineVariant = GlassSurfaceRaised,
    scrim = DeepSpace
)

private val AppLightColorScheme = lightColorScheme(
    primary = AzureDark,
    onPrimary = LightBackground,
    primaryContainer = CyanDeep,
    onPrimaryContainer = Chalk,

    secondary = Azure,
    onSecondary = LightBackground,
    secondaryContainer = LightSurfaceRaised,
    onSecondaryContainer = Ink,

    tertiary = VioletDark,
    onTertiary = Chalk,

    background = LightBackground,
    onBackground = Ink,

    surface = LightSurface,
    onSurface = Ink,
    surfaceVariant = LightSurfaceRaised,
    onSurfaceVariant = Slate,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceRaised,
    surfaceContainerHighest = LightSurfaceRaised,

    error = RoseDark,
    onError = Chalk,
    errorContainer = LightSurfaceRaised,
    onErrorContainer = RoseDark,

    outline = LightBorder,
    outlineVariant = LightSurfaceRaised,
    scrim = LightBackground
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun InventorySystemTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AppDarkColorScheme else AppLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = if (darkTheme) DeepSpace.toArgb() else LightBackground.toArgb()
            window.navigationBarColor = if (darkTheme) GlassBase.toArgb() else LightBase.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
