package com.sagar.voice_shield.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
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

            mediaPlayer = MediaPlayer.create(context, R.raw.be_aware)?.apply {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                setAudioAttributes(audioAttributes)
                setVolume(1.0f, 1.0f)

                setOnCompletionListener { mp ->
                    Log.d(TAG, "'Be aware' alert finished playing")
                    onAlertStateChanged(false)
                    try {
                        mp.release()
                    } catch (_: Exception) {}
                    if (mediaPlayer === mp) {
                        mediaPlayer = null
                    }
                }

                setOnErrorListener { mp, what, extra ->
                    Log.w(TAG, "Error playing alert sound: what=$what, extra=$extra")
                    onAlertStateChanged(false)
                    try {
                        mp.release()
                    } catch (_: Exception) {}
                    if (mediaPlayer === mp) {
                        mediaPlayer = null
                    }
                    true
                }

                start()
                Log.d(TAG, "Playing 'Be aware' warning sound exclusively to local user")
            }
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
