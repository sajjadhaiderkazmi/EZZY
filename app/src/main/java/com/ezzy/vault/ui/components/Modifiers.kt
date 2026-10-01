package com.ezzy.vault.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/**
 * Lets a full-width child spill [bleed] past its parent's side padding on both sides — for a
 * horizontal carousel inside a padded list, so its last card slides off the screen edge and
 * reads as "scroll for more" instead of being sliced off mid-page.
 */
fun Modifier.bleedHorizontally(bleed: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = (bleed * 2).roundToPx()
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = constraints.maxWidth + extra,
                maxWidth = constraints.maxWidth + extra,
            ),
        )
        layout(constraints.maxWidth, placeable.height) {
            placeable.place(-bleed.roundToPx(), 0)
        }
    }
