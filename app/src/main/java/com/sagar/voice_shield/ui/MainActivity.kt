package com.sagar.voice_shield.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sagar.voice_shield.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sagar.voice_shield.VoiceShieldApp
import com.sagar.voice_shield.navigation.BottomNavBar
import com.sagar.voice_shield.navigation.NavGraph
import com.sagar.voice_shield.navigation.Screen
import com.sagar.voice_shield.navigation.bottomNavItems
import com.sagar.voice_shield.ui.auth.AuthViewModel
import com.sagar.voice_shield.ui.screens.PulsingDot
import com.sagar.voice_shield.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoiceShieldTheme {
                VoiceShieldMainApp()
            }
        }
    }
}

@Composable
fun VoiceShieldMainApp() {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as VoiceShieldApp).appContainer

    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(appContainer.authRepository, appContainer.preferencesManager)
    )
    val isLoggedIn by authViewModel.isLoggedIn.collectAsStateWithLifecycle()
    val backendOnline by authViewModel.backendOnline.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val mainRoutes = bottomNavItems.map { it.route }
    val showShell = currentRoute in mainRoutes

    val incomingCall by appContainer.voipCallManager.incomingCall.collectAsStateWithLifecycle()
    val callState by appContainer.voipCallManager.callState.collectAsStateWithLifecycle()
    val activePeerPhone by appContainer.voipCallManager.activePeerPhone.collectAsStateWithLifecycle()
    val activePeerName by appContainer.voipCallManager.activePeerName.collectAsStateWithLifecycle()

    // Auto navigate to active call screen if call is active and user is not already on it
    LaunchedEffect(callState, currentRoute) {
        if ((callState == com.sagar.voice_shield.service.VoipCallState.CONNECTED ||
             callState == com.sagar.voice_shield.service.VoipCallState.DIALING ||
             callState == com.sagar.voice_shield.service.VoipCallState.OFFLINE_DEMO) &&
            currentRoute?.startsWith("call/") != true) {
            navController.navigate(
                Screen.ActiveCall.createRoute(
                    phone = activePeerPhone,
                    name = activePeerName
                )
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = VsBackground,
        topBar = {
            if (showShell) {
                VoiceShieldTopBar(
                    currentRoute = currentRoute,
                    backendOnline = backendOnline,
                    onSettingsClick = { navController.navigate(Screen.Settings.route) }
                )
            }
        },
        bottomBar = {
            if (showShell) {
                BottomNavBar(navController = navController)
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavGraph(
                navController = navController,
                isLoggedIn = isLoggedIn
            )

            // Incoming Call Overlay Dialog
            incomingCall?.let { incoming ->
                AlertDialog(
                    onDismissRequest = { appContainer.voipCallManager.declineIncomingCall() },
                    containerColor = VsSurfaceContainerHighest,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PulsingDot(color = VsSecondary, size = 8)
                            Text("Incoming VOIP Call", style = MaterialTheme.typography.titleMedium, color = VsOnSurface)
                        }
                    },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(VsSecondaryContainer.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Call, null, tint = VsSecondary, modifier = Modifier.size(32.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(incoming.fromName, style = MaterialTheme.typography.headlineSmall, color = VsOnSurface, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text(incoming.fromPhone, style = MaterialTheme.typography.bodyMedium, color = VsOnSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Surface(shape = RoundedCornerShape(50), color = VsSecondaryContainer.copy(alpha = 0.2f)) {
                                Text(
                                    "AI Biometric Shield Active",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VsSecondary
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                appContainer.voipCallManager.acceptIncomingCall()
                                navController.navigate(
                                    Screen.ActiveCall.createRoute(
                                        phone = incoming.fromPhone,
                                        name = incoming.fromName
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VsSecondary)
                        ) {
                            Icon(Icons.Filled.Call, null, modifier = Modifier.size(18.dp), tint = VsOnSecondary)
                            Spacer(Modifier.width(6.dp))
                            Text("Accept", color = VsOnSecondary)
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = { appContainer.voipCallManager.declineIncomingCall() },
                            colors = ButtonDefaults.buttonColors(containerColor = VsErrorContainer.copy(alpha = 0.3f), contentColor = VsError)
                        ) {
                            Icon(Icons.Filled.CallEnd, null, modifier = Modifier.size(18.dp), tint = VsError)
                            Spacer(Modifier.width(6.dp))
                            Text("Decline", color = VsError)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun VoiceShieldTopBar(
    currentRoute: String?,
    backendOnline: Boolean?,
    onSettingsClick: () -> Unit
) {
    val currentLabel = when (currentRoute) {
        Screen.Favorites.route -> "Favorites"
        Screen.Recents.route -> "Recents"
        Screen.Keypad.route -> "Keypad"
        Screen.ShieldHub.route -> "Shield Hub"
        else -> "VoiceShield"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = VsSurface.copy(alpha = 0.85f),
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(50),
                color = VsSurfaceContainerHigh,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "VoiceShield Logo",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                    )

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Search contacts & places", style = MaterialTheme.typography.bodyMedium, color = VsOnSurfaceVariant)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(6.dp).clip(CircleShape)
                                    .background(if (backendOnline == true) VsSecondary else VsError)
                            )
                            Text("AI Shield Active", style = MaterialTheme.typography.labelSmall, color = VsSecondary, fontWeight = FontWeight.SemiBold)
                            Text("• $currentLabel", style = MaterialTheme.typography.labelSmall, color = VsOnSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }

                    IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Mic, null, tint = VsOnSurfaceVariant, modifier = Modifier.size(22.dp))
                    }

                    IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.AccountCircle, null, tint = VsOnSurfaceVariant, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}
