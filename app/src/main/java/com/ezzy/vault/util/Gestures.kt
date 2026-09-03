package com.ezzy.vault.util

/** Which screen edge a trigger strip is pinned to. */
enum class GestureEdge { BOTTOM, TOP, LEFT, RIGHT }

/** The way a swipe travels, once the dominant axis has been decided. */
enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

/**
 * The gestures EZZY will listen for.
 *
 * Finger count is the whole safety story. Three fingers are claimed by the screenshot shortcut
 * on Xiaomi, Samsung, Oppo, Realme, Vivo, Honor and OnePlus, and by app switching on a few
 * others, so they are deliberately absent. Two and four fingers are unclaimed on every skin —
 * the one exception being a two-finger pull inside the status bar, which the top strip stays
 * clear of. The system's own Back and Home gestures are single-finger, so a two-finger swipe
 * from an edge never competes with them.
 */
enum class Gesture(
    val fingers: Int,
    val direction: SwipeDirection,
    val edge: GestureEdge,
    val label: String,
    val hint: String,
) {
    TWO_UP(
        fingers = 2,
        direction = SwipeDirection.UP,
        edge = GestureEdge.BOTTOM,
        label = "2 fingers · swipe up",
        hint = "From the strip just above the navigation bar",
    ),
    FOUR_UP(
        fingers = 4,
        direction = SwipeDirection.UP,
        edge = GestureEdge.BOTTOM,
        label = "4 fingers · swipe up",
        hint = "From the strip just above the navigation bar",
    ),
    TWO_DOWN(
        fingers = 2,
        direction = SwipeDirection.DOWN,
        edge = GestureEdge.TOP,
        label = "2 fingers · swipe down",
        hint = "From the strip below the status bar",
    ),
    FOUR_DOWN(
        fingers = 4,
        direction = SwipeDirection.DOWN,
        edge = GestureEdge.TOP,
        label = "4 fingers · swipe down",
        hint = "From the strip below the status bar",
    ),
    TWO_FROM_RIGHT(
        fingers = 2,
        direction = SwipeDirection.LEFT,
        edge = GestureEdge.RIGHT,
        label = "2 fingers · swipe in from the right",
        hint = "Back is a one-finger gesture, so this never collides with it",
    ),
    TWO_FROM_LEFT(
        fingers = 2,
        direction = SwipeDirection.RIGHT,
        edge = GestureEdge.LEFT,
        label = "2 fingers · swipe in from the left",
        hint = "Back is a one-finger gesture, so this never collides with it",
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
