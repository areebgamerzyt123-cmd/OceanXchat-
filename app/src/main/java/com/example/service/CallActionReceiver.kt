package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.audio.SoundManager
import com.example.data.repository.FirebaseManager
import com.example.data.repository.OceanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val callId = intent.getStringExtra("callId") ?: ""
        val callerUid = intent.getStringExtra("callerUid") ?: ""

        if (action == CallNotificationHelper.ACTION_REJECT_CALL) {
            val notificationHelper = CallNotificationHelper(context)
            notificationHelper.cancelIncomingCall()

            val soundManager = SoundManager(context)
            soundManager.stopAll()

            FirebaseManager.init(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    OceanRepository().rejectCall(callId, callerUid)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
