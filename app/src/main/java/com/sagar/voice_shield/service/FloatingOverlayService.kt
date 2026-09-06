package com.sagar.voice_shield.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.MotionEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Floating overlay service that displays VoiceShield risk status above other apps.
 * Uses SYSTEM_ALERT_WINDOW permission.
 */
class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlay()
        observeRiskUpdates()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        scope.cancel()
        super.onDestroy()
    }

    private fun createOverlay() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 200
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 20, 32, 20)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1D1F27"))
                cornerRadius = 32f
                setStroke(2, Color.parseColor("#3D494C"))
            }
            elevation = 16f
        }

        // Title row
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val shieldEmoji = TextView(this).apply {
            text = "🛡"
            textSize = 16f
            setPadding(0, 0, 12, 0)
        }

        val titleText = TextView(this).apply {
            text = "VoiceShield"
            textSize = 14f
            setTextColor(Color.parseColor("#E1E2EC"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        titleRow.addView(shieldEmoji)
        titleRow.addView(titleText)

        // Risk score
        val riskLabel = TextView(this).apply {
            text = "Voice Risk"
            textSize = 11f
            setTextColor(Color.parseColor("#BCC9CD"))
            setPadding(0, 12, 0, 4)
        }

        val riskScore = TextView(this).apply {
            text = "0 / 100"
            textSize = 24f
            setTextColor(Color.parseColor("#4EDEA3"))
            typeface = Typeface.MONOSPACE
            tag = "risk_score"
        }

        val riskStatus = TextView(this).apply {
            text = "🟢 SAFE"
            textSize = 13f
            setTextColor(Color.parseColor("#4EDEA3"))
            tag = "risk_status"
            setPadding(0, 8, 0, 0)
        }

        val explanation = TextView(this).apply {
            text = "Monitoring active..."
            textSize = 11f
            setTextColor(Color.parseColor("#869397"))
            tag = "explanation"
            setPadding(0, 8, 0, 0)
        }

        // Dismiss button
        val dismissBtn = TextView(this).apply {
            text = "DISMISS"
            textSize = 11f
            setTextColor(Color.parseColor("#4CD7F6"))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, 16, 0, 0)
            setOnClickListener { stopSelf() }
        }

        container.addView(titleRow)
        container.addView(riskLabel)
        container.addView(riskScore)
        container.addView(riskStatus)
        container.addView(explanation)
        container.addView(dismissBtn)

        overlayView = container

        // Make draggable
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX - (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(container, params)
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(container, params)
    }

    private fun observeRiskUpdates() {
        scope.launch {
            AudioAnalysisService.riskScore.collectLatest { score ->
                updateOverlay(score, AudioAnalysisService.severity.value, AudioAnalysisService.explanations.value)
            }
        }
        scope.launch {
            AudioAnalysisService.explanations.collectLatest { exps ->
                updateOverlay(AudioAnalysisService.riskScore.value, AudioAnalysisService.severity.value, exps)
            }
        }
    }

    private fun updateOverlay(score: Int, severity: String, explanations: List<String>) {
        val container = overlayView as? LinearLayout ?: return

        val scoreView = container.findViewWithTag<TextView>("risk_score")
        val statusView = container.findViewWithTag<TextView>("risk_status")
        val explanationView = container.findViewWithTag<TextView>("explanation")

        scoreView?.text = "$score / 100"
        
        when (severity) {
            "HIGH" -> {
                scoreView?.setTextColor(Color.parseColor("#FF4444"))
                statusView?.text = "🔴 HIGH RISK (Scam / Deepfake)"
                statusView?.setTextColor(Color.parseColor("#FF4444"))
            }
            "MEDIUM" -> {
                scoreView?.setTextColor(Color.parseColor("#FFB74D"))
                statusView?.text = "🟠 SUSPICIOUS CALL"
                statusView?.setTextColor(Color.parseColor("#FFB74D"))
            }
            else -> {
                scoreView?.setTextColor(Color.parseColor("#4EDEA3"))
                statusView?.text = "🟢 NORMAL CALL (Verified Safe)"
                statusView?.setTextColor(Color.parseColor("#4EDEA3"))
            }
        }

        explanationView?.text = explanations.firstOrNull() ?: "Monitoring active..."
    }
}
