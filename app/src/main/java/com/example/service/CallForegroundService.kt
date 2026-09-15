package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

class CallForegroundService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_PEER_NAME = "peerName"
        const val EXTRA_CALL_TYPE = "callType"

        fun startService(context: Context, peerName: String, callType: String) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_CALL_TYPE, callType)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: "User"
                val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "voice"

                val notificationHelper = CallNotificationHelper(this)
                val notification = notificationHelper.buildActiveCallNotification(peerName, callType)

                var foregroundServiceType = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    if (callType == "video") {
                        foregroundServiceType = foregroundServiceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    }
                }

                ServiceCompat.startForeground(
                    this,
                    CallNotificationHelper.NOTIFICATION_ID_ACTIVE_CALL,
                    notification,
                    foregroundServiceType
                )
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
