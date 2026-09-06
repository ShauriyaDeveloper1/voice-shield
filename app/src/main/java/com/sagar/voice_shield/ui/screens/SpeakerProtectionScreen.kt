package com.sagar.voice_shield.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.sagar.voice_shield.VoiceShieldApp
import com.sagar.voice_shield.service.AudioAnalysisService
import com.sagar.voice_shield.service.FloatingOverlayService
import com.sagar.voice_shield.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SpeakerProtectionScreen(navController: NavController) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as VoiceShieldApp).appContainer
    val preferencesManager = appContainer.preferencesManager
    val scope = rememberCoroutineScope()

    val savedProtectionEnabled by preferencesManager.speakerProtectionEnabled.collectAsState(initial = false)
    val isServiceRunning by AudioAnalysisService.isRunning.collectAsState()

    var hasMicPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    // Periodically re-check overlay permission when user returns to the app
    DisposableEffect(Unit) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        onDispose {}
    }

    fun startProtectionServices() {
        try {
            val audioIntent = Intent(context, AudioAnalysisService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(audioIntent)
            } else {
                context.startService(audioIntent)
            }

            if (Settings.canDrawOverlays(context)) {
                context.startService(Intent(context, FloatingOverlayService::class.java))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopProtectionServices() {
        try {
            context.stopService(Intent(context, AudioAnalysisService::class.java))
            context.stopService(Intent(context, FloatingOverlayService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        if (hasOverlayPermission && hasMicPermission) {
            scope.launch { preferencesManager.setSpeakerProtection(true) }
            startProtectionServices()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            if (!Settings.canDrawOverlays(context)) {
                overlayLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                )
            } else {
                scope.launch { preferencesManager.setSpeakerProtection(true) }
                startProtectionServices()
            }
        }
    }

    val isProtectionActive = savedProtectionEnabled || isServiceRunning

    Column(
        modifier = Modifier.fillMaxSize().background(VsBackground)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, null, tint = VsOnSurface)
            }
            Text("Speaker Protection", style = MaterialTheme.typography.headlineSmall, color = VsOnSurface, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = VsSurfaceContainerLow),
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(80.dp).clip(CircleShape)
                        .background(if (isProtectionActive) Brush.radialGradient(listOf(VsSecondary.copy(alpha = 0.3f), Color.Transparent))
                        else Brush.radialGradient(listOf(VsSurfaceContainerHigh, Color.Transparent))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.SpeakerPhone, null, tint = if (isProtectionActive) VsSecondary else VsOnSurfaceVariant, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    if (isProtectionActive) "Protection Active" else "Protection Disabled",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isProtectionActive) VsSecondary else VsOnSurfaceVariant, fontWeight = FontWeight.Bold
                )
                Text("Acoustic Call Analysis Mode", style = MaterialTheme.typography.bodyMedium, color = VsOnSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Switch(
                    checked = isProtectionActive,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (!hasMicPermission) {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else if (!Settings.canDrawOverlays(context)) {
                                overlayLauncher.launch(
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                )
                            } else {
                                scope.launch { preferencesManager.setSpeakerProtection(true) }
                                startProtectionServices()
                            }
                        } else {
                            scope.launch { preferencesManager.setSpeakerProtection(false) }
                            stopProtectionServices()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VsOnSecondary, checkedTrackColor = VsSecondary,
                        uncheckedThumbColor = VsOnSurfaceVariant, uncheckedTrackColor = VsSurfaceContainerHighest
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = VsSurfaceContainer)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("How It Works", style = MaterialTheme.typography.titleSmall, color = VsOnSurface)
                Spacer(Modifier.height(12.dp))
                val steps = listOf(
                    Icons.Filled.Call to "Receive a call on any app (WhatsApp, Telegram, etc.)",
                    Icons.Filled.VolumeUp to "Enable speakerphone on the calling app",
                    Icons.Filled.Mic to "VoiceShield captures audio through microphone",
                    Icons.Filled.Psychology to "AI analyzes voice for deepfake signals",
                    Icons.Filled.Shield to "Real-time risk score displayed as overlay"
                )
                steps.forEachIndexed { index, (icon, text) ->
                    Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(VsPrimaryContainer.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = VsPrimary, fontWeight = FontWeight.Bold)
                        }
                        Icon(icon, null, tint = VsPrimary, modifier = Modifier.size(20.dp))
                        Text(text, style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = VsSurfaceContainer)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("REQUIRED PERMISSIONS", style = MaterialTheme.typography.labelMedium, color = VsOnSurfaceVariant, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
                PermissionRow(
                    name = "Microphone Access",
                    granted = hasMicPermission,
                    onClick = {
                        if (!hasMicPermission) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
                PermissionRow(
                    name = "Display Over Apps",
                    granted = hasOverlayPermission,
                    onClick = {
                        if (!hasOverlayPermission) {
                            overlayLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            )
                        }
                    }
                )
                PermissionRow(name = "Notifications", granted = true, onClick = {})
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = VsTertiaryContainer.copy(alpha = 0.1f))) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Info, null, tint = VsTertiary, modifier = Modifier.size(20.dp))
                Column {
                    Text("Important", style = MaterialTheme.typography.titleSmall, color = VsTertiary)
                    Text("VoiceShield does not access any app's private audio stream. It analyzes sound captured acoustically through the device microphone when speakerphone is enabled.",
                        style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
fun PermissionRow(name: String, granted: Boolean, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = VsOnSurface)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(if (granted) Icons.Filled.CheckCircle else Icons.Filled.Cancel, null, tint = if (granted) VsSecondary else VsError, modifier = Modifier.size(18.dp))
            Text(if (granted) "Granted" else "Required", style = MaterialTheme.typography.labelSmall, color = if (granted) VsSecondary else VsError)
        }
    }
}

