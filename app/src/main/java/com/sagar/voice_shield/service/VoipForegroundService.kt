package com.sagar.voice_shield.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sagar.voice_shield.R
import com.sagar.voice_shield.VoiceShieldApp
import com.sagar.voice_shield.ui.MainActivity

class VoipForegroundService : Service() {

    companion object {
        private const val TAG = "VoipForegroundService"
        const val CHANNEL_PERSISTENT_ID = "voip_persistent_channel"
        const val CHANNEL_INCOMING_ID = "voip_incoming_call_channel"
        const val NOTIF_PERSISTENT_ID = 2001
        const val NOTIF_INCOMING_ID = 2002

        const val ACTION_START_SERVICE = "com.sagar.voice_shield.action.START_VOIP_SERVICE"
        const val ACTION_STOP_SERVICE = "com.sagar.voice_shield.action.STOP_VOIP_SERVICE"
        const val ACTION_INCOMING_CALL = "com.sagar.voice_shield.action.INCOMING_CALL"
        const val ACTION_ACCEPT_CALL = "com.sagar.voice_shield.action.ACCEPT_CALL"
        const val ACTION_DECLINE_CALL = "com.sagar.voice_shield.action.DECLINE_CALL"
        const val ACTION_STOP_RINGING = "com.sagar.voice_shield.action.STOP_RINGING"

        const val EXTRA_FROM_PHONE = "extra_from_phone"
        const val EXTRA_FROM_NAME = "extra_from_name"

        fun startService(context: Context) {
            try {
                val intent = Intent(context, VoipForegroundService::class.java).apply {
                    action = ACTION_START_SERVICE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed starting VoipForegroundService", e)
            }
        }

        fun showIncomingCall(context: Context, fromPhone: String, fromName: String) {
            val intent = Intent(context, VoipForegroundService::class.java).apply {
                action = ACTION_INCOMING_CALL
                putExtra(EXTRA_FROM_PHONE, fromPhone)
                putExtra(EXTRA_FROM_NAME, fromName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopRinging(context: Context) {
            val intent = Intent(context, VoipForegroundService::class.java).apply {
                action = ACTION_STOP_RINGING
            }
            context.startService(intent)
        }
    }

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        initAudioAndHaptics()
    }

    private fun initAudioAndHaptics() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio or vibrator", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Persistent background channel
            val persistentChannel = NotificationChannel(
                CHANNEL_PERSISTENT_ID,
                "VoiceShield Protection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps VoiceShield AI Call Protection alive in the background"
                setShowBadge(false)
            }

            // High-priority incoming call channel
            val incomingChannel = NotificationChannel(
                CHANNEL_INCOMING_ID,
                "VoiceShield Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts and rings for incoming VoiceShield protected calls"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 1000)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager?.createNotificationChannel(persistentChannel)
            notificationManager?.createNotificationChannel(incomingChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startForeground(NOTIF_PERSISTENT_ID, buildPersistentNotification())
            }

            ACTION_INCOMING_CALL -> {
                val fromPhone = intent.getStringExtra(EXTRA_FROM_PHONE) ?: "Unknown"
                val fromName = intent.getStringExtra(EXTRA_FROM_NAME) ?: "VoiceShield Caller"
                handleIncomingCall(fromPhone, fromName)
            }

            ACTION_ACCEPT_CALL -> {
                stopIncomingCallAlerts()
                val app = application as? VoiceShieldApp
                app?.appContainer?.voipCallManager?.acceptIncomingCall()

                // Bring MainActivity to foreground
                val launchIntent = Intent(this, MainActivity::class.java).apply {
                    this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(launchIntent)
            }

            ACTION_DECLINE_CALL -> {
                stopIncomingCallAlerts()
                val app = application as? VoiceShieldApp
                app?.appContainer?.voipCallManager?.declineIncomingCall()
            }

            ACTION_STOP_RINGING -> {
                stopIncomingCallAlerts()
            }

            ACTION_STOP_SERVICE -> {
                stopIncomingCallAlerts()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun buildPersistentNotification(): Notification {
        val launchIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_PERSISTENT_ID)
            .setContentTitle("VoiceShield Active")
            .setContentText("24/7 AI Protected Calling • Shield Operational")
            .setSmallIcon(R.drawable.app_logo)
            .setContentIntent(launchIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("WakelockTimeout")
    private fun handleIncomingCall(fromPhone: String, fromName: String) {
        Log.d(TAG, "Handling incoming call ringing from $fromName ($fromPhone)")

        // Wake screen if device is locked
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "VoiceShield:IncomingCallWakeLock"
            )
            wakeLock?.acquire(30000)
        } catch (e: Exception) {
            Log.w(TAG, "Failed acquiring wake lock", e)
        }

        // Play loud ringtone
        try {
            if (ringtone?.isPlaying != true) {
                ringtone?.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing ringtone", e)
        }

        // Vibrate
        try {
            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating", e)
        }

        // Fullscreen Intent to open MainActivity immediately
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 101, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept Action PendingIntent
        val acceptIntent = Intent(this, VoipForegroundService::class.java).apply {
            action = ACTION_ACCEPT_CALL
        }
        val acceptPendingIntent = PendingIntent.getService(
            this, 102, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline Action PendingIntent
        val declineIntent = Intent(this, VoipForegroundService::class.java).apply {
            action = ACTION_DECLINE_CALL
        }
        val declinePendingIntent = PendingIntent.getService(
            this, 103, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val incomingNotif = NotificationCompat.Builder(this, CHANNEL_INCOMING_ID)
            .setContentTitle("Incoming Call • VoiceShield")
            .setContentText("$fromName ($fromPhone) is calling")
            .setSmallIcon(R.drawable.app_logo)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(R.drawable.app_logo, "Accept", acceptPendingIntent)
            .addAction(R.drawable.app_logo, "Decline", declinePendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIF_INCOMING_ID, incomingNotif)
    }

    private fun stopIncomingCallAlerts() {
        try {
            if (ringtone?.isPlaying == true) {
                ringtone?.stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping ringtone", e)
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping vibrator", e)
        }

        try {
            wakeLock?.release()
            wakeLock = null
        } catch (e: Exception) {
            // ignore
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.cancel(NOTIF_INCOMING_ID)
    }

    override fun onDestroy() {
        stopIncomingCallAlerts()
        super.onDestroy()
    }
}
