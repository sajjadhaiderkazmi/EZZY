package com.ezzy.vault.tasks

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.ezzy.vault.MainActivity
import com.ezzy.vault.R
import com.ezzy.vault.data.db.TaskEntity
import com.ezzy.vault.ui.nav.Routes

/**
 * Hands task reminders to Android's alarm clock and shows them when they go off.
 *
 * Reminders use [AlarmManager.setAlarmClock], the same call a clock app makes: it fires on the
 * minute even in Doze, and it is the one kind of alarm battery savers leave alone.
 */
object TaskAlarms {

    const val ACTION_RING = "com.ezzy.vault.tasks.RING"
    const val ACTION_DONE = "com.ezzy.vault.tasks.DONE"
    const val ACTION_SNOOZE = "com.ezzy.vault.tasks.SNOOZE"
    const val EXTRA_TASK_ID = "com.ezzy.vault.tasks.extra.TASK_ID"
    const val EXTRA_MINUTES = "com.ezzy.vault.tasks.extra.MINUTES"

    private const val CHANNEL_TASKS = "ezzy_task_reminders"
    private const val CHANNEL_ALARMS = "ezzy_task_alarms"

    /** When this task's reminder should ring next, or null if it should not ring at all. */
    fun ringAt(task: TaskEntity): Long? =
        if (!task.isDone && task.remind && task.hasTime) task.snoozedUntil ?: task.dueAt else null

    fun sync(context: Context, task: TaskEntity) {
        val at = ringAt(task)
        // A minute already gone never rings — an alarm set in the past would go off at once.
        if (at == null || at < System.currentTimeMillis() - 30_000L) {
            cancel(context, task.id)
            return
        }
        val alarms = context.getSystemService<AlarmManager>() ?: return
        val operation = ringIntent(context, task.id)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            } else {
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, openTodo(context)), operation)
            }
        }.onFailure {
            // Exact alarms withdrawn mid-call: an inexact one is still better than none.
            runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation) }
        }
    }

    fun cancel(context: Context, taskId: String) {
        context.getSystemService<AlarmManager>()?.cancel(ringIntent(context, taskId))
    }

    fun notificationId(taskId: String): Int = 7_000_000 + (taskId.hashCode() and 0x00FFFFFF)

    fun dismissNotification(context: Context, taskId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(taskId))
    }

    private fun taskUri(taskId: String): Uri = Uri.parse("ezzy-task:$taskId")

    private fun ringIntent(context: Context, taskId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, TaskReceiver::class.java)
                .setAction(ACTION_RING)
                .setData(taskUri(taskId))
                .putExtra(EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun openTodo(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        42,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_ROUTE, Routes.TODO),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(context: Context, action: String, taskId: String, minutes: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode() + minutes,
            Intent(context, TaskReceiver::class.java)
                .setAction(action)
                .setData(taskUri(taskId))
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_MINUTES, minutes),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarmAudio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TASKS,
                context.getString(R.string.task_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.task_channel_description)
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARMS,
                context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.alarm_channel_description)
                setSound(alarmSound, alarmAudio)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
                setBypassDnd(true)
            }
        )
    }

    /**
     * Shows a reminder that has just gone off: a heads-up with Done and Snooze on it, and — for
     * an alarm, or a task on a locked phone — the full-screen alarm page on top.
     */
    @SuppressLint("MissingPermission") // checked at the top, before anything is posted
    fun showRinging(context: Context, task: TaskEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // No way to show it as a notification, so the alarm page is the only voice left.
            runCatching { context.startActivity(TaskAlarmActivity.intent(context, task.id)) }
            return
        }
        ensureChannels(context)
        val isAlarm = TaskKind.from(task.kind) == TaskKind.ALARM
        val fullScreen = PendingIntent.getActivity(
            context,
            notificationId(task.id),
            TaskAlarmActivity.intent(context, task.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val due = ringAt(task) ?: System.currentTimeMillis()
        val detail = buildString {
            append(if (isAlarm) "Alarm" else "Due")
            append(" · ")
            append(TaskTime.timeLabel(due))
            if (task.snoozeCount > 0) {
                append(" · snoozed ${task.snoozeCount}×")
            }
        }

        val builder = NotificationCompat.Builder(context, if (isAlarm) CHANNEL_ALARMS else CHANNEL_TASKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(detail)
            .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, context.getString(R.string.task_action_done), actionIntent(context, ACTION_DONE, task.id))
            .addAction(0, context.getString(R.string.task_action_snooze_10), actionIntent(context, ACTION_SNOOZE, task.id, 10))
            .addAction(0, context.getString(R.string.task_action_snooze_60), actionIntent(context, ACTION_SNOOZE, task.id, 60))

        val notification = builder.build()
        // An alarm keeps sounding until it is answered, like a clock app's does.
        if (isAlarm) notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(task.id), notification) }
    }

    /** Whether Android will let reminders open the full-screen alarm page (Android 14+ asks). */
    fun canUseFullScreen(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService<NotificationManager>()?.canUseFullScreenIntent() ?: true
        } else {
            true
        }

    /** Whether reminders can ring on the exact minute (Android 12+ can withdraw this). */
    fun canRingExactly(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
}
