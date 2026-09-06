package com.sagar.voice_shield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sagar.voice_shield.BuildConfig
import com.sagar.voice_shield.VoiceShieldApp
import com.sagar.voice_shield.navigation.Screen
import com.sagar.voice_shield.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    navController: NavController,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as VoiceShieldApp).appContainer
    val prefs = appContainer.preferencesManager
    val scope = rememberCoroutineScope()

    val currentName by prefs.userName.collectAsStateWithLifecycle(initialValue = "User")
    val currentEmail by prefs.userEmail.collectAsStateWithLifecycle(initialValue = "user@voiceshield.ai")
    val currentPhone by prefs.userPhone.collectAsStateWithLifecycle(initialValue = "+91 98765 43210")

    // Dialog Visibility States
    var showProfileDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showAiDetectionDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showBackendStatusDialog by remember { mutableStateOf(false) }

    // Settings Toggle States
    var twoFactorAuth by remember { mutableStateOf(true) }
    var biometricLock by remember { mutableStateOf(true) }
    var sensitivityLevel by remember { mutableStateOf("Balanced (65%)") }
    var vocoderDetection by remember { mutableStateOf(true) }
    var contextAnalysis by remember { mutableStateOf(true) }
    var alertOnHighRisk by remember { mutableStateOf(true) }
    var soundVibrate by remember { mutableStateOf(true) }
    var dailySummary by remember { mutableStateOf(false) }

    // Backend Ping Test State
    var pingStatus by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VsBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = VsOnSurface, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        // Account section
        Text("ACCOUNT", style = MaterialTheme.typography.labelMedium, color = VsOnSurfaceVariant, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))

        SettingsCard {
            SettingsItem(
                icon = Icons.Filled.Person,
                title = "Profile",
                subtitle = "${currentName ?: "User"} • ${currentPhone ?: "+91 98765 43210"}"
            ) {
                showProfileDialog = true
            }
            HorizontalDivider(color = VsSurfaceContainerHighest)
            SettingsItem(
                icon = Icons.Filled.Security,
                title = "Security",
                subtitle = "2FA ${if (twoFactorAuth) "Enabled" else "Disabled"} • Biometrics"
            ) {
                showSecurityDialog = true
            }
        }

        Spacer(Modifier.height(20.dp))

        // Protection section
        Text("PROTECTION", style = MaterialTheme.typography.labelMedium, color = VsOnSurfaceVariant, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))

        SettingsCard {
            SettingsItem(
                icon = Icons.Filled.Shield,
                title = "AI Detection",
                subtitle = "$sensitivityLevel • Neural Vocoders"
            ) {
                showAiDetectionDialog = true
            }
            HorizontalDivider(color = VsSurfaceContainerHighest)
            SettingsItem(
                icon = Icons.Filled.SpeakerPhone,
                title = "Speaker Protection",
                subtitle = "Third-party call analysis & floating overlay"
            ) {
                navController.navigate(Screen.SpeakerProtection.route)
            }
            HorizontalDivider(color = VsSurfaceContainerHighest)
            SettingsItem(
                icon = Icons.Filled.Notifications,
                title = "Notifications",
                subtitle = if (alertOnHighRisk) "Instant alerts enabled" else "Alerts muted"
            ) {
                showNotificationDialog = true
            }
        }

        Spacer(Modifier.height(20.dp))

        // About section
        Text("ABOUT", style = MaterialTheme.typography.labelMedium, color = VsOnSurfaceVariant, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))

        SettingsCard {
            SettingsItem(
                icon = Icons.Filled.Info,
                title = "About VoiceShield",
                subtitle = "Version 1.0 • Hybrid Edge-Cloud AI"
            ) {
                showAboutDialog = true
            }
            HorizontalDivider(color = VsSurfaceContainerHighest)
            SettingsItem(
                icon = Icons.Filled.Code,
                title = "Backend Status",
                subtitle = "voice-shield-backend-7xpl.onrender.com"
            ) {
                showBackendStatusDialog = true
            }
        }

        Spacer(Modifier.height(24.dp))

        // Logout button
        Button(
            onClick = {
                scope.launch {
                    prefs.clearLoginData()
                    onLogout()
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VsErrorContainer.copy(alpha = 0.3f), contentColor = VsError)
        ) {
            Icon(Icons.Filled.Logout, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Log Out", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "VoiceShield Prototype — v1.0",
            style = MaterialTheme.typography.labelSmall,
            color = VsOutline,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(100.dp))
    }

    // ─────────────────────────────────────────────────────────────────
    // 1. Profile Dialog
    // ─────────────────────────────────────────────────────────────────
    if (showProfileDialog) {
        var editName by remember { mutableStateOf(currentName ?: "") }
        var editEmail by remember { mutableStateOf(currentEmail ?: "") }
        var editPhone by remember { mutableStateOf(currentPhone ?: "") }

        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            containerColor = VsSurfaceContainerHighest,
            title = {
                Text("Edit Profile", style = MaterialTheme.typography.titleLarge, color = VsOnSurface)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val token = prefs.authToken.firstOrNull() ?: ""
                            val id = prefs.userId.firstOrNull() ?: ""
                            prefs.saveLoginData(token, id, editName, editEmail, editPhone)
                            appContainer.voipCallManager.updateMyCredentials(editPhone, editName)
                            showProfileDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VsPrimary)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Cancel", color = VsOnSurfaceVariant)
                }
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. Security Dialog
    // ─────────────────────────────────────────────────────────────────
    if (showSecurityDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            containerColor = VsSurfaceContainerHighest,
            title = {
                Text("Security Settings", style = MaterialTheme.typography.titleLarge, color = VsOnSurface)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Two-Factor Authentication", style = MaterialTheme.typography.titleSmall, color = VsOnSurface)
                            Text("Protect outgoing high-risk transfers", style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
                        }
                        Switch(
                            checked = twoFactorAuth,
                            onCheckedChange = { twoFactorAuth = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = VsSecondary, checkedTrackColor = VsSecondaryContainer)
                        )
                    }

                    HorizontalDivider(color = VsSurfaceContainer)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Biometric App Lock", style = MaterialTheme.typography.titleSmall, color = VsOnSurface)
                            Text("Require fingerprint or face ID to open", style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
                        }
                        Switch(
                            checked = biometricLock,
                            onCheckedChange = { biometricLock = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = VsSecondary, checkedTrackColor = VsSecondaryContainer)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSecurityDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = VsSecondary)) {
                    Text("Done")
                }
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. AI Detection Sensitivity Dialog
    // ─────────────────────────────────────────────────────────────────
    if (showAiDetectionDialog) {
        AlertDialog(
            onDismissRequest = { showAiDetectionDialog = false },
            containerColor = VsSurfaceContainerHighest,
            title = {
                Text("AI Detection Settings", style = MaterialTheme.typography.titleLarge, color = VsOnSurface)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Deepfake Detection Sensitivity:", style = MaterialTheme.typography.titleSmall, color = VsOnSurface)

                    listOf("Balanced (65%)", "High Sensitivity (80%)", "Maximum Security (92%)").forEach { level ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = sensitivityLevel == level,
                                onClick = { sensitivityLevel = level },
                                colors = RadioButtonDefaults.colors(selectedColor = VsSecondary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(level, style = MaterialTheme.typography.bodyMedium, color = VsOnSurface)
                        }
                    }

                    HorizontalDivider(color = VsSurfaceContainer)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vocoder Artifact Detection", style = MaterialTheme.typography.titleSmall, color = VsOnSurface)
                            Text("Flags HiFi-GAN & neural synthesis signatures", style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
                        }
                        Switch(checked = vocoderDetection, onCheckedChange = { vocoderDetection = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Context Risk Engine", style = MaterialTheme.typography.titleSmall, color = VsOnSurface)
                            Text("Identifies urgent language & OTP extraction", style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
                        }
                        Switch(checked = contextAnalysis, onCheckedChange = { contextAnalysis = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAiDetectionDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = VsSecondary)) {
                    Text("Apply")
                }
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // 4. Notifications Dialog
    // ─────────────────────────────────────────────────────────────────
    if (showNotificationDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationDialog = false },
            containerColor = VsSurfaceContainerHighest,
            title = {
                Text("Notification Preferences", style = MaterialTheme.typography.titleLarge, color = VsOnSurface)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("High Risk Call Alerts", style = MaterialTheme.typography.titleSmall, color = VsOnSurface)
                            Text("Immediate banner when risk score > 66", style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
                        }
                        Switch(checked = alertOnHighRisk, onCheckedChange = { alertOnHighRisk = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Haptic & Sound Alerts", style = MaterialTheme.typography.titleSmall, color = VsOnSurface)
                            Text("Vibrate device during synthetic voice detection", style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
                        }
                        Switch(checked = soundVibrate, onCheckedChange = { soundVibrate = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Daily Protection Summary", style = MaterialTheme.typography.titleSmall, color = VsOnSurface)
                            Text("Daily report of scanned and safe calls", style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
                        }
                        Switch(checked = dailySummary, onCheckedChange = { dailySummary = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showNotificationDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = VsSecondary)) {
                    Text("Save")
                }
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // 5. About VoiceShield Dialog
    // ─────────────────────────────────────────────────────────────────
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = VsSurfaceContainerHighest,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Shield, null, tint = VsPrimary)
                    Text("VoiceShield v1.0", style = MaterialTheme.typography.titleLarge, color = VsOnSurface)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "VoiceShield is an advanced AI-powered defense system against real-time voice cloning, deepfake imposter scams, and social engineering attacks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VsOnSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Defense Architecture:", style = MaterialTheme.typography.titleSmall, color = VsSecondary)
                    Text("• AASIST Neural Spectrogram Analysis\n• XLM-Roberta Transformer Context Engine\n• Real-Time WebRTC & WebSocket VOIP Signaling\n• Encrypted Room Database Telemetry",
                        style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Built for Google AI Solution Sprint 2026", style = MaterialTheme.typography.labelSmall, color = VsOutline)
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = VsPrimary)) {
                    Text("Close")
                }
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // 6. Backend Status Dialog
    // ─────────────────────────────────────────────────────────────────
    if (showBackendStatusDialog) {
        AlertDialog(
            onDismissRequest = { showBackendStatusDialog = false },
            containerColor = VsSurfaceContainerHighest,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CloudSync, null, tint = VsSecondary)
                    Text("Backend Status", style = MaterialTheme.typography.titleLarge, color = VsOnSurface)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("REST API: ${BuildConfig.API_BASE_URL}", style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
                    Text("WebSocket: /api/calls/ws/{phone}", style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = VsSurfaceContainer,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Health Status:", style = MaterialTheme.typography.labelSmall, color = VsOnSurfaceVariant)
                            Text(
                                pingStatus ?: "Ready to test connection",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (pingStatus?.contains("Online") == true) VsSecondary else VsOnSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = {
                            isTestingConnection = true
                            scope.launch {
                                val startTime = System.currentTimeMillis()
                                val isHealthy = withContext(Dispatchers.IO) {
                                    try {
                                        appContainer.authRepository.checkHealth()
                                    } catch (e: Exception) {
                                        false
                                    }
                                }
                                val latency = System.currentTimeMillis() - startTime
                                pingStatus = if (isHealthy) "Online • Latency: ${latency}ms (HTTP 200)" else "Server unreachable or sleeping"
                                isTestingConnection = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isTestingConnection,
                        colors = ButtonDefaults.buttonColors(containerColor = VsSecondary)
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Pinging Server...")
                        } else {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Test Live Ping")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackendStatusDialog = false }) {
                    Text("Close", color = VsOnSurfaceVariant)
                }
            }
        )
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VsSurfaceContainer)
    ) {
        Column(modifier = Modifier.padding(4.dp), content = content)
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = VsSurfaceContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = VsPrimary, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = VsOnSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = VsOnSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
