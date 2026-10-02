package com.ezzy.vault.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// EZZY's own palette: "Ocean Blue". White cards and near-black text on a cool off-white, with
// one calm blue for icons, links and the main buttons. The dark theme is a deep navy-black with a
// softer, brighter blue so it stays easy on the eyes at night.

/**
 * The app's accent blue: icons, links and the main buttons in both themes. The name is kept
 * from the earlier green palette so every screen follows the one accent without a rename.
 */
val EzzyGreen = Color(0xFF2563EB)

/** A lighter blue for accent-on-dark: icons and text on the dark theme's surfaces. */
val EzzyGreenBright = Color(0xFF7EA6FF)

/**
 * The fill of the main call-to-action buttons. The name is kept so every button across the app
 * follows the one palette.
 */
val EzzyLime = EzzyGreen

/** What is written on top of [EzzyLime]: white on the blue. */
val EzzyOnLime = Color(0xFFFFFFFF)

internal val EzzyLightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2EBFF),
    onPrimaryContainer = Color(0xFF0A2A6B),
    inversePrimary = Color(0xFF7EA6FF),

    secondary = Color(0xFF4F5B70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E7F1),
    onSecondaryContainer = Color(0xFF141B27),

    tertiary = Color(0xFF5C4DB0),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE6DEFF),
    onTertiaryContainer = Color(0xFF1A0A63),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF3F5F9),
    onBackground = Color(0xFF141821),
    surface = Color(0xFFF3F5F9),
    onSurface = Color(0xFF141821),
    surfaceVariant = Color(0xFFE3E8F0),
    onSurfaceVariant = Color(0xFF5F6779),
    surfaceTint = Color(0xFF2563EB),
    inverseSurface = Color(0xFF1C2230),
    inverseOnSurface = Color(0xFFEEF2F8),

    outline = Color(0xFF858DA0),
    outlineVariant = Color(0xFFE1E6EF),
    scrim = Color(0xFF000000),

    surfaceBright = Color(0xFFF3F5F9),
    surfaceDim = Color(0xFFD9DEE7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEEF1F6),
    surfaceContainerHigh = Color(0xFFE8ECF3),
    surfaceContainerHighest = Color(0xFFE1E6EE),
)

internal val EzzyDarkColors = darkColorScheme(
    primary = Color(0xFF7EA6FF),
    onPrimary = Color(0xFF0A1A3D),
    primaryContainer = Color(0xFF1B2842),
    onPrimaryContainer = Color(0xFFD6E3FF),
    inversePrimary = Color(0xFF2563EB),

    secondary = Color(0xFFB9C3D6),
    onSecondary = Color(0xFF243044),
    secondaryContainer = Color(0xFF222A38),
    onSecondaryContainer = Color(0xFFDCE3F0),

    tertiary = Color(0xFFC9BCFF),
    onTertiary = Color(0xFF2B1C7C),
    tertiaryContainer = Color(0xFF3E3478),
    onTertiaryContainer = Color(0xFFE6DEFF),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0D1017),
    onBackground = Color(0xFFEDF0F6),
    surface = Color(0xFF0D1017),
    onSurface = Color(0xFFEDF0F6),
    surfaceVariant = Color(0xFF262D3A),
    onSurfaceVariant = Color(0xFF939BAD),
    surfaceTint = Color(0xFF7EA6FF),
    inverseSurface = Color(0xFFE3E8F1),
    inverseOnSurface = Color(0xFF1A202B),

    outline = Color(0xFF788197),
    outlineVariant = Color(0xFF252D3B),
    scrim = Color(0xFF000000),

    surfaceBright = Color(0xFF2D3443),
    surfaceDim = Color(0xFF0D1017),
    surfaceContainerLowest = Color(0xFF090B10),
    surfaceContainerLow = Color(0xFF161B25),
    surfaceContainer = Color(0xFF1B212C),
    surfaceContainerHigh = Color(0xFF222935),
    surfaceContainerHighest = Color(0xFF2A3240),
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
