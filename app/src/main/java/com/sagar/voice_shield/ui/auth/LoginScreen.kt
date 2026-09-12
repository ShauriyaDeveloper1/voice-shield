package com.sagar.voice_shield.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sagar.voice_shield.R
import com.sagar.voice_shield.VoiceShieldApp
import com.sagar.voice_shield.ui.theme.*

import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.sagar.voice_shield.BuildConfig
import kotlinx.coroutines.launch

@Composable
fun GoogleLogoIcon(modifier: Modifier = Modifier.size(20.dp)) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            Icons.Filled.AccountCircle,
            contentDescription = "Google",
            tint = Color(0xFFEA4335),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as VoiceShieldApp).appContainer
    val viewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(appContainer.authRepository, appContainer.preferencesManager)
    )

    val credentialManager = remember { CredentialManager.create(context) }
    val coroutineScope = rememberCoroutineScope()

    fun launchGoogleSignIn() {
        coroutineScope.launch {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = context as android.app.Activity
                )

                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
                val email = googleIdTokenCredential.id
                val name = googleIdTokenCredential.displayName ?: email.substringBefore("@")
                val idToken = googleIdTokenCredential.idToken

                viewModel.loginWithGoogleAccount(
                    email = email,
                    name = name,
                    id = googleIdTokenCredential.id.hashCode().toString(),
                    idToken = idToken
                )
            } catch (e: GetCredentialCancellationException) {
                android.util.Log.d("AUTH_GOOGLE", "User cancelled Google Sign-In")
            } catch (e: Exception) {
                android.util.Log.e("AUTH_GOOGLE", "Google Sign-In failed", e)
                viewModel.setAuthError("Google Sign-In failed: ${e.message}")
            }
        }
    }


    val uiState by viewModel.uiState.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) onLoginSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VsBackground),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = VsDarkCardBg),
            border = BorderStroke(1.dp, VsInputBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // VoiceShield Branding
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "VoiceShield Logo",
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Text(
                        "VoiceShield",
                        color = VsOnSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Heading
                Text(
                    "Sign in",
                    color = VsOnSurface,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Monitor calls and keep conversations safer.",
                    color = VsOnSurfaceVariant,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Error Banner
                AnimatedVisibility(visible = uiState.error != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = VsErrorContainer.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Error, null, tint = VsError, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(uiState.error ?: "", color = VsError, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VsPrimary,
                    unfocusedBorderColor = VsInputBorder,
                    focusedContainerColor = VsInputFieldBg,
                    unfocusedContainerColor = VsInputFieldBg,
                    cursorColor = VsPrimary,
                    focusedTextColor = VsOnSurface,
                    unfocusedTextColor = VsOnSurface,
                    focusedPlaceholderColor = VsOnSurfaceVariant.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = VsOnSurfaceVariant.copy(alpha = 0.6f)
                )

                // NAME OR EMAIL
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "NAME OR EMAIL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VsOnSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        placeholder = { Text("Enter your name or email", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // PASSWORD
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "PASSWORD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VsOnSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Your password", fontSize = 14.sp) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    null,
                                    tint = VsOnSurfaceVariant
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            if (username.isNotBlank() && password.isNotBlank()) viewModel.login(username, password)
                        })
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Sign in Button
                Button(
                    onClick = { viewModel.login(username, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = username.isNotBlank() && password.isNotBlank() && !uiState.isLoading,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VsPrimary,
                        contentColor = VsOnPrimary,
                        disabledContainerColor = VsPrimary.copy(alpha = 0.4f),
                        disabledContentColor = VsOnPrimary.copy(alpha = 0.6f)
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = VsOnPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Sign in", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // OR Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = VsInputBorder)
                    Text(
                        "OR",
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = VsOnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = VsInputBorder)
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Continue with Google Button
                OutlinedButton(
                    onClick = { launchGoogleSignIn() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = VsInputFieldBg,
                        contentColor = VsOnSurface
                    ),
                    border = BorderStroke(1.dp, VsInputBorder)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        GoogleLogoIcon()
                        Spacer(Modifier.width(10.dp))
                        Text("Continue with Google", color = VsOnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Footer link
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("New here? ", color = VsOnSurfaceVariant, fontSize = 13.sp)
                    Text(
                        "Create an account",
                        color = VsPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onNavigateToRegister() }
                    )
                }
            }
        }
    }
}
