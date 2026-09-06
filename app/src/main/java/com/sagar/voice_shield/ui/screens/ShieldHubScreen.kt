package com.sagar.voice_shield.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.sagar.voice_shield.R
import com.sagar.voice_shield.navigation.Screen
import com.sagar.voice_shield.ui.theme.*

@Composable
fun ShieldHubScreen(navController: NavController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(VsBackground),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Protection Status Hero
        item {
            Card(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = VsSurfaceContainerLow),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Large shield indicator
                    Box(
                        modifier = Modifier.size(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Animated ring
                        val infiniteTransition = rememberInfiniteTransition(label = "shield")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f, targetValue = 360f,
                            animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
                            label = "rotation"
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            drawArc(
                                color = VsSecondary.copy(alpha = 0.3f),
                                startAngle = 0f, sweepAngle = 360f,
                                useCenter = false, style = stroke,
                                topLeft = Offset(8f, 8f),
                                size = Size(size.width - 16f, size.height - 16f)
                            )
                            drawArc(
                                brush = Brush.sweepGradient(listOf(VsSecondary, VsPrimary, VsSecondary)),
                                startAngle = rotation, sweepAngle = 120f,
                                useCenter = false, style = stroke,
                                topLeft = Offset(8f, 8f),
                                size = Size(size.width - 16f, size.height - 16f)
                            )
                        }

                        Box(
                            modifier = Modifier.size(80.dp).clip(CircleShape)
                                .background(Brush.radialGradient(listOf(VsSecondary.copy(alpha = 0.15f), Color.Transparent))),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = "VoiceShield Protection Active",
                                modifier = Modifier.size(60.dp).clip(CircleShape)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text("Protection Active", style = MaterialTheme.typography.headlineMedium, color = VsSecondary, fontWeight = FontWeight.Bold)
                    Text("All defense layers operational", style = MaterialTheme.typography.bodyMedium, color = VsOnSurfaceVariant)

                    Spacer(Modifier.height(20.dp))

                    // Stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatBadge("128", "Calls\nScanned", VsPrimary)
                        StatBadge("7", "Threats\nBlocked", VsError)
                        StatBadge("94.8%", "Avg\nConfidence", VsSecondary)
                    }
                }
            }
        }

        // Section: Defense Layers
        item {
            Text("DEFENSE LAYERS", modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelMedium, color = VsOnSurfaceVariant, letterSpacing = 2.sp)
        }

        // Defense layer cards
        item { DefenseLayerCard("Deepfake Detection", "AASIST Neural Network", "Active", VsSecondary, Icons.Filled.Psychology, "v4.2 • On-device") }
        item { DefenseLayerCard("Prosody Analysis", "Speech Rhythm & Pitch DSP", "Active", VsSecondary, Icons.Filled.GraphicEq, "Real-time F0 tracking") }
        item { DefenseLayerCard("Social Engineering", "XLM-Roberta NLP Classifier", "Active", VsPrimary, Icons.Filled.TextSnippet, "Cloud inference") }
        item { DefenseLayerCard("Speaker Verification", "Voice Embedding Match", "Active", VsSecondary, Icons.Filled.RecordVoiceOver, "Cosine similarity") }

        // Speaker Protection Mode
        item {
            Card(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = VsSurfaceContainer),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(VsPrimaryContainer.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.SpeakerPhone, null, tint = VsPrimary, modifier = Modifier.size(24.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Speaker Protection Mode", style = MaterialTheme.typography.titleSmall, color = VsOnSurface)
                            Text("Analyze calls from WhatsApp, Telegram & more", style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Captures acoustic audio through your microphone when speakerphone is enabled. Works with any calling app.",
                        style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { navController.navigate(Screen.SpeakerProtection.route) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VsPrimaryContainer, contentColor = VsOnSurface)
                    ) {
                        Icon(Icons.Filled.Shield, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Enable Speaker Protection", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Model Telemetry
        item {
            Text("MODEL TELEMETRY", modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelMedium, color = VsOnSurfaceVariant, letterSpacing = 2.sp)
        }

        item {
            Card(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = VsSurfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TelemetryRow("AASIST Model", "v4.2 Lite", "426 KB", VsSecondary)
                    HorizontalDivider(color = VsSurfaceContainerHighest)
                    TelemetryRow("Prosody Engine", "DSP-based", "0 KB", VsPrimary)
                    HorizontalDivider(color = VsSurfaceContainerHighest)
                    TelemetryRow("Whisper STT", "tiny.en", "39 MB", VsPrimary)
                    HorizontalDivider(color = VsSurfaceContainerHighest)
                    TelemetryRow("XLM-Roberta", "Cloud API", "Remote", VsOnSurfaceVariant)
                    HorizontalDivider(color = VsSurfaceContainerHighest)
                    TelemetryRow("Risk Engine", "v1.0", "On-device", VsSecondary)
                }
            }
        }

        // Bottom pill
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(50), color = VsSurfaceContainerHigh, shadowElevation = 8.dp) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PulsingDot(color = VsSecondary)
                        Text("VoiceShield Engine Active • All Models Loaded", style = MaterialTheme.typography.labelSmall, color = VsOnSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun StatBadge(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.displaySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = VsOnSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 14.sp)
    }
}

@Composable
fun DefenseLayerCard(title: String, subtitle: String, status: String, statusColor: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, detail: String) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VsSurfaceContainer)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = statusColor, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = VsOnSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = VsOutline)
            }
            Surface(shape = RoundedCornerShape(50), color = statusColor.copy(alpha = 0.15f)) {
                Text(status, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall, color = statusColor)
            }
        }
    }
}

@Composable
fun TelemetryRow(name: String, version: String, size: String, statusColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = VsOnSurface)
            Text(version, style = MaterialTheme.typography.labelSmall, color = VsOnSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(size, style = MaterialTheme.typography.labelSmall, color = VsOutline)
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
        }
    }
}
