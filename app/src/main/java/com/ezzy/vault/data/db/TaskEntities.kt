package com.ezzy.vault.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * One thing to do, or one plain alarm. Lives in the same encrypted database as the vault, so a
 * task is as private as anything else in it.
 *
 * No column carries a default or an index: the v8→v9 migration creates this table by hand, and
 * Room checks the two match exactly when the database opens.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val note: String,
    /** [com.ezzy.vault.tasks.TaskKind] name: a task to tick off, or a bare alarm. */
    val kind: String,
    /** 1 Urgent, 2 High, 3 Normal, 4 Low — lower sorts first. */
    val priority: Int,
    /** When it is due, or null for "someday". Midnight of that day when [hasTime] is false. */
    val dueAt: Long?,
    val hasTime: Boolean,
    /** Ring an alarm at [dueAt] (or at [snoozedUntil] once snoozed). Needs a time. */
    val remind: Boolean,
    /** [com.ezzy.vault.tasks.TaskRepeat] name. */
    val repeat: String,
    val isDone: Boolean,
    val doneAt: Long?,
    /** How many times the reminder has been pushed back — shown so putting it off is visible. */
    val snoozeCount: Int,
    val snoozedUntil: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One tick of a task, kept apart from the task itself. A repeating task goes straight back to
 * open when it is ticked, so this is the only record that it was done that day — it is what
 * "done today" and the streak count.
 */
@Entity(tableName = "task_completions")
data class TaskCompletionEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val doneAt: Long,
)

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE isDone = 0")
    fun observeOpen(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDone = 1 ORDER BY doneAt DESC LIMIT :limit")
    fun observeDone(limit: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE isDone = 0 AND remind = 1")
    suspend fun openWithReminders(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE isDone = 0 AND dueAt IS NOT NULL AND dueAt < :before")
    suspend fun openDueBefore(before: Long): List<TaskEntity>

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM tasks WHERE isDone = 1")
    suspend fun deleteDone()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: TaskCompletionEntity)

    @Query("DELETE FROM task_completions WHERE id = :id")
    suspend fun deleteCompletion(id: String)

    @Query("SELECT doneAt FROM task_completions WHERE doneAt >= :since ORDER BY doneAt DESC")
    fun observeCompletionTimesSince(since: Long): Flow<List<Long>>
}
