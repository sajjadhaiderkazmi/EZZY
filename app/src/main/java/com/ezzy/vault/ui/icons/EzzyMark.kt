package com.ezzy.vault.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * EZZY's own bolt, the same outline as the launcher icon.
 *
 * The app used Material's generic bolt glyph everywhere, so the launcher, the floating button
 * and the lock screen never quite matched. This is one shape, drawn once, used by all three.
 */
object EzzyMark {

    /** The brand ground the mark sits on — the launcher icon's background colour. */
    val Brand = Color(0xFF3A26B4)

    val Bolt: ImageVector by lazy {
        ImageVector.Builder(
            name = "EzzyBolt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(13.8f, 3.6f)
                lineTo(7.5f, 12.9f)
                lineTo(11.41f, 12.9f)
                lineTo(10.2f, 20.4f)
                lineTo(16.5f, 11.1f)
                lineTo(12.59f, 11.1f)
                close()
            }
        }.build()
    }
}
