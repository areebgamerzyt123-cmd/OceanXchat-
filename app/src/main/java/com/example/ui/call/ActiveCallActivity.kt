package com.example.ui.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.audio.SoundManager
import com.example.data.repository.FirebaseManager
import com.example.data.repository.OceanRepository
import com.example.service.CallForegroundService
import com.example.service.CallNotificationHelper
import com.example.ui.theme.OceanDanger
import com.example.ui.theme.OceanPrimary
import com.example.ui.theme.OceanXChatTheme
import com.example.webrtc.WebRTCClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import kotlin.math.roundToInt

class ActiveCallActivity : ComponentActivity() {

    private var webrtcClient: WebRTCClient? = null
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        soundManager = SoundManager(this)
        FirebaseManager.init(applicationContext)

        val callId = intent.getStringExtra("callId") ?: ""
        val peerUid = intent.getStringExtra("peerUid") ?: ""
        val peerName = intent.getStringExtra("peerName") ?: "User"
        val peerDp = intent.getStringExtra("peerDp") ?: "https://picsum.photos/200"
        val callType = intent.getStringExtra("callType") ?: "voice"
        val isCaller = intent.getBooleanExtra("isCaller", false)

        val isVideo = callType == "video"

        // Start Ongoing Call Foreground Service
        CallForegroundService.startService(this, peerName, callType)

        // Cancel any incoming notification
        CallNotificationHelper(this).cancelIncomingCall()

        // Start caller tone if caller
        if (isCaller) {
            soundManager.startCallerTone()
        }

        webrtcClient = WebRTCClient(
            context = this,
            callId = callId,
            isCaller = isCaller,
            isVideo = isVideo,
            onConnectionStateChange = { state ->
                if (state == org.webrtc.PeerConnection.PeerConnectionState.CONNECTED) {
                    soundManager.stopAll()
                }
            },
            onCallEnded = {
                runOnUiThread {
                    endAndFinish(callId, peerUid, isCaller, callType)
                }
            }
        )

        setContent {
            OceanXChatTheme(darkTheme = true) {
                ActiveCallContent(
                    peerName = peerName,
                    peerDp = peerDp,
                    isVideo = isVideo,
                    webrtcClient = webrtcClient,
                    soundManager = soundManager,
                    onEndCall = {
                        endAndFinish(callId, peerUid, isCaller, callType)
                    }
                )
            }
        }
    }

    private fun endAndFinish(callId: String, peerUid: String, isCaller: Boolean, type: String) {
        soundManager.stopAll()
        CallForegroundService.stopService(this)
        webrtcClient?.cleanup()
        webrtcClient = null

        CoroutineScope(Dispatchers.IO).launch {
            try {
                OceanRepository().endCall(callId, peerUid, isCaller, type)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        finish()
    }

    override fun onDestroy() {
        soundManager.stopAll()
        CallForegroundService.stopService(this)
        webrtcClient?.cleanup()
        webrtcClient = null
        super.onDestroy()
    }
}

@Composable
fun ActiveCallContent(
    peerName: String,
    peerDp: String,
    isVideo: Boolean,
    webrtcClient: WebRTCClient?,
    soundManager: SoundManager,
    onEndCall: () -> Unit
) {
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(isVideo) }
    var durationSeconds by remember { mutableLongStateOf(0L) }

    var localVideoOffsetX by remember { mutableFloatStateOf(0f) }
    var localVideoOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        soundManager.setSpeakerphoneOn(isSpeakerOn)
        while (true) {
            delay(1000)
            durationSeconds++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isVideo && webrtcClient != null) {
            // Remote Full-Screen Video
            AndroidView(
                factory = { context ->
                    SurfaceViewRenderer(context).apply {
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        setEnableHardwareScaler(true)
                        webrtcClient.attachRemoteVideo(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Local Draggable Video PiP
            Box(
                modifier = Modifier
                    .offset { IntOffset(localVideoOffsetX.roundToInt(), localVideoOffsetY.roundToInt()) }
                    .padding(16.dp)
                    .size(width = 110.dp, height = 160.dp)
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 100.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, OceanPrimary, RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            localVideoOffsetX += dragAmount.x
                            localVideoOffsetY += dragAmount.y
                        }
                    }
            ) {
                AndroidView(
                    factory = { context ->
                        SurfaceViewRenderer(context).apply {
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            setEnableHardwareScaler(true)
                            webrtcClient.attachLocalVideo(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Voice Call Interface
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF001A12), Color(0xFF0A0A0A))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .border(4.dp, OceanPrimary, CircleShape)
                    ) {
                        AsyncImage(
                            model = peerDp,
                            contentDescription = peerName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = peerName,
                        fontSize = 26.sp,
                        color = Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val minutes = durationSeconds / 60
                    val seconds = durationSeconds % 60
                    val durationText = String.format("%02d:%02d", minutes, seconds)
                    Text(
                        text = durationText,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Call Controls (Bottom Bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera switch (video only)
            if (isVideo) {
                FilledIconButton(
                    onClick = { webrtcClient?.switchCamera() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.18f)
                    ),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.FlipCameraIos, contentDescription = "Flip Camera", tint = Color.White)
                }
            }

            // Mute Button
            FilledIconButton(
                onClick = {
                    isMuted = webrtcClient?.toggleMute() ?: !isMuted
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isMuted) Color.White else Color.White.copy(alpha = 0.18f)
                ),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Mute",
                    tint = if (isMuted) Color.Black else Color.White
                )
            }

            // Speaker Button
            FilledIconButton(
                onClick = {
                    isSpeakerOn = !isSpeakerOn
                    soundManager.setSpeakerphoneOn(isSpeakerOn)
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isSpeakerOn) OceanPrimary else Color.White.copy(alpha = 0.18f)
                ),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = "Speaker",
                    tint = Color.White
                )
            }

            // End Call Button
            FilledIconButton(
                onClick = onEndCall,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = OceanDanger),
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
