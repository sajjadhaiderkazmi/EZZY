package com.ezzy.vault.util

/** Which screen edge a trigger strip is pinned to. */
enum class GestureEdge { BOTTOM, TOP }

/** The way a swipe travels, once the dominant axis has been decided. */
enum class SwipeDirection { UP, DOWN }

/** Swipes need travel; taps need stillness. The detector treats them differently. */
enum class GestureKind { SWIPE, TAP, DOUBLE_TAP }

/**
 * The gestures EZZY will listen for.
 *
 * Finger count is most of the safety story. Three fingers are claimed by the screenshot
 * shortcut on Xiaomi, Samsung, Oppo, Realme, Vivo, Honor and OnePlus, and by app switching on a
 * few others, so they are deliberately absent. One finger belongs to Back, Home and the
 * notification shade. Two and four are unclaimed on every skin — the one exception being a
 * two-finger pull inside the status bar, which the top strip stays clear of.
 *
 * Side edges are not offered at all: a strip there sits exactly where apps expect their own
 * horizontal scrolling and edge swipes, and the two fight.
 */
enum class Gesture(
    val fingers: Int,
    val kind: GestureKind,
    val direction: SwipeDirection?,
    val edge: GestureEdge,
    val label: String,
    val hint: String,
) {
    TWO_UP(
        fingers = 2,
        kind = GestureKind.SWIPE,
        direction = SwipeDirection.UP,
        edge = GestureEdge.BOTTOM,
        label = "2 fingers · swipe up",
        hint = "From the strip just above the navigation bar",
    ),
    FOUR_UP(
        fingers = 4,
        kind = GestureKind.SWIPE,
        direction = SwipeDirection.UP,
        edge = GestureEdge.BOTTOM,
        label = "4 fingers · swipe up",
        hint = "From the strip just above the navigation bar",
    ),
    TWO_DOWN(
        fingers = 2,
        kind = GestureKind.SWIPE,
        direction = SwipeDirection.DOWN,
        edge = GestureEdge.TOP,
        label = "2 fingers · swipe down",
        hint = "From the strip below the status bar",
    ),
    FOUR_DOWN(
        fingers = 4,
        kind = GestureKind.SWIPE,
        direction = SwipeDirection.DOWN,
        edge = GestureEdge.TOP,
        label = "4 fingers · swipe down",
        hint = "From the strip below the status bar",
    ),
    FOUR_TAP(
        fingers = 4,
        kind = GestureKind.TAP,
        direction = null,
        edge = GestureEdge.BOTTOM,
        label = "4 fingers · tap",
        hint = "One tap with four fingers on the bottom strip. No phone uses this.",
    ),
    TWO_DOUBLE_TAP(
        fingers = 2,
        kind = GestureKind.DOUBLE_TAP,
        direction = null,
        edge = GestureEdge.BOTTOM,
        label = "2 fingers · double tap",
        hint = "Two quick taps with two fingers on the bottom strip",
    ),
    ;

    companion object {
        val default: Set<Gesture> = setOf(TWO_UP)

        fun from(names: Set<String>?): Set<Gesture> =
            names?.mapNotNull { name -> entries.firstOrNull { it.name == name } }?.toSet()
                ?: default
    }
}

/**
 * How much of an edge a strip covers. Anything under a strip stops reaching the app beneath it,
 * so a shorter strip leaves more of the screen — corners and bottom navigation especially —
 * behaving normally.
 */
enum class StripLength(val fraction: Float, val label: String) {
    SHORT(0.40f, "Short — leaves the corners free"),
    MEDIUM(0.70f, "Medium — recommended"),
    FULL(1.00f, "Full edge — easiest to hit"),
}
