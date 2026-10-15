package com.ezzy.vault.tasks

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.lifecycle.lifecycleScope
import com.ezzy.vault.MainActivity
import com.ezzy.vault.appContainer
import com.ezzy.vault.data.db.TaskEntity
import com.ezzy.vault.ui.icons.EzzyMark
import com.ezzy.vault.ui.nav.Routes
import com.ezzy.vault.ui.theme.EzzyTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The full-screen page a reminder opens, over the lock screen if need be — the same idea as a
 * clock app's alarm. Always the navy brand ground, light or dark, so it reads as an alarm and
 * not as just another screen of the app.
 */
class TaskAlarmActivity : ComponentActivity() {

    private var task by mutableStateOf<TaskEntity?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        load(intent)

        setContent {
            EzzyTheme {
                AlarmPage(
                    task = task,
                    onDone = { act { appContainer.taskRepository.complete(it) } },
                    onSnooze = { minutes -> act { appContainer.taskRepository.snooze(it, minutes) } },
                    onTomorrow = { act { appContainer.taskRepository.snoozeToTomorrow(it) } },
                    onOpen = {
                        task?.let { TaskAlarms.dismissNotification(this, it.id) }
                        openApp()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        load(intent)
    }

    private fun load(intent: Intent?) {
        val id = intent?.getStringExtra(TaskAlarms.EXTRA_TASK_ID) ?: return finish()
        lifecycleScope.launch {
            val loaded = runCatching { appContainer.taskRepository.get(id) }.getOrNull()
            if (loaded == null || loaded.isDone) {
                TaskAlarms.dismissNotification(this@TaskAlarmActivity, id)
                finish()
            } else {
                task = loaded
            }
        }
    }

    private fun act(block: suspend (String) -> Unit) {
        val id = task?.id ?: return finish()
        lifecycleScope.launch {
            runCatching { block(id) }
            TaskAlarms.dismissNotification(this@TaskAlarmActivity, id)
            finish()
        }
    }

    private fun openApp() {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val goToApp = {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(MainActivity.EXTRA_ROUTE, Routes.TODO)
            )
            finish()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguard?.isKeyguardLocked == true) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = goToApp()
            })
        } else {
            goToApp()
        }
    }

    companion object {
        fun intent(context: Context, taskId: String): Intent =
            Intent(context, TaskAlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(TaskAlarms.EXTRA_TASK_ID, taskId)
    }
}

private val Ink = Color(0xFFE7ECF5)
private val InkSoft = Color(0xFF97A2B6)
private val Amber = Color(0xFFF2B866)

@Composable
private fun AlarmPage(
    task: TaskEntity?,
    onDone: () -> Unit,
    onSnooze: (Int) -> Unit,
    onTomorrow: () -> Unit,
    onOpen: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val isAlarm = task?.let { TaskKind.from(it.kind) == TaskKind.ALARM } ?: false
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EzzyMark.Brand),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isAlarm) "Alarm" else "To Do reminder",
                color = InkSoft,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(now)),
                color = Ink,
                fontSize = 64.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)),
                color = InkSoft,
            )

            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(EzzyMark.Spark.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isAlarm) Icons.Rounded.Alarm else Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = EzzyMark.Spark,
                    modifier = Modifier.size(46.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = task?.title.orEmpty(),
                color = Ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp,
            )
            if (task != null && task.snoozeCount > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (task.snoozeCount == 1) "You've snoozed this once" else "You've snoozed this ${task.snoozeCount} times",
                    color = Amber,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.weight(1f))

            Button(
                onClick = onDone,
                enabled = task != null,
                colors = ButtonDefaults.buttonColors(containerColor = EzzyMark.Spark, contentColor = EzzyMark.Brand),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (isAlarm) "Dismiss" else "Done", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AlarmChoice("10 min", Modifier.weight(1f)) { onSnooze(10) }
                AlarmChoice("1 hour", Modifier.weight(1f)) { onSnooze(60) }
                AlarmChoice("Tomorrow", Modifier.weight(1f), onTomorrow)
            }
            if (!isAlarm) {
                Spacer(Modifier.height(8.dp))
                AlarmChoice("Open To Do", Modifier.fillMaxWidth(), onOpen)
            }
        }
    }
}

@Composable
private fun AlarmChoice(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Ink.copy(alpha = 0.2f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = Ink,
        ),
        modifier = modifier.height(48.dp),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
