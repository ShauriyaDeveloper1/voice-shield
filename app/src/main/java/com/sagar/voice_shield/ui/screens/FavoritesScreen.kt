package com.sagar.voice_shield.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.sagar.voice_shield.navigation.Screen
import com.sagar.voice_shield.ui.theme.*

data class FavoriteContact(
    val name: String,
    val phone: String,
    val relation: String,
    val voiceEnrolled: Boolean = true,
    val trustLevel: String = "HIGH", // HIGH, MEDIUM, LOW
    val lastVerified: String = "2 days ago"
)

@Composable
fun FavoritesScreen(navController: NavController) {
    val favorites = listOf(
        FavoriteContact("Rahul Kumar", "+91 98765 43210", "Son", true, "HIGH", "Today"),
        FavoriteContact("Priya Sharma", "+91 88765 43117", "Wife", true, "HIGH", "Yesterday"),
        FavoriteContact("Dr. Mehta", "+91 98111 22233", "Doctor", true, "MEDIUM", "3 days ago"),
        FavoriteContact("ICICI Bank", "1800 102 2552", "Bank", false, "LOW", "Never"),
        FavoriteContact("Mom", "+91 99887 76655", "Mother", true, "HIGH", "Today"),
        FavoriteContact("Office - HR", "+91 11223 34455", "Work", false, "LOW", "Never")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VsBackground),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Header card
        item {
            Card(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = VsSurfaceContainerLow),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(VsPrimaryContainer.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Star, null, tint = VsPrimary, modifier = Modifier.size(24.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Trusted Favorites", style = MaterialTheme.typography.headlineSmall, color = VsOnSurface)
                        Text(
                            "${favorites.count { it.voiceEnrolled }} voice profiles enrolled • ${favorites.size} contacts",
                            style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.PersonAdd, null, tint = VsPrimary)
                    }
                }
            }
        }

        // Section label
        item {
            Text(
                "PINNED CONTACTS",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = VsOnSurfaceVariant,
                letterSpacing = 2.sp
            )
        }

        // Favorite contacts
        items(favorites) { contact ->
            FavoriteContactCard(
                contact = contact,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                onCallClick = {
                    navController.navigate(Screen.ActiveCall.createRoute(phone = contact.phone, name = contact.name))
                }
            )
        }
    }
}

@Composable
fun FavoriteContactCard(
    contact: FavoriteContact,
    modifier: Modifier = Modifier,
    onCallClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VsSurfaceContainer),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar with voice status
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(
                            if (contact.voiceEnrolled) VsSecondaryContainer.copy(alpha = 0.2f)
                            else VsSurfaceContainerHigh
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = contact.name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
                    Text(initials, style = MaterialTheme.typography.titleMedium, color = if (contact.voiceEnrolled) VsSecondary else VsOnSurfaceVariant)
                }
                if (contact.voiceEnrolled) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp)
                            .size(16.dp).clip(CircleShape).background(VsSecondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Mic, null, tint = VsOnSecondary, modifier = Modifier.size(10.dp))
                    }
                }
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(contact.name, style = MaterialTheme.typography.titleSmall, color = VsOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Surface(shape = RoundedCornerShape(50), color = VsSurfaceVariant) {
                        Text(contact.relation, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall, color = VsOnSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (contact.voiceEnrolled) {
                        Icon(Icons.Filled.RecordVoiceOver, null, tint = VsSecondary, modifier = Modifier.size(14.dp))
                        Text("Voice Enrolled", style = MaterialTheme.typography.bodySmall, color = VsSecondary, fontWeight = FontWeight.Medium)
                    } else {
                        Icon(Icons.Filled.MicOff, null, tint = VsOutline, modifier = Modifier.size(14.dp))
                        Text("No Voice Profile", style = MaterialTheme.typography.bodySmall, color = VsOutline)
                    }
                    Text("•", style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
                    Text(contact.lastVerified, style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                // Trust level indicator
                val trustColor = when (contact.trustLevel) {
                    "HIGH" -> VsSecondary
                    "MEDIUM" -> VsPrimary
                    else -> VsOutline
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { index ->
                        val filled = when (contact.trustLevel) {
                            "HIGH" -> true
                            "MEDIUM" -> index < 2
                            else -> index < 1
                        }
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (filled) trustColor else VsSurfaceContainerHighest)
                        )
                    }
                    Text("Trust: ${contact.trustLevel}", style = MaterialTheme.typography.labelSmall,
                        color = trustColor, modifier = Modifier.padding(start = 4.dp))
                }
            }

            // Call button
            IconButton(
                onClick = onCallClick,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(VsSecondaryContainer.copy(alpha = 0.2f))
            ) {
                Icon(Icons.Filled.Call, null, tint = VsSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
