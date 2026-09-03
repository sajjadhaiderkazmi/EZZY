package com.ezzy.vault.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ezzy.vault.ui.screens.AppearanceSettingsScreen
import com.ezzy.vault.ui.screens.CategoryEditorScreen
import com.ezzy.vault.ui.screens.CategoryScreen
import com.ezzy.vault.ui.screens.DataSettingsScreen
import com.ezzy.vault.ui.screens.EditorScreen
import com.ezzy.vault.ui.screens.FloatingBarSettingsScreen
import com.ezzy.vault.ui.screens.HomeScreen
import com.ezzy.vault.ui.screens.ItemDetailScreen
import com.ezzy.vault.ui.screens.SearchScreen
import com.ezzy.vault.ui.screens.SecuritySettingsScreen
import com.ezzy.vault.ui.screens.SettingsScreen
import com.ezzy.vault.ui.screens.TemplatesScreen
import com.ezzy.vault.util.EzzySettings

@Composable
fun EzzyNavHost(
    navController: NavHostController,
    settings: EzzySettings,
) {
    // A short slide-and-fade in both directions: enough to show which way the stack moved,
    // short enough that it never feels like waiting.
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            slideInHorizontally(animationSpec = tween(280)) { it / 6 } + fadeIn(tween(200))
        },
        exitTransition = {
            slideOutHorizontally(animationSpec = tween(280)) { -it / 8 } + fadeOut(tween(160))
        },
        popEnterTransition = {
            slideInHorizontally(animationSpec = tween(280)) { -it / 8 } + fadeIn(tween(200))
        },
        popExitTransition = {
            slideOutHorizontally(animationSpec = tween(280)) { it / 6 } + fadeOut(tween(160))
        },
    ) {

        composable(Routes.HOME) {
            HomeScreen(
                settings = settings,
                onOpenCategory = { navController.navigate(Routes.category(it)) },
                onOpenItem = { navController.navigate(Routes.item(it)) },
                onAddItem = { navController.navigate(Routes.editor()) },
                onAddCategory = { navController.navigate(Routes.categoryEditor()) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.CATEGORY,
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
        ) { entry ->
            val categoryId = entry.arguments?.getString("categoryId").orEmpty()
            CategoryScreen(
                categoryId = categoryId,
                onBack = { navController.popBackStack() },
                onOpenItem = { navController.navigate(Routes.item(it)) },
                onAddItem = { navController.navigate(Routes.editor(categoryId = categoryId)) },
                onEditCategory = { navController.navigate(Routes.categoryEditor(categoryId)) },
            )
        }

        composable(
            route = Routes.ITEM,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) { entry ->
            val itemId = entry.arguments?.getString("itemId").orEmpty()
            ItemDetailScreen(
                itemId = itemId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.editor(itemId = itemId)) },
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType; defaultValue = "" },
                navArgument("categoryId") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val itemId = entry.arguments?.getString("itemId").orEmpty().ifBlank { null }
            val categoryId = entry.arguments?.getString("categoryId").orEmpty().ifBlank { null }
            EditorScreen(
                itemId = itemId,
                categoryId = categoryId,
                onClose = { navController.popBackStack() },
                onSaved = { savedId ->
                    // Land on the saved entry rather than back where the user started, so the
                    // result of the wizard is immediately visible.
                    navController.popBackStack()
                    if (itemId == null) navController.navigate(Routes.item(savedId))
                },
            )
        }

        composable(
            route = Routes.CATEGORY_EDITOR,
            arguments = listOf(
                navArgument("categoryId") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            CategoryEditorScreen(
                categoryId = entry.arguments?.getString("categoryId").orEmpty().ifBlank { null },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenItem = { navController.navigate(Routes.item(it)) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenFloatingBar = { navController.navigate(Routes.SETTINGS_FLOATING_BAR) },
                onOpenSecurity = { navController.navigate(Routes.SETTINGS_SECURITY) },
                onOpenAppearance = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                onOpenData = { navController.navigate(Routes.SETTINGS_DATA) },
            )
        }

        composable(Routes.SETTINGS_FLOATING_BAR) {
            FloatingBarSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS_SECURITY) {
            SecuritySettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS_APPEARANCE) {
            AppearanceSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS_DATA) {
            DataSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenTemplates = { navController.navigate(Routes.TEMPLATES) },
                // The vault is empty now, so drop the whole settings stack and land on Home.
                onErased = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }

        composable(Routes.TEMPLATES) {
            TemplatesScreen(onBack = { navController.popBackStack() })
        }
    }
}
