package com.example.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    currentProfile: User?,
    contacts: List<Triple<User, UserPresence, Int>>,
    groups: List<Group>,
    requests: List<User>,
    callLogs: List<CallLog>,
    statusStories: Map<String, List<StatusStory>>,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenContactChat: (User) -> Unit,
    onOpenGroupChat: (Group) -> Unit,
    onOpenOceanAI: () -> Unit,
    onOpenStatus: (authorName: String, authorDp: String, stories: List<StatusStory>, isOwn: Boolean) -> Unit,
    onUploadStatus: () -> Unit,
    onOpenAddFriend: () -> Unit,
    onOpenCreateGroup: () -> Unit,
    onOpenMyQr: () -> Unit,
    onOpenScanQr: () -> Unit,
    onOpenEditProfile: () -> Unit,
    onAcceptRequest: (String) -> Unit,
    onDeclineRequest: (String) -> Unit,
    onDeleteContact: (String, String) -> Unit,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Chats, 1: Requests, 2: Calls
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight(),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Top row: Theme toggle & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { scope.launch { drawerState.close() } }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Menu", tint = OceanTextMuted)
                        }
                    }

                    // Profile info centered
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // QR & Edit shortcuts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    onOpenMyQr()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = "My QR", tint = OceanPrimary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    onOpenEditProfile()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .border(3.dp, OceanPrimary, CircleShape)
                        ) {
                            AsyncImage(
                                model = currentProfile?.dpUrl?.ifBlank { "https://picsum.photos/200" } ?: "https://picsum.photos/200",
                                contentDescription = currentProfile?.name ?: "Me",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = currentProfile?.name ?: "User",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "ID: ${currentProfile?.arId ?: ""}",
                            color = OceanPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )

                        if (!currentProfile?.searchNum.isNullOrBlank()) {
                            Text(
                                text = "No: ${currentProfile?.searchNum}",
                                color = OceanSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = currentProfile?.bio ?: "",
                            fontSize = 12.sp,
                            color = OceanTextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    // Logout Button at bottom
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(containerColor = OceanDanger),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Logout", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("OceanClient", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    },
                    navigationIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                            IconButton(onClick = onOpenScanQr) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = OceanPrimary)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenCreateGroup) {
                            Icon(Icons.Default.GroupAdd, contentDescription = "Create Group")
                        }
                        IconButton(onClick = onOpenAddFriend) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add Friend", tint = OceanPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Chat, contentDescription = "Chats") },
                        label = { Text("Chats") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = OceanPrimary,
                            selectedTextColor = OceanPrimary,
                            indicatorColor = OceanPrimary.copy(alpha = 0.15f)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (requests.isNotEmpty()) {
                                        Badge(containerColor = OceanPrimary) {
                                            Text(requests.size.toString(), color = Color.White)
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.People, contentDescription = "Requests")
                            }
                        },
                        label = { Text("Requests") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = OceanPrimary,
                            selectedTextColor = OceanPrimary,
                            indicatorColor = OceanPrimary.copy(alpha = 0.15f)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Call, contentDescription = "Calls") },
                        label = { Text("Calls") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = OceanPrimary,
                            selectedTextColor = OceanPrimary,
                            indicatorColor = OceanPrimary.copy(alpha = 0.15f)
                        )
                    )
                }
            },
            floatingActionButton = {
                if (selectedTab == 0) {
                    FloatingActionButton(
                        onClick = onOpenOceanAI,
                        containerColor = Color.Transparent,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp),
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(OceanPrimary, OceanSecondary)
                                )
                            )
                            .testTag("ocean_ai_fab")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "OceanAI", tint = Color.White)
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (selectedTab) {
                    0 -> ChatsTab(
                        currentProfile = currentProfile,
                        contacts = contacts,
                        groups = groups,
                        statusStories = statusStories,
                        onOpenContactChat = onOpenContactChat,
                        onOpenGroupChat = onOpenGroupChat,
                        onOpenStatus = onOpenStatus,
                        onUploadStatus = onUploadStatus,
                        onDeleteContact = onDeleteContact
                    )
                    1 -> RequestsTab(
                        requests = requests,
                        onAccept = onAcceptRequest,
                        onDecline = onDeclineRequest
                    )
                    2 -> CallsTab(
                        callLogs = callLogs,
                        contacts = contacts.map { it.first }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatsTab(
    currentProfile: User?,
    contacts: List<Triple<User, UserPresence, Int>>,
    groups: List<Group>,
    statusStories: Map<String, List<StatusStory>>,
    onOpenContactChat: (User) -> Unit,
    onOpenGroupChat: (Group) -> Unit,
    onOpenStatus: (authorName: String, authorDp: String, stories: List<StatusStory>, isOwn: Boolean) -> Unit,
    onUploadStatus: () -> Unit,
    onDeleteContact: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Status Row
        item {
            StatusRow(
                currentProfile = currentProfile,
                contacts = contacts.map { it.first },
                statusStories = statusStories,
                onOpenStatus = onOpenStatus,
                onUploadStatus = onUploadStatus
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Groups List
        if (groups.isNotEmpty()) {
            item {
                Text(
                    text = "Groups",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OceanTextMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            items(groups, key = { "grp_${it.id}" }) { group ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenGroupChat(group) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = group.dpUrl.ifBlank { "https://picsum.photos/100" },
                            contentDescription = group.name,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text("Group", fontSize = 12.sp, color = OceanTextMuted)
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(10.dp)) }
        }

        // Contacts List
        if (contacts.isEmpty() && groups.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No contacts yet. Tap + to add friends!", color = OceanTextMuted, fontSize = 14.sp)
                }
            }
        } else {
            items(contacts, key = { it.first.uid }) { (user, presence, unread) ->
                var showDeleteDialog by remember { mutableStateOf(false) }

                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text("Delete Contact") },
                        text = { Text("Do you want to delete ${user.name} from your contact list and clear chat history?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showDeleteDialog = false
                                    onDeleteContact(user.uid, user.name)
                                }
                            ) {
                                Text("Delete", color = OceanDanger, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenContactChat(user) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            AsyncImage(
                                model = user.dpUrl.ifBlank { "https://picsum.photos/100" },
                                contentDescription = user.name,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            val isOnline = presence.state == "online"
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnline) OceanPrimary else Color.Gray)
                                    .border(1.5.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = user.bio.ifBlank { "Hey there! I am using OceanXChat." },
                                fontSize = 12.sp,
                                color = OceanTextMuted,
                                maxLines = 1
                            )
                        }

                        if (unread > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(OceanPrimary)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = unread.toString(),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusRow(
    currentProfile: User?,
    contacts: List<User>,
    statusStories: Map<String, List<StatusStory>>,
    onOpenStatus: (authorName: String, authorDp: String, stories: List<StatusStory>, isOwn: Boolean) -> Unit,
    onUploadStatus: () -> Unit
) {
    val myStories = statusStories[currentProfile?.uid ?: ""] ?: emptyList()

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        // My status item
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable {
                        if (myStories.isNotEmpty()) {
                            onOpenStatus("My Status", currentProfile?.dpUrl ?: "", myStories, true)
                        } else {
                            onUploadStatus()
                        }
                    }
                    .width(68.dp)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .border(
                                width = 2.dp,
                                color = if (myStories.isNotEmpty()) OceanPrimary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .padding(3.dp)
                    ) {
                        AsyncImage(
                            model = currentProfile?.dpUrl?.ifBlank { "https://picsum.photos/100" } ?: "https://picsum.photos/100",
                            contentDescription = "My Status",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(OceanPrimary)
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                            .clickable { onUploadStatus() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Status", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "My Status",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }

        // Friends statuses
        items(contacts) { contact ->
            val stories = statusStories[contact.uid] ?: emptyList()
            if (stories.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            onOpenStatus(contact.name, contact.dpUrl, stories, false)
                        }
                        .width(68.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .border(2.dp, OceanPrimary, CircleShape)
                            .padding(3.dp)
                    ) {
                        AsyncImage(
                            model = contact.dpUrl.ifBlank { "https://picsum.photos/100" },
                            contentDescription = contact.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = contact.name,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun RequestsTab(
    requests: List<User>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit
) {
    if (requests.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No pending friend requests.", color = OceanTextMuted, fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(requests, key = { it.uid }) { sender ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = sender.dpUrl.ifBlank { "https://picsum.photos/100" },
                            contentDescription = sender.name,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sender.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Wants to connect", fontSize = 12.sp, color = OceanTextMuted)
                        }
                        IconButton(
                            onClick = { onDecline(sender.uid) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Decline", tint = OceanDanger)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { onAccept(sender.uid) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(OceanPrimary, CircleShape)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallsTab(
    callLogs: List<CallLog>,
    contacts: List<User>
) {
    if (callLogs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No recent calls.", color = OceanTextMuted, fontSize = 14.sp)
        }
    } else {
        val userMap = remember(contacts) { contacts.associateBy { it.uid } }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(callLogs, key = { it.id }) { log ->
                val peer = userMap[log.peer]
                val peerName = peer?.name ?: "User"
                val peerDp = peer?.dpUrl?.ifBlank { "https://picsum.photos/100" } ?: "https://picsum.photos/100"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = peerDp,
                            contentDescription = peerName,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(peerName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            val timeStr = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault()).format(Date(log.timestamp))
                            Text(
                                text = "${log.type.replaceFirstChar { it.uppercase() }} • $timeStr",
                                fontSize = 12.sp,
                                color = OceanTextMuted
                            )
                        }
                        Icon(
                            imageVector = if (log.type == "video") Icons.Default.Videocam else Icons.Default.Phone,
                            contentDescription = log.type,
                            tint = OceanPrimary
                        )
                    }
                }
            }
        }
    }
}
