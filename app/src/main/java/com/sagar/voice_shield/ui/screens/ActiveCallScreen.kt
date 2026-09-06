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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sagar.voice_shield.VoiceShieldApp
import com.sagar.voice_shield.service.VoipCallState
import com.sagar.voice_shield.ui.theme.*
import kotlinx.coroutines.delay
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

    // Auto-initiate call via VoipCallManager if entering with a targetPhone
    LaunchedEffect(targetPhone) {
        if (targetPhone.isNotBlank() && callState == VoipCallState.IDLE) {
            voipCallManager.initiateCall(targetPhone, callerName)
        }
    }

    // Manage Call Sound Engine (Ringtone and Active Call Audio) and auto exit
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
            VoipCallState.ENDED -> {
                audioCallEngine.stopCallAudio()
                delay(1500)
                navController.popBackStack()
            }
            VoipCallState.IDLE -> {
                audioCallEngine.stopCallAudio()
                if (callDuration > 0) {
                    delay(1000)
                    navController.popBackStack()
                }
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
                            navController.popBackStack()
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
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 8.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                    drawArc(
                        color = VsSurfaceContainerHighest,
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

                Spacer(Modifier.width(56.dp))

                // End call
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val isEnded = (callState == VoipCallState.ENDED)
                    IconButton(
                        onClick = {
                            if (!isEnded) {
                                audioCallEngine.stopCallAudio()
                                voipCallManager.endCall(saveHistory = true, riskScore = riskScore)
                                navController.popBackStack()
                            }
                        },
                        enabled = !isEnded,
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
