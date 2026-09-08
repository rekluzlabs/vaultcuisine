/*
 * Copyright (c) 2026 Rekluz Labs. All rights reserved.
 * This code and its assets are the exclusive property of Rekluz Labs.
 * Unauthorized copying, distribution, or commercial use is strictly prohibited.
 */
package com.rekluzlabs.vaultcuisine.timer

const val CLOCK_TIMER_RECIPE_ID = "clock_timer"
const val CLOCK_TIMER_STEP_INDEX = 0
const val CLOCK_TIMER_MAP_KEY = "${CLOCK_TIMER_RECIPE_ID}_$CLOCK_TIMER_STEP_INDEX"

const val CLOCK_ALARM_RECIPE_ID = "clock_alarm"
const val CLOCK_ALARM_STEP_INDEX = 0
const val CLOCK_ALARM_MAP_KEY = "${CLOCK_ALARM_RECIPE_ID}_$CLOCK_ALARM_STEP_INDEX"

data class ActiveTimer(
    val recipeId: String,
    val stepIndex: Int,
    val stepText: String,
    val endTimeMillis: Long,
    val totalSeconds: Int
) {
    val remainingSeconds: Long
        get() = maxOf(0, (endTimeMillis - System.currentTimeMillis()) / 1000)

    val isRunning: Boolean
        get() = remainingSeconds > 0

    val timerKey: String
        get() = "timer_${recipeId}_$stepIndex"
}
