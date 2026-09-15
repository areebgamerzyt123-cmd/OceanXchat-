package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.MainActivity
import com.example.R
import com.example.ui.call.ActiveCallActivity
import com.example.ui.call.IncomingCallActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallNotificationHelper(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_INCOMING_CALLS = "incoming_calls_channel"
        const val CHANNEL_ACTIVE_CALLS = "active_calls_channel"
        const val CHANNEL_MESSAGES = "messages_channel"
        const val CHANNEL_MISSED_CALLS = "missed_calls_channel"

        const val NOTIFICATION_ID_INCOMING_CALL = 1001
        const val NOTIFICATION_ID_ACTIVE_CALL = 1002
        const val NOTIFICATION_ID_MISSED_CALL_BASE = 2000
        const val NOTIFICATION_ID_MESSAGE_BASE = 3000

        const val ACTION_ACCEPT_CALL = "com.example.oceanxchat.ACTION_ACCEPT_CALL"
        const val ACTION_REJECT_CALL = "com.example.oceanxchat.ACTION_REJECT_CALL"
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            // 1. Incoming Calls Channel
            val incomingChannel = NotificationChannel(
                CHANNEL_INCOMING_CALLS,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming voice and video calls notifications"
                setSound(ringtoneUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 1000)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // 2. Active Calls Channel
            val activeChannel = NotificationChannel(
                CHANNEL_ACTIVE_CALLS,
                "Active Call",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing call status notification"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // 3. Messages Channel
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Chat messages notifications"
                enableVibration(true)
            }

            // 4. Missed Calls Channel
            val missedChannel = NotificationChannel(
                CHANNEL_MISSED_CALLS,
                "Missed Calls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Missed calls notifications"
            }

            notificationManager.createNotificationChannels(
                listOf(incomingChannel, activeChannel, messagesChannel, missedChannel)
            )
        }
    }

    suspend fun showIncomingCallNotification(
        callId: String,
        callerUid: String,
        callerName: String,
        callerDp: String?,
        callType: String
    ) {
        // Full screen Intent to IncomingCallActivity
        val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("callId", callId)
            putExtra("callerUid", callerUid)
            putExtra("callerName", callerName)
            putExtra("callerDp", callerDp)
            putExtra("callType", callType)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_INCOMING_CALL,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept Action Intent -> Opens ActiveCallActivity directly
        val acceptIntent = Intent(context, ActiveCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("callId", callId)
            putExtra("peerUid", callerUid)
            putExtra("peerName", callerName)
            putExtra("peerDp", callerDp)
            putExtra("callType", callType)
            putExtra("isCaller", false)
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            context,
            1,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Reject Action Intent
        val rejectIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = ACTION_REJECT_CALL
            putExtra("callId", callId)
            putExtra("callerUid", callerUid)
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val avatarBitmap = loadAvatarBitmap(callerDp)

        val title = if (callType == "video") "Incoming Video Call" else "Incoming Voice Call"

        val notification = NotificationCompat.Builder(context, CHANNEL_INCOMING_CALLS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(callerName)
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setOngoing(true)
            .setLargeIcon(avatarBitmap)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, "Decline", rejectPendingIntent)
            .addAction(0, "Accept", acceptPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notificationManager.notify(NOTIFICATION_ID_INCOMING_CALL, notification)
    }

    fun buildActiveCallNotification(
        peerName: String,
        callType: String
    ): Notification {
        val intent = Intent(context, ActiveCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_ACTIVE_CALL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ACTIVE_CALLS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("OceanXChat Call")
            .setContentText("Ongoing $callType call with $peerName")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    fun cancelIncomingCall() {
        notificationManager.cancel(NOTIFICATION_ID_INCOMING_CALL)
    }

    suspend fun showMissedCallNotification(callerName: String, callType: String, callerUid: String) {
        cancelIncomingCall()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openTab", "calls")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_MISSED_CALL_BASE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED_CALLS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Missed Call")
            .setContentText("Missed $callType call from $callerName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_MISSED_CALL_BASE + callerUid.hashCode(), notification)
    }

    suspend fun showMessageNotification(
        senderUid: String,
        senderName: String,
        messagePreview: String,
        chatId: String,
        senderDp: String?
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openChatId", chatId)
            putExtra("peerUid", senderUid)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_MESSAGE_BASE + senderUid.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val avatarBitmap = loadAvatarBitmap(senderDp)

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName)
            .setContentText(messagePreview)
            .setLargeIcon(avatarBitmap)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_MESSAGE_BASE + senderUid.hashCode(), notification)
    }

    private suspend fun loadAvatarBitmap(url: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank() || !url.startsWith("http")) return@withContext null
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                (result.drawable as? BitmapDrawable)?.bitmap
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
