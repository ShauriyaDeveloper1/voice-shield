package com.sagar.voice_shield.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.sagar.voice_shield.ui.theme.*

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Favorites.route, "Favorites", Icons.Filled.Star, Icons.Outlined.StarOutline),
    BottomNavItem(Screen.Recents.route, "Recents", Icons.Filled.History, Icons.Outlined.History),
    BottomNavItem(Screen.Keypad.route, "Keypad", Icons.Filled.Dialpad, Icons.Outlined.Dialpad),
    BottomNavItem(Screen.ShieldHub.route, "Shield", Icons.Filled.Shield, Icons.Outlined.Shield)
)

@Composable
fun BottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = VsSurfaceContainer.copy(alpha = 0.95f),
        contentColor = VsOnSurfaceVariant,
        tonalElevation = 8.dp
    ) {
        bottomNavItems.forEach { item ->
            val isSelected = currentRoute == item.route

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(Screen.Recents.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = VsPrimary,
                    selectedTextColor = VsPrimary,
                    unselectedIconColor = VsOnSurfaceVariant,
                    unselectedTextColor = VsOnSurfaceVariant,
                    indicatorColor = VsPrimary.copy(alpha = 0.15f)
                )
            )
        }
    }
}
