package com.ezzy.vault.ui.todo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ezzy.vault.data.db.TaskEntity
import com.ezzy.vault.data.repo.TaskRepository
import com.ezzy.vault.data.repo.TaskUndo
import com.ezzy.vault.tasks.TaskKind
import com.ezzy.vault.ui.LocalSnackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The task changes every screen makes — Home, To Do and the floating bar — in one place, run
 * on the caller's own scope so a tick is never lost when the screen goes away.
 */
class TaskActions(private val scope: CoroutineScope, private val tasks: TaskRepository) {

    fun complete(id: String, then: (TaskUndo?) -> Unit = {}) {
        scope.launch { then(runCatching { tasks.complete(id) }.getOrNull()) }
    }

    fun undo(undo: TaskUndo) {
        scope.launch { runCatching { tasks.undo(undo) } }
    }

    fun reopen(id: String) {
        scope.launch { runCatching { tasks.reopen(id) } }
    }

    fun delete(id: String) {
        scope.launch { runCatching { tasks.delete(id) } }
    }

    fun clearDone() {
        scope.launch { runCatching { tasks.clearDone() } }
    }

    fun moveOverdueToToday(then: (Int) -> Unit = {}) {
        scope.launch { then(runCatching { tasks.moveOverdueToToday() }.getOrDefault(0)) }
    }

    fun save(initial: TaskEntity?, draft: TaskDraft) {
        scope.launch {
            runCatching {
                if (initial == null) {
                    tasks.create(
                        title = draft.title,
                        kind = draft.kind,
                        dueAt = draft.dueAt,
                        hasTime = draft.hasTime,
                        priority = draft.priority,
                        remind = draft.remind,
                        repeat = draft.repeat,
                        note = draft.note,
                    )
                } else {
                    tasks.update(
                        initial.copy(
                            title = draft.title,
                            note = draft.note,
                            kind = draft.kind.name,
                            priority = draft.priority.level,
                            dueAt = draft.dueAt,
                            hasTime = draft.hasTime,
                            remind = draft.remind,
                            repeat = draft.repeat.name,
                        )
                    )
                }
            }
        }
    }
}

/** Ticks a task off and offers "Undo" in the snackbar for a few seconds. */
@Composable
fun rememberCompleteWithUndo(actions: TaskActions): (TaskEntity) -> Unit {
    val snackbar = LocalSnackbar.current
    val uiScope = rememberCoroutineScope()
    return remember(actions, snackbar) {
        { task: TaskEntity ->
            actions.complete(task.id) { undo ->
                if (undo != null) {
                    uiScope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        val isAlarm = TaskKind.from(task.kind) == TaskKind.ALARM
                        val result = snackbar.showSnackbar(
                            message = if (isAlarm) "Alarm done" else "Done: ${task.title}",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) actions.undo(undo)
                    }
                }
            }
        }
    }
}

/**
 * Asks for notification permission the first time a reminder is saved on Android 13+: without
 * it a reminder can only ring through the full-screen page.
 */
@Composable
fun rememberNotificationAsker(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember<() -> Unit>(context, launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        }
    }
}

/** The current time, refreshed every half minute so "late" and "Today" stay true on screen. */
@Composable
fun rememberNow(): Long {
    val state = androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            value = System.currentTimeMillis()
        }
    }
    return state.value
}
