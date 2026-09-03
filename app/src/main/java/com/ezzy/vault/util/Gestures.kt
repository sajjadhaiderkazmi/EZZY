package com.ezzy.vault.util

/**
 * How the floating bar is reached.
 *
 * The two are exclusive. Always active keeps a draggable button on screen at all times — the
 * simplest, most reliable way in. On trigger keeps nothing on screen at all: the bar only
 * appears when the EZZY tile in Quick Settings is tapped, and disappears again after the chosen
 * quiet period, so it never sits on top of another app uninvited.
 */
enum class TriggerMode(val label: String, val hint: String) {
    ALWAYS_ACTIVE(
        label = "Always active",
        hint = "A small draggable button stays on screen, ready to tap",
    ),
    ON_TRIGGER(
        label = "On trigger",
        hint = "Nothing on screen until you tap the EZZY tile in Quick Settings",
    ),
    ;

    companion object {
        val default = ALWAYS_ACTIVE

        fun from(name: String?): TriggerMode =
            entries.firstOrNull { it.name == name } ?: default
    }
}

/** How long the opened bar waits before closing itself. */
enum class AutoHide(val seconds: Int, val label: String) {
    TEN(10, "After 10 seconds"),
    TWENTY(20, "After 20 seconds"),
    MINUTE(60, "After 1 minute"),
    TEN_MINUTES(600, "After 10 minutes"),
    NEVER(0, "Never — close it myself"),
    ;

    companion object {
        val default = TWENTY

        fun from(name: String?): AutoHide = entries.firstOrNull { it.name == name } ?: default
    }
}
