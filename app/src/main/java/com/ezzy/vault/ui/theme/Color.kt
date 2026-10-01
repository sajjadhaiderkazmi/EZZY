package com.ezzy.vault.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// EZZY's own palette: "lime noir". A near-black olive ink with an electric lime accent and soft
// pastel companions (mint, lavender, butter) — bold enough that the primary action on every
// screen is unmistakable, and calm enough around it that a vault full of numbers stays easy to
// read. The light scheme keeps the same hues on a warm off-white.

/** The lime used for the big call-to-action buttons and the hero card in both themes. */
val EzzyLime = Color(0xFFC8F25A)

/** What is written on top of [EzzyLime] — always the dark ink, never white. */
val EzzyOnLime = Color(0xFF151A04)

internal val EzzyLightColors = lightColorScheme(
    primary = Color(0xFF4B6A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6F77E),
    onPrimaryContainer = Color(0xFF151F00),
    inversePrimary = Color(0xFFB4DC45),

    secondary = Color(0xFF55624A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE9CB),
    onSecondaryContainer = Color(0xFF131F0B),

    tertiary = Color(0xFF5C4DB0),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE6DEFF),
    onTertiaryContainer = Color(0xFF1A0A63),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF5F6EF),
    onBackground = Color(0xFF181B13),
    surface = Color(0xFFF5F6EF),
    onSurface = Color(0xFF181B13),
    surfaceVariant = Color(0xFFE1E5D5),
    onSurfaceVariant = Color(0xFF52584A),
    surfaceTint = Color(0xFF4B6A00),
    inverseSurface = Color(0xFF1C1F17),
    inverseOnSurface = Color(0xFFF0F2E6),

    outline = Color(0xFF7A8070),
    outlineVariant = Color(0xFFD2D7C5),
    scrim = Color(0xFF000000),

    surfaceBright = Color(0xFFF5F6EF),
    surfaceDim = Color(0xFFD9DCCF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEDEFE5),
    surfaceContainerHigh = Color(0xFFE6E9DD),
    surfaceContainerHighest = Color(0xFFDFE3D4),
)

internal val EzzyDarkColors = darkColorScheme(
    primary = EzzyLime,
    onPrimary = EzzyOnLime,
    primaryContainer = Color(0xFF34420F),
    onPrimaryContainer = Color(0xFFE2FB9E),
    inversePrimary = Color(0xFF4B6A00),

    secondary = Color(0xFFBFD0A8),
    onSecondary = Color(0xFF29341D),
    secondaryContainer = Color(0xFF2E3626),
    onSecondaryContainer = Color(0xFFDDE9CB),

    tertiary = Color(0xFFC9BCFF),
    onTertiary = Color(0xFF2B1C7C),
    tertiaryContainer = Color(0xFF3E3478),
    onTertiaryContainer = Color(0xFFE6DEFF),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0E0F0B),
    onBackground = Color(0xFFE7EADD),
    surface = Color(0xFF0E0F0B),
    onSurface = Color(0xFFE7EADD),
    surfaceVariant = Color(0xFF3A3F33),
    onSurfaceVariant = Color(0xFFB7BDA9),
    surfaceTint = EzzyLime,
    inverseSurface = Color(0xFFE7EADD),
    inverseOnSurface = Color(0xFF1C1F17),

    outline = Color(0xFF858B7A),
    outlineVariant = Color(0xFF353A2E),
    scrim = Color(0xFF000000),

    surfaceBright = Color(0xFF34382D),
    surfaceDim = Color(0xFF0E0F0B),
    surfaceContainerLowest = Color(0xFF090A07),
    surfaceContainerLow = Color(0xFF171913),
    surfaceContainer = Color(0xFF1C1F17),
    surfaceContainerHigh = Color(0xFF25291F),
    surfaceContainerHighest = Color(0xFF2F3428),
)

/** A category accent, with a variant for each theme so contrast holds either way. */
data class AccentColor(
    val key: String,
    val label: String,
    val light: Color,
    val dark: Color,
)

object Accents {

    val all: List<AccentColor> = listOf(
        AccentColor("indigo", "Indigo", Color(0xFF5B5BD6), Color(0xFFA5A4FB)),
        AccentColor("lime", "Lime", Color(0xFF557A00), Color(0xFFC8F25A)),
        AccentColor("blue", "Blue", Color(0xFF2C6FDD), Color(0xFF89B7FF)),
        AccentColor("teal", "Teal", Color(0xFF0E8F81), Color(0xFF54D3C2)),
        AccentColor("green", "Green", Color(0xFF2A8A4E), Color(0xFF74D69A)),
        AccentColor("amber", "Amber", Color(0xFFB07C08), Color(0xFFF0C24B)),
        AccentColor("orange", "Orange", Color(0xFFC4581B), Color(0xFFFFA26B)),
        AccentColor("red", "Red", Color(0xFFC4342F), Color(0xFFFF9490)),
        AccentColor("pink", "Pink", Color(0xFFBE3A73), Color(0xFFF991BA)),
        AccentColor("purple", "Purple", Color(0xFF7C4DD1), Color(0xFFC3A7FF)),
        AccentColor("slate", "Slate", Color(0xFF566275), Color(0xFFA9B6C8)),
    )

    private val byKey = all.associateBy { it.key }

    fun of(key: String?): AccentColor = byKey[key] ?: all.first()

    fun color(key: String?, dark: Boolean): Color = of(key).let { if (dark) it.dark else it.light }
}

/**
 * The two hues for the entry banner — a deep olive ink running into a darker green, so the
 * lime writing and white text on it read at well over 7:1 in either theme, and the banner
 * matches the hero card on Home.
 */
@Composable
fun brandBannerColors(): List<Color> = if (LocalIsDarkTheme.current) {
    listOf(Color(0xFF2A3510), Color(0xFF14170F))
} else {
    listOf(Color(0xFF3D5600), Color(0xFF1E2A05))
}

/**
 * A soft pastel wash of a category's accent, for big tinted cards (the reference look: mint,
 * lavender, butter tiles). Kept low-alpha over the surface so text on it stays the normal
 * on-surface colour in both themes.
 */
@Composable
fun accentWash(colorKey: String?): Color {
    val dark = LocalIsDarkTheme.current
    return Accents.color(colorKey, dark).copy(alpha = if (dark) 0.16f else 0.13f)
}
