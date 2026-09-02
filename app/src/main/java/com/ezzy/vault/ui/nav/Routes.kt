package com.ezzy.vault.ui.nav

/** Every destination in the app, with helpers so call sites never hand-build a URL. */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val TEMPLATES = "templates"

    const val CATEGORY = "category/{categoryId}"
    fun category(categoryId: String) = "category/$categoryId"

    const val ITEM = "item/{itemId}"
    fun item(itemId: String) = "item/$itemId"

    const val EDITOR = "editor?itemId={itemId}&categoryId={categoryId}"
    fun editor(itemId: String? = null, categoryId: String? = null) =
        "editor?itemId=${itemId.orEmpty()}&categoryId=${categoryId.orEmpty()}"

    const val CATEGORY_EDITOR = "categoryEditor?categoryId={categoryId}"
    fun categoryEditor(categoryId: String? = null) =
        "categoryEditor?categoryId=${categoryId.orEmpty()}"
}
