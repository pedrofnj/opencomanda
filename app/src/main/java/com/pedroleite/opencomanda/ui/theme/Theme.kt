package com.pedroleite.opencomanda.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Role mapping used across the app:
// - primary (teal): the main action of a screen — Nova comanda, Fechar conta, Confirmar.
// - tertiary (saffron): Quick Sale, and attention badges (low stock).
// - inverseSurface (graphite): "money" hero cards — today's takings, cash in the drawer, the sale bar.
// - surfaceContainerLowest: cards and lists on the paper ground.
private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = CardWhite,
    primaryContainer = TealSoft,
    onPrimaryContainer = TealDeep,
    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = PaperDeep,
    onSecondaryContainer = Ink,
    tertiary = Saffron,
    onTertiary = Ink,
    tertiaryContainer = SaffronSoft,
    onTertiaryContainer = SaffronDeep,
    error = Rust,
    onError = CardWhite,
    errorContainer = RustSoft,
    onErrorContainer = Rust,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = InkMuted,
    surfaceTint = Teal,
    surfaceBright = CardWhite,
    surfaceDim = PaperDeep,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = CardWhite,
    surfaceContainer = PaperDim,
    surfaceContainerHigh = PaperDim,
    surfaceContainerHighest = PaperDeep,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    inversePrimary = TealLight,
    outline = HairlineStrong,
    outlineVariant = Hairline,
    scrim = Ink,
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = TealOnLight,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealSoft,
    secondary = Mist,
    onSecondary = Night,
    secondaryContainer = NightRaised,
    onSecondaryContainer = Mist,
    tertiary = Saffron,
    onTertiary = Ink,
    tertiaryContainer = SaffronContainerDark,
    onTertiaryContainer = SaffronSoft,
    error = RustLight,
    onError = RustContainerDark,
    errorContainer = RustContainerDark,
    onErrorContainer = RustSoft,
    background = Night,
    onBackground = Mist,
    surface = Night,
    onSurface = Mist,
    surfaceVariant = NightRaised,
    onSurfaceVariant = MistMuted,
    surfaceTint = TealLight,
    surfaceBright = NightRaised,
    surfaceDim = Night,
    surfaceContainerLowest = NightCard,
    surfaceContainerLow = NightCard,
    surfaceContainer = NightRaised,
    surfaceContainerHigh = NightRaised,
    surfaceContainerHighest = NightLine,
    inverseSurface = Mist,
    inverseOnSurface = Ink,
    inversePrimary = Teal,
    outline = MistMuted,
    outlineVariant = NightLine,
    scrim = Night,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * OpenComanda's own look. Android's dynamic (wallpaper) colors are deliberately not used: the app's
 * palette carries meaning — saffron for Quick Sale, graphite for money — that a wallpaper-derived
 * scheme would erase.
 */
@Composable
fun OpenComandaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
