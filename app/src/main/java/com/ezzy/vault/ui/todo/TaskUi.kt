package com.ezzy.vault.ui.todo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ezzy.vault.data.db.TaskEntity
import com.ezzy.vault.tasks.TaskKind
import com.ezzy.vault.tasks.TaskPriority
import com.ezzy.vault.tasks.TaskRepeat
import com.ezzy.vault.tasks.TaskTextParser
import com.ezzy.vault.tasks.TaskTime
import com.ezzy.vault.ui.components.EzzyChip
import com.ezzy.vault.ui.theme.LocalIsDarkTheme
import java.util.Calendar
import java.util.TimeZone

// ---- Colours ---------------------------------------------------------------

/** Priority is the one place To Do uses colour beyond the app's blue: red, amber, blue, grey. */
@Composable
fun priorityColor(priority: TaskPriority): Color {
    val dark = LocalIsDarkTheme.current
    return when (priority) {
        TaskPriority.URGENT -> if (dark) Color(0xFFF87171) else Color(0xFFDC2626)
        TaskPriority.HIGH -> if (dark) Color(0xFFFBBF24) else Color(0xFFD97706)
        TaskPriority.NORMAL -> MaterialTheme.colorScheme.primary
        TaskPriority.LOW -> MaterialTheme.colorScheme.outline
    }
}

@Composable
fun overdueColor(): Color = if (LocalIsDarkTheme.current) Color(0xFFF87171) else Color(0xFFDC2626)

// ---- Grouping --------------------------------------------------------------

/** One heading's worth of open tasks on the To Do page. */
data class TaskBucket(val key: String, val title: String, val tasks: List<TaskEntity>, val overdue: Boolean = false)

private val taskOrder = compareBy<TaskEntity>({ it.dueAt ?: Long.MAX_VALUE }, { it.priority }, { it.createdAt })

/** Overdue, Today, then each coming day, then Someday — each sorted by time, then priority. */
fun bucketTasks(tasks: List<TaskEntity>, now: Long): List<TaskBucket> {
    val today = TaskTime.startOfDay(now)
    val tomorrow = TaskTime.addDays(today, 1)
    val overdue = tasks.filter { isOverdue(it, now) }
    val todays = tasks.filter { !isOverdue(it, now) && it.dueAt != null && it.dueAt < tomorrow }
    val later = tasks.filter { it.dueAt != null && it.dueAt >= tomorrow }
    val someday = tasks.filter { it.dueAt == null }
    return buildList {
        if (overdue.isNotEmpty()) add(TaskBucket("overdue", "Overdue", overdue.sortedWith(taskOrder), overdue = true))
        if (todays.isNotEmpty()) add(TaskBucket("today", "Today", todays.sortedWith(taskOrder)))
        later.groupBy { TaskTime.startOfDay(it.dueAt!!) }.toSortedMap().forEach { (day, list) ->
            add(TaskBucket("day-$day", TaskTime.dayLabel(day, now), list.sortedWith(taskOrder)))
        }
        if (someday.isNotEmpty()) {
            add(TaskBucket("someday", "Someday", someday.sortedWith(compareBy({ it.priority }, { it.createdAt }))))
        }
    }
}

/** A task is overdue once its minute has gone, or — with no time — once its day has. */
fun isOverdue(task: TaskEntity, now: Long): Boolean {
    val due = task.dueAt ?: return false
    return if (task.hasTime) due < now - TaskTime.MINUTE else due < TaskTime.startOfDay(now)
}

/** Open tasks that belong to today: today's own plus anything overdue. */
fun dueByToday(tasks: List<TaskEntity>, now: Long): List<TaskEntity> {
    val tomorrow = TaskTime.addDays(TaskTime.startOfDay(now), 1)
    return tasks.filter { it.dueAt != null && it.dueAt < tomorrow }
}

/** The three to show on Home: urgent and overdue first, then by time. */
fun topThree(tasks: List<TaskEntity>, now: Long): List<TaskEntity> {
    val pool = dueByToday(tasks, now).ifEmpty { tasks.filter { it.dueAt == null && it.priority <= TaskPriority.HIGH.level } }
    return pool.sortedWith(compareBy<TaskEntity>({ it.priority }, { it.dueAt ?: Long.MAX_VALUE }, { it.createdAt })).take(3)
}

/** "Today 5:00 PM", "Tomorrow", "Fri 20 Oct" — what a row says about when. */
fun whenLabel(task: TaskEntity, now: Long): String? {
    val due = task.snoozedUntil?.takeIf { it > (task.dueAt ?: 0L) } ?: task.dueAt ?: return null
    val day = TaskTime.dayLabel(due, now)
    return if (task.hasTime) "$day ${TaskTime.timeLabel(due)}" else day
}

// ---- Small pieces ----------------------------------------------------------

/** The round tick box, in the task's priority colour. */
@Composable
fun TaskCheck(checked: Boolean, color: Color, onClick: () -> Unit, size: Dp = 24.dp) {
    Box(
        modifier = Modifier
            .size(size + 12.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (checked) color else Color.Transparent)
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(Icons.Rounded.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(size - 8.dp))
            }
        }
    }
}

