/*
 * Copyright (c) 2026 Rekluz Labs. All rights reserved.
 * This code and its assets are the exclusive property of Rekluz Labs.
 * Unauthorized copying, distribution, or commercial use is strictly prohibited.
 */
package com.rekluzlabs.vaultcuisine.timer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import com.rekluzlabs.vaultcuisine.data.AppPreferences

class TimerRingService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var overlayView: View? = null
    private var recipeId: String = ""
    private var stepIndex: Int = -1
    private var stepText: String = "Timer"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            recipeId = intent?.getStringExtra(TimerBroadcastReceiver.EXTRA_RECIPE_ID) ?: run {
                stopSelf()
                return START_NOT_STICKY
            }
            stepIndex = intent.getIntExtra(TimerBroadcastReceiver.EXTRA_STEP_INDEX, -1)
            stepText = intent.getStringExtra(TimerBroadcastReceiver.EXTRA_STEP_TEXT) ?: "Timer"

            val overlayShown = showOverlayIfPermitted()

            val notification = TimerNotificationHelper.buildRingingNotification(
                this, recipeId, stepIndex, stepText, useFullScreenIntent = !overlayShown
            )
            startForeground(RINGING_NOTIFICATION_ID, notification)
            startRinging()
        } catch (e: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun canDrawOverlays(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun showOverlayIfPermitted(): Boolean {
        if (!canDrawOverlays()) return false
        removeOverlay()
        return try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER

            val view = buildAlarmView()
            wm.addView(view, params)
            overlayView = view
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildAlarmView(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Float): Int = (value * density).toInt()

        val title = if (recipeId == CLOCK_ALARM_RECIPE_ID) "ALARM!" else "TIME'S UP!"

        val clockTv = TextView(this).apply {
            text = "\u23F0"
            textSize = 64f
            gravity = Gravity.CENTER
        }
        val titleTv = TextView(this).apply {
            text = title
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        val labelTv = TextView(this).apply {
            text = stepText
            textSize = 16f
            gravity = Gravity.CENTER
            maxLines = 3
            setTextColor(Color.WHITE)
        }
        val dismissBtn = Button(this).apply {
            text = "DISMISS"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFB71C1C.toInt())
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14f).toFloat()
            }
            setOnClickListener { dismissAlarm() }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFFB71C1C.toInt())
                cornerRadius = dp(24f).toFloat()
            }
            setPadding(dp(28f), dp(28f), dp(28f), dp(28f))
            addView(clockTv)
            addView(
                titleTv,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(16f) }
            )
            addView(
                labelTv,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8f) }
            )
            addView(
                dismissBtn,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(56f)
                ).apply { topMargin = dp(24f) }
            )
        }

        return LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(0xE6000000.toInt())
            setOnClickListener { dismissAlarm() }
            addView(
                card,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(32f)
                    marginEnd = dp(32f)
                }
            )
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                wm.removeView(view)
            } catch (_: Exception) {
            }
            overlayView = null
        }
    }

    private fun dismissAlarm() {
        val prefs = getSharedPreferences(TimerBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("ringing_${recipeId}_$stepIndex").apply()
        stopSelf()
    }

    private fun startRinging() {
        stopRinging()

        val storedUri = AppPreferences(this).load().alarmSoundUri
        val alarmUri = storedUri.ifEmpty {
            (RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)).toString()
        }

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            try {
                setDataSource(this@TimerRingService, Uri.parse(alarmUri))
                prepare()
                start()
            } catch (_: Exception) {
            }
        }
    }

    private fun stopRinging() {
        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
            } catch (_: Exception) { }
            release()
        }
        mediaPlayer = null
    }

    override fun onDestroy() {
        removeOverlay()
        stopRinging()
        NotificationManagerCompat.from(this).cancel(RINGING_NOTIFICATION_ID)
        super.onDestroy()
    }

    companion object {
        const val RINGING_NOTIFICATION_ID = 9911
        const val ACTION_STOP_RINGING = "com.rekluzlabs.vaultcuisine.STOP_RINGING"
    }
}
