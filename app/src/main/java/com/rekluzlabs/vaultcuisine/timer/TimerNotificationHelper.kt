package com.rekluzlabs.vaultcuisine.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rekluzlabs.vaultcuisine.timer.TimerBroadcastReceiver.Companion.EXTRA_RECIPE_ID
import com.rekluzlabs.vaultcuisine.timer.TimerBroadcastReceiver.Companion.EXTRA_STEP_INDEX
import com.rekluzlabs.vaultcuisine.timer.TimerBroadcastReceiver.Companion.EXTRA_STEP_TEXT

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
        stepText: String,
        useFullScreenIntent: Boolean = true
    ): Notification {
        val openIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_RECIPE_ID, recipeId)
            putExtra(EXTRA_STEP_INDEX, stepIndex)
            putExtra(EXTRA_STEP_TEXT, stepText)
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

        val title = when (recipeId) {
            CLOCK_TIMER_RECIPE_ID -> "\u23F0 Timer done!"
            CLOCK_ALARM_RECIPE_ID -> "\u23F0 Alarm!"
            else -> "\u23F0 Step ${stepIndex + 1} timer done!"
        }

        val builder = NotificationCompat.Builder(context, RING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(label)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", stopPendingIntent)

        if (useFullScreenIntent) {
            builder.setFullScreenIntent(contentPendingIntent, true)
        }

        return builder.build()
    }
}
