package com.example.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val bio: String = "",
    val dpUrl: String = "",
    val arId: String = "",
    val searchNum: String? = null,
    val ringtoneUrl: String? = null,
    val callerToneUrl: String? = null
)

data class UserPresence(
    val state: String = "offline",
    val lastSeen: Long = 0L
)

data class Message(
    val id: String = "",
    val sender: String = "",
    val senderName: String? = null,
    val senderDp: String? = null,
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val msgStatus: String = "sent", // "sent", "delivered", "read"
    val isFile: Boolean = false,
    val fileData: String? = null,
    val fileType: String? = null,
    val system: Boolean = false
)

data class Group(
    val id: String = "",
    val name: String = "",
    val dpUrl: String = "",
    val admin: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class CallData(
    val callId: String = "",
    val caller: String = "",
    val callee: String = "",
    val type: String = "voice", // "voice" or "video"
    val status: String = "calling", // "calling", "answered", "ended"
    val timestamp: Long = System.currentTimeMillis(),
    val endedAt: Long? = null
)

data class IncomingCall(
    val callId: String = "",
    val caller: String = "",
    val callerName: String = "User",
    val callerDp: String = "",
    val type: String = "voice",
    val status: String = "calling"
)

data class CallLog(
    val id: String = "",
    val peer: String = "",
    val type: String = "voice",
    val timestamp: Long = System.currentTimeMillis()
)

data class StatusStory(
    val id: String = "",
    val uid: String = "",
    val img: String = "",
    val type: String = "image", // "image" or "video"
    val timestamp: Long = System.currentTimeMillis()
)
