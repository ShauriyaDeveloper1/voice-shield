package com.sagar.voice_shield.ui.auth

import android.widget.Toast
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sagar.voice_shield.R
import com.sagar.voice_shield.VoiceShieldApp
import com.sagar.voice_shield.ui.theme.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val container = (context.applicationContext as VoiceShieldApp).appContainer
    val viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory(container.authRepository, container.preferencesManager))

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("846997243859-0jjs99qi5odj4e98ckndf2rtrvp1u04o.apps.googleusercontent.com")
            .requestEmail()
            .requestProfile()
            .build()
    }
    val googleSignInClient = remember {
        GoogleSignIn.getClient(context, gso)
    }

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { accountResult ->
        if (accountResult.resultCode == android.app.Activity.RESULT_OK && accountResult.data != null) {
            val selectedEmail = accountResult.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            if (!selectedEmail.isNullOrBlank()) {
                val derivedName = selectedEmail.substringBefore("@")
                    .replace(".", " ")
                    .replace("_", " ")
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

                viewModel.loginWithGoogleAccount(
                    email = selectedEmail,
                    name = derivedName,
                    id = selectedEmail.hashCode().toString(),
                    idToken = null
                )
                container.voipCallManager.updateMyCredentials(phone = "", name = derivedName)
            }
        }
    }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        var handled = false
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                val email = account.email ?: "user@voiceshield.ai"
                val name = account.displayName ?: email.substringBefore("@")
                viewModel.loginWithGoogleAccount(
                    email = email,
                    name = name,
                    id = account.id,
                    idToken = account.idToken
                )
                container.voipCallManager.updateMyCredentials(phone = "", name = name)
                handled = true
            }
        } catch (e: Exception) {
            android.util.Log.w("AUTH_GOOGLE", "GoogleSignIn native failed: ${e.message}")
            try {
                val chooseAccountIntent = android.accounts.AccountManager.newChooseAccountIntent(
                    null, null, arrayOf("com.google"), null, null, null, null
                )
                accountPickerLauncher.launch(chooseAccountIntent)
                handled = true
            } catch (ex: Exception) {
                android.util.Log.e("AUTH_GOOGLE", "AccountPicker fallback failed", ex)
            }
        }

        if (!handled && result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val selectedEmail = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            if (!selectedEmail.isNullOrBlank()) {
                val derivedName = selectedEmail.substringBefore("@")
                    .replace(".", " ")
                    .replace("_", " ")
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

                viewModel.loginWithGoogleAccount(
                    email = selectedEmail,
                    name = derivedName,
                    id = selectedEmail.hashCode().toString(),
                    idToken = null
                )
                container.voipCallManager.updateMyCredentials(phone = "", name = derivedName)
            }
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val verificationStatus by viewModel.verificationStatus.collectAsState()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf("+91") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) onRegisterSuccess()
    }

    LaunchedEffect(verificationStatus) {
        verificationStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
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
                // Brand Header
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

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Create account",
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

                Spacer(modifier = Modifier.height(22.dp))

                // Continue with Google Button at the top
                OutlinedButton(
                    onClick = {
                        try {
                            googleLauncher.launch(googleSignInClient.signInIntent)
                        } catch (e: Exception) {
                            try {
                                val intent = android.accounts.AccountManager.newChooseAccountIntent(
                                    null,
                                    null,
                                    arrayOf("com.google"),
                                    null,
                                    null,
                                    null,
                                    null
                                )
                                googleLauncher.launch(intent)
                            } catch (e2: Exception) {
                                android.util.Log.e("AUTH_GOOGLE", "Failed launching account chooser", e2)
                            }
                        }
                    },
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

                Spacer(modifier = Modifier.height(18.dp))

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

                Spacer(modifier = Modifier.height(18.dp))

                // Error Banner
                AnimatedVisibility(visible = uiState.error != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
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

                // FULL NAME
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "FULL NAME",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VsOnSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Your name", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // EMAIL ADDRESS with Verify button
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "EMAIL ADDRESS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VsOnSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = { Text("you@example.com", fontSize = 14.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                        )
                        Button(
                            onClick = {
                                if (email.isNotBlank()) {
                                    viewModel.sendVerification(email, name, "$countryCode $phoneNumber")
                                }
                            },
                            enabled = email.contains("@"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VsPrimaryContainer,
                                contentColor = VsOnPrimaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text("Verify", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // MOBILE NUMBER (IN +91 selector + phone input)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "MOBILE NUMBER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VsOnSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Country Code Selector
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VsInputFieldBg,
                            border = BorderStroke(1.dp, VsInputBorder),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("IN +91", color = VsOnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Filled.ArrowDropDown, null, tint = VsOnSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }

                        // Phone Number Input
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            placeholder = { Text("99999 99999", fontSize = 14.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                        placeholder = { Text("Choose a password", fontSize = 14.sp) },
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
                            val fullPhone = "$countryCode ${phoneNumber.trim()}"
                            if (name.isNotBlank() && email.isNotBlank() && password.isNotBlank()) {
                                viewModel.register(name, email, fullPhone, password)
                            }
                        })
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Create account Button
                Button(
                    onClick = {
                        val fullPhone = "$countryCode ${phoneNumber.trim()}"
                        viewModel.register(name, email, fullPhone, password)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = name.isNotBlank() && email.isNotBlank() && password.isNotBlank() && !uiState.isLoading,
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
                        Text("Create account", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Footer link
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Already have an account? ", color = VsOnSurfaceVariant, fontSize = 13.sp)
                    Text(
                        "Sign in",
                        color = VsPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onNavigateToLogin() }
                    )
                }
            }
        }
    }
}
