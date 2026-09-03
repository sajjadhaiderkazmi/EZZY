package com.ezzy.vault.util

/**
 * The two gestures EZZY listens for.
 *
 * Finger count is the safety story. Three fingers are the screenshot shortcut on Xiaomi,
 * Samsung, Oppo, Realme, Vivo, Honor and OnePlus. One finger belongs to Back, Home and the
 * notification shade. Two and four are unclaimed on every skin.
 */
enum class Gesture(val label: String, val hint: String) {
    TWO_DOUBLE_TAP(
        label = "2 fingers · double tap",
        hint = "Two quick taps with two fingers",
    ),
    FOUR_DOWN(
        label = "4 fingers · swipe down",
        hint = "Four fingers, swipe downwards",
    ),
    ;

    companion object {
        val default = FOUR_DOWN

        fun from(name: String?): Gesture = entries.firstOrNull { it.name == name } ?: default
    }
}

/**
 * How much of the screen watches for the gesture.
 *
 * Android has no way to look at a touch and still let it reach the app underneath — seeing it
 * is taking it — so a whole-screen listener would swallow every tap on the phone, EZZY's own
 * screens included. The listening area is therefore a band across the bottom of the screen, and
 * this is how tall it gets to be: taller is easier to hit, smaller leaves more of the app below
 * working normally.
 */
enum class GestureArea(val fraction: Float, val label: String) {
    SMALL(0.16f, "Small — a band above the navigation bar"),
    THIRD(0.33f, "Bottom third — recommended"),
    HALF(0.50f, "Bottom half — easiest to hit"),
}
