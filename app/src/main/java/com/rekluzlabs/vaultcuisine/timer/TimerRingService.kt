package com.rekluzlabs.vaultcuisine.timer

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat

class TimerRingService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recipeId = intent?.getStringExtra(TimerBroadcastReceiver.EXTRA_RECIPE_ID) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val stepIndex = intent.getIntExtra(TimerBroadcastReceiver.EXTRA_STEP_INDEX, -1)
        val stepText = intent.getStringExtra(TimerBroadcastReceiver.EXTRA_STEP_TEXT) ?: "Timer"

        val notification = TimerNotificationHelper.buildRingingNotification(
            this, recipeId, stepIndex, stepText
        )
        startForeground(RINGING_NOTIFICATION_ID, notification)
        startRinging()

        return START_NOT_STICKY
    }

    private fun startRinging() {
        stopRinging()

        val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            try {
                setDataSource(this@TimerRingService, alarmUri)
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
        stopRinging()
        NotificationManagerCompat.from(this).cancel(RINGING_NOTIFICATION_ID)
        super.onDestroy()
    }

    companion object {
        const val RINGING_NOTIFICATION_ID = 9911
        const val ACTION_STOP_RINGING = "com.rekluzlabs.vaultcuisine.STOP_RINGING"
    }
}
