package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.audio.SoundManager
import com.example.data.model.*
import com.example.data.repository.FirebaseManager
import com.example.data.repository.OceanRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.ProfileSetupScreen
import com.example.ui.call.ActiveCallActivity
import com.example.ui.call.IncomingCallActivity
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.OceanAIScreen
import com.example.ui.dialogs.AddFriendDialog
import com.example.ui.dialogs.CreateGroupDialog
import com.example.ui.dialogs.EditProfileDialog
import com.example.ui.dialogs.QrCodeDialog
import com.example.ui.main.MainScreen
import com.example.ui.status.StatusViewer
import com.example.ui.theme.OceanPrimary
import com.example.ui.theme.OceanXChatTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class Screen {
    AUTH,
    PROFILE_SETUP,
    MAIN,
    CHAT,
    OCEAN_AI
}

class MainActivity : ComponentActivity() {

    private val repository = OceanRepository()
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        FirebaseManager.init(applicationContext)
        soundManager = SoundManager(this)

        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }

            OceanXChatTheme(darkTheme = isDarkTheme) {
                AppRoot(
                    activity = this,
                    repository = repository,
                    soundManager = soundManager,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }

    override fun onDestroy() {
        soundManager.stopAll()
        super.onDestroy()
    }
}

@Composable
fun AppRoot(
    activity: MainActivity,
    repository: OceanRepository,
    soundManager: SoundManager,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    var firebaseUser by remember { mutableStateOf(auth.currentUser) }

    // Runtime permissions launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionsLauncher.launch(needed.toTypedArray())
        }
    }

    // State flows
    val currentUid = firebaseUser?.uid
    val profile: User? by remember(currentUid) {
        if (currentUid != null) repository.getProfileFlow(currentUid) else kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)

    val contactsWithPresence: List<Triple<User, UserPresence, Int>> by remember(currentUid) {
        if (currentUid != null) repository.getContactsWithPresenceAndUnreadFlow(currentUid) else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val groups: List<Group> by remember(currentUid) {
        if (currentUid != null) repository.getGroupsFlow(currentUid) else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val requests: List<User> by remember(currentUid) {
        if (currentUid != null) repository.getFriendRequestsFlow(currentUid) else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val callLogs: List<CallLog> by remember(currentUid) {
        if (currentUid != null) repository.getCallLogsFlow() else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val statusStories: Map<String, List<StatusStory>> by remember {
        repository.getStatusStoriesFlow()
    }.collectAsState(initial = emptyMap())

    // Foreground incoming call watcher
    val incomingCallData: IncomingCall? by remember(currentUid) {
        if (currentUid != null) repository.getIncomingCallFlow(currentUid) else kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)

    // Active Navigation and Dialog state
    var currentScreen by remember { mutableStateOf(if (firebaseUser != null) Screen.MAIN else Screen.AUTH) }
    var activeChatId by remember { mutableStateOf("") }
    var activeChatUser by remember { mutableStateOf<User?>(null) }
    var isActiveChatGroup by remember { mutableStateOf(false) }

    // Dialogs
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var qrDialogData by remember { mutableStateOf<Pair<String, String>?>(null) } // Pair(title, qrData)
    var activeStatusViewing by remember { mutableStateOf<Pair<User, List<StatusStory>>?>(null) }
    var isViewingOwnStatus by remember { mutableStateOf(false) }

    // Manage FCM token and presence upon login
    LaunchedEffect(currentUid) {
        if (currentUid != null) {
            FirebaseManager.setUserPresence(currentUid)
            FirebaseManager.registerFcmToken(activity)
        }
    }

    // Handle incoming call if received while app is running
    LaunchedEffect(incomingCallData) {
        val incoming = incomingCallData
        if (incoming != null && incoming.status == "ringing") {
            val intent = Intent(activity, IncomingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("callId", incoming.callId)
                putExtra("callerUid", incoming.caller)
                putExtra("callerName", incoming.callerName)
                putExtra("callerDp", incoming.callerDp)
                putExtra("callType", incoming.type)
            }
            activity.startActivity(intent)
        }
    }

    // Main screen router
    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            Screen.AUTH -> {
                AuthScreen(
                    onLoginSuccess = {
                        firebaseUser = auth.currentUser
                        currentScreen = Screen.MAIN
                    },
                    onNeedProfileSetup = {
                        firebaseUser = auth.currentUser
                        currentScreen = Screen.PROFILE_SETUP
                    },
                    onLoginClick = { rawId, pass ->
                        repository.login(rawId, pass)
                    },
                    onSignUpClick = { rawId, pass ->
                        repository.signUp(rawId, pass)
                    }
                )
            }

            Screen.PROFILE_SETUP -> {
                ProfileSetupScreen(
                    onSaveProfile = { name, bio, dpUrl ->
                        repository.saveProfile(name, bio, dpUrl)
                    },
                    onSetupDone = {
                        currentScreen = Screen.MAIN
                    }
                )
            }

            Screen.MAIN -> {
                MainScreen(
                    currentProfile = profile,
                    contacts = contactsWithPresence,
                    groups = groups,
                    requests = requests,
                    callLogs = callLogs,
                    statusStories = statusStories,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onOpenContactChat = { peer ->
                        activeChatId = peer.uid
                        activeChatUser = peer
                        isActiveChatGroup = false
                        currentScreen = Screen.CHAT
                    },
                    onOpenGroupChat = { grp ->
                        activeChatId = grp.id
                        activeChatUser = User(uid = grp.id, name = grp.name, dpUrl = grp.dpUrl)
                        isActiveChatGroup = true
                        currentScreen = Screen.CHAT
                    },
                    onOpenOceanAI = {
                        currentScreen = Screen.OCEAN_AI
                    },
                    onOpenStatus = { name, dp, stories, isOwn ->
                        isViewingOwnStatus = isOwn
                        activeStatusViewing = Pair(User(uid = "", name = name, dpUrl = dp), stories)
                    },
                    onUploadStatus = {
                        val uid = currentUid ?: return@MainScreen
                        scope.launch {
                            repository.uploadStatusStory(uid, "https://picsum.photos/seed/${System.currentTimeMillis()}/600/1000", "image")
                        }
                    },
                    onOpenAddFriend = { showAddFriendDialog = true },
                    onOpenCreateGroup = { showCreateGroupDialog = true },
                    onOpenMyQr = {
                        profile?.let { p ->
                            qrDialogData = Pair("My OceanClient QR", "oceanclient://add?uid=${p.uid}&id=${p.arId}")
                        }
                    },
                    onOpenScanQr = {
                        // Quick add shortcut for scanning
                        showAddFriendDialog = true
                    },
                    onOpenEditProfile = { showEditProfileDialog = true },
                    onAcceptRequest = { senderUid ->
                        currentUid?.let { myUid ->
                            scope.launch { repository.acceptFriendRequest(myUid, senderUid) }
                        }
                    },
                    onDeclineRequest = { senderUid ->
                        currentUid?.let { myUid ->
                            scope.launch { repository.declineFriendRequest(myUid, senderUid) }
                        }
                    },
                    onDeleteContact = { contactUid, contactName ->
                        currentUid?.let { myUid ->
                            scope.launch { repository.deleteContact(myUid, contactUid, contactName) }
                        }
                    },
                    onLogout = {
                        scope.launch {
                            repository.logout()
                            firebaseUser = null
                            currentScreen = Screen.AUTH
                        }
                    }
                )
            }

            Screen.CHAT -> {
                val peer = activeChatUser
                if (currentUid != null && peer != null) {
                    val messages: List<Message> by remember(activeChatId, isActiveChatGroup) {
                        if (isActiveChatGroup) {
                            repository.getGroupMessagesFlow(activeChatId)
                        } else {
                            repository.getOneToOneMessagesFlow(currentUid, activeChatId)
                        }
                    }.collectAsState(initial = emptyList())

                    val peerPresence = contactsWithPresence.firstOrNull { it.first.uid == activeChatId }?.second

                    val isBlockedByMe by repository.isUserBlockedByMe(currentUid, activeChatId).collectAsState(initial = false)
                    val hasBlockedMe by repository.hasUserBlockedMe(currentUid, activeChatId).collectAsState(initial = false)
                    val isPeerTyping by repository.getTypingIndicatorFlow(currentUid, activeChatId).collectAsState(initial = false)

                    ChatScreen(
                        currentUid = currentUid,
                        chatId = activeChatId,
                        isGroup = isActiveChatGroup,
                        peerUser = peer,
                        peerPresence = peerPresence,
                        isBlockedByMe = isBlockedByMe,
                        hasBlockedMe = hasBlockedMe,
                        messages = messages,
                        isPeerTyping = isPeerTyping,
                        onBack = { currentScreen = Screen.MAIN },
                        onStartVoiceCall = {
                            scope.launch {
                                val callId = repository.initiateCall(currentUid, peer.uid, "voice")
                                val intent = Intent(activity, ActiveCallActivity::class.java).apply {
                                    putExtra("callId", callId)
                                    putExtra("peerUid", peer.uid)
                                    putExtra("peerName", peer.name)
                                    putExtra("peerDp", peer.dpUrl)
                                    putExtra("callType", "voice")
                                    putExtra("isCaller", true)
                                }
                                activity.startActivity(intent)
                            }
                        },
                        onStartVideoCall = {
                            scope.launch {
                                val callId = repository.initiateCall(currentUid, peer.uid, "video")
                                val intent = Intent(activity, ActiveCallActivity::class.java).apply {
                                    putExtra("callId", callId)
                                    putExtra("peerUid", peer.uid)
                                    putExtra("peerName", peer.name)
                                    putExtra("peerDp", peer.dpUrl)
                                    putExtra("callType", "video")
                                    putExtra("isCaller", true)
                                }
                                activity.startActivity(intent)
                            }
                        },
                        onToggleBlock = {
                            scope.launch {
                                repository.toggleBlockUser(currentUid, peer.uid, isBlockedByMe)
                            }
                        },
                        onClearChat = {
                            scope.launch {
                                if (isActiveChatGroup) {
                                    repository.clearGroupChat(activeChatId)
                                } else {
                                    repository.clearOneToOneChat(currentUid, activeChatId)
                                }
                            }
                        },
                        onOpenGroupQr = {
                            qrDialogData = Pair("Group QR Code", "oceanclient://group?id=${activeChatId}")
                        },
                        onSendMessage = { text, replyQuote ->
                            scope.launch {
                                if (isActiveChatGroup) {
                                    repository.sendGroupMessage(activeChatId, currentUid, text)
                                } else {
                                    repository.sendOneToOneMessage(currentUid, activeChatId, text, replyQuote)
                                }
                            }
                        },
                        onSendFile = { name, url, type ->
                            scope.launch {
                                if (isActiveChatGroup) {
                                    repository.sendGroupMessage(activeChatId, currentUid, name, isFile = true, fileData = url, fileType = type)
                                } else {
                                    repository.sendOneToOneMessage(currentUid, activeChatId, name, isFile = true, fileData = url, fileType = type)
                                }
                            }
                        },
                        onTypingChanged = { typing ->
                            scope.launch {
                                if (!isActiveChatGroup) {
                                    repository.setTypingIndicator(currentUid, activeChatId, typing)
                                }
                            }
                        },
                        onDeleteMessage = { msgId ->
                            scope.launch {
                                if (isActiveChatGroup) {
                                    repository.deleteGroupMessage(activeChatId, msgId)
                                } else {
                                    repository.deleteOneToOneMessage(currentUid, activeChatId, msgId)
                                }
                            }
                        }
                    )
                }
            }

            Screen.OCEAN_AI -> {
                OceanAIScreen(onBack = { currentScreen = Screen.MAIN })
            }
        }

        // Active Dialogs
        if (showAddFriendDialog && currentUid != null) {
            AddFriendDialog(
                onDismiss = { showAddFriendDialog = false },
                onSearch = { query -> repository.searchUser(query) },
                onSendRequest = { targetUid -> repository.sendFriendRequest(targetUid) }
            )
        }

        if (showCreateGroupDialog && currentUid != null) {
            CreateGroupDialog(
                onDismiss = { showCreateGroupDialog = false },
                onCreateGroup = { name, dpUrl ->
                    val grpId = repository.createGroup(name, dpUrl)
                    showCreateGroupDialog = false
                    activeChatId = grpId
                    activeChatUser = User(uid = grpId, name = name, dpUrl = dpUrl)
                    isActiveChatGroup = true
                    currentScreen = Screen.CHAT
                }
            )
        }

        if (showEditProfileDialog && profile != null) {
            val curProfile = profile!!
            EditProfileDialog(
                user = curProfile,
                onDismiss = { showEditProfileDialog = false },
                onSave = { name, bio, ringtoneUrl, callerToneUrl ->
                    scope.launch {
                        repository.updateProfile(curProfile.uid, name, bio, curProfile.dpUrl, ringtoneUrl, callerToneUrl)
                    }
                }
            )
        }

        qrDialogData?.let { (title, data) ->
            QrCodeDialog(
                title = title,
                subtitle = "Ask your friend to scan this QR to connect instantly.",
                qrData = data,
                onDismiss = { qrDialogData = null }
            )
        }

        activeStatusViewing?.let { (author, stories) ->
            StatusViewer(
                authorName = author.name,
                authorDp = author.dpUrl,
                stories = stories,
                isOwnStatus = isViewingOwnStatus,
                onDeleteStory = { storyId ->
                    currentUid?.let { uid ->
                        scope.launch { repository.deleteStatusStory(uid, storyId) }
                    }
                },
                onDismiss = { activeStatusViewing = null }
            )
        }
    }
}
