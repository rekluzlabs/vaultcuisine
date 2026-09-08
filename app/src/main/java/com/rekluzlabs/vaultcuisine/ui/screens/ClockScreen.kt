/*
 * Copyright (c) 2026 Rekluz Labs. All rights reserved.
 * This code and its assets are the exclusive property of Rekluz Labs.
 * Unauthorized copying, distribution, or commercial use is strictly prohibited.
 */
package com.rekluzlabs.vaultcuisine.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rekluzlabs.vaultcuisine.MainViewModel
import com.rekluzlabs.vaultcuisine.timer.ActiveTimer
import com.rekluzlabs.vaultcuisine.timer.CLOCK_ALARM_MAP_KEY
import com.rekluzlabs.vaultcuisine.timer.CLOCK_ALARM_RECIPE_ID
import com.rekluzlabs.vaultcuisine.timer.CLOCK_ALARM_STEP_INDEX
import com.rekluzlabs.vaultcuisine.timer.CLOCK_TIMER_MAP_KEY
import com.rekluzlabs.vaultcuisine.timer.CLOCK_TIMER_RECIPE_ID
import com.rekluzlabs.vaultcuisine.timer.CLOCK_TIMER_STEP_INDEX
import com.rekluzlabs.vaultcuisine.timer.TimerCircle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activeTimers by vm.activeTimers.collectAsState()
    val ringingTimers by vm.ringingTimers.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Timer", "Alarm")

    var showOverlayPrompt by remember { mutableStateOf(false) }

    val beforeStart: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            showOverlayPrompt = true
        }
    }

    val timerActive = activeTimers[CLOCK_TIMER_MAP_KEY]
    val timerRinging = CLOCK_TIMER_MAP_KEY in ringingTimers
    val alarmActive = activeTimers[CLOCK_ALARM_MAP_KEY]
    val alarmRinging = CLOCK_ALARM_MAP_KEY in ringingTimers

    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    LaunchedEffect(Unit) {
        vm.loadActiveTimers()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VaultCuisine Recipe Timer") },
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
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            when (selectedTab) {
                0 -> TimerTab(
                    vm = vm,
                    active = timerActive,
                    isRinging = timerRinging,
                    tick = tick,
                    onBeforeStart = beforeStart
                )
                1 -> AlarmTab(
                    vm = vm,
                    active = alarmActive,
                    isRinging = alarmRinging,
                    tick = tick,
                    onBeforeStart = beforeStart
                )
            }
        }
    }

    if (showOverlayPrompt) {
        AlertDialog(
            onDismissRequest = { showOverlayPrompt = false },
            title = { Text("Show timer over other apps") },
            text = {
                Text(
                    "To make the timer popup appear on top of everything " +
                        "(even when you are in another app), enable " +
                        "\"Display over other apps\" for VaultCuisine."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOverlayPrompt = false
                        try {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                        }
                    }
                ) {
                    Text("Open settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPrompt = false }) {
                    Text("Not now")
                }
            }
        )
    }
}

@Composable
private fun TimerTab(
    vm: MainViewModel,
    active: ActiveTimer?,
    isRinging: Boolean,
    tick: Long,
    onBeforeStart: () -> Unit
) {
    val context = LocalContext.current
    val isRunning = active != null && active.isRunning

    var minutes by remember { mutableIntStateOf(5) }
    var seconds by remember { mutableIntStateOf(0) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        when {
            isRinging -> {
                Text(
                    text = "TIME'S UP!",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { vm.dismissAlarm(context, CLOCK_TIMER_RECIPE_ID, CLOCK_TIMER_STEP_INDEX) }
                ) {
                    Text("Dismiss")
                }
            }
            isRunning && active != null -> {
                TimerCircle(activeTimer = active, tick = tick, size = 220.dp)
                Spacer(Modifier.height(32.dp))
                OutlinedButton(
                    onClick = { vm.cancelTimer(context, CLOCK_TIMER_RECIPE_ID, CLOCK_TIMER_STEP_INDEX) }
                ) {
                    Text("Stop")
                }
            }
            else -> {
                Text(
                    text = "Set a timer",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 3, 5, 10, 15, 30, 45, 60).forEach { m ->
                        FilterChip(
                            selected = minutes == m && seconds == 0,
                            onClick = { minutes = m; seconds = 0 },
                            label = { Text("$m min") }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { minutes = (minutes - 1).coerceAtLeast(0) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease minutes")
                    }
                    Text(
                        text = "$minutes min",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = { minutes = (minutes + 1).coerceAtMost(99) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase minutes")
                    }
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = { seconds = (seconds - 5).coerceAtLeast(0) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease seconds")
                    }
                    Text(
                        text = "$seconds sec",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = { seconds = (seconds + 5).coerceAtMost(55) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase seconds")
                    }
                }
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = {
                        onBeforeStart()
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                        vm.startTimer(
                            context,
                            CLOCK_TIMER_RECIPE_ID,
                            CLOCK_TIMER_STEP_INDEX,
                            "Timer",
                            maxOf(5, minutes * 60 + seconds)
                        )
                    }
                ) {
                    Text("Start")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmTab(
    vm: MainViewModel,
    active: ActiveTimer?,
    isRinging: Boolean,
    tick: Long,
    onBeforeStart: () -> Unit
) {
    val context = LocalContext.current
    val isRunning = active != null && active.isRunning

    val timePickerState = rememberTimePickerState(
        initialHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        initialMinute = 0,
        is24Hour = true
    )

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        when {
            isRinging -> {
                Text(
                    text = "ALARM!",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { vm.dismissAlarm(context, CLOCK_ALARM_RECIPE_ID, CLOCK_ALARM_STEP_INDEX) }
                ) {
                    Text("Dismiss")
                }
            }
            isRunning && active != null -> {
                Text(
                    text = formatClockTime(active.endTimeMillis),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Alarm rings in ${formatRemainingLong(active.remainingSeconds)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(32.dp))
                OutlinedButton(
                    onClick = { vm.cancelTimer(context, CLOCK_ALARM_RECIPE_ID, CLOCK_ALARM_STEP_INDEX) }
                ) {
                    Text("Cancel alarm")
                }
            }
            else -> {
                Text(
                    text = "Set a specific time for the alarm",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                TimePicker(state = timePickerState)
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = {
                        onBeforeStart()
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                        val now = Calendar.getInstance()
                        val target = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
                        }
                        val totalSeconds = ((target.timeInMillis - now.timeInMillis) / 1000L).toInt()
                        vm.startTimer(
                            context,
                            CLOCK_ALARM_RECIPE_ID,
                            CLOCK_ALARM_STEP_INDEX,
                            "Alarm",
                            totalSeconds
                        )
                    }
                ) {
                    Text("Set alarm")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun formatClockTime(timeMillis: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMillis))
}

private fun formatRemainingLong(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
