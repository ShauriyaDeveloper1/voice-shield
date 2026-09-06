package com.sagar.voice_shield.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sagar.voice_shield.ui.auth.LoginScreen
import com.sagar.voice_shield.ui.auth.RegisterScreen
import com.sagar.voice_shield.ui.screens.*

@Composable
fun NavGraph(
    navController: NavHostController,
    isLoggedIn: Boolean
) {
    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) Screen.Recents.route else Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Recents.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Screen.Recents.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Recents.route) {
            RecentsScreen(navController = navController)
        }

        composable(Screen.Favorites.route) {
            FavoritesScreen(navController = navController)
        }

        composable(Screen.Keypad.route) {
            KeypadScreen(navController = navController)
        }

        composable(Screen.ShieldHub.route) {
            ShieldHubScreen(navController = navController)
        }

        composable(
            route = Screen.ActiveCall.route,
            arguments = listOf(
                androidx.navigation.navArgument("phone") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                },
                androidx.navigation.navArgument("name") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val rawPhone = backStackEntry.arguments?.getString("phone") ?: ""
            val rawName = backStackEntry.arguments?.getString("name") ?: ""
            val phone = try { java.net.URLDecoder.decode(rawPhone, "UTF-8") } catch (_: Exception) { rawPhone }
            val name = try { java.net.URLDecoder.decode(rawName, "UTF-8") } catch (_: Exception) { rawName }
            ActiveCallScreen(
                navController = navController,
                targetPhone = phone,
                targetName = name
            )
        }

        composable(Screen.SpeakerProtection.route) {
            SpeakerProtectionScreen(navController = navController)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                navController = navController,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
