package com.ezzy.vault.tasks

import java.util.Calendar

/** What a one-line quick add said, once the date, time and priority words are taken out. */
data class ParsedTask(
    val title: String,
    /** Midnight of the day it named, or the exact time when [hasTime]; null if it named neither. */
    val dueAt: Long?,
    val hasTime: Boolean,
    val priority: TaskPriority?,
) {
    val foundSomething: Boolean get() = dueAt != null || priority != null
}

/**
 * Reads "Gas bill tomorrow 5pm !1" or "kal 9 baje doctor" the way a person would, in English
 * and in Roman Urdu. Only clear date and time words are taken: a bare number in a title ("Pay
 * 500") is never read as a time, so a title is never quietly eaten.
 */
object TaskTextParser {

    private val priorityRegex = Regex("""(?<![\w!])!([1-4])(?!\w)""")

    // "at 5", "5pm", "5:30 pm", "17:30", "5 baje", "5:30 baje", "at 9:15"
    private val timeRegex = Regex(
        """(?i)(?<![\w:])(?:(?:at|@)\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.|baje|bjy|bajay)(?!\w)""" +
            """|(?i)(?<![\w:])(?:(?:at|@)\s+)(\d{1,2})(?::(\d{2}))?(?![\w:])""" +
            """|(?<![\w:])([01]?\d|2[0-3]):([0-5]\d)(?![\w:])"""
    )

    private val partOfDayRegex = Regex(
        """(?i)(?<!\w)(morning|subah|subha|afternoon|dopahar|dopehar|evening|shaam|sham|night|raat|tonight)(?!\w)"""
    )

    private val inRegex = Regex(
        """(?i)(?<!\w)(?:in\s+(\d{1,3})\s*(min|mins|minutes|minute|m|hour|hours|hr|hrs|h)""" +
            """|(\d{1,3})\s*(min|mins|minute|minutes|ghanta|ghante|ghanty|hour|hours)\s+(?:baad|bad|later))(?!\w)"""
    )

    private val dayWords: List<Pair<Regex, Int>> = listOf(
        Regex("""(?i)(?<!\w)(day after tomorrow|parson|parso|parsun)(?!\w)""") to 2,
        Regex("""(?i)(?<!\w)(tomorrow|tmrw|tmr|kal|kl)(?!\w)""") to 1,
        Regex("""(?i)(?<!\w)(today|aaj|aj|tonight)(?!\w)""") to 0,
    )

    private val weekdays = listOf(
        Calendar.MONDAY to "monday|pir",
        Calendar.TUESDAY to "tuesday|mangal",
        Calendar.WEDNESDAY to "wednesday|budh",
        Calendar.THURSDAY to "thursday|jumerat",
        Calendar.FRIDAY to "friday|juma|jumma|jummah",
        Calendar.SATURDAY to "saturday",
        Calendar.SUNDAY to "sunday|itwar|itwaar",
    ).map { (day, words) -> day to Regex("""(?i)(?<!\w)(?:next\s+|on\s+)?($words)(?!\w)""") }

