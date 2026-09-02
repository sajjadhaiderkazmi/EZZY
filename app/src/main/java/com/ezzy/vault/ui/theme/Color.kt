package com.ezzy.vault.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// EZZY's own palette: an indigo core with a mint accent, tuned so that the overlay reads
// clearly on top of whatever app happens to be underneath it.

internal val EzzyLightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E0FF),
    onPrimaryContainer = Color(0xFF120C4B),
    inversePrimary = Color(0xFFC0C1FF),

    secondary = Color(0xFF5B5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),

    tertiary = Color(0xFF006C51),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF8CF7CE),
    onTertiaryContainer = Color(0xFF002117),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceTint = Color(0xFF4F46E5),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF3EFF7),

    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0),
    scrim = Color(0xFF000000),

    surfaceBright = Color(0xFFFBF8FF),
    surfaceDim = Color(0xFFDBD9E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE3E1E9),
)

internal val EzzyDarkColors = darkColorScheme(
    primary = Color(0xFFC0C1FF),
    onPrimary = Color(0xFF24219B),
    primaryContainer = Color(0xFF3B37B4),
    onPrimaryContainer = Color(0xFFE2E0FF),
    inversePrimary = Color(0xFF4F46E5),

    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434559),
    onSecondaryContainer = Color(0xFFE0E1F9),

    tertiary = Color(0xFF6FDBB4),
    onTertiary = Color(0xFF003829),
    tertiaryContainer = Color(0xFF00513B),
    onTertiaryContainer = Color(0xFF8CF7CE),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF131318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceTint = Color(0xFFC0C1FF),
    inverseSurface = Color(0xFFE4E1E9),
    inverseOnSurface = Color(0xFF303036),

    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF46464F),
    scrim = Color(0xFF000000),

    surfaceBright = Color(0xFF39383F),
    surfaceDim = Color(0xFF131318),
    surfaceContainerLowest = Color(0xFF0E0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF2A2A30),
    surfaceContainerHighest = Color(0xFF35343B),
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
