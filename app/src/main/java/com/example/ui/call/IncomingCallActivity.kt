package com.example.ui.call

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.audio.SoundManager
import com.example.data.repository.FirebaseManager
import com.example.data.repository.OceanRepository
import com.example.service.CallNotificationHelper
import com.example.ui.theme.OceanDanger
import com.example.ui.theme.OceanPrimary
import com.example.ui.theme.OceanXChatTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IncomingCallActivity : ComponentActivity() {

    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAndUnlock()

        soundManager = SoundManager(this)

        val callId = intent.getStringExtra("callId") ?: ""
        val callerUid = intent.getStringExtra("callerUid") ?: ""
        val callerName = intent.getStringExtra("callerName") ?: "OceanXChat User"
        val callerDp = intent.getStringExtra("callerDp") ?: "https://picsum.photos/200"
        val callType = intent.getStringExtra("callType") ?: "voice"

        FirebaseManager.init(applicationContext)

        setContent {
            OceanXChatTheme(darkTheme = true) {
                IncomingCallContent(
                    callerName = callerName,
                    callerDp = callerDp,
                    callType = callType,
                    onAccept = {
                        soundManager.stopAll()
                        CallNotificationHelper(this).cancelIncomingCall()
                        CoroutineScope(Dispatchers.IO).launch {
                            OceanRepository().acceptCall(callId)
                        }
                        val activeIntent = Intent(this, ActiveCallActivity::class.java).apply {
                            putExtra("callId", callId)
                            putExtra("peerUid", callerUid)
                            putExtra("peerName", callerName)
                            putExtra("peerDp", callerDp)
                            putExtra("callType", callType)
                            putExtra("isCaller", false)
                        }
                        startActivity(activeIntent)
                        finish()
                    },
                    onDecline = {
                        soundManager.stopAll()
                        CallNotificationHelper(this).cancelIncomingCall()
                        CoroutineScope(Dispatchers.IO).launch {
                            OceanRepository().rejectCall(callId, callerUid)
                        }
                        finish()
                    }
                )
            }
        }
    }

    private fun wakeAndUnlock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    override fun onDestroy() {
        soundManager.stopAll()
        super.onDestroy()
    }
}

@Composable
fun IncomingCallContent(
    callerName: String,
    callerDp: String,
    callType: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF001A12), Color(0xFF0A0A0A), Color.Black)
                )
            )
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .border(4.dp, OceanPrimary, CircleShape)
            ) {
                AsyncImage(
                    model = callerDp,
                    contentDescription = callerName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = callerName,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (callType == "video") "Incoming Video Call..." else "Incoming Voice Call...",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.weight(1.5f))

            // Accept & Reject Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reject Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(
                        onClick = onDecline,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = OceanDanger),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            contentDescription = "Decline",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Decline", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }

                // Accept Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(
                        onClick = onAccept,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = OceanPrimary),
                        modifier = Modifier
                            .size(72.dp)
                            .scale(pulseScale)
                    ) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "Accept",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Accept", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
