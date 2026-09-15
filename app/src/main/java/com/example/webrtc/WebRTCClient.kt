package com.example.webrtc

import android.content.Context
import com.example.data.repository.FirebaseManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import org.webrtc.*

class WebRTCClient(
    private val context: Context,
    private val callId: String,
    private val isCaller: Boolean,
    private val isVideo: Boolean,
    private val onConnectionStateChange: (PeerConnection.PeerConnectionState) -> Unit = {},
    private val onCallEnded: () -> Unit = {}
) {
    val eglBase: EglBase = EglBase.create()
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null

    private var remoteAudioTrack: AudioTrack? = null
    private var remoteVideoTrack: VideoTrack? = null

    private val db = FirebaseManager.database
    private var callListener: ValueEventListener? = null
    private var candidateListener: ValueEventListener? = null

    private val pendingCandidates = mutableListOf<IceCandidate>()
    private val processedCandidateKeys = mutableSetOf<String>()

    private var isMuted = false

    init {
        initPeerConnectionFactory()
        createPeerConnection()
        initMediaTracks()
        setupSignaling()
    }

    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    private fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.FAILED ||
                    state == PeerConnection.IceConnectionState.CLOSED
                ) {
                    onCallEnded()
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { sendIceCandidate(it) }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {}

            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onDataChannel(channel: DataChannel?) {}

            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                receiver?.track()?.let { track ->
                    if (track is VideoTrack) {
                        remoteVideoTrack = track
                    } else if (track is AudioTrack) {
                        remoteAudioTrack = track
                        remoteAudioTrack?.setEnabled(true)
                    }
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                newState?.let { onConnectionStateChange(it) }
            }
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
    }

    private fun initMediaTracks() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            if (isVideo) {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
        }

        // Local Audio
        localAudioSource = peerConnectionFactory?.createAudioSource(constraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", localAudioSource)
        localAudioTrack?.setEnabled(true)
        localAudioTrack?.let {
            peerConnection?.addTrack(it, listOf("ARDAMS"))
        }

        // Local Video
        if (isVideo) {
            val enumerator = Camera2Enumerator(context)
            val frontCamera = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull()

            if (frontCamera != null) {
                videoCapturer = enumerator.createCapturer(frontCamera, null)
                localVideoSource = peerConnectionFactory?.createVideoSource(videoCapturer!!.isScreencast)
                videoCapturer?.initialize(
                    SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext),
                    context,
                    localVideoSource?.capturerObserver
                )
                videoCapturer?.startCapture(640, 480, 30)

                localVideoTrack = peerConnectionFactory?.createVideoTrack("ARDAMSv0", localVideoSource)
                localVideoTrack?.setEnabled(true)
                localVideoTrack?.let {
                    peerConnection?.addTrack(it, listOf("ARDAMS"))
                }
            }
        }
    }

    fun attachLocalVideo(renderer: SurfaceViewRenderer) {
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setMirror(true)
        localVideoTrack?.addSink(renderer)
    }

    fun attachRemoteVideo(renderer: SurfaceViewRenderer) {
        renderer.init(eglBase.eglBaseContext, null)
        remoteVideoTrack?.addSink(renderer)
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        localAudioTrack?.setEnabled(!isMuted)
        return isMuted
    }

    private fun setupSignaling() {
        val callRef = db.getReference("calls/$callId")
        val candidateMePath = if (isCaller) "calls/$callId/callerCandidates" else "calls/$callId/calleeCandidates"
        val candidateOtherPath = if (isCaller) "calls/$callId/calleeCandidates" else "calls/$callId/callerCandidates"

        if (isCaller) {
            val sdpConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                if (isVideo) mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }

            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    desc?.let { offer ->
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(d: SessionDescription?) {}
                            override fun onSetSuccess() {
                                val offerMap = mapOf("type" to offer.type.canonicalForm(), "sdp" to offer.description)
                                callRef.child("offer").setValue(offerMap)
                            }
                            override fun onCreateFailure(s: String?) {}
                            override fun onSetFailure(s: String?) {}
                        }, offer)
                    }
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(s: String?) {}
                override fun onSetFailure(s: String?) {}
            }, sdpConstraints)

            callListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val status = snapshot.child("status").getValue(String::class.java)
                    if (status == "ended") {
                        onCallEnded()
                        return
                    }
                    val answerSnap = snapshot.child("answer")
                    if (answerSnap.exists() && peerConnection?.remoteDescription == null) {
                        val sdp = answerSnap.child("sdp").getValue(String::class.java) ?: return
                        val type = answerSnap.child("type").getValue(String::class.java) ?: "answer"
                        val sessionDesc = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
                        peerConnection?.setRemoteDescription(object : SdpObserver {
                            override fun onCreateSuccess(d: SessionDescription?) {}
                            override fun onSetSuccess() {
                                drainPendingCandidates()
                            }
                            override fun onCreateFailure(s: String?) {}
                            override fun onSetFailure(s: String?) {}
                        }, sessionDesc)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            callRef.addValueEventListener(callListener!!)
        } else {
            // Callee: Wait for offer, create answer
            callListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val status = snapshot.child("status").getValue(String::class.java)
                    if (status == "ended") {
                        onCallEnded()
                        return
                    }
                    val offerSnap = snapshot.child("offer")
                    if (offerSnap.exists() && peerConnection?.remoteDescription == null) {
                        val sdp = offerSnap.child("sdp").getValue(String::class.java) ?: return
                        val type = offerSnap.child("type").getValue(String::class.java) ?: "offer"
                        val sessionDesc = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)

                        peerConnection?.setRemoteDescription(object : SdpObserver {
                            override fun onCreateSuccess(d: SessionDescription?) {}
                            override fun onSetSuccess() {
                                drainPendingCandidates()
                                val sdpConstraints = MediaConstraints().apply {
                                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                                    if (isVideo) mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                                }
                                peerConnection?.createAnswer(object : SdpObserver {
                                    override fun onCreateSuccess(answer: SessionDescription?) {
                                        answer?.let { ans ->
                                            peerConnection?.setLocalDescription(object : SdpObserver {
                                                override fun onCreateSuccess(d: SessionDescription?) {}
                                                override fun onSetSuccess() {
                                                    val answerMap = mapOf("type" to ans.type.canonicalForm(), "sdp" to ans.description)
                                                    callRef.child("answer").setValue(answerMap)
                                                }
                                                override fun onCreateFailure(s: String?) {}
                                                override fun onSetFailure(s: String?) {}
                                            }, ans)
                                        }
                                    }
                                    override fun onSetSuccess() {}
                                    override fun onCreateFailure(s: String?) {}
                                    override fun onSetFailure(s: String?) {}
                                }, sdpConstraints)
                            }
                            override fun onCreateFailure(s: String?) {}
                            override fun onSetFailure(s: String?) {}
                        }, sessionDesc)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            callRef.addValueEventListener(callListener!!)
        }

        // Listen for remote ICE candidates
        val otherCandRef = db.getReference(candidateOtherPath)
        candidateListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val key = child.key ?: continue
                    if (processedCandidateKeys.add(key)) {
                        val sdpMid = child.child("sdpMid").getValue(String::class.java) ?: ""
                        val sdpMLineIndex = (child.child("sdpMLineIndex").getValue(Long::class.java) ?: 0L).toInt()
                        val sdp = child.child("sdp").getValue(String::class.java) ?: child.child("candidate").getValue(String::class.java) ?: ""
                        if (sdp.isNotEmpty()) {
                            val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                            if (peerConnection?.remoteDescription != null) {
                                peerConnection?.addIceCandidate(candidate)
                            } else {
                                pendingCandidates.add(candidate)
                            }
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        otherCandRef.addValueEventListener(candidateListener!!)
    }

    private fun sendIceCandidate(candidate: IceCandidate) {
        val path = if (isCaller) "calls/$callId/callerCandidates" else "calls/$callId/calleeCandidates"
        val candidateMap = mapOf(
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "sdp" to candidate.sdp
        )
        db.getReference(path).push().setValue(candidateMap)
    }

    private fun drainPendingCandidates() {
        for (cand in pendingCandidates) {
            peerConnection?.addIceCandidate(cand)
        }
        pendingCandidates.clear()
    }

    fun cleanup() {
        callListener?.let { db.getReference("calls/$callId").removeEventListener(it) }
        candidateListener?.let {
            val path = if (isCaller) "calls/$callId/calleeCandidates" else "calls/$callId/callerCandidates"
            db.getReference(path).removeEventListener(it)
        }
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
        } catch (e: Exception) {}

        localVideoTrack?.dispose()
        localVideoSource?.dispose()
        localAudioTrack?.dispose()
        localAudioSource?.dispose()

        peerConnection?.close()
        peerConnection?.dispose()
        peerConnectionFactory?.dispose()
        eglBase.release()
    }
}
