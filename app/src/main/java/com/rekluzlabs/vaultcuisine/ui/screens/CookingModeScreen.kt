package com.rekluzlabs.vaultcuisine.ui.screens

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info

import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rekluzlabs.vaultcuisine.MainViewModel
import com.rekluzlabs.vaultcuisine.data.Recipe
import com.rekluzlabs.vaultcuisine.timer.ActiveTimer
import com.rekluzlabs.vaultcuisine.timer.DraggableTimerOverlay
import com.rekluzlabs.vaultcuisine.timer.TimerCircle
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookingModeScreen(
    recipeId: String,
    initialStep: Int = 0,
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val recipes by vm.recipes.collectAsState()
    val recipe = recipes.find { it.id == recipeId }
    val activeTimers by vm.activeTimers.collectAsState()
    val ringingTimers by vm.ringingTimers.collectAsState()

    var currentStepIndex by remember { mutableStateOf(initialStep.coerceAtLeast(0)) }
    var showExactAlarmBanner by remember { mutableStateOf(true) }
    var minimizedTimers by remember { mutableStateOf(setOf<String>()) }
    val context = LocalContext.current

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        vm.loadActiveTimers()
    }

    if (recipe == null) return

    val steps = recipe.steps
    if (steps.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No steps in this recipe")
        }
        return
    }

    val currentStep = steps[currentStepIndex]
    val timerKey = "${recipeId}_$currentStepIndex"
    val activeTimer = activeTimers[timerKey]
    val stepTimerRunning = activeTimer != null && activeTimer.isRunning
    val stepIsRinging = timerKey in ringingTimers

    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(activeTimers.keys) {
        while (activeTimers.values.any { it.isRunning }) {
            delay(1000)
            tick++
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Cooking Mode") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val canScheduleExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (!canScheduleExact && showExactAlarmBanner) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Info, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Enable Alarms & Reminders in Settings for precise timer alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Open", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { showExactAlarmBanner = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Text(
                text = "Step ${currentStepIndex + 1} of ${steps.size}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))

            Text(
                text = currentStep.text,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Normal,
                    lineHeight = 32.sp
                ),
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            currentStep.timerSeconds?.let { seconds ->
                if (stepTimerRunning) {
                    Text(
                        text = "Timer running \u23F0",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else if (stepIsRinging) {
                    Text(
                        text = "\u23F0 Timer finished!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (activeTimer != null && !activeTimer.isRunning) {
                    Button(
                        onClick = { vm.clearTimer(context, recipeId, currentStepIndex) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Done!")
                    }
                } else {
                    var adjustedSeconds by remember { mutableStateOf(seconds) }
                    LaunchedEffect(currentStepIndex) { adjustedSeconds = seconds }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val step = when {
                                adjustedSeconds < 180 -> 15
                                adjustedSeconds < 600 -> 30
                                else -> 60
                            }
                            IconButton(
                                onClick = { adjustedSeconds = maxOf(5, adjustedSeconds - step) }
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease timer")
                            }
                            Text(
                                text = formatDuration(adjustedSeconds),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            IconButton(
                                onClick = { adjustedSeconds = minOf(5999, adjustedSeconds + step) }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase timer")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                                vm.startTimer(context, recipeId, currentStepIndex, currentStep.text, adjustedSeconds)
                            }
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Start ${formatDuration(adjustedSeconds)}")
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { currentStepIndex = (currentStepIndex - 1).coerceAtLeast(0) },
                    enabled = currentStepIndex > 0
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous step")
                }
                IconButton(
                    onClick = { currentStepIndex = (currentStepIndex + 1).coerceAtMost(steps.size - 1) },
                    enabled = currentStepIndex < steps.size - 1
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next step")
                }
            }

            if (activeTimers.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("Running Timers", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                activeTimers.forEach { (key, timer) ->
                    val isMinimized = key in minimizedTimers
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isMinimized) Modifier.clickable {
                                    minimizedTimers = minimizedTimers - key
                                } else Modifier
                            )
                            .padding(vertical = 6.dp, horizontal = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (isMinimized) Modifier.background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) else Modifier
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Timer, contentDescription = null,
                            tint = if (isMinimized) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = timer.stepText.take(40),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isMinimized) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        LiveCountdownText(
                            timer,
                            tick,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            val minimizedList = activeTimers.filterKeys { it in minimizedTimers }
            if (minimizedList.isNotEmpty()) {
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    minimizedList.forEach { (key, timer) ->
                        Box(
                            modifier = Modifier
                                .clickable { minimizedTimers = minimizedTimers - key }
                        ) {
                            TimerCircle(
                                activeTimer = timer,
                                tick = tick,
                                size = 64.dp,
                                strokeWidth = 3.dp,
                                bgColor = Color.White.copy(alpha = 0.12f),
                                arcColor = Color(0xFFFF9800),
                                textColor = Color.White.copy(alpha = 0.7f),
                                circleBgColor = Color(0x1AFFFFFF)
                            )
                        }
                    }
                }
            }
            }
        }

        val popupMinimized = timerKey in minimizedTimers
        if ((stepTimerRunning || stepIsRinging) && !popupMinimized) {
            DraggableTimerOverlay(
                activeTimer = activeTimer,
                tick = tick,
                isRinging = stepIsRinging,
                onStop = {
                    minimizedTimers = minimizedTimers - timerKey
                    if (stepIsRinging) {
                        vm.dismissAlarm(context, recipeId, currentStepIndex)
                    } else {
                        vm.cancelTimer(context, recipeId, currentStepIndex)
                    }
                },
                onClose = {
                    minimizedTimers = minimizedTimers - timerKey
                    if (stepIsRinging) {
                        vm.dismissAlarm(context, recipeId, currentStepIndex)
                    } else {
                        vm.cancelTimer(context, recipeId, currentStepIndex)
                    }
                }
            )
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

private fun formatRemaining(remainingSeconds: Long): String {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

@Composable
private fun LiveCountdownText(
    timer: ActiveTimer,
    tick: Long,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge,
    fontWeight: FontWeight? = null
) {
    @Suppress("UNUSED_EXPRESSION")
    tick
    Text(
        text = formatRemaining(timer.remainingSeconds),
        style = style,
        fontWeight = fontWeight ?: FontWeight.Normal
    )
}
