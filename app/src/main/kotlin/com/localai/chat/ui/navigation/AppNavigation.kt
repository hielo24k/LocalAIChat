package com.localai.chat.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.localai.chat.ui.chat.ChatScreen
import com.localai.chat.ui.models.ModelScreen
import com.localai.chat.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object Models : Screen("models")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route
    ) {
        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateToModels = { navController.navigate(Screen.Models.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Models.route) {
            ModelScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
