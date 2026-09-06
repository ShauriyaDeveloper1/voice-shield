package com.sagar.voice_shield.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object Recents : Screen("recents")
    data object Favorites : Screen("favorites")
    data object Keypad : Screen("keypad")
    data object ShieldHub : Screen("shield_hub")
    data object ActiveCall : Screen("active_call?phone={phone}&name={name}") {
        fun createRoute(phone: String = "", name: String = ""): String {
            val p = java.net.URLEncoder.encode(phone, "UTF-8")
            val n = java.net.URLEncoder.encode(name, "UTF-8")
            return "active_call?phone=$p&name=$n"
        }
    }
    data object SpeakerProtection : Screen("speaker_protection")
    data object Settings : Screen("settings")
}
