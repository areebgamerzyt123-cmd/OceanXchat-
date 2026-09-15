package com.example.service

import com.example.audio.SoundManager
import com.example.data.crypto.CryptoUtils
import com.example.data.repository.FirebaseManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OceanFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FirebaseManager.init(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            FirebaseManager.registerFcmToken(applicationContext)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        FirebaseManager.init(applicationContext)

        val data = remoteMessage.data
        val notificationHelper = CallNotificationHelper(applicationContext)
        val scope = CoroutineScope(Dispatchers.IO)

        val type = data["type"] ?: "message"

        when (type) {
            "call", "incoming_call" -> {
                val callId = data["callId"] ?: return
                val callerUid = data["callerUid"] ?: data["caller"] ?: return
                val callerName = data["callerName"] ?: "OceanXChat User"
                val callerDp = data["callerDp"]
                val callType = data["callType"] ?: "voice"

                // Start native sound and vibration
                val soundManager = SoundManager(applicationContext)
                soundManager.startRingtone(data["ringtoneUrl"])

                scope.launch {
                    notificationHelper.showIncomingCallNotification(
                        callId = callId,
                        callerUid = callerUid,
                        callerName = callerName,
                        callerDp = callerDp,
                        callType = callType
                    )
                }
            }

            "missed_call" -> {
                val callerName = data["callerName"] ?: "Unknown"
                val callType = data["callType"] ?: "voice"
                val callerUid = data["callerUid"] ?: ""
                scope.launch {
                    notificationHelper.showMissedCallNotification(callerName, callType, callerUid)
                }
            }

            "message" -> {
                val senderUid = data["senderUid"] ?: data["sender"] ?: return
                val senderName = data["senderName"] ?: "New Message"
                val rawText = data["text"] ?: ""
                val chatId = data["chatId"] ?: ""
                val senderDp = data["senderDp"]
                val isFile = data["isFile"] == "true"

                val displayText = if (!isFile) {
                    CryptoUtils.decrypt(rawText)
                } else rawText

                scope.launch {
                    notificationHelper.showMessageNotification(
                        senderUid = senderUid,
                        senderName = senderName,
                        messagePreview = displayText,
                        chatId = chatId,
                        senderDp = senderDp
                    )
                }
            }
        }
    }
}
