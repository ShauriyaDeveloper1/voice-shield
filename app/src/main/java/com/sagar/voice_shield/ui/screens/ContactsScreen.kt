package com.sagar.voice_shield.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sagar.voice_shield.VoiceShieldApp
import com.sagar.voice_shield.data.local.room.TrustedContactEntity
import com.sagar.voice_shield.navigation.Screen
import com.sagar.voice_shield.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun ContactsScreen(navController: NavController) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as VoiceShieldApp).appContainer
    val dao = appContainer.trustedContactDao
    val syncManager = appContainer.contactsSyncManager
    val scope = rememberCoroutineScope()

    val contactsList by dao.getAllContacts().collectAsStateWithLifecycle(initialValue = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<TrustedContactEntity?>(null) }

    fun runSync() {
        scope.launch {
            isSyncing = true
            val count = syncManager.syncDeviceContacts()
            isSyncing = false
            if (count > 0) {
                Toast.makeText(context, "Synced $count contacts from Google / Phone", Toast.LENGTH_SHORT).show()
            } else if (count == 0) {
                Toast.makeText(context, "All contacts are already up to date", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Please allow contacts permission to sync", Toast.LENGTH_LONG).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            runSync()
        } else {
            Toast.makeText(context, "Contacts permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestSyncOrPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            runSync()
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    val filteredContacts = remember(contactsList, searchQuery) {
        if (searchQuery.isBlank()) contactsList
        else contactsList.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.phone.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = VsPrimary,
                contentColor = VsOnPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = "Add Contact")
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
            // Header summary
            item {
                Card(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VsSurfaceContainerLow),
                    elevation = CardDefaults.cardElevation(3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(VsPrimaryContainer.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Contacts, null, tint = VsPrimary, modifier = Modifier.size(24.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("All Contacts", style = MaterialTheme.typography.titleLarge, color = VsOnSurface, fontWeight = FontWeight.Bold)
                                Text(
                                    "${contactsList.size} contacts • ${contactsList.count { it.source == "GOOGLE" }} from Google",
                                    style = MaterialTheme.typography.bodySmall, color = VsOnSurfaceVariant
                                )
                            }
                            // Google Sync Button
                            FilledTonalButton(
                                onClick = { requestSyncOrPermission() },
                                enabled = !isSyncing,
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = VsPrimary)
                                } else {
                                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sync", style = MaterialTheme.typography.labelMedium)
                                }
                            }
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
                    placeholder = { Text("Search by name or number...", color = VsOnSurfaceVariant, fontSize = 14.sp) },
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
                        if (searchQuery.isBlank()) "ALL CONTACTS (${contactsList.size})" else "SEARCH RESULTS (${filteredContacts.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = VsOnSurfaceVariant,
                        letterSpacing = 2.sp
                    )
                }
            }

            // Empty state
            if (filteredContacts.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (searchQuery.isNotBlank()) Icons.Filled.SearchOff else Icons.Filled.ContactPhone,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = VsOnSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (searchQuery.isNotBlank()) "No contacts match '$searchQuery'"
                            else "No contacts found yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VsOnSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { requestSyncOrPermission() },
                            colors = ButtonDefaults.buttonColors(containerColor = VsPrimary)
                        ) {
                            Icon(Icons.Filled.Sync, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sync Google Contacts")
                        }
                    }
                }
            }

            // Contacts items
            items(filteredContacts, key = { it.id }) { contact ->
                ContactListItem(
                    contact = contact,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    onCallClick = {
                        navController.navigate(Screen.ActiveCall.createRoute(contact.phone, contact.name))
                    },
                    onToggleFavorite = {
                        scope.launch(Dispatchers.IO) {
                            dao.setFavorite(contact.id, !contact.isFavorite)
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
        var nameInput by remember { mutableStateOf("") }
        var phoneInput by remember { mutableStateOf("") }
        var relationInput by remember { mutableStateOf("Friend") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = VsSurfaceContainerHighest,
            title = {
                Text("Add New Contact", style = MaterialTheme.typography.titleLarge, color = VsOnSurface)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Contact Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = relationInput,
                        onValueChange = { relationInput = it },
                        label = { Text("Tag / Relationship (e.g. Family, Work)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank() && phoneInput.isNotBlank()) {
                            val newContact = TrustedContactEntity(
                                id = UUID.randomUUID().toString(),
                                name = nameInput.trim(),
                                phone = phoneInput.trim(),
                                relation = relationInput.trim(),
                                voiceEnrolled = false,
                                trustLevel = "HIGH",
                                isFavorite = false,
                                source = "LOCAL"
                            )
                            scope.launch(Dispatchers.IO) {
                                dao.insertContact(newContact)
                            }
                            showAddDialog = false
                            Toast.makeText(context, "Contact saved", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please enter name and phone number", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VsPrimary)
                ) {
                    Text("Save Contact")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = VsOnSurfaceVariant)
                }
            }
        )
    }

    // Delete Confirmation Dialog
    contactToDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            containerColor = VsSurfaceContainerHighest,
            title = {
                Text("Delete Contact", style = MaterialTheme.typography.titleLarge, color = VsOnSurface)
            },
            text = {
                Text(
                    "Are you sure you want to delete ${contact.name} (${contact.phone})?",
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
fun ContactListItem(
    contact: TrustedContactEntity,
    modifier: Modifier = Modifier,
    onCallClick: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onDeleteClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VsSurfaceContainer),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (contact.isFavorite) VsPrimaryContainer.copy(alpha = 0.3f)
                        else VsSurfaceContainerHigh
                    ),
                contentAlignment = Alignment.Center
            ) {
                val initials = contact.name.split(" ").take(2)
                    .mapNotNull { it.firstOrNull()?.uppercase() }
                    .joinToString("")
                    .ifBlank { "?" }
                Text(
                    initials,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (contact.isFavorite) VsPrimary else VsOnSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        contact.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = VsOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (contact.relation.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (contact.source == "GOOGLE") VsSecondaryContainer.copy(alpha = 0.3f) else VsSurfaceVariant
                        ) {
                            Text(
                                if (contact.relation == "Google Contact") "Google" else contact.relation,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (contact.source == "GOOGLE") VsSecondary else VsOnSurfaceVariant,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    contact.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = VsOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Star / Favorite toggle
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (contact.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "Favorite",
                    tint = if (contact.isFavorite) Color(0xFFFFB300) else VsOnSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Call button
            FilledIconButton(
                onClick = onCallClick,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = VsSecondaryContainer.copy(alpha = 0.4f),
                    contentColor = VsSecondary
                )
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Call", modifier = Modifier.size(20.dp))
            }

            // Delete button
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = VsOnSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