/** A thin progress ring, used for "done today". */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.outlineVariant,
    stroke: Dp = 5.dp,
    content: @Composable () -> Unit = {},
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = stroke.toPx()
            val inset = width / 2
            val arcSize = Size(size.width - width, size.height - width)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(width))
            drawArc(
                color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                style = Stroke(width, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

@Composable
private fun MetaPill(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/**
 * One task: tick box, title, and a line of what matters — when (red if late), a bell if it
 * will ring, repeat, and how many times it has been put off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskRow(
    task: TaskEntity,
    now: Long,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    done: Boolean = task.isDone,
) {
    val priority = TaskPriority.from(task.priority)
    val tint = priorityColor(priority)
    val late = !done && isOverdue(task, now)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        ) {
            TaskCheck(checked = done, color = if (done) MaterialTheme.colorScheme.outline else tint, onClick = onToggle)
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val label = whenLabel(task, now)
                val kind = TaskKind.from(task.kind)
                val hasMeta = label != null || task.remind || task.repeat != TaskRepeat.NONE.name ||
                    task.snoozeCount > 0 || priority.level <= TaskPriority.HIGH.level
                if (hasMeta && !done) {
                    Spacer(Modifier.height(3.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (label != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (task.remind) {
                                    Icon(
                                        if (kind == TaskKind.ALARM) Icons.Rounded.Alarm else Icons.Rounded.Notifications,
                                        contentDescription = null,
                                        tint = if (late) overdueColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Spacer(Modifier.width(3.dp))
                                }
                                Text(
                                    text = if (late) "$label · late" else label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (late) overdueColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (late) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                        if (task.repeat != TaskRepeat.NONE.name) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Repeat, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    TaskRepeat.from(task.repeat).label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (priority.level <= TaskPriority.HIGH.level) MetaPill(priority.label, tint)
                        if (task.snoozeCount > 0) {
                            MetaPill(
                                if (task.snoozeCount == 1) "Put off once" else "Put off ${task.snoozeCount}×",
                                priorityColor(TaskPriority.HIGH),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Add menu --------------------------------------------------------------

/** The sheet behind every "+" on Home: a task, an alarm, a vault entry or a section. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMenuSheet(
    onDismiss: () -> Unit,
    onTask: () -> Unit,
    onAlarm: () -> Unit,
    onEntry: () -> Unit,
    onSection: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "What do you want to add?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            AddOption(Icons.Rounded.TaskAlt, "Task", "Something to do, with a reminder if you like", onTask)
            AddOption(Icons.Rounded.Alarm, "Alarm", "Rings at a time, like a clock alarm", onAlarm)
            AddOption(Icons.Rounded.Lock, "Vault entry", "A card, login, document or note", onEntry)
            AddOption(Icons.Rounded.CreateNewFolder, "Section", "A new folder for your vault", onSection)
        }
    }
}

@Composable
private fun AddOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- Editor ----------------------------------------------------------------

/** What the editor hands back on Save. */
data class TaskDraft(
    val title: String,
    val note: String,
    val kind: TaskKind,
    val priority: TaskPriority,
    val dueAt: Long?,
    val hasTime: Boolean,
    val remind: Boolean,
    val repeat: TaskRepeat,
)

/**
 * Adds or edits a task or alarm. Typing "tomorrow 5pm !1" into the title fills the date, time
 * and priority on its own — shown under the title, so it is never a surprise.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
    initial: TaskEntity?,
    kind: TaskKind,
    onDismiss: () -> Unit,
    onSave: (TaskDraft) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val now = remember { System.currentTimeMillis() }
    val today = TaskTime.startOfDay(now)
    val isAlarm = (initial?.let { TaskKind.from(it.kind) } ?: kind) == TaskKind.ALARM
    val nextHour = remember {
        Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
        }.let { it.get(Calendar.HOUR_OF_DAY) * 60 }
    }

    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    var day by rememberSaveable {
        mutableStateOf(initial?.dueAt?.let { TaskTime.startOfDay(it) } ?: if (isAlarm) today else null)
    }
    // Minutes after midnight, or null for "any time that day".
    var minutes by rememberSaveable {
        mutableStateOf(
            initial?.takeIf { it.hasTime && it.dueAt != null }?.let { TaskTime.hourOf(it.dueAt!!) * 60 + TaskTime.minuteOf(it.dueAt) }
                ?: if (isAlarm) nextHour else null
        )
    }
    var remind by rememberSaveable { mutableStateOf(initial?.remind ?: true) }
    var priority by rememberSaveable { mutableStateOf(TaskPriority.from(initial?.priority ?: TaskPriority.default.level)) }
    var repeat by rememberSaveable { mutableStateOf(TaskRepeat.from(initial?.repeat)) }
    var touchedWhen by rememberSaveable { mutableStateOf(initial != null) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    // An alarm whose time has already gone today rings tomorrow instead.
    if (isAlarm && day == today && minutes != null && TaskTime.at(today, minutes!! / 60, minutes!! % 60) <= now && initial == null) {
        day = TaskTime.addDays(today, 1)
    }

    val parsed = remember(title) { if (title.isBlank()) null else TaskTextParser.parse(title, now) }
    val detected = parsed?.takeIf { it.foundSomething && it.title.isNotBlank() }

    fun applyParsed() {
        val p = detected ?: return
        title = p.title
        p.dueAt?.let {
            day = TaskTime.startOfDay(it)
            minutes = if (p.hasTime) TaskTime.hourOf(it) * 60 + TaskTime.minuteOf(it) else minutes.takeIf { isAlarm }
        }
        p.priority?.let { priority = it }
        touchedWhen = true
    }

    fun save() {
        // Words in the title that were never applied still count, unless the user picked a
        // day or time by hand — then their choice wins and the title is left as typed.
        if (detected != null && !touchedWhen) applyParsed()
        val finalTitle = title.trim()
        if (finalTitle.isEmpty()) return
        val due = day?.let { d -> minutes?.let { TaskTime.at(d, it / 60, it % 60) } ?: d }
        onSave(
            TaskDraft(
                title = finalTitle,
                note = note.trim(),
                kind = if (isAlarm) TaskKind.ALARM else TaskKind.TASK,
                priority = priority,
                dueAt = due,
                hasTime = due != null && minutes != null,
                remind = if (isAlarm) true else remind,
                repeat = if (due == null) TaskRepeat.NONE else repeat,
            )
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = when {
                    initial != null && isAlarm -> "Edit alarm"
                    initial != null -> "Edit task"
                    isAlarm -> "New alarm"
                    else -> "New task"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(if (isAlarm) "e.g. Wake up, Namaz, Medicine" else "e.g. Pay gas bill tomorrow 5pm") },
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            if (detected != null && !touchedWhen) {
                val p = detected
                val text = buildString {
                    p.dueAt?.let {
                        append(TaskTime.dayLabel(it, now))
                        if (p.hasTime) append(" ").append(TaskTime.timeLabel(it))
                    }
                    p.priority?.let { if (isNotEmpty()) append(" · "); append(it.label) }
                }
                Surface(
                    onClick = { applyParsed() },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Found: $text", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text("Use", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            EditorLabel("When")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val tomorrow = TaskTime.addDays(today, 1)
                EzzyChip("Today", selected = day == today, onClick = { day = today; touchedWhen = true })
                EzzyChip("Tomorrow", selected = day == tomorrow, onClick = { day = tomorrow; touchedWhen = true })
                val custom = day?.takeIf { it != today && it != tomorrow }
                EzzyChip(
                    custom?.let { TaskTime.dayLabel(it, now) } ?: "Pick date",
                    selected = custom != null,
                    onClick = { pickDate = true },
                )
                if (!isAlarm) {
                    EzzyChip("No date", selected = day == null, onClick = { day = null; minutes = null; touchedWhen = true })
                }
            }
            if (day != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EzzyChip(
                        minutes?.let { TaskTime.timeLabel(TaskTime.at(today, it / 60, it % 60)) } ?: "Add time",
                        leadingIcon = Icons.Rounded.Alarm,
                        selected = minutes != null,
                        onClick = { pickTime = true },
                    )
                    if (!isAlarm && minutes != null) {
                        EzzyChip("Any time", onClick = { minutes = null; touchedWhen = true })
                    }
                }
            }
            if (!isAlarm && day != null && minutes != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Notifications, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Remind me", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "A reminder with Done and Snooze at that time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = remind, onCheckedChange = { remind = it })
                }
            }

            if (day != null) {
                EditorLabel("Repeat")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskRepeat.entries.forEach { option ->
                        EzzyChip(option.label, selected = repeat == option, onClick = { repeat = option })
                    }
                }
            }

            if (!isAlarm) {
                EditorLabel("Priority")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskPriority.entries.forEach { option ->
                        PriorityChip(option, selected = priority == option, onClick = { priority = option })
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Note (optional)") },
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = overdueColor(), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(6.dp))
                Surface(
                    onClick = { save() },
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (title.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (title.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = "Save",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }

    if (pickDate) {
        val zone = TimeZone.getDefault()
        val initialDay = day ?: today
        // The date picker works in UTC midnights; the task stores local ones.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDay + zone.getOffset(initialDay),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utc ->
                        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                        val local = Calendar.getInstance().apply {
                            clear()
                            set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                        }
                        day = local.timeInMillis
                        touchedWhen = true
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (pickTime) {
        val start = minutes ?: nextHour
        val timeState = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    minutes = timeState.hour * 60 + timeState.minute
                    touchedWhen = true
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timeState)
                }
            },
        )
    }
}

@Composable
private fun EditorLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun PriorityChip(priority: TaskPriority, selected: Boolean, onClick: () -> Unit) {
    val tint = priorityColor(priority)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) tint.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (selected) tint else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.width(6.dp))
            Text(
                priority.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) tint else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
