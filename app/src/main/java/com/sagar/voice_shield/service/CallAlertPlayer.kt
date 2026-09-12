package com.sagar.voice_shield.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import com.sagar.voice_shield.R

/**
 * Manages playback of in-call security alerts (e.g., 'Be aware') exclusively to the local user.
 * During alert playback, outgoing VoIP microphone capture is temporarily silenced
 * so that the remote peer/caller never hears the warning.
 */
class CallAlertPlayer(
    private val context: Context,
    private val onAlertStateChanged: (isPlaying: Boolean) -> Unit
) {
    private val TAG = "CallAlertPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var lastAlertTime = 0L
    private val alertCooldownMs = 15_000L // 15 seconds cooldown between repeated alerts

    @Synchronized
    fun playBeAwareAlert() {
        val now = System.currentTimeMillis()
        if (now - lastAlertTime < alertCooldownMs) {
            Log.d(TAG, "Alert on cooldown, skipping duplicate playback")
            return
        }
        lastAlertTime = now

        try {
            release() // Clean up any previous instance

            // 1. Suppress outgoing VoIP microphone chunks
            onAlertStateChanged(true)

            val mp = MediaPlayer()
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            mp.setAudioAttributes(audioAttributes)

            val afd = context.resources.openRawResourceFd(R.raw.be_aware)
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val targetDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        audioManager.communicationDevice
                    } else {
                        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                        }
                    }
                    if (targetDevice != null) {
                        mp.preferredDevice = targetDevice
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set preferred device on alert MediaPlayer", e)
                }
            }

            mp.setVolume(1.0f, 1.0f)
            mp.setOnCompletionListener { player ->
                Log.d(TAG, "'Be aware' alert finished playing")
                onAlertStateChanged(false)
                try {
                    player.release()
                } catch (_: Exception) {}
                if (mediaPlayer === player) {
                    mediaPlayer = null
                }
            }

            mp.setOnErrorListener { player, what, extra ->
                Log.w(TAG, "Error playing alert sound: what=$what, extra=$extra")
                onAlertStateChanged(false)
                try {
                    player.release()
                } catch (_: Exception) {}
                if (mediaPlayer === player) {
                    mediaPlayer = null
                }
                true
            }

            mp.prepare()
            mp.start()
            mediaPlayer = mp
            Log.d(TAG, "Playing 'Be aware' warning sound exclusively into in-call stream")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize and play alert sound", e)
            onAlertStateChanged(false)
        }
    }

    @Synchronized
    fun release() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaPlayer", e)
        } finally {
            mediaPlayer = null
            onAlertStateChanged(false)
        }
    }
}
