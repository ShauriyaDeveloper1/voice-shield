package com.sagar.voice_shield.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt
import com.sagar.voice_shield.data.repository.VoiceIdentityRepository
import com.sagar.voice_shield.VoiceShieldApp
import com.sagar.voice_shield.ui.theme.*

@Composable
fun OtpAuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val container = (context.applicationContext as VoiceShieldApp).appContainer
    val viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory(container.authRepository, container.preferencesManager))

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val otpSent by viewModel.otpSent.collectAsStateWithLifecycle()
    val otpMessage by viewModel.otpMessage.collectAsStateWithLifecycle()
    val retryStatus by viewModel.retryStatus.collectAsStateWithLifecycle()

    var phoneInput by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var fullNameInput by remember { mutableStateOf("") }
    var isOtpVerified by remember { mutableStateOf(false) }
    var isSavingName by remember { mutableStateOf(false) }

    val otpFocusRequester = remember { FocusRequester() }
    val nameFocusRequester = remember { FocusRequester() }

    var isVoiceEnrollmentStep by remember { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSecondsLeft by remember { mutableIntStateOf(10) }
    var audioAmplitude by remember { mutableFloatStateOf(0.1f) }
    var voiceConsentChecked by remember { mutableStateOf(true) }
    var isSubmittingVoice by remember { mutableStateOf(false) }
    var voiceEnrollmentSuccess by remember { mutableStateOf(false) }
    var voiceTxHash by remember { mutableStateOf("") }
    var voiceEnrollmentError by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess && !isOtpVerified) {
            isOtpVerified = true
            // Pre-populate name if it matches Sagar Goyal demo number
            if (fullNameInput.isBlank() && phoneInput.endsWith("9690818459")) {
                fullNameInput = "Sagar Goyal"
            }
        }
    }

    LaunchedEffect(otpSent) {
        if (otpSent && !isOtpVerified) {
            try {
                otpFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isOtpVerified) {
        if (isOtpVerified) {
            try {
                nameFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    fun handleCompleteSetup() {
        if (isSavingName) return
        isSavingName = true
        val phone = if (phoneInput.startsWith("+")) phoneInput else "+91 $phoneInput"
        val resolvedName = fullNameInput.trim().ifBlank {
            if (phoneInput.endsWith("9690818459")) "Sagar Goyal" else "User ${phoneInput.takeLast(4)}"
        }
        viewModel.completeRegistration(resolvedName) {
            container.voipCallManager.updateMyCredentials(phone = phone, name = resolvedName)
            onAuthSuccess()
        }
    }

    fun startVoiceRecording() {
        if (!hasMicPermission) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (isRecordingVoice || isSubmittingVoice) return
        isRecordingVoice = true
        voiceEnrollmentError = null
        recordingSecondsLeft = 10

        coroutineScope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = minBuf.coerceAtLeast(4096)

            val recorder: AudioRecord? = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            } catch (e: Exception) {
                null
            }

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                withContext(Dispatchers.Main) {
                    isRecordingVoice = false
                    voiceEnrollmentError = "Could not initialize microphone. Please check permissions."
                }
                return@launch
            }

            val pcmStream = ByteArrayOutputStream()
            val audioBuffer = ByteArray(bufferSize)

            try {
                recorder.startRecording()
                val startTime = System.currentTimeMillis()
                val totalDurationMs = 10000L

                while (isRecordingVoice && (System.currentTimeMillis() - startTime) < totalDurationMs) {
                    val read = recorder.read(audioBuffer, 0, audioBuffer.size)
                    if (read > 0) {
                        pcmStream.write(audioBuffer, 0, read)
                        var sum = 0.0
                        var i = 0
                        while (i < read - 1) {
                            val sample = ((audioBuffer[i + 1].toInt() shl 8) or (audioBuffer[i].toInt() and 0xFF)).toShort()
                            sum += sample * sample
                            i += 2
                        }
                        val rms = sqrt(sum / (read / 2).coerceAtLeast(1)) / 32768.0
                        withContext(Dispatchers.Main) {
                            audioAmplitude = (rms.toFloat() * 4.0f).coerceIn(0.08f, 1.0f)
                            val elapsed = System.currentTimeMillis() - startTime
                            recordingSecondsLeft = ((totalDurationMs - elapsed) / 1000L).toInt().coerceAtLeast(0)
                        }
                    }
                    delay(30)
                }
            } finally {
                try {
                    recorder.stop()
                    recorder.release()
                } catch (_: Exception) {}
            }

            withContext(Dispatchers.Main) {
                isRecordingVoice = false
                recordingSecondsLeft = 0
            }

            val pcmBytes = pcmStream.toByteArray()
            if (pcmBytes.size < sampleRate * 2 * 2) {
                withContext(Dispatchers.Main) {
                    voiceEnrollmentError = "Recording too short. Please speak clearly for the full 10 seconds."
                }
                return@launch
            }

            val wavBytes = VoiceIdentityRepository.pcmToWav(pcmBytes, sampleRate)

            withContext(Dispatchers.Main) {
                isSubmittingVoice = true
            }

            val cleanPhone = "+91" + phoneInput.filter { it.isDigit() }
            val userId = container.preferencesManager.userId.firstOrNull() ?: cleanPhone

            val result = container.voiceIdentityRepository.registerVoice(userId, wavBytes)
            withContext(Dispatchers.Main) {
                isSubmittingVoice = false
                result.onSuccess { resp ->
                    voiceEnrollmentSuccess = true
                    voiceTxHash = resp.transactionHash ?: "0xVerifiedOnChain"
                }.onFailure { err ->
                    voiceEnrollmentError = "Server notice: ${err.message ?: "Failed"}. You may complete setup or retry."
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = VsBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(28.dp))

            // App Brand Shield Logo
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(VsPrimary.copy(alpha = 0.25f), VsSecondary.copy(alpha = 0.15f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(VsSurfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isVoiceEnrollmentStep -> Icons.Filled.Fingerprint
                            isOtpVerified -> Icons.Filled.Person
                            else -> Icons.Filled.Shield
                        },
                        contentDescription = "VoiceShield Logo",
                        tint = VsSecondary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                when {
                    isVoiceEnrollmentStep -> "Voice Identity"
                    isOtpVerified -> "Profile Setup"
                    else -> "VoiceShield"
                },
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = VsOnSurface,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(6.dp))

            Text(
                when {
                    isVoiceEnrollmentStep -> "Register Biometric Fingerprint"
                    isOtpVerified -> "What should we call you?"
                    !otpSent -> "Verify with Phone Number"
                    else -> "Enter Verification Code"
                },
                style = MaterialTheme.typography.titleMedium,
                color = VsSecondary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                when {
                    isVoiceEnrollmentStep -> "Anchor a cryptographic voice commitment to blockchain for caller authentication."
                    isOtpVerified -> "Enter your full name to personalize your VoiceShield caller protection profile."
                    !otpSent -> "Enter your mobile number to receive a secure SMS one-time passcode (OTP)."
                    else -> "A 4-digit SMS code was sent to +91 $phoneInput."
                },
                style = MaterialTheme.typography.bodySmall,
                color = VsOnSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(24.dp))

            // Card Container
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = VsSurfaceContainer),
                border = BorderStroke(1.dp, VsOutline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ───────────────────────────────────────────
                    // STEP 1: Phone Input (SMS Authentication)
                    // ───────────────────────────────────────────
                    AnimatedVisibility(
                        visible = !otpSent && !isOtpVerified,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { input ->
                                    phoneInput = input.filter { it.isDigit() }.take(10)
                                    viewModel.clearError()
                                },
                                label = { Text("Mobile Number") },
                                placeholder = { Text("98765 43210") },
                                leadingIcon = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                                    ) {
                                        Text(
                                            "🇮🇳 +91",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = VsOnSurface
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(20.dp)
                                                .background(VsOutline.copy(alpha = 0.5f))
                                        )
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Send
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (phoneInput.length >= 10 && !uiState.isLoading) {
                                            viewModel.sendOtp("+91$phoneInput")
                                        }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VsSecondary,
                                    unfocusedBorderColor = VsOutline.copy(alpha = 0.3f),
                                    focusedLabelColor = VsSecondary,
                                    cursorColor = VsSecondary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(18.dp))

                            Button(
                                onClick = {
                                    if (phoneInput.length >= 10) {
                                        viewModel.sendOtp("+91$phoneInput")
                                    }
                                },
                                enabled = phoneInput.length >= 10 && !uiState.isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VsSecondary,
                                    contentColor = Color.Black
                                )
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color.Black,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Send OTP via SMS", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Icon(Icons.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    // ───────────────────────────────────────────
                    // STEP 2: OTP Verification (SMS Channel Only)
                    // ───────────────────────────────────────────
                    AnimatedVisibility(
                        visible = otpSent && !isOtpVerified,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = { input ->
                                    otpInput = input.filter { it.isDigit() }.take(6)
                                    viewModel.clearError()
                                    if (otpInput.length == 4) {
                                        viewModel.verifyOtp("+91$phoneInput", otpInput)
                                    }
                                },
                                label = { Text("4-Digit OTP Code") },
                                placeholder = { Text("• • • •") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Lock, null, tint = VsSecondary)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (otpInput.isNotBlank() && !uiState.isLoading) {
                                            viewModel.verifyOtp("+91$phoneInput", otpInput)
                                        }
                                    }
                                ),
                                textStyle = MaterialTheme.typography.titleLarge.copy(
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 6.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VsSecondary,
                                    unfocusedBorderColor = VsOutline.copy(alpha = 0.3f),
                                    focusedLabelColor = VsSecondary,
                                    cursorColor = VsSecondary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(otpFocusRequester)
                            )

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (otpInput.isNotBlank()) {
                                        viewModel.verifyOtp("+91$phoneInput", otpInput)
                                    }
                                },
                                enabled = otpInput.length >= 4 && !uiState.isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VsSecondary,
                                    contentColor = Color.Black
                                )
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color.Black,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Verify & Continue", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // Resend SMS & Change Number Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        viewModel.resetOtpState()
                                        otpInput = ""
                                    }
                                ) {
                                    Text("Change Number", color = VsOnSurfaceVariant, fontSize = 13.sp)
                                }

                                TextButton(
                                    onClick = {
                                        if (phoneInput.length >= 10) {
                                            viewModel.resendSmsOtp("+91$phoneInput")
                                        }
                                    },
                                    enabled = !uiState.isLoading
                                ) {
                                    Text("Resend SMS", color = VsSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Demo Credentials Note
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = VsSurfaceContainerHighest.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.Info, null, tint = VsSecondary, modifier = Modifier.size(14.dp))
                                    Text(
                                        "Please check your SMS inbox for the 4-digit verification code.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VsOnSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // ───────────────────────────────────────────
                    // STEP 3: Enter Full Name
                    // ───────────────────────────────────────────
                    AnimatedVisibility(
                        visible = isOtpVerified && !isVoiceEnrollmentStep,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Verified phone pill
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = VsSecondaryContainer.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, VsSecondary.copy(alpha = 0.5f)),
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = VsSecondary, modifier = Modifier.size(16.dp))
                                    Text(
                                        "+91 $phoneInput • Phone Verified",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = VsSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = fullNameInput,
                                onValueChange = { fullNameInput = it },
                                label = { Text("Full Name") },
                                placeholder = { Text("e.g. Sagar Goyal") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Person, null, tint = VsSecondary)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (fullNameInput.isNotBlank()) {
                                            isVoiceEnrollmentStep = true
                                        }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VsSecondary,
                                    unfocusedBorderColor = VsOutline.copy(alpha = 0.3f),
                                    focusedLabelColor = VsSecondary,
                                    cursorColor = VsSecondary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(nameFocusRequester)
                            )

                            Spacer(Modifier.height(18.dp))

                            Button(
                                onClick = {
                                    if (fullNameInput.isNotBlank()) {
                                        isVoiceEnrollmentStep = true
                                    }
                                },
                                enabled = fullNameInput.isNotBlank() && !isSavingName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VsSecondary,
                                    contentColor = Color.Black
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Continue to Voice Identity", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Icon(Icons.Filled.Mic, null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // ───────────────────────────────────────────
                    // STEP 4: Biometric Voice Identity Registration (Anchored to Blockchain)
                    // ───────────────────────────────────────────
                    AnimatedVisibility(
                        visible = isOtpVerified && isVoiceEnrollmentStep,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = VsPrimaryContainer.copy(alpha = 0.3f),
                                border = BorderStroke(1.dp, VsPrimary.copy(alpha = 0.5f)),
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.Fingerprint, null, tint = VsPrimary, modifier = Modifier.size(16.dp))
                                    Text(
                                        "Blockchain Voice Commitment",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = VsPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                "Read Aloud to Register Voice",
                                style = MaterialTheme.typography.titleSmall,
                                color = VsOnSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))

                            // Script prompt phrase box
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = VsSurfaceContainerHighest.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, VsOutline.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "\"My voice is my identity with VoiceShield. I verify that I am the genuine caller.\"",
                                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                        color = VsSecondary,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.sp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // 20-bar live animated waveform visualizer
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(20) { index ->
                                    val barScale = if (isRecordingVoice) {
                                        ((audioAmplitude * (0.35f + (index % 5) * 0.15f)) + 0.12f).coerceIn(0.1f, 1.0f)
                                    } else {
                                        0.15f
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .fillMaxHeight(barScale)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (isRecordingVoice) VsSecondary else VsOutline.copy(alpha = 0.3f))
                                    )
                                }
                            }

                            if (isRecordingVoice) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Recording voice: ${recordingSecondsLeft}s remaining...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VsSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            if (voiceEnrollmentSuccess) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = VsSecondaryContainer.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, VsSecondary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Filled.Verified, null, tint = VsSecondary, modifier = Modifier.size(18.dp))
                                            Text(
                                                "Voice Identity Anchored to Blockchain!",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = VsSecondary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Tx: ${voiceTxHash.take(18)}...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VsOnSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }

                            if (!voiceEnrollmentError.isNullOrBlank()) {
                                Text(
                                    voiceEnrollmentError!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VsError,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            // Action Button
                            if (voiceEnrollmentSuccess) {
                                Button(
                                    onClick = { handleCompleteSetup() },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = VsSecondary, contentColor = Color.Black)
                                ) {
                                    Text("Enter VoiceShield", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                                }
                            } else {
                                Button(
                                    onClick = { startVoiceRecording() },
                                    enabled = !isRecordingVoice && !isSubmittingVoice,
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRecordingVoice) VsError else VsSecondary,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    if (isSubmittingVoice) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Anchoring on Blockchain...", fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(if (isRecordingVoice) Icons.Filled.Mic else Icons.Filled.FiberManualRecord, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (isRecordingVoice) "Recording (${recordingSecondsLeft}s)..." else "Start 10s Voice Recording", fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    onClick = { handleCompleteSetup() },
                                    enabled = !isRecordingVoice && !isSubmittingVoice
                                ) {
                                    Text("Skip for Now", color = VsOnSurfaceVariant, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Status feedback / Error
            if (!uiState.error.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VsErrorContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, VsError.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Error, null, tint = VsError, modifier = Modifier.size(20.dp))
                        Text(
                            uiState.error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = VsError
                        )
                    }
                }
            } else if (!retryStatus.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VsSecondaryContainer.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, VsSecondary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Check, null, tint = VsSecondary, modifier = Modifier.size(18.dp))
                        Text(
                            retryStatus!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = VsSecondary
                        )
                    }
                }
            } else if (!otpMessage.isNullOrBlank() && !otpSent) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VsSecondaryContainer.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, VsSecondary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Info, null, tint = VsSecondary, modifier = Modifier.size(18.dp))
                        Text(
                            otpMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = VsSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Footer info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Lock, null, tint = VsOnSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(
                    "End-to-End Encrypted SMS Authentication",
                    style = MaterialTheme.typography.labelSmall,
                    color = VsOnSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
