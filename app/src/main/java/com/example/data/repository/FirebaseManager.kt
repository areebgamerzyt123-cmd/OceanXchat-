package com.example.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object FirebaseManager {
    private const val API_KEY = "AIzaSyBPk2xcUpfB-oAHFb4ICmvxitPuzHQzUXU"
    private const val DATABASE_URL = "https://zyonix-chats-3862e-default-rtdb.firebaseio.com"
    private const val PROJECT_ID = "zyonix-chats-3862e"
    private const val APP_ID = "1:884425619501:android:9d45e7f82b012345"

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(API_KEY)
                    .setApplicationId(APP_ID)
                    .setProjectId(PROJECT_ID)
                    .setDatabaseUrl(DATABASE_URL)
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
            FirebaseDatabase.getInstance(DATABASE_URL).setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Already initialized or persistence enabled
        }
        initialized = true
    }

    val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    val database: FirebaseDatabase
        get() = FirebaseDatabase.getInstance(DATABASE_URL)

    val currentUid: String?
        get() = auth.currentUser?.uid

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: Build.MODEL.replace(" ", "_")
        } catch (e: Exception) {
            "device_${Build.SERIAL}"
        }
    }

    fun setupPresence(uid: String) {
        val presenceRef = database.getReference("status/$uid")
        val connectedRef = database.getReference(".info/connected")

        connectedRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    presenceRef.onDisconnect().setValue(
                        mapOf(
                            "state" to "offline",
                            "lastSeen" to ServerValue.TIMESTAMP
                        )
                    )
                    presenceRef.setValue(mapOf("state" to "online"))
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    fun setUserPresence(uid: String) = setupPresence(uid)

    fun setOffline(uid: String) {
        val presenceRef = database.getReference("status/$uid")
        presenceRef.setValue(
            mapOf(
                "state" to "offline",
                "lastSeen" to ServerValue.TIMESTAMP
            )
        )
    }

    suspend fun registerFcmToken(context: Context) {
        val uid = currentUid ?: return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            val deviceId = getDeviceId(context)
            val deviceRef = database.getReference("users/$uid/devices/$deviceId")
            val tokenData = mapOf(
                "fcmToken" to token,
                "updatedAt" to ServerValue.TIMESTAMP,
                "model" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "platform" to "android"
            )
            deviceRef.setValue(tokenData).await()

            // Also keep top-level fcmToken for simple lookup
            database.getReference("users/$uid/fcmToken").setValue(token).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
