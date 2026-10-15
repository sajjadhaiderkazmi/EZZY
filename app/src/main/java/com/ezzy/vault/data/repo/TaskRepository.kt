package com.ezzy.vault.data.repo

import android.content.Context
import com.ezzy.vault.data.db.TaskCompletionEntity
import com.ezzy.vault.data.db.TaskDao
import com.ezzy.vault.data.db.TaskEntity
import com.ezzy.vault.tasks.TaskAlarms
import com.ezzy.vault.tasks.TaskKind
import com.ezzy.vault.tasks.TaskPriority
import com.ezzy.vault.tasks.TaskRepeat
import com.ezzy.vault.tasks.TaskTime
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** What it takes to put a ticked task back exactly as it was. */
data class TaskUndo(val before: TaskEntity, val completionId: String)

/**
 * Everything To Do reads and writes. Every change that can move a reminder goes through here,
 * so the alarm Android holds always matches the task on disk.
 */
class TaskRepository(context: Context, private val dao: TaskDao) {

    private val appContext = context.applicationContext

    fun observeOpen(): Flow<List<TaskEntity>> = dao.observeOpen()

    fun observeDone(limit: Int = 50): Flow<List<TaskEntity>> = dao.observeDone(limit)

    /** Every tick from the last couple of months — enough for "done today" and the streak. */
    fun observeRecentCompletions(now: Long = System.currentTimeMillis()): Flow<List<Long>> =
        dao.observeCompletionTimesSince(TaskTime.addDays(TaskTime.startOfDay(now), -60))

    suspend fun get(id: String): TaskEntity? = dao.get(id)

    suspend fun create(
        title: String,
        kind: TaskKind = TaskKind.TASK,
        dueAt: Long? = null,
        hasTime: Boolean = false,
        priority: TaskPriority = TaskPriority.default,
        remind: Boolean = hasTime,
        repeat: TaskRepeat = TaskRepeat.NONE,
        note: String = "",
    ): TaskEntity {
        val now = System.currentTimeMillis()
        val task = TaskEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            note = note.trim(),
            kind = kind.name,
            priority = priority.level,
            dueAt = dueAt,
            hasTime = hasTime && dueAt != null,
            remind = remind && hasTime && dueAt != null,
            repeat = repeat.name,
            isDone = false,
            doneAt = null,
            snoozeCount = 0,
            snoozedUntil = null,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(task)
        TaskAlarms.sync(appContext, task)
        return task
    }

