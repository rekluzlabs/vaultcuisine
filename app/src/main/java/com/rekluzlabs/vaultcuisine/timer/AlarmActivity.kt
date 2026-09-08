package com.rekluzlabs.vaultcuisine.timer

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rekluzlabs.vaultcuisine.MainViewModel
import com.rekluzlabs.vaultcuisine.VaultCuisineApp
import kotlinx.coroutines.delay

class AlarmActivity : ComponentActivity() {

    private val recipeId: String by lazy {
        intent.getStringExtra(TimerBroadcastReceiver.EXTRA_RECIPE_ID) ?: ""
    }
    private val stepIndex: Int by lazy {
        intent.getIntExtra(TimerBroadcastReceiver.EXTRA_STEP_INDEX, -1)
    }
    private val stepText: String by lazy {
        intent.getStringExtra(TimerBroadcastReceiver.EXTRA_STEP_TEXT) ?: "Timer"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContent {
            val app = (LocalContext.current.applicationContext as VaultCuisineApp)
            val vm: MainViewModel = viewModel(factory = MainViewModel.factory(app))
            MaterialTheme {
                AlarmScreen(
                    vm = vm,
                    recipeId = recipeId,
                    stepIndex = stepIndex,
                    stepText = stepText,
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
private fun AlarmScreen(
    vm: MainViewModel,
    recipeId: String,
    stepIndex: Int,
    stepText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isAlarm = recipeId == CLOCK_ALARM_RECIPE_ID

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            val prefs = context.getSharedPreferences(
                TimerBroadcastReceiver.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val stillRinging = prefs.getBoolean("ringing_${recipeId}_$stepIndex", false)
            if (!stillRinging) {
                onDismiss()
                return@LaunchedEffect
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "alarmPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF7B1414))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(200.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Color(0xFFB71C1C))
            ) {
                Text(text = "\u23F0", fontSize = 80.sp)
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = if (isAlarm) "ALARM!" else "TIME'S UP!",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stepText,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 3
            )
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = {
                    vm.dismissAlarm(context, recipeId, stepIndex)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFFB71C1C)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("DISMISS", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
    }
}
