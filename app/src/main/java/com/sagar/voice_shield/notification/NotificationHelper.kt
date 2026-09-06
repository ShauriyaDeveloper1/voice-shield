package com.sagar.voice_shield.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

import android.app.PendingIntent
import android.content.Intent
import com.sagar.voice_shield.R
import com.sagar.voice_shield.ui.MainActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ALERTS = "voiceshield_alerts"
        const val CHANNEL_PROTECTION = "voiceshield_protection"
        const val ALERT_NOTIFICATION_ID = 2001
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS, "Threat Alerts", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "VoiceShield threat detection alerts"
            enableVibration(true)
        }

        val protectionChannel = NotificationChannel(
            CHANNEL_PROTECTION, "Protection Status", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Speaker Protection Mode status"
        }

        manager.createNotificationChannel(alertChannel)
        manager.createNotificationChannel(protectionChannel)
    }

    private fun createLaunchPendingIntent(): PendingIntent {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_ACTIVE_CALL", true)
        }
        return PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 10000).toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showThreatAlert(title: String, message: String, severity: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("⚠️ $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(createLaunchPendingIntent())
            .setPriority(if (severity == "HIGH") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(ALERT_NOTIFICATION_ID + (System.currentTimeMillis() % 1000).toInt(), notification)
    }

    fun showCallNotification(title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(createLaunchPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(ALERT_NOTIFICATION_ID + 100 + (System.currentTimeMillis() % 1000).toInt(), notification)
    }
}
