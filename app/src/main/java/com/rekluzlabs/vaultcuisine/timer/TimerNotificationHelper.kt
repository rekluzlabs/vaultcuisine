package com.rekluzlabs.vaultcuisine.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rekluzlabs.vaultcuisine.MainActivity

object TimerNotificationHelper {

    private const val CHANNEL_ID = "cooking_timers"
    private const val CHANNEL_NAME = "Cooking Timers"
    private const val CHANNEL_DESC = "Timer countdowns and notifications for Cooking Mode"

    private const val RING_CHANNEL_ID = "cooking_timer_ringing"
    private const val RING_CHANNEL_NAME = "Timer Ringing"
    private const val RING_CHANNEL_DESC = "Ongoing alert while a cooking timer rings until dismissed"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val countdownChannel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = CHANNEL_DESC
            setShowBadge(false)
        }
        nm.createNotificationChannel(countdownChannel)

        val ringChannel = NotificationChannel(
            RING_CHANNEL_ID, RING_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = RING_CHANNEL_DESC
            enableVibration(true)
            setSound(null, null)
        }
        nm.createNotificationChannel(ringChannel)
    }

    fun buildRingingNotification(
        context: Context,
        recipeId: String,
        stepIndex: Int,
        stepText: String
    ): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_cooking", recipeId)
            putExtra("cooking_step", stepIndex)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, recipeId.hashCode() + stepIndex, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(context, TimerBroadcastReceiver::class.java).apply {
            action = TimerRingService.ACTION_STOP_RINGING
            putExtra(TimerBroadcastReceiver.EXTRA_RECIPE_ID, recipeId)
            putExtra(TimerBroadcastReceiver.EXTRA_STEP_INDEX, stepIndex)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context, recipeId.hashCode() + stepIndex + 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val label = stepText.take(60)

        return NotificationCompat.Builder(context, RING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("\u23F0 Step ${stepIndex + 1} timer done!")
            .setContentText(label)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", stopPendingIntent)
            .build()
    }
}
