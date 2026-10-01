package com.ezzy.vault.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// EZZY's own palette: "lime noir". A near-black olive ink with an electric lime accent and soft
// pastel companions (mint, lavender, butter) — bold enough that the primary action on every
// screen is unmistakable, and calm enough around it that a vault full of numbers stays easy to
// read. The light scheme keeps the same hues on a warm off-white.

/** The app's green: icons, links and the main buttons in both themes. */
val EzzyGreen = Color(0xFF2F8A3B)

/** A lighter green for green-on-dark: icons and text on the dark theme's surfaces. */
val EzzyGreenBright = Color(0xFF7BD37E)

/**
 * The fill of the main call-to-action buttons. Once a lime, now the app's green — the name is
 * kept so every button across the app follows the one palette.
 */
val EzzyLime = EzzyGreen

/** What is written on top of [EzzyLime]: white on the green. */
val EzzyOnLime = Color(0xFFFFFFFF)

internal val EzzyLightColors = lightColorScheme(
    primary = Color(0xFF2F8A3B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4F3DF),
    onPrimaryContainer = Color(0xFF0F3317),
    inversePrimary = Color(0xFF7BD37E),

    secondary = Color(0xFF52604E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3EADD),
    onSecondaryContainer = Color(0xFF161E13),

    tertiary = Color(0xFF5C4DB0),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE6DEFF),
    onTertiaryContainer = Color(0xFF1A0A63),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF4F5F1),
    onBackground = Color(0xFF121410),
    surface = Color(0xFFF4F5F1),
    onSurface = Color(0xFF121410),
    surfaceVariant = Color(0xFFE6E8E1),
    onSurfaceVariant = Color(0xFF5F6558),
    surfaceTint = Color(0xFF2F8A3B),
    inverseSurface = Color(0xFF1C1F17),
    inverseOnSurface = Color(0xFFF0F2E6),

    outline = Color(0xFF8A9082),
    outlineVariant = Color(0xFFE3E6DD),
    scrim = Color(0xFF000000),

    surfaceBright = Color(0xFFF4F5F1),
    surfaceDim = Color(0xFFDCDED6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEFF0EB),
    surfaceContainerHigh = Color(0xFFE9EBE4),
    surfaceContainerHighest = Color(0xFFE2E5DC),
)

internal val EzzyDarkColors = darkColorScheme(
    primary = Color(0xFF7BD37E),
    onPrimary = Color(0xFF0E100D),
    primaryContainer = Color(0xFF1E2E1C),
    onPrimaryContainer = Color(0xFFCFEFCB),
    inversePrimary = Color(0xFF2F8A3B),

    secondary = Color(0xFFBCC8B4),
    onSecondary = Color(0xFF29341D),
    secondaryContainer = Color(0xFF262C22),
    onSecondaryContainer = Color(0xFFDDE6D6),

    tertiary = Color(0xFFC9BCFF),
    onTertiary = Color(0xFF2B1C7C),
    tertiaryContainer = Color(0xFF3E3478),
    onTertiaryContainer = Color(0xFFE6DEFF),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0E100D),
    onBackground = Color(0xFFF1F3EC),
    surface = Color(0xFF0E100D),
    onSurface = Color(0xFFF1F3EC),
    surfaceVariant = Color(0xFF2A2E26),
    onSurfaceVariant = Color(0xFF9EA496),
    surfaceTint = Color(0xFF7BD37E),
    inverseSurface = Color(0xFFE7EADD),
    inverseOnSurface = Color(0xFF1C1F17),

    outline = Color(0xFF7E8476),
    outlineVariant = Color(0xFF272B23),
    scrim = Color(0xFF000000),

    surfaceBright = Color(0xFF30352C),
    surfaceDim = Color(0xFF0E100D),
    surfaceContainerLowest = Color(0xFF090A08),
    surfaceContainerLow = Color(0xFF1A1D18),
    surfaceContainer = Color(0xFF1F221C),
    surfaceContainerHigh = Color(0xFF262A23),
    surfaceContainerHighest = Color(0xFF2E332A),
)

/** A category accent, with a variant for each theme so contrast holds either way. */
data class AccentColor(
    val key: String,
    val label: String,
    val light: Color,
    val dark: Color,
    /** The bright pastel a big card of this colour is filled with on the light theme. */
    val cardLight: Color,
    /** The same card on the dark theme: a deep jewel tone, rich but easy on the eyes. */
    val cardDark: Color,
)

object Accents {

    val all: List<AccentColor> = listOf(
        AccentColor("indigo", "Indigo", Color(0xFF5B5BD6), Color(0xFFA5A4FB), Color(0xFFDCDBFF), Color(0xFF3D3B95)),
        AccentColor("lime", "Lime", Color(0xFF557A00), Color(0xFFC8F25A), Color(0xFFE4F9A8), Color(0xFF4A5E12)),
        AccentColor("blue", "Blue", Color(0xFF2C6FDD), Color(0xFF89B7FF), Color(0xFFD2E3FF), Color(0xFF1F4F98)),
        AccentColor("teal", "Teal", Color(0xFF0E8F81), Color(0xFF54D3C2), Color(0xFFC6F1EA), Color(0xFF0F6B62)),
        AccentColor("green", "Green", Color(0xFF2A8A4E), Color(0xFF74D69A), Color(0xFFCDF1D9), Color(0xFF1C6B41)),
        AccentColor("amber", "Amber", Color(0xFFB07C08), Color(0xFFF0C24B), Color(0xFFFFEAB0), Color(0xFF9C6410)),
        AccentColor("orange", "Orange", Color(0xFFC4581B), Color(0xFFFFA26B), Color(0xFFFFDCC6), Color(0xFF8A4018)),
        AccentColor("red", "Red", Color(0xFFC4342F), Color(0xFFFF9490), Color(0xFFFFD7D4), Color(0xFF8C2E2E)),
        AccentColor("pink", "Pink", Color(0xFFBE3A73), Color(0xFFF991BA), Color(0xFFFFD6E8), Color(0xFF8A2D5C)),
        AccentColor("purple", "Purple", Color(0xFF7C4DD1), Color(0xFFC3A7FF), Color(0xFFE8DCFF), Color(0xFF5B3C9E)),
        AccentColor("slate", "Slate", Color(0xFF566275), Color(0xFFA9B6C8), Color(0xFFDDE3EC), Color(0xFF3A4659)),
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

/** The dark ink written on accent cards on the light theme. */
val AccentInk = Color(0xFF15170F)

/**
 * The fill of a section's big card. Every card in the app is now the same plain white card
 * (dark grey on the dark theme) with black writing and a green icon, so this is the card
 * surface whatever the section's colour.
 */
@Composable
fun accentCard(@Suppress("UNUSED_PARAMETER") colorKey: String?): Color =
    androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow

/** What is written on an [accentCard]: the normal text colour. */
@Composable
fun accentOnCard(): Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface

/** The little count / label pills sitting on an [accentCard]. */
@Composable
fun accentChip(): Color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer

/** Kept for the cards that used to carry a sheen: the plain white cards need none. */
@Composable
fun accentSheen(): Brush = Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))

/**
 * A soft pastel wash of a category's accent, for quieter tinted surfaces. Kept low-alpha over
 * the surface so text on it stays the normal on-surface colour in both themes.
 */
@Composable
fun accentWash(colorKey: String?): Color {
    val dark = LocalIsDarkTheme.current
    return Accents.color(colorKey, dark).copy(alpha = if (dark) 0.16f else 0.13f)
}
