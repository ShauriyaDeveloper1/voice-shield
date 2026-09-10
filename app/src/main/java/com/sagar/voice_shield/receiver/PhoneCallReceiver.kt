package com.sagar.voice_shield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import com.sagar.voice_shield.VoiceShieldApp
import com.sagar.voice_shield.service.AudioAnalysisService
import com.sagar.voice_shield.service.FloatingOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for phone state changes.
 * Automatically activates VoiceShield Speaker Protection & Audio Analysis
 * whenever an incoming or outgoing cellular call is active.
 */
class PhoneCallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PhoneCallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        Log.d(TAG, "Telephony state changed: $stateStr")

        val app = context.applicationContext as? VoiceShieldApp ?: return
        val prefs = app.appContainer.preferencesManager

        CoroutineScope(Dispatchers.IO).launch {
            val isProtectionEnabled = try {
                prefs.speakerProtectionEnabled.first()
            } catch (e: Exception) {
                true
            }

            if (!isProtectionEnabled) {
                Log.d(TAG, "Speaker Protection is disabled in preferences; skipping auto-start.")
                return@launch
            }

            when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING,
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    Log.i(TAG, "Incoming/Active cellular call detected -> Starting Audio Analysis (Mic Active)")
                    try {
                        val audioIntent = Intent(context, AudioAnalysisService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(audioIntent)
                        } else {
                            context.startService(audioIntent)
                        }

                        // Activate microphone recording now that a call is present
                        AudioAnalysisService.startAnalysis()

                        if (Settings.canDrawOverlays(context)) {
                            val overlayIntent = Intent(context, FloatingOverlayService::class.java)
                            context.startService(overlayIntent)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed starting call audio analysis service", e)
                    }
                }

                TelephonyManager.EXTRA_STATE_IDLE -> {
                    Log.i(TAG, "Cellular call ended -> Stopping Mic Audio Analysis (Returning to Standby)")
                    try {
                        // Pause mic capture and return to standby mode
                        AudioAnalysisService.stopAnalysis()
                        context.stopService(Intent(context, FloatingOverlayService::class.java))
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed stopping call audio analysis service", e)
                    }
                }
            }
        }
    }
}
