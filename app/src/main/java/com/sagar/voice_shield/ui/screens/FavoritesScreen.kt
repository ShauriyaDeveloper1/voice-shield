package com.sagar.voice_shield.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.sagar.voice_shield.VoiceShieldApp
import com.sagar.voice_shield.data.local.room.TrustedContactEntity
import com.sagar.voice_shield.navigation.Screen
import com.sagar.voice_shield.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun FavoritesScreen(navController: NavController) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as VoiceShieldApp).appContainer
    val dao = appContainer.trustedContactDao
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<TrustedContactEntity?>(null) }

    // Clean up mock dummy test contacts
    LaunchedEffect(Unit) {
        val dummyNames = listOf("Rahul Kumar", "Priya Sharma", "Dr. Mehta", "Mom", "ICICI Bank", "Office - HR")
        scope.launch(Dispatchers.IO) {
            dao.deleteMockContacts(dummyNames)
        }
    }

    // Dynamic contact flow from Room DB (Favorites only)
    val contactsList by if (searchQuery.isBlank()) {
        dao.getFavoriteContacts().collectAsState(initial = emptyList())
    } else {
        dao.searchContacts(searchQuery.trim()).collectAsState(initial = emptyList())
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.Keypad.route) },
                containerColor = VsPrimary,
                contentColor = VsOnPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Filled.Dialpad, contentDescription = "Open Dialpad")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Header card
            item {
                Card(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VsSurfaceContainerLow),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(VsPrimaryContainer.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Star, null, tint = VsPrimary, modifier = Modifier.size(24.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Trusted Favorites", style = MaterialTheme.typography.titleLarge, color = VsOnSurface, fontWeight = FontWeight.Bold)
                            Text(
                                "${contactsList.count { it.voiceEnrolled }} voice profiles enrolled • ${contactsList.size} contacts",
                                style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { navController.navigate(Screen.Keypad.route) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(VsPrimaryContainer.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Filled.Dialpad, "Dialpad", tint = VsPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    placeholder = { Text("Search name or phone number...", color = VsOnSurfaceVariant, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = VsOnSurfaceVariant)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = VsOnSurfaceVariant)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = VsSurfaceContainer,
                        unfocusedContainerColor = VsSurfaceContainer,
                        focusedBorderColor = VsPrimary,
                        unfocusedBorderColor = VsOutlineVariant,
                        focusedTextColor = VsOnSurface,
                        unfocusedTextColor = VsOnSurface
                    )
                )
            }

            // Section label
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (searchQuery.isBlank()) "PINNED CONTACTS" else "SEARCH RESULTS (${contactsList.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = VsOnSurfaceVariant,
                        letterSpacing = 2.sp
                    )
                }
            }

            // Empty state
            if (contactsList.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (searchQuery.isNotBlank()) Icons.Filled.SearchOff else Icons.Filled.Contacts,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = VsOnSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (searchQuery.isNotBlank()) "No favorite contacts match '$searchQuery'" else "No favorite contacts yet\nStar contacts in the Contacts tab to see them here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VsOnSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { navController.navigate(Screen.Contacts.route) },
                            colors = ButtonDefaults.buttonColors(containerColor = VsPrimary)
                        ) {
                            Icon(Icons.Filled.Contacts, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open Contacts Tab")
                        }
                    }
                }
            }

            // Contacts list
            items(contactsList, key = { it.id }) { contact ->
                TrustedContactCard(
                    contact = contact,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    onCallClick = {
                        appContainer.voipCallManager.initiateCall(contact.phone, contact.name)
                        navController.navigate(Screen.ActiveCall.createRoute(phone = contact.phone, name = contact.name)) {
                            launchSingleTop = true
                        }
                    },
                    onDeleteClick = {
                        contactToDelete = contact
                    }
                )
            }
        }
    }

    // Add Contact Dialog
    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, phone, relation, isEnrolled, trustLevel ->
                val newContact = TrustedContactEntity(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    phone = phone.trim(),
                    relation = relation.trim().ifBlank { "Contact" },
                    voiceEnrolled = isEnrolled,
                    trustLevel = trustLevel,
                    lastVerified = "Just now"
                )
                scope.launch(Dispatchers.IO) {
                    dao.insertContact(newContact)
                }
                showAddDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    contactToDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            containerColor = VsSurfaceContainerHighest,
            title = {
                Text("Delete Contact", color = VsOnSurface, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to remove ${contact.name} (${contact.phone}) from your trusted contacts?",
                    color = VsOnSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            dao.deleteById(contact.id)
                        }
                        contactToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VsError)
                ) {
                    Text("Delete", color = VsOnError)
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDelete = null }) {
                    Text("Cancel", color = VsOnSurfaceVariant)
                }
            }
        )
    }
}

@Composable
fun TrustedContactCard(
    contact: TrustedContactEntity,
    modifier: Modifier = Modifier,
    onCallClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {}
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
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (contact.voiceEnrolled) VsSecondaryContainer.copy(alpha = 0.25f)
                            else VsSurfaceContainerHigh
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = contact.name.split(" ").take(2)
                        .mapNotNull { it.firstOrNull()?.uppercase() }
                        .joinToString("")
                        .ifBlank { "?" }
                    Text(initials, style = MaterialTheme.typography.titleMedium, color = if (contact.voiceEnrolled) VsSecondary else VsOnSurfaceVariant)
                }
                if (contact.voiceEnrolled) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(VsSecondary),
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
                    if (contact.relation.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(50), color = VsSurfaceVariant) {
                            Text(
                                contact.relation,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = VsOnSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(contact.phone, style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
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
                                .width(18.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (filled) trustColor else VsSurfaceContainerHighest)
                        )
                    }
                    Text("Trust: ${contact.trustLevel}", style = MaterialTheme.typography.labelSmall,
                        color = trustColor, modifier = Modifier.padding(start = 4.dp))
                }
            }

            // Delete action
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = VsOnSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }

            // Call button
            IconButton(
                onClick = onCallClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(VsSecondaryContainer.copy(alpha = 0.2f))
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Call", tint = VsSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, phone: String, relation: String, isEnrolled: Boolean, trustLevel: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("Family") }
    var voiceEnrolled by remember { mutableStateOf(true) }
    var trustLevel by remember { mutableStateOf("HIGH") }

    val relations = listOf("Family", "Son", "Daughter", "Wife", "Mother", "Doctor", "Work", "Bank", "Friend")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VsSurfaceContainerHighest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = VsPrimary)
                Text("Add Trusted Contact", color = VsOnSurface, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number *") },
                    placeholder = { Text("+91 98765 43210") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Text("Relationship", style = MaterialTheme.typography.labelMedium, color = VsOnSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    relations.forEach { rel ->
                        FilterChip(
                            selected = (relation == rel),
                            onClick = { relation = rel },
                            label = { Text(rel, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VsPrimaryContainer,
                                selectedLabelColor = VsOnPrimaryContainer
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Voiceprint Protected", style = MaterialTheme.typography.bodyMedium, color = VsOnSurface)
                        Text("Enable AI acoustic protection", style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant)
                    }
                    Switch(
                        checked = voiceEnrolled,
                        onCheckedChange = { voiceEnrolled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = VsSecondary, checkedTrackColor = VsSecondaryContainer)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        onAdd(name, phone, relation, voiceEnrolled, trustLevel)
                    }
                },
                enabled = name.isNotBlank() && phone.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = VsPrimary)
            ) {
                Text("Save Contact")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VsOnSurfaceVariant)
            }
        }
    )
}
