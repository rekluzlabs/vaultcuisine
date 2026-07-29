package com.rekluzlabs.vaultcuisine.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimerBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TimerRingService.ACTION_STOP_RINGING) {
            val recipeId = intent.getStringExtra(EXTRA_RECIPE_ID) ?: return
            val stepIndex = intent.getIntExtra(EXTRA_STEP_INDEX, -1)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove("ringing_${recipeId}_$stepIndex").apply()
            context.stopService(Intent(context, TimerRingService::class.java))
            return
        }

        val recipeId = intent.getStringExtra(EXTRA_RECIPE_ID) ?: return
        val stepIndex = intent.getIntExtra(EXTRA_STEP_INDEX, -1)
        val stepText = intent.getStringExtra(EXTRA_STEP_TEXT) ?: "Timer"

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "timer_${recipeId}_$stepIndex"
        prefs.edit().remove(key).apply()

        prefs.edit().putBoolean("ringing_${recipeId}_$stepIndex", true).apply()

        val serviceIntent = Intent(context, TimerRingService::class.java).apply {
            putExtra(EXTRA_RECIPE_ID, recipeId)
            putExtra(EXTRA_STEP_INDEX, stepIndex)
            putExtra(EXTRA_STEP_TEXT, stepText)
        }
        context.startForegroundService(serviceIntent)
    }

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
        const val EXTRA_STEP_INDEX = "step_index"
        const val EXTRA_STEP_TEXT = "step_text"
        const val PREFS_NAME = "vaultcuisine_timers"
        const val ACTION_TIMER_DONE = "com.rekluzlabs.vaultcuisine.TIMER_DONE"
    }
}
