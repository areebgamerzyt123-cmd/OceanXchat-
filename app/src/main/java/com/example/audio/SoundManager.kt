package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class SoundManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var ringbackThread: Thread? = null
    private var isPlayingRingback = false

    init {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun startRingtone(customUrl: String? = null) {
        stopAll()
        try {
            val uri = if (!customUrl.isNullOrBlank() && customUrl.startsWith("http")) {
                Uri.parse(customUrl)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                )
                isLooping = true
                prepareAsync()
                setOnPreparedListener { start() }
            }

            // Start vibration
            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startCallerTone(customUrl: String? = null) {
        stopAll()
        try {
            if (!customUrl.isNullOrBlank() && customUrl.startsWith("http")) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, Uri.parse(customUrl))
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .build()
                    )
                    isLooping = true
                    prepareAsync()
                    setOnPreparedListener { start() }
                }
            } else {
                // Native telephone ringback tone
                toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
                isPlayingRingback = true
                ringbackThread = Thread {
                    while (isPlayingRingback) {
                        try {
                            toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1500)
                            Thread.sleep(3500)
                        } catch (e: InterruptedException) {
                            break
                        }
                    }
                }.also { it.start() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopAll() {
        isPlayingRingback = false
        ringbackThread?.interrupt()
        ringbackThread = null

        toneGenerator?.stopTone()
        toneGenerator?.release()
        toneGenerator = null

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null

        vibrator?.cancel()
    }

    fun setSpeakerphoneOn(on: Boolean) {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = on
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
