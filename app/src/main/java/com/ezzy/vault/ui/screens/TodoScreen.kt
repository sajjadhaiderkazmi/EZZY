package com.ezzy.vault.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ezzy.vault.AppContainer
import com.ezzy.vault.data.db.TaskEntity
import com.ezzy.vault.tasks.TaskAlarms
import com.ezzy.vault.tasks.TaskKind
import com.ezzy.vault.tasks.TaskTime
import com.ezzy.vault.ui.LocalSnackbar
import com.ezzy.vault.ui.components.EmptyState
import com.ezzy.vault.ui.ezzyViewModel
import com.ezzy.vault.ui.todo.ProgressRing
import com.ezzy.vault.ui.todo.TaskActions
import com.ezzy.vault.ui.todo.TaskEditorSheet
import com.ezzy.vault.ui.todo.TaskRow
import com.ezzy.vault.ui.todo.bucketTasks
import com.ezzy.vault.ui.todo.dueByToday
import com.ezzy.vault.ui.todo.isOverdue
import com.ezzy.vault.ui.todo.overdueColor
import com.ezzy.vault.ui.todo.rememberCompleteWithUndo
import com.ezzy.vault.ui.todo.rememberNotificationAsker
import com.ezzy.vault.ui.todo.rememberNow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodoViewModel(container: AppContainer) : ViewModel() {

    private val tasks = container.taskRepository
    val actions = TaskActions(viewModelScope, tasks)

    val open: StateFlow<List<TaskEntity>> = tasks.observeOpen()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val done: StateFlow<List<TaskEntity>> = tasks.observeDone()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val completions: StateFlow<List<Long>> = tasks.observeRecentCompletions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Ticks today, and the streak of days in a row with at least one. */
data class TodayProgress(val done: Int, val total: Int, val streak: Int, val overdue: Int) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

fun todayProgress(open: List<TaskEntity>, completions: List<Long>, now: Long): TodayProgress {
    val today = TaskTime.startOfDay(now)
    val doneToday = completions.count { it >= today }
    val left = dueByToday(open, now).size
    return TodayProgress(
        done = doneToday,
        total = doneToday + left,
        streak = TaskTime.streak(completions, now),
        overdue = open.count { isOverdue(it, now) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(onBack: () -> Unit) {
    val viewModel: TodoViewModel = ezzyViewModel { TodoViewModel(it) }
    val open by viewModel.open.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    val completions by viewModel.completions.collectAsStateWithLifecycle()
    val now = rememberNow()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val completeTask = rememberCompleteWithUndo(viewModel.actions)
    val askNotifications = rememberNotificationAsker()

    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var creating by remember { mutableStateOf<TaskKind?>(null) }
    var showDone by remember { mutableStateOf(false) }
    var canNotify by remember { mutableStateOf(true) }
    var canRingExactly by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canNotify = NotificationManagerCompat.from(context).areNotificationsEnabled()
        canRingExactly = TaskAlarms.canRingExactly(context)
    }

    val buckets = bucketTasks(open, now)
    val progress = todayProgress(open, completions, now)
    val hasReminders = open.any { it.remind }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("To Do") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { creating = TaskKind.ALARM }) {
                        Icon(Icons.Rounded.Alarm, contentDescription = "New alarm")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = TaskKind.TASK },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Task", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "summary") { SummaryCard(progress) }

            if (hasReminders && (!canNotify || !canRingExactly)) {
                item(key = "warn") {
                    NoticeCard(
                        icon = Icons.Rounded.NotificationsOff,
                        title = if (!canNotify) "Reminders can't show" else "Reminders may ring late",
                        message = if (!canNotify) "Allow notifications for EZZY so reminders can ring."
                        else "Allow \"Alarms & reminders\" for EZZY so they ring on the minute.",
                        action = "Allow",
                        onAction = {
                            val intent = if (!canNotify) {
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                android.content.Intent(
                                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    android.net.Uri.parse("package:${context.packageName}"),
                                )
                            } else {
                                null
                            }
                            if (intent != null) {
                                runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        },
                    )
                }
            }

            if (progress.overdue > 0) {
                item(key = "rescue") {
                    NoticeCard(
                        icon = Icons.Rounded.Today,
                        title = if (progress.overdue == 1) "1 task is late" else "${progress.overdue} tasks are late",
                        message = "Give them a fresh start today instead of letting them pile up.",
                        action = "Do today",
                        warning = true,
                        onAction = {
                            viewModel.actions.moveOverdueToToday { moved ->
                                scope.launch { snackbar.showSnackbar("Moved $moved to today") }
                            }
                        },
                    )
                }
            }

            if (open.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.TaskAlt,
                        title = "Nothing to do",
                        message = "Add a task with + Task. Try typing \"Gas bill tomorrow 5pm\" — the day and time fill in on their own.",
                    )
                }
            }

            buckets.forEach { bucket ->
                item(key = "h-" + bucket.key) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp, bottom = 2.dp),
                    ) {
                        Text(
                            text = bucket.title.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (bucket.overdue) overdueColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${bucket.tasks.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(bucket.tasks, key = { bucket.key + "-" + it.id }) { task ->
                    TaskRow(
                        task = task,
                        now = now,
                        onToggle = { completeTask(task) },
                        onClick = { editing = task },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (done.isNotEmpty()) {
                item(key = "done-header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        TextButton(onClick = { showDone = !showDone }) {
                            Text(if (showDone) "Hide done (${done.size})" else "Show done (${done.size})", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.weight(1f))
                        if (showDone) {
                            TextButton(onClick = { viewModel.actions.clearDone() }) {
                                Text("Clear", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (showDone) {
                    items(done, key = { "done-" + it.id }) { task ->
                        TaskRow(
                            task = task,
                            now = now,
                            onToggle = { viewModel.actions.reopen(task.id) },
                            onClick = { editing = task },
                            done = true,
                        )
                    }
                }
            }
        }
    }

    creating?.let { kind ->
        TaskEditorSheet(
            initial = null,
            kind = kind,
            onDismiss = { creating = null },
            onSave = { draft ->
                viewModel.actions.save(null, draft)
                if (draft.remind && draft.hasTime) askNotifications()
                creating = null
            },
        )
    }

    editing?.let { task ->
        TaskEditorSheet(
            initial = task,
            kind = TaskKind.from(task.kind),
            onDismiss = { editing = null },
            onSave = { draft ->
                viewModel.actions.save(task, draft)
                if (draft.remind && draft.hasTime) askNotifications()
                editing = null
            },
            onDelete = {
                viewModel.actions.delete(task.id)
                editing = null
            },
        )
    }
}

@Composable
private fun SummaryCard(progress: TodayProgress) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            ProgressRing(
                fraction = progress.fraction,
                modifier = Modifier.size(64.dp),
                stroke = 6.dp,
                track = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            ) {
                Text(
                    "${progress.done}/${progress.total}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        progress.total == 0 -> "Nothing due today"
                        progress.done >= progress.total -> "All done for today"
                        else -> "${progress.total - progress.done} left today"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.size(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = when (progress.streak) {
                            0 -> "Tick one off to start a streak"
                            1 -> "1-day streak"
                            else -> "${progress.streak}-day streak"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(
    icon: ImageVector,
    title: String,
    message: String,
    action: String,
    onAction: () -> Unit,
    warning: Boolean = false,
) {
    val tint = if (warning) overdueColor() else MaterialTheme.colorScheme.primary
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (warning) tint.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onAction) {
                Text(action, fontWeight = FontWeight.Bold, color = tint)
            }
        }
    }
}
