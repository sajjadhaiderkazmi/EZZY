package com.ezzy.vault.ui.components

/** What a Quick access row actually points at — each one opens a different screen. */
enum class QuickKind { SECTION, GROUP, ENTRY }

/**
 * One thing sitting in Quick access, flattened to what both the home strip and the Quick access
 * screen need in order to draw it. Sections, groups and entries are different rows of different
 * tables, but on this shelf they are all just a name, an icon and somewhere to go.
 */
data class QuickTarget(
    val id: String,
    val kind: QuickKind,
    val title: String,
    val subtitle: String,
    val iconKey: String?,
    val colorKey: String?,
) {
    /** Unique across kinds — two tables can hand out the same id without colliding in a list. */
    val key: String get() = "${kind.name}:$id"
}

/** The icon a group borrows, since a group has no icon of its own to pick. */
const val GROUP_ICON_KEY = "folder"