    /** Saves an edit. A new time clears any snooze, since the user just chose when. */
    suspend fun update(task: TaskEntity) {
        val previous = dao.get(task.id)
        val timeChanged = previous == null || previous.dueAt != task.dueAt || previous.hasTime != task.hasTime
        val saved = task.copy(
            title = task.title.trim(),
            hasTime = task.hasTime && task.dueAt != null,
            remind = task.remind && task.hasTime && task.dueAt != null,
            snoozedUntil = if (timeChanged) null else task.snoozedUntil,
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsert(saved)
        TaskAlarms.sync(appContext, saved)
    }

    /**
     * Ticks a task off. A repeating one goes straight back to open for its next turn, with its
     * snooze count reset; anything else is marked done. Either way the tick is logged.
     */
    suspend fun complete(id: String): TaskUndo? {
        val task = dao.get(id) ?: return null
        if (task.isDone) return null
        val now = System.currentTimeMillis()
        val completionId = UUID.randomUUID().toString()
        dao.insertCompletion(TaskCompletionEntity(completionId, task.id, now))

        val repeat = TaskRepeat.from(task.repeat)
        val next = task.dueAt?.let { TaskTime.nextOccurrence(it, task.hasTime, repeat, now) }
        val saved = if (next != null) {
            task.copy(
                dueAt = next,
                doneAt = now,
                snoozeCount = 0,
                snoozedUntil = null,
                updatedAt = now,
            )
        } else {
            task.copy(isDone = true, doneAt = now, snoozedUntil = null, updatedAt = now)
        }
        dao.upsert(saved)
        TaskAlarms.sync(appContext, saved)
        TaskAlarms.dismissNotification(appContext, task.id)
        return TaskUndo(task, completionId)
    }

    suspend fun undo(undo: TaskUndo) {
        dao.deleteCompletion(undo.completionId)
        dao.upsert(undo.before)
        TaskAlarms.sync(appContext, undo.before)
    }

    /** Opens a ticked task again, from the done list. */
    suspend fun reopen(id: String) {
        val task = dao.get(id) ?: return
        val saved = task.copy(isDone = false, doneAt = null, updatedAt = System.currentTimeMillis())
        dao.upsert(saved)
        TaskAlarms.sync(appContext, saved)
    }

    /** Pushes the reminder back by [minutes] and counts it, so putting it off stays visible. */
    suspend fun snooze(id: String, minutes: Int) {
        val task = dao.get(id) ?: return
        val now = System.currentTimeMillis()
        val until = now + minutes * TaskTime.MINUTE
        val saved = task.copy(
            snoozeCount = task.snoozeCount + 1,
            snoozedUntil = until - until % TaskTime.MINUTE,
            remind = true,
            hasTime = true,
            // A task with no time yet takes the snooze's: it is now clearly meant for then.
            dueAt = if (task.dueAt == null || !task.hasTime) until - until % TaskTime.MINUTE else task.dueAt,
            updatedAt = now,
        )
        dao.upsert(saved)
        TaskAlarms.sync(appContext, saved)
        TaskAlarms.dismissNotification(appContext, task.id)
    }

    /** Moves a reminder to the same time tomorrow, counted as a snooze. */
    suspend fun snoozeToTomorrow(id: String) {
        val task = dao.get(id) ?: return
        val now = System.currentTimeMillis()
        val base = task.snoozedUntil ?: task.dueAt ?: now
        val tomorrow = TaskTime.at(
            TaskTime.addDays(TaskTime.startOfDay(now), 1),
            TaskTime.hourOf(base),
            TaskTime.minuteOf(base),
        )
        val saved = task.copy(
            dueAt = tomorrow,
            hasTime = true,
            snoozedUntil = null,
            snoozeCount = task.snoozeCount + 1,
            updatedAt = now,
        )
        dao.upsert(saved)
        TaskAlarms.sync(appContext, saved)
        TaskAlarms.dismissNotification(appContext, task.id)
    }

    /**
     * "Do today" on the overdue card: every open task from before today moves onto today. One
     * whose time today has already gone keeps the day but loses the time, since an alarm for a
     * minute that has passed would never ring.
     */
    suspend fun moveOverdueToToday(): Int {
        val now = System.currentTimeMillis()
        val today = TaskTime.startOfDay(now)
        val overdue = dao.openDueBefore(today)
        overdue.forEach { task ->
            val due = task.dueAt ?: return@forEach
            val moved = if (task.hasTime) {
                val sameTime = TaskTime.at(today, TaskTime.hourOf(due), TaskTime.minuteOf(due))
                if (sameTime > now) {
                    task.copy(dueAt = sameTime, snoozedUntil = null)
                } else {
                    task.copy(dueAt = today, hasTime = false, remind = false, snoozedUntil = null)
                }
            } else {
                task.copy(dueAt = today)
            }
            val saved = moved.copy(snoozeCount = task.snoozeCount + 1, updatedAt = now)
            dao.upsert(saved)
            TaskAlarms.sync(appContext, saved)
        }
        return overdue.size
    }

    suspend fun delete(id: String) {
        dao.delete(id)
        TaskAlarms.cancel(appContext, id)
        TaskAlarms.dismissNotification(appContext, id)
    }

    suspend fun clearDone() = dao.deleteDone()

    /** After a reboot, an app update or a clock change: hand Android every reminder again. */
    suspend fun rescheduleAll() {
        dao.openWithReminders().forEach { TaskAlarms.sync(appContext, it) }
    }
}
