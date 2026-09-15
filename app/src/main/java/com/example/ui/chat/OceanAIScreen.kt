package com.example.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class AIMessage(
    val id: String = UUID.randomUUID().toString(),
    val isMe: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OceanAIScreen(
    onBack: () -> Unit
) {
    val messages = remember {
        mutableStateListOf(
            AIMessage(
                isMe = false,
                text = "Hello! Main OceanAI hu, mujhe Areeb ne banaya hai. Aapki kaise madad kar sakta hu?"
            )
        )
    }
    var inputText by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }
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
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(OceanSecondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "OceanAI", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("OceanAI", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Powered by AREEB", fontSize = 11.sp, color = OceanPrimary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        messages.clear()
                        messages.add(
                            AIMessage(
                                isMe = false,
                                text = "Hello! Main OceanAI hu, mujhe Areeb ne banaya hai. Aapki kaise madad kar sakta hu?"
                            )
                        )
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Reset Chat", tint = OceanDanger)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (msg.isMe) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.isMe) OceanBubbleMe else OceanBubbleOther
                            ),
                            modifier = Modifier.widthIn(max = 290.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(
                                    text = msg.text,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(msg.timestamp))
                                Text(
                                    text = timeStr,
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isTyping) {
                Text(
                    text = "OceanAI is typing...",
                    fontStyle = FontStyle.Italic,
                    color = OceanSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask OceanAI...") },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp, max = 100.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = OceanPrimary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        val text = inputText.trim()
                        if (text.isNotBlank()) {
                            messages.add(AIMessage(isMe = true, text = text))
                            inputText = ""
                            isTyping = true
                            scope.launch {
                                delay(900)
                                val reply = generateAIResponse(text)
                                messages.add(AIMessage(isMe = false, text = reply))
                                isTyping = false
                            }
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = OceanSecondary),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}

private fun generateAIResponse(userPrompt: String): String {
    val p = userPrompt.lowercase()
    return when {
        p.contains("who created you") || p.contains("who made you") || p.contains("who are you") ->
            "I am OceanAI, an intelligent conversational assistant integrated inside OceanXChat, proudly created by Areeb!"
        p.contains("hello") || p.contains("hi") || p.contains("hey") ->
            "Hello there! How can I assist you in OceanXChat today?"
        p.contains("call") || p.contains("calling") ->
            "OceanXChat supports high-definition crystal clear voice and video calls powered by native Android WebRTC with STUN signaling!"
        p.contains("security") || p.contains("encrypt") ->
            "All one-on-one and group messages in OceanXChat are encrypted using robust OpenSSL AES-256-CBC encryption."
        else ->
            "Thanks for your message! OceanAI is online and ready to help you with messaging, calling, and groups in OceanXChat."
    }
}
