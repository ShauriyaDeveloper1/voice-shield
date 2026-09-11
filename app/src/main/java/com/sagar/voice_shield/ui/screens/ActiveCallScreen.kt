package com.sagar.voice_shield.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sagar.voice_shield.VoiceShieldApp
import com.sagar.voice_shield.navigation.Screen
import com.sagar.voice_shield.service.VoipCallState
import com.sagar.voice_shield.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ActiveCallScreen(
    navController: NavController,
    targetPhone: String = "",
    targetName: String = ""
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as VoiceShieldApp).appContainer
    val voipCallManager = appContainer.voipCallManager
    val audioCallEngine = appContainer.audioCallEngine

    val callState by voipCallManager.callState.collectAsStateWithLifecycle()
    val activePeerNameState by voipCallManager.activePeerName.collectAsStateWithLifecycle()
    val activePeerPhoneState by voipCallManager.activePeerPhone.collectAsStateWithLifecycle()
    val statusMessage by voipCallManager.statusMessage.collectAsStateWithLifecycle()

    val riskScore by audioCallEngine.realtimeRiskScore.collectAsStateWithLifecycle()
    val prosodyMatch by audioCallEngine.realtimeProsodyMatch.collectAsStateWithLifecycle()
    val vocoderMatch by audioCallEngine.realtimeVocoderMatch.collectAsStateWithLifecycle()
    val embeddingMatch by audioCallEngine.realtimeEmbeddingMatch.collectAsStateWithLifecycle()

    // Clean Caller Name (strip URL encoding '+' characters)
    val callerName = remember(targetName, activePeerNameState, targetPhone) {
        val raw = when {
            targetName.isNotBlank() -> targetName
            activePeerNameState.isNotBlank() -> activePeerNameState
            targetPhone.isNotBlank() -> targetPhone
            else -> "Secure Contact"
        }
        raw.replace("+", " ").replace("\\s+".toRegex(), " ").trim()
    }

    // Clean Caller Phone
    val callerPhone = remember(targetPhone, activePeerPhoneState) {
        val raw = when {
            targetPhone.isNotBlank() -> targetPhone
            activePeerPhoneState.isNotBlank() -> activePeerPhoneState
            else -> "+91 Secured Peer"
        }
        val digits = raw.filter { it.isDigit() }
        if (digits.length >= 10) {
            val last10 = digits.takeLast(10)
            "+91 $last10"
        } else {
            raw.replace("+", " ").trim()
        }
    }

    // Avatar Initials
    val avatarInitials = remember(callerName) {
        val parts = callerName.trim().split(" ").filter { it.isNotBlank() }
        if (parts.size >= 2) {
            "${parts[0].first().uppercase()}${parts[1].first().uppercase()}"
        } else if (parts.isNotEmpty() && parts[0].length >= 2) {
            parts[0].take(2).uppercase()
        } else if (parts.isNotEmpty()) {
            parts[0].take(1).uppercase()
        } else {
            "VS"
        }
    }

    var callDuration by remember { mutableIntStateOf(0) }

    // Permission Management
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasMicPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    var hasExitedScreen by remember { mutableStateOf(false) }

    fun exitCallScreen() {
        if (!hasExitedScreen) {
            hasExitedScreen = true
            audioCallEngine.stopCallAudio()
            val popped = navController.popBackStack(Screen.ActiveCall.route, inclusive = true)
            if (!popped || navController.currentBackStackEntry?.destination?.route?.startsWith("active_call") == true) {
                navController.navigate(Screen.Recents.route) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
            // Reset call state to IDLE after screen exits
            voipCallManager.resetToIdle()
        }
    }

    // Manage Call Sound Engine (Ringtone and Active Call Audio) and clean exit
    LaunchedEffect(callState) {
        when (callState) {
            VoipCallState.DIALING -> {
                audioCallEngine.startRinging()
            }
            VoipCallState.CONNECTED -> {
                audioCallEngine.startActiveCallAudio(isVoipWebRtc = true)
            }
            VoipCallState.OFFLINE_DEMO -> {
                audioCallEngine.startActiveCallAudio(isVoipWebRtc = false)
            }
            VoipCallState.ENDED, VoipCallState.IDLE -> {
                audioCallEngine.stopCallAudio()
                exitCallScreen()
            }
            else -> {}
        }
    }


    DisposableEffect(Unit) {
        onDispose {
            audioCallEngine.stopCallAudio()
        }
    }

    val aiConfirmation by audioCallEngine.aiConfirmation.collectAsStateWithLifecycle()

    // Show model active popup banner state
    var showModelActivePopup by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }

    // Spam reporting and detection state
    var isSpamCaller by remember { mutableStateOf(false) }
    var spamReportCount by remember { mutableIntStateOf(0) }
    var showReportDialog by remember { mutableStateOf(false) }
    var selectedReason by remember { mutableStateOf("AI Voice Clone / Impersonation Scam") }
    val coroutineScope = rememberCoroutineScope()

    val rawDigits = remember(callerPhone) { callerPhone.filter { it.isDigit() } }
    LaunchedEffect(rawDigits) {
        if (rawDigits.length >= 7) {
            try {
                val res = appContainer.api.checkSpam(rawDigits)
                isSpamCaller = res.isSpam
                spamReportCount = res.reportCount
            } catch (_: Exception) {
                // Ignore network errors on background check
            }
        }
    }

    // Report Number Dialog
    if (showReportDialog) {
        val reasons = listOf(
            "AI Voice Clone / Impersonation Scam",
            "OTP / Banking Fraud Attempt",
            "Robocall / Automated Telemarketing",
            "Harassment or Threat"
        )
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.ReportProblem, null, tint = VsError)
                    Text("Report Caller", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = VsOnSurface)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Report $callerPhone as spam or fraud. Numbers with 20+ community reports are marked globally as SPAM for all VoiceShield users.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VsOnSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Select reason:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = VsOnSurface)
                    reasons.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedReason = reason }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (selectedReason == reason),
                                onClick = { selectedReason = reason },
                                colors = RadioButtonDefaults.colors(selectedColor = VsError)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(reason, style = MaterialTheme.typography.bodySmall, color = VsOnSurface)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val userId = appContainer.preferencesManager.userId.first() ?: "user_${System.currentTimeMillis()}"
                                val response = appContainer.api.reportNumber(
                                    com.sagar.voice_shield.data.remote.dto.ReportNumberRequest(
                                        reporterUserId = userId,
                                        reportedPhone = rawDigits,
                                        reason = selectedReason
                                    )
                                )
                                spamReportCount = response.reportCount
                                isSpamCaller = response.isSpam
                                android.widget.Toast.makeText(
                                    context,
                                    "Report submitted! Total reports: ${response.reportCount}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Report failed: ${e.message ?: "Network error"}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                showReportDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VsError)
                ) {
                    Text("Submit Report", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel", color = VsOnSurfaceVariant)
                }
            },
            containerColor = VsSurfaceContainerHigh
        )
    }

    // 4-5 Audio Chunks Analysis Confirmation Dialog
    if (aiConfirmation != null) {
        val conf = aiConfirmation!!
        AlertDialog(
            onDismissRequest = { audioCallEngine.dismissConfirmation() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (conf.isVerifiedSafe) Icons.Filled.VerifiedUser else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (conf.isVerifiedSafe) VsSecondary else VsError,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        if (conf.isVerifiedSafe) "Voice Authenticity Confirmed" else "Threat Warning",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (conf.isVerifiedSafe) VsSecondary else VsError,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        conf.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VsOnSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = VsSurfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Risk Score: ${conf.finalRiskScore}/100", style = MaterialTheme.typography.labelMedium, color = VsOnSurface)
                            Text("Confidence: ${conf.confidence}%", style = MaterialTheme.typography.labelMedium, color = VsSecondary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { audioCallEngine.dismissConfirmation() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (conf.isVerifiedSafe) VsSecondary else VsError,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Understood", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = VsSurfaceContainerHigh
        )
    }

    // Timer
    LaunchedEffect(callState) {
        while (callState != VoipCallState.ENDED && callState != VoipCallState.IDLE) {
            delay(1000)
            callDuration++
        }
    }

    val minutes = callDuration / 60
    val seconds = callDuration % 60
    val timeDisplay = "%02d:%02d".format(minutes, seconds)

    val severity = when {
        riskScore > 66 -> "HIGH"
        riskScore > 33 -> "MEDIUM"
        else -> "LOW"
    }
    val severityColor = when (severity) {
        "HIGH" -> VsError
        "MEDIUM" -> VsTertiary
        else -> VsSecondary
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VsBackground),
        contentPadding = PaddingValues(bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Live header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = {
                            voipCallManager.endCall(saveHistory = true, riskScore = riskScore)
                            exitCallScreen()
                        },

                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(VsSurfaceContainerHigh)
                    ) {
                        Icon(Icons.Filled.ArrowBack, null, tint = VsOnSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    Surface(shape = RoundedCornerShape(50), color = VsError.copy(alpha = 0.15f)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PulsingDot(color = VsError, size = 6)
                            Text(
                                if (callState == VoipCallState.DIALING) "DIALING..."
                                else if (callState == VoipCallState.OFFLINE_DEMO) "OFFLINE DEMO"
                                else if (callState == VoipCallState.ENDED) "CALL ENDED"
                                else "LIVE ANALYSIS",
                                style = MaterialTheme.typography.labelSmall,
                                color = VsError,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    timeDisplay,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = VsOnSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }

        // ─────────────────────────────────────────────────────────────
        // Prominent Model Active & Analyzing Banner Popup
        // ─────────────────────────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = showModelActivePopup,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = VsSurfaceContainerHighest),
                    border = BorderStroke(1.5.dp, VsSecondary)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(VsSecondary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Psychology,
                                contentDescription = null,
                                tint = VsSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PulsingDot(color = VsSecondary, size = 7)
                                Text(
                                    "MODEL ACTIVE & ANALYZING",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = VsSecondary,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "Real-time AI protection is active. Analyzing live speech for neural vocoders, acoustic anomalies, and impersonation risk.",
                                style = MaterialTheme.typography.bodySmall,
                                color = VsOnSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }

                        IconButton(
                            onClick = { showModelActivePopup = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Dismiss",
                                tint = VsOnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Permission Warning if Microphone is not granted
        if (!hasMicPermission) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = VsErrorContainer.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, VsError.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.MicOff, null, tint = VsError, modifier = Modifier.size(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Microphone Permission Required", style = MaterialTheme.typography.titleSmall, color = VsError)
                            Text(
                                "VoiceShield needs microphone access to monitor and protect your call against deepfakes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = VsOnSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                permissionLauncher.launch(perms.toTypedArray())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VsError),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Grant", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Status banner if present from VOIP manager
        if (!statusMessage.isNullOrBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = VsSurfaceContainerHighest,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        statusMessage ?: "",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = VsSecondary
                    )
                }
            }
        }

        // Avatar & dynamic caller info
        item {
            Column(
                modifier = Modifier.padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(severityColor.copy(alpha = 0.35f), Color.Transparent))),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(VsSurfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(avatarInitials, style = MaterialTheme.typography.headlineMedium, color = severityColor, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(callerName, style = MaterialTheme.typography.headlineMedium, color = VsOnSurface, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(callerPhone, style = MaterialTheme.typography.bodyMedium, color = VsOnSurfaceVariant)

                if (isSpamCaller || spamReportCount >= 20) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = VsError.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, VsError)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.ReportProblem, null, tint = VsError, modifier = Modifier.size(14.dp))
                            Text(
                                "COMMUNITY SPAM ($spamReportCount REPORTS)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = VsError,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Waveform
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(24) { i ->
                    val height = (15 + (sin(callDuration * 2.0 + i) * 15) + (Random.nextFloat() * 25)).toFloat()
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight(height / 100f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Brush.verticalGradient(listOf(severityColor.copy(alpha = 0.8f), severityColor.copy(alpha = 0.2f))))
                    )
                }
            }
        }

        // Risk Score Gauge
        item {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val trackColor = VsSurfaceContainerHighest
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 8.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                    drawArc(
                        color = trackColor,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(strokeWidth, cap = StrokeCap.Round),
                        topLeft = topLeft,
                        size = arcSize
                    )
                    drawArc(
                        color = severityColor,
                        startAngle = 135f,
                        sweepAngle = 270f * (riskScore / 100f),
                        useCenter = false,
                        style = Stroke(strokeWidth, cap = StrokeCap.Round),
                        topLeft = topLeft,
                        size = arcSize
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        riskScore.toString(),
                        style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
                        color = severityColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text("RISK SCORE", style = MaterialTheme.typography.labelSmall, color = VsOnSurfaceVariant)
                }
            }
        }

        // Controls
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            audioCallEngine.setMute(isMuted)
                            voipCallManager.setMute(isMuted)
                        },
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) VsError.copy(alpha = 0.2f) else VsSurfaceContainerHigh)
                    ) {
                        Icon(
                            if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            null,
                            tint = if (isMuted) VsError else VsOnSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(if (isMuted) "Unmute" else "Mute", style = MaterialTheme.typography.labelSmall, color = VsOnSurfaceVariant)
                }

                Spacer(Modifier.width(36.dp))

                // End call
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val isEnded = (callState == VoipCallState.ENDED)
                    IconButton(
                        onClick = {
                            voipCallManager.endCall(saveHistory = true, riskScore = riskScore)
                            exitCallScreen()
                        },

                        enabled = !hasExitedScreen,
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(if (isEnded) VsSurfaceContainerHighest else VsError)
                    ) {
                        Icon(Icons.Filled.CallEnd, null, tint = if (isEnded) VsOutline else Color.White)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(if (isEnded) "Ended" else "End", style = MaterialTheme.typography.labelSmall, color = VsOnSurfaceVariant)
                }

                Spacer(Modifier.width(36.dp))

                // Report Spam
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { showReportDialog = true },
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(VsSurfaceContainerHigh)
                    ) {
                        Icon(Icons.Filled.Flag, "Report Spam", tint = VsError)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Report", style = MaterialTheme.typography.labelSmall, color = VsError)
                }
            }
        }

        // Analysis feed
        item {
            Text(
                "AI ANALYSIS",
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = VsOnSurfaceVariant,
                letterSpacing = 2.sp
            )
        }

        item {
            AnalysisFeedItem(
                "Voice Embedding",
                "Speaker profile consistent with verified calls ($embeddingMatch% match)",
                if (embeddingMatch > 85) VsSecondary else VsTertiary
            )
        }
        item {
            AnalysisFeedItem(
                "Spectral Analysis",
                if (vocoderMatch > 80) "No vocoder signature. Natural harmonic variance ($vocoderMatch% match)"
                else "Acoustic synthesis artifact detected ($vocoderMatch% match)",
                if (vocoderMatch > 80) VsSecondary else VsError
            )
        }
        item {
            AnalysisFeedItem(
                "Prosody Dynamics",
                if (prosodyMatch > 75) "Speaking rhythm natural, below spoofing threshold ($prosodyMatch% naturalness)"
                else "Unnatural pitch variance and robotic cadence ($prosodyMatch% naturalness)",
                if (prosodyMatch > 75) VsPrimary else VsError
            )
        }
        item {
            AnalysisFeedItem(
                "Conversation Context",
                if (riskScore < 50) "No requests for OTP, transfers, or passwords"
                else "Suspicious urgency signals detected in speech stream",
                if (riskScore < 50) VsSecondary else VsTertiary
            )
        }

        // Verdict
        item {
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (riskScore <= 35) VsSecondary.copy(alpha = 0.1f) else VsError.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (riskScore <= 35) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        null,
                        tint = if (riskScore <= 35) VsSecondary else VsError,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            if (riskScore <= 35) "Safe to continue" else "Threat Warning",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (riskScore <= 35) VsSecondary else VsError
                        )
                        Text(
                            if (riskScore <= 35) "No high-confidence impersonation signal detected"
                            else "High risk spoofing markers identified. Exercise extreme caution.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VsOnSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnalysisFeedItem(title: String, description: String, accentColor: Color) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VsSurfaceContainer)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = VsOnSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
            }
        }
    }
}
