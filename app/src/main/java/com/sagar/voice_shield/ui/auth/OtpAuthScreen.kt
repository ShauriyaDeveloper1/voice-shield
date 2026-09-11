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
                        imageVector = if (isOtpVerified) Icons.Filled.Person else Icons.Filled.Shield,
                        contentDescription = "VoiceShield Logo",
                        tint = VsSecondary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                if (isOtpVerified) "Profile Setup" else "VoiceShield",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = VsOnSurface,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(6.dp))

            Text(
                when {
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
                    // STEP 3: Enter Full Name (Always shows after OTP verified)
                    // ───────────────────────────────────────────
                    AnimatedVisibility(
                        visible = isOtpVerified,
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
                                    onDone = { handleCompleteSetup() }
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
                                onClick = { handleCompleteSetup() },
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
                                if (isSavingName) {
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
                                        Text("Complete Setup", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Icon(Icons.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                                    }
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
