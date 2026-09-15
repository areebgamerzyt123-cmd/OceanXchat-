package com.example.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Message
import com.example.data.model.User
import com.example.data.model.UserPresence
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUid: String,
    chatId: String,
    isGroup: Boolean,
    peerUser: User?,
    peerPresence: UserPresence?,
    isBlockedByMe: Boolean,
    hasBlockedMe: Boolean,
    messages: List<Message>,
    isPeerTyping: Boolean,
    onBack: () -> Unit,
    onStartVoiceCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onToggleBlock: () -> Unit,
    onClearChat: () -> Unit,
    onOpenGroupQr: () -> Unit,
    onSendMessage: (text: String, replyToQuote: String?) -> Unit,
    onSendFile: (name: String, url: String, type: String) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    onDeleteMessage: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var activeReplyTo by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            AsyncImage(
                                model = peerUser?.dpUrl?.ifBlank { "https://picsum.photos/100" } ?: "https://picsum.photos/100",
                                contentDescription = peerUser?.name ?: "Chat",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            if (!isGroup && !isBlockedByMe && !hasBlockedMe) {
                                val isOnline = peerPresence?.state == "online"
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (isOnline) OceanPrimary else Color.Gray)
                                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = peerUser?.name ?: if (isGroup) "Group Chat" else "User",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val statusText = when {
                                isGroup -> "Group"
                                isBlockedByMe || hasBlockedMe -> ""
                                peerPresence?.state == "online" -> "Online"
                                (peerPresence?.lastSeen ?: 0L) > 0L -> {
                                    val time = SimpleDateFormat("h:mm a", Locale.getDefault())
                                        .format(Date(peerPresence!!.lastSeen))
                                    "last seen at $time"
                                }
                                else -> "Offline"
                            }
                            if (statusText.isNotBlank()) {
                                Text(
                                    text = statusText,
                                    fontSize = 11.sp,
                                    color = OceanTextMuted
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isGroup) {
                        IconButton(onClick = onStartVoiceCall, enabled = !isBlockedByMe && !hasBlockedMe) {
                            Icon(Icons.Default.Phone, contentDescription = "Voice Call", tint = OceanPrimary)
                        }
                        IconButton(onClick = onStartVideoCall, enabled = !isBlockedByMe && !hasBlockedMe) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = OceanPrimary)
                        }
                        IconButton(onClick = onToggleBlock) {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = "Block",
                                tint = if (isBlockedByMe) OceanDanger else OceanTextMuted
                            )
                        }
                    } else {
                        IconButton(onClick = onOpenGroupQr) {
                            Icon(Icons.Default.QrCode, contentDescription = "Group QR", tint = OceanPrimary)
                        }
                    }
                    IconButton(onClick = onClearChat) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Chat", tint = OceanDanger)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Blocked Notification Banner
            if (isBlockedByMe) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OceanDanger)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("You have blocked this contact. Tap block icon to unblock.", color = Color.White, fontSize = 12.sp)
                }
            } else if (hasBlockedMe) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OceanDanger)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("You have been blocked by this user.", color = Color.White, fontSize = 12.sp)
                }
            }

            // Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.system) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OceanTextMuted,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        val isMe = msg.sender == currentUid
                        MessageBubble(
                            message = msg,
                            isMe = isMe,
                            isGroup = isGroup,
                            onReply = { activeReplyTo = msg.text },
                            onDelete = { onDeleteMessage(msg.id) }
                        )
                    }
                }
            }

            // Typing Indicator
            AnimatedVisibility(visible = isPeerTyping) {
                Text(
                    text = "typing...",
                    fontStyle = FontStyle.Italic,
                    color = OceanTextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                )
            }

            // Reply Indicator
            AnimatedVisibility(visible = activeReplyTo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(androidx.compose.foundation.BorderStroke(1.dp, OceanPrimary))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Replying to:", color = OceanPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = activeReplyTo?.take(50) ?: "",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { activeReplyTo = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel reply", tint = OceanTextMuted)
                    }
                }
            }

            // Input Bar
            if (!isBlockedByMe && !hasBlockedMe) {
                ChatInputBar(
                    text = inputText,
                    onTextChanged = {
                        inputText = it
                        onTypingChanged(it.isNotBlank())
                    },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText.trim(), activeReplyTo)
                            inputText = ""
                            activeReplyTo = null
                            onTypingChanged(false)
                        }
                    },
                    onAttachFile = {
                        // Quick media sample upload
                        onSendFile("Photo_${System.currentTimeMillis()}.jpg", "https://picsum.photos/400/300", "image/jpeg")
                    },
                    onSendVoice = {
                        onSendFile("Voice_${System.currentTimeMillis()}.mp3", "https://actions.google.com/sounds/v1/alarms/beep_short.ogg", "audio/mp3")
                    }
                )
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isMe: Boolean,
    isGroup: Boolean,
    onReply: () -> Unit,
    onDelete: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .draggable(
                state = rememberDraggableState { delta ->
                    if (delta > 0 || offsetX > 0) {
                        offsetX = (offsetX + delta).coerceIn(0f, 120f)
                    }
                },
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    if (offsetX > 60f) {
                        onReply()
                    }
                    offsetX = 0f
                }
            ),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isMe) OceanBubbleMe else OceanBubbleOther
            ),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clickable { /* Tapping bubble */ }
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Group member sender header
                if (isGroup && !isMe && !message.senderName.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        AsyncImage(
                            model = message.senderDp ?: "https://picsum.photos/40",
                            contentDescription = message.senderName,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = message.senderName ?: "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = OceanSecondary
                        )
                    }
                }

                // File Attachment (Image, Audio, Document)
                if (message.isFile && !message.fileData.isNullOrBlank()) {
                    if (message.fileType?.startsWith("image/") == true) {
                        AsyncImage(
                            model = message.fileData,
                            contentDescription = "Image attachment",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    } else if (message.fileType?.startsWith("audio/") == true) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play voice", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Voice Message", color = Color.White, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // Message Text
                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Time and Delivery Ticks
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))
                    Text(
                        text = timeStr,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    if (isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val tickColor = if (message.msgStatus == "read") OceanTickRead else Color.White.copy(alpha = 0.8f)
                        val tickText = when (message.msgStatus) {
                            "read" -> "✓✓"
                            "delivered" -> "✓✓"
                            else -> "✓"
                        }
                        Text(
                            text = tickText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = tickColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onAttachFile: () -> Unit,
    onSendVoice: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAttachFile) {
            Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = OceanTextMuted)
        }

        OutlinedTextField(
            value = text,
            onValueChange = onTextChanged,
            placeholder = { Text("Message...", fontSize = 14.sp) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp, max = 120.dp),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = OceanPrimary,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.width(6.dp))

        if (text.isNotBlank()) {
            FilledIconButton(
                onClick = onSend,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = OceanPrimary),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
            }
        } else {
            FilledIconButton(
                onClick = {
                    if (!isRecording) {
                        isRecording = true
                    } else {
                        isRecording = false
                        onSendVoice()
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isRecording) OceanDanger else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Record Voice",
                    tint = if (isRecording) Color.White else OceanTextMuted
                )
            }
        }
    }
}
