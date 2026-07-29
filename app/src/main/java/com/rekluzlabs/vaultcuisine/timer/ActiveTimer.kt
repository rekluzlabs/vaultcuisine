package com.rekluzlabs.vaultcuisine.timer

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
