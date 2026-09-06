package com.sagar.voice_shield.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sagar.voice_shield.data.local.room.CallHistoryEntity
import androidx.navigation.NavController
import com.sagar.voice_shield.navigation.Screen
import com.sagar.voice_shield.ui.theme.*

data class CallHistoryItem(
    val name: String,
    val phone: String,
    val time: String,
    val simInfo: String,
    val riskType: String, // "safe", "blocked", "warning"
    val riskLabel: String,
    val riskDetail: String,
    val duration: String? = null,
    val isIncoming: Boolean = true,
    val deepfakePercent: Int? = null
)

@Composable
fun RecentsScreen(navController: NavController) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as com.sagar.voice_shield.VoiceShieldApp).appContainer
    val dbCalls by appContainer.callHistoryDao.getAllCalls().collectAsStateWithLifecycle(initialValue = emptyList<CallHistoryEntity>())

    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Missed", "AI Flagged", "Blocked")

    val realItems = remember(dbCalls) {
        dbCalls.map { entity ->
            val date = java.util.Date(entity.timestamp)
            val timeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
            val timeStr = timeFormat.format(date)

            val riskType = when {
                entity.isBlocked || entity.riskScore > 66 -> "blocked"
                entity.riskScore > 33 -> "warning"
                else -> "safe"
            }

            val riskLabel = when (riskType) {
                "blocked" -> "AI Clone"
                "warning" -> "High Risk"
                else -> "Verified"
            }

            val riskDetail = when (riskType) {
                "blocked" -> "BLOCKED • ${(entity.deepfakeProbability * 100).toInt().coerceAtLeast(85)}% AI Voice Clone"
                "warning" -> "Warning • Acoustic Anomaly Flagged"
                else -> "Verified Acoustic Profile"
            }

            val minutes = entity.durationSeconds / 60
            val seconds = entity.durationSeconds % 60
            val durStr = if (entity.durationSeconds > 0) "${minutes}m ${seconds}s" else "Missed"

            CallHistoryItem(
                name = entity.callerName.ifBlank { entity.callerNumber },
                phone = entity.callerNumber,
                time = timeStr,
                simInfo = "VoiceShield Secure",
                riskType = riskType,
                riskLabel = riskLabel,
                riskDetail = riskDetail,
                duration = durStr,
                isIncoming = true,
                deepfakePercent = (entity.deepfakeProbability * 100).toInt()
            )
        }
    }

    val sampleToday = listOf(
        CallHistoryItem(
            name = "Rahul Kumar", phone = "+91 98765 43210", time = "12:40 PM",
            simInfo = "Airtel SIM 2", riskType = "safe", riskLabel = "Son",
            riskDetail = "Verified Voice Match", duration = "4m 12s"
        ),
        CallHistoryItem(
            name = "Electricity Bill Dept", phone = "+91 80000 22221", time = "10:15 AM",
            simInfo = "Spam Risk 78%", riskType = "warning", riskLabel = "",
            riskDetail = "Robotic Cadence Flagged"
        )
    )

    val sampleEarlier = listOf(
        CallHistoryItem(
            name = "Dr. Mehta", phone = "+91 98111 22233", time = "17:30",
            simInfo = "+91 98111 22233", riskType = "safe", riskLabel = "",
            riskDetail = "Safe Acoustic Profile", isIncoming = false
        )
    )

    val allCalls = if (realItems.isNotEmpty()) realItems else sampleToday + sampleEarlier

    val filteredCalls = remember(allCalls, selectedFilter) {
        when (selectedFilter) {
            "Missed" -> allCalls.filter { it.duration == "Missed" }
            "AI Flagged" -> allCalls.filter { it.riskType == "warning" || it.riskType == "blocked" }
            "Blocked" -> allCalls.filter { it.riskType == "blocked" }
            else -> allCalls
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VsBackground),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Filter Chips
        item {
            LazyRow(
                modifier = Modifier.padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = if (selectedFilter == filter) {
                            { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                        } else if (filter == "AI Flagged") {
                            { PulsingDot(color = VsTertiaryContainer) }
                        } else if (filter == "Blocked") {
                            { Box(Modifier.size(6.dp).clip(CircleShape).background(VsError)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VsPrimaryContainer,
                            selectedLabelColor = VsOnPrimaryContainer,
                            containerColor = VsSurfaceContainerHigh,
                            labelColor = VsOnSurfaceVariant
                        ),
                        shape = RoundedCornerShape(50)
                    )
                }
            }
        }

        // Telemetry Banner
        item {
            TelemetryBanner(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Recent calls section header
        item {
            SectionHeader(
                title = if (realItems.isNotEmpty()) "Recent Calls (${filteredCalls.size})" else "Today",
                subtitle = "Real-time Biometrics"
            )
        }

        // Call items
        items(filteredCalls) { call ->
            CallCard(
                call = call,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                onCallClick = {
                    navController.navigate(Screen.ActiveCall.createRoute(phone = call.phone, name = call.name))
                }
            )
        }

        // Floating status pill
        item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = VsSurfaceContainerHigh,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PulsingDot(color = VsSecondary)
                        Text(
                            "Continuous Biometric Model v4.2 Running",
                            style = MaterialTheme.typography.labelSmall,
                            color = VsOnSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VsSurfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Shield icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(VsSecondaryContainer.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.VerifiedUser, null, tint = VsSecondary, modifier = Modifier.size(24.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("VoiceShield Telemetry", style = MaterialTheme.typography.headlineSmall, color = VsOnSurface)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = VsSecondaryContainer.copy(alpha = 0.2f)
                    ) {
                        Text(
                            "Active",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = VsSecondary
                        )
                    }
                }
                Text(
                    "14 calls scanned today • 1 AI synthetic voice blocked",
                    style = MaterialTheme.typography.bodySmall,
                    color = VsOnSurfaceVariant
                )
            }

            IconButton(
                onClick = {},
                modifier = Modifier.size(32.dp).clip(CircleShape).background(VsSurfaceContainerHighest)
            ) {
                Icon(Icons.Filled.Insights, null, tint = VsOnSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = VsOnSurfaceVariant,
            letterSpacing = 2.sp
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = VsOutline
        )
    }
}

@Composable
fun CallCard(
    call: CallHistoryItem,
    modifier: Modifier = Modifier,
    onCallClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VsSurfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Avatar
                    Box(contentAlignment = Alignment.Center) {
                        val (bgColor, iconColor) = when (call.riskType) {
                            "blocked" -> VsErrorContainer.copy(alpha = 0.3f) to VsError
                            "warning" -> VsTertiaryContainer.copy(alpha = 0.3f) to VsTertiary
                            else -> VsPrimaryContainer.copy(alpha = 0.2f) to VsPrimary
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(bgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            when (call.riskType) {
                                "blocked" -> Icon(Icons.Filled.Block, null, tint = iconColor, modifier = Modifier.size(24.dp))
                                "warning" -> Icon(Icons.Filled.Warning, null, tint = iconColor, modifier = Modifier.size(22.dp))
                                else -> {
                                    val initials = call.name.split(" ").take(2).map { it.firstOrNull()?.uppercase() ?: "" }.joinToString("")
                                    Text(initials.ifEmpty { "?" }, style = MaterialTheme.typography.titleMedium, color = iconColor)
                                }
                            }
                        }
                        // Status badge
                        val badgeColor = when (call.riskType) {
                            "blocked" -> VsError
                            "warning" -> VsTertiaryContainer
                            else -> VsSecondary
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 2.dp, y = 2.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(badgeColor),
                            contentAlignment = Alignment.Center
                        ) {
                            when (call.riskType) {
                                "safe" -> Icon(Icons.Filled.Verified, null, tint = VsOnSecondary, modifier = Modifier.size(10.dp))
                                else -> Text("!", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.White)
                            }
                        }
                    }

                    // Details
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                call.name,
                                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 16.sp),
                                color = VsOnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (call.riskLabel.isNotEmpty()) {
                                val labelColor = when (call.riskType) {
                                    "blocked" -> VsError
                                    "warning" -> VsTertiary
                                    else -> VsOnSurfaceVariant
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (call.riskType == "blocked") VsErrorContainer.copy(alpha = 0.4f) else VsSurfaceVariant
                                ) {
                                    Text(
                                        call.riskLabel,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = labelColor
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(2.dp))

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (call.riskType == "safe" && call.isIncoming) {
                                Icon(Icons.Filled.CallReceived, null, tint = VsSecondary, modifier = Modifier.size(15.dp))
                            } else if (!call.isIncoming) {
                                Icon(Icons.Filled.CallMade, null, tint = VsPrimaryContainer, modifier = Modifier.size(15.dp))
                            }
                            val detailColor = when (call.riskType) {
                                "blocked" -> VsError
                                "warning" -> VsTertiary
                                "safe" -> VsSecondary
                                else -> VsOnSurfaceVariant
                            }
                            Text(
                                call.riskDetail,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = detailColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (call.duration != null) {
                                Text("• ${call.duration}", style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(call.time, style = MaterialTheme.typography.labelSmall, color = VsOnSurfaceVariant)
                            Text("•", style = MaterialTheme.typography.labelSmall, color = VsOnSurfaceVariant)
                            if (call.riskType == "warning") {
                                Text(call.simInfo, style = MaterialTheme.typography.labelSmall, color = VsTertiary, fontWeight = FontWeight.Medium)
                            } else {
                                Text(call.simInfo, style = MaterialTheme.typography.labelSmall, color = VsOnSurfaceVariant)
                            }
                        }
                    }
                }

                // Action button
                IconButton(
                    onClick = onCallClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (call.riskType == "safe") VsSecondaryContainer.copy(alpha = 0.2f) else VsSurfaceContainerHighest)
                ) {
                    Icon(
                        if (call.riskType == "safe") Icons.Filled.Call
                        else if (call.riskType == "blocked") Icons.Filled.Info
                        else Icons.Filled.Flag,
                        null,
                        tint = if (call.riskType == "safe") VsSecondary
                        else if (call.riskType == "warning") VsTertiary
                        else VsOnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Threat breakdown for blocked calls
            if (call.riskType == "blocked") {
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = {},
                    shape = RoundedCornerShape(8.dp),
                    color = VsSurfaceContainerHighest.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.GraphicEq, null, tint = VsError, modifier = Modifier.size(18.dp))
                            Text("Acoustic synthesis anomaly detected", style = MaterialTheme.typography.bodySmall, color = VsError)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("VIEW TELEMETRY", style = MaterialTheme.typography.labelSmall, color = VsOnSurfaceVariant)
                            Icon(Icons.Filled.ChevronRight, null, tint = VsOnSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PulsingDot(color: Color, size: Int = 8) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}
