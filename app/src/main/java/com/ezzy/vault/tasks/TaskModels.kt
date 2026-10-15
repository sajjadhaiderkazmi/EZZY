package com.ezzy.vault.tasks

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** A task is ticked off; an alarm just rings at its time and is done once dismissed. */
enum class TaskKind {
    TASK, ALARM;

    companion object {
        fun from(raw: String?): TaskKind = entries.firstOrNull { it.name == raw } ?: TASK
    }
}

enum class TaskPriority(val level: Int, val label: String) {
    URGENT(1, "Urgent"),
    HIGH(2, "High"),
    NORMAL(3, "Normal"),
    LOW(4, "Low");

    companion object {
        val default = NORMAL
        fun from(level: Int): TaskPriority = entries.firstOrNull { it.level == level } ?: NORMAL
    }
}

enum class TaskRepeat(val label: String) {
    NONE("Once"),
    DAILY("Daily"),
    WEEKDAYS("Weekdays"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly");

    companion object {
        fun from(raw: String?): TaskRepeat = entries.firstOrNull { it.name == raw } ?: NONE
    }
}

/** Plain date maths for tasks, kept free of Android so it can be reasoned about on its own. */
object TaskTime {

    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR

    fun startOfDay(millis: Long): Long = Calendar.getInstance().run {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    /** Midnight [days] days after the day [millis] falls on — safe across clock changes. */
    fun addDays(millis: Long, days: Int): Long = Calendar.getInstance().run {
        timeInMillis = millis
        add(Calendar.DAY_OF_YEAR, days)
        timeInMillis
    }

    fun at(dayMillis: Long, hour: Int, minute: Int): Long = Calendar.getInstance().run {
        timeInMillis = dayMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    fun hourOf(millis: Long): Int = Calendar.getInstance().run {
        timeInMillis = millis
        get(Calendar.HOUR_OF_DAY)
    }

    fun minuteOf(millis: Long): Int = Calendar.getInstance().run {
        timeInMillis = millis
        get(Calendar.MINUTE)
    }

    /**
     * When a repeating task is due next after the turn dated [dueAt] was ticked. Always at
     * least one step on from that turn, and then on until it is no longer in the past — so a
     * daily task left undone for a week comes back for today, not for six days ago, and
     * ticking today's turn early brings back tomorrow's.
     */
    fun nextOccurrence(dueAt: Long, hasTime: Boolean, repeat: TaskRepeat, now: Long): Long? {
        if (repeat == TaskRepeat.NONE) return null
        val cal = Calendar.getInstance().apply { timeInMillis = dueAt }
        // A turn with a time is past once that minute has gone; a whole-day turn only once
        // the day itself has.
        val floor = if (hasTime) now else startOfDay(now) - 1
        var guard = 0
        do {
            when (repeat) {
                TaskRepeat.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                TaskRepeat.WEEKDAYS -> {
                    do {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    } while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                        cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                    )
                }
                TaskRepeat.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                TaskRepeat.MONTHLY -> cal.add(Calendar.MONTH, 1)
                TaskRepeat.NONE -> return null
            }
            guard++
        } while (cal.timeInMillis <= floor && guard < 2000)
        return cal.timeInMillis
    }

    /** "Today", "Tomorrow", "Yesterday", a weekday within the week, or "Mon 20 Oct". */
    fun dayLabel(millis: Long, now: Long): String {
        val diff = ((startOfDay(millis) - startOfDay(now)).toDouble() / DAY).let { Math.round(it).toInt() }
        return when {
            diff == 0 -> "Today"
            diff == 1 -> "Tomorrow"
            diff == -1 -> "Yesterday"
            diff in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(millis))
            else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(millis))
        }
    }

    fun timeLabel(millis: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

    /** Days in a row, ending today (or yesterday, while today is still open), with a tick. */
    fun streak(completionTimes: List<Long>, now: Long): Int {
        if (completionTimes.isEmpty()) return 0
        val days = completionTimes.map { startOfDay(it) }.toSet()
        var day = startOfDay(now)
        if (day !in days) day = addDays(day, -1)
        var count = 0
        while (day in days) {
            count++
            day = addDays(day, -1)
        }
        return count
    }
}
