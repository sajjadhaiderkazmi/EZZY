package com.ezzy.vault.tasks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.ezzy.vault.appContainer
import com.ezzy.vault.overlay.OverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything a task reminder does without the app open: ringing, and the Done and Snooze
 * buttons on the reminder. Also takes the "+ Task" reply typed into the floating bar's
 * notification.
 */
class TaskReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        app.appContainer.scope.launch {
            try {
                handle(app, intent)
            } catch (error: Throwable) {
                // A reminder that fails to show must never crash the app in the background.
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val tasks = context.appContainer.taskRepository
        val taskId = intent.getStringExtra(TaskAlarms.EXTRA_TASK_ID)
        when (intent.action) {
            TaskAlarms.ACTION_RING -> {
                val task = taskId?.let { tasks.get(it) } ?: return
                val ringAt = TaskAlarms.ringAt(task) ?: return
                // Rescheduled since this alarm was set: the new one will ring in its own time.
                if (ringAt > System.currentTimeMillis() + 60_000L) return
                TaskAlarms.showRinging(context, task)
            }

            TaskAlarms.ACTION_DONE -> taskId?.let {
                TaskAlarms.dismissNotification(context, it)
                tasks.complete(it)
            }

            TaskAlarms.ACTION_SNOOZE -> taskId?.let {
                val minutes = intent.getIntExtra(TaskAlarms.EXTRA_MINUTES, 10).coerceAtLeast(1)
                tasks.snooze(it, minutes)
            }

            ACTION_QUICK_ADD -> {
                val typed = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_QUICK_ADD)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                if (typed.isNotEmpty()) {
                    val parsed = TaskTextParser.parse(typed)
                    val task = tasks.create(
                        title = parsed.title,
                        dueAt = parsed.dueAt,
                        hasTime = parsed.hasTime,
                        priority = parsed.priority ?: TaskPriority.default,
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Added: ${describe(task.title, task.dueAt, task.hasTime)}", Toast.LENGTH_SHORT).show()
                    }
                }
                // The reply box keeps spinning until the notification is posted again.
                OverlayService.repostNotification(context)
            }
        }
    }

    companion object {
        const val ACTION_QUICK_ADD = "com.ezzy.vault.tasks.QUICK_ADD"
        const val KEY_QUICK_ADD = "com.ezzy.vault.tasks.key.QUICK_ADD"

        fun describe(title: String, dueAt: Long?, hasTime: Boolean): String {
            if (dueAt == null) return title
            val now = System.currentTimeMillis()
            val day = TaskTime.dayLabel(dueAt, now)
            return if (hasTime) "$title · $day ${TaskTime.timeLabel(dueAt)}" else "$title · $day"
        }
    }
}
