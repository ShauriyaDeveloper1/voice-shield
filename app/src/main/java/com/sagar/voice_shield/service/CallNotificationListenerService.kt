package com.sagar.voice_shield.service

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.sagar.voice_shield.VoiceShieldApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Universal Call Notification Listener.
 * Automatically detects incoming and ongoing calls from ANY third-party application
 * (e.g., WhatsApp, Telegram, Truecaller, Google Meet, Skype, Signal, Phone)
 * and activates VoiceShield Speaker Protection & AI Audio Analysis in real time.
 */
class CallNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "CallNotifListener"
        private val activeCallKeys = mutableSetOf<String>()

        private val CALLING_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.thunderdog.challegram",
            "com.truecaller",
            "com.google.android.apps.tachyon", // Google Meet
            "com.google.android.dialer",
            "com.samsung.android.incallui",
            "com.skype.raider",
            "org.thoughtcrime.securesms",      // Signal
            "com.facebook.orca",               // Messenger
            "com.instagram.android"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: ""
        // Don't listen to VoiceShield's own notifications
        if (pkg == packageName) return

        val notif = sbn.notification ?: return
        val category = notif.category ?: ""
        val extras = notif.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val template = extras?.getString("android.template") ?: ""

        val isCallCategory = (category == Notification.CATEGORY_CALL)
        val isCallStyle = template.contains("CallStyle", ignoreCase = true)
        val isKnownCallingApp = CALLING_PACKAGES.contains(pkg)

        val textLower = "$title $text".lowercase()
        val hasCallKeywords = textLower.contains("incoming call") ||
                textLower.contains("incoming voice call") ||
                textLower.contains("incoming video call") ||
                textLower.contains("ongoing call") ||
                textLower.contains("call in progress") ||
                textLower.contains("calling...") ||
                textLower.contains("active call")

        val isCall = (isCallCategory || isCallStyle || (isKnownCallingApp && hasCallKeywords)) &&
                !textLower.contains("missed call")

        if (isCall) {
            Log.i(TAG, "Incoming/Active call detected from package '$pkg' (key: ${sbn.key})")
            activeCallKeys.add(sbn.key)
            triggerSpeakerProtectionStart()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return

        if (activeCallKeys.remove(sbn.key)) {
            Log.i(TAG, "Call notification removed for key: ${sbn.key}")
            if (activeCallKeys.isEmpty()) {
                triggerSpeakerProtectionStop()
            }
        }
    }

    private fun triggerSpeakerProtectionStart() {
        val app = applicationContext as? VoiceShieldApp ?: return
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

            try {
                // Start the service in standby if not already running
                val audioIntent = Intent(applicationContext, AudioAnalysisService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(audioIntent)
                } else {
                    startService(audioIntent)
                }

                // Signal the service to begin actual mic recording
                AudioAnalysisService.startAnalysis()

                if (Settings.canDrawOverlays(applicationContext)) {
                    val overlayIntent = Intent(applicationContext, FloatingOverlayService::class.java)
                    startService(overlayIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed starting Speaker Protection from notification listener", e)
            }
        }
    }

    private fun triggerSpeakerProtectionStop() {
        try {
            // Stop mic recording but keep service alive in standby for future calls
            AudioAnalysisService.stopAnalysis()
            stopService(Intent(applicationContext, FloatingOverlayService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Failed stopping Speaker Protection from notification listener", e)
        }
    }
}
