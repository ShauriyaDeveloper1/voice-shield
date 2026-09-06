package com.sagar.voice_shield.ui.screens

import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.sagar.voice_shield.navigation.Screen
import com.sagar.voice_shield.ui.theme.*

@Composable
fun KeypadScreen(navController: NavController) {
    var phoneNumber by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VsBackground)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Number display
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (phoneNumber.isNotEmpty()) {
                Text(
                    text = phoneNumber,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = if (phoneNumber.length > 12) 24.sp else 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp
                    ),
                    color = VsOnSurface,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "Enter number",
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                    color = VsOnSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        // Dial pad grid
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val keys = listOf(
                listOf("1" to "", "2" to "ABC", "3" to "DEF"),
                listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
                listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
                listOf("*" to "", "0" to "+", "#" to "")
            )

            keys.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { (digit, letters) ->
                        DialKey(
                            digit = digit,
                            letters = letters,
                            onClick = { phoneNumber += digit },
                            onLongClick = {
                                if (digit == "0") phoneNumber += "+"
                            }
                        )
                    }
                }
            }
        }

        // Bottom actions
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 100.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Empty spacer for alignment
                Spacer(modifier = Modifier.size(56.dp))

                // Call button
                Button(
                    onClick = {
                        if (phoneNumber.isNotBlank()) {
                            navController.navigate(Screen.ActiveCall.createRoute(phone = phoneNumber, name = "Outgoing Call"))
                        }
                    },
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VsSecondary,
                        disabledContainerColor = VsSecondary.copy(alpha = 0.3f)
                    ),
                    enabled = phoneNumber.isNotBlank(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Filled.Call, null, modifier = Modifier.size(28.dp), tint = VsOnSecondary)
                }

                // Backspace
                IconButton(
                    onClick = { if (phoneNumber.isNotEmpty()) phoneNumber = phoneNumber.dropLast(1) },
                    modifier = Modifier.size(56.dp),
                    enabled = phoneNumber.isNotEmpty()
                ) {
                    Icon(
                        Icons.Filled.Backspace, null,
                        tint = if (phoneNumber.isNotEmpty()) VsOnSurfaceVariant else VsOnSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // AI protection notice
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PulsingDot(color = VsSecondary, size = 6)
                Text(
                    "AI Protection will scan this call",
                    style = MaterialTheme.typography.labelSmall,
                    color = VsOnSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialKey(
    digit: String,
    letters: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        color = VsSurfaceContainerHigh.copy(alpha = 0.7f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                digit,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 28.sp
                ),
                color = VsOnSurface
            )
            if (letters.isNotEmpty()) {
                Text(
                    letters,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = VsOnSurfaceVariant,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}