    fun parse(input: String, now: Long = System.currentTimeMillis()): ParsedTask {
        var text = " $input "

        var priority: TaskPriority? = null
        priorityRegex.find(text)?.let { match ->
            priority = TaskPriority.from(match.groupValues[1].toInt())
            text = text.removeRange(match.range)
        }

        // "in 20 min" / "2 ghante baad" sets the exact time and the day with it.
        inRegex.find(text)?.let { match ->
            val amount = (match.groupValues[1].ifEmpty { match.groupValues[3] }).toIntOrNull()
            val unit = (match.groupValues[2].ifEmpty { match.groupValues[4] }).lowercase()
            if (amount != null && amount > 0) {
                val minutes = if (unit.startsWith("m")) amount else amount * 60
                val at = now + minutes * TaskTime.MINUTE
                text = text.removeRange(match.range)
                return ParsedTask(clean(text, input), at - at % TaskTime.MINUTE, true, priority)
            }
        }

        var dayOffset: Int? = null
        var weekday: Int? = null
        var tonight = false
        for ((regex, offset) in dayWords) {
            val match = regex.find(text) ?: continue
            dayOffset = offset
            if (match.value.trim().equals("tonight", ignoreCase = true)) tonight = true
            text = text.removeRange(match.range)
            break
        }
        if (dayOffset == null) {
            for ((day, regex) in weekdays) {
                val match = regex.find(text) ?: continue
                weekday = day
                text = text.removeRange(match.range)
                break
            }
        }

        var hour: Int? = null
        var minute = 0
        var meridiem: String? = null
        timeRegex.find(text)?.let { match ->
            val g = match.groupValues
            when {
                g[1].isNotEmpty() -> {
                    hour = g[1].toInt()
                    minute = g[2].toIntOrNull() ?: 0
                    meridiem = g[3].lowercase()
                }
                g[4].isNotEmpty() -> {
                    hour = g[4].toInt()
                    minute = g[5].toIntOrNull() ?: 0
                }
                else -> {
                    hour = g[6].toInt()
                    minute = g[7].toInt()
                    meridiem = "24h"
                }
            }
            text = text.removeRange(match.range)
        }

        var partOfDay: String? = null
        partOfDayRegex.find(text)?.let { match ->
            partOfDay = match.groupValues[1].lowercase()
            text = text.removeRange(match.range)
        }
        if (tonight && partOfDay == null) partOfDay = "night"

        val h = hour
        val resolvedHour: Int? = when {
            h == null -> partOfDay?.let { defaultHourFor(it) }
            h > 23 || minute > 59 -> null
            meridiem == "24h" -> h
            meridiem == "am" || meridiem == "a.m." -> if (h == 12) 0 else h
            meridiem == "pm" || meridiem == "p.m." -> if (h == 12) 12 else (h % 12) + 12
            h > 12 -> h
            partOfDay != null -> applyPartOfDay(h, partOfDay!!)
            // "5 baje" on its own: early numbers are afternoon, 7 to 11 are morning.
            h in 1..6 -> h + 12
            else -> h
        }
        val hasTime = resolvedHour != null
        if (h != null && resolvedHour == null) minute = 0

        val dayStart: Long? = when {
            dayOffset != null -> TaskTime.addDays(TaskTime.startOfDay(now), dayOffset!!)
            weekday != null -> nextWeekday(now, weekday!!)
            hasTime -> TaskTime.startOfDay(now)
            else -> null
        }

        var dueAt: Long? = dayStart?.let {
            if (resolvedHour != null) TaskTime.at(it, resolvedHour, minute) else it
        }
        // A time with no day that has already gone today means the next one.
        if (dueAt != null && hasTime && dayOffset == null && weekday == null && dueAt <= now) {
            dueAt = TaskTime.addDays(dueAt, 1)
        }

        return ParsedTask(clean(text, input), dueAt, hasTime, priority)
    }

    private fun defaultHourFor(part: String): Int = when (part) {
        "morning", "subah", "subha" -> 9
        "afternoon", "dopahar", "dopehar" -> 14
        "evening", "shaam", "sham" -> 18
        else -> 21
    }

    private fun applyPartOfDay(hour: Int, part: String): Int = when (part) {
        "morning", "subah", "subha" -> if (hour == 12) 0 else hour
        "night", "raat", "tonight" -> if (hour in 1..4) hour else if (hour < 12) hour + 12 else hour
        else -> if (hour < 12) hour + 12 else hour
    }

    private fun nextWeekday(now: Long, day: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = TaskTime.startOfDay(now) }
        var steps = 0
        do {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            steps++
        } while (cal.get(Calendar.DAY_OF_WEEK) != day && steps < 8)
        return cal.timeInMillis
    }

    /** What is left of the title, tidied — or the original line if parsing left nothing. */
    private fun clean(text: String, original: String): String {
        val tidy = text
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim(',', '-', '·')
            .replace(Regex("""(?i)\s+(at|on|by|ko|pe|par)$"""), "")
            .trim()
        return tidy.ifEmpty { original.trim() }
    }
}
