package com.ezzy.vault.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezzy.vault.appContainer
import com.ezzy.vault.data.repo.TaskUndo
import com.ezzy.vault.tasks.TaskPriority
import com.ezzy.vault.tasks.TaskTextParser
import com.ezzy.vault.tasks.TaskTime
import com.ezzy.vault.ui.components.EzzyChip
import com.ezzy.vault.ui.nav.Routes
import com.ezzy.vault.ui.todo.TaskActions
import com.ezzy.vault.ui.todo.TaskDraft
import com.ezzy.vault.ui.todo.TaskRow
import com.ezzy.vault.ui.todo.isOverdue
import com.ezzy.vault.ui.todo.rememberNow
import kotlinx.coroutines.delay

/** A ready-made "when" for a quick add, so a time can be set without typing it. */
private data class QuickWhen(val label: String, val at: Long, val hasTime: Boolean)

private fun quickWhens(now: Long): List<QuickWhen> {
    val today = TaskTime.startOfDay(now)
    val tomorrow = TaskTime.addDays(today, 1)
    val inHour = now + TaskTime.HOUR
    val tonight = TaskTime.at(today, 21, 0)
    return buildList {
        add(QuickWhen("In 1 hour", inHour - inHour % TaskTime.MINUTE, true))
        if (tonight > now + 15 * TaskTime.MINUTE) add(QuickWhen("Tonight 9 PM", tonight, true))
        add(QuickWhen("Today", today, false))
        add(QuickWhen("Tomorrow 9 AM", TaskTime.at(tomorrow, 9, 0), true))
        add(QuickWhen("Tomorrow", tomorrow, false))
    }
}

/**
 * The bar's To Do page: a quick-add line at the top and the open tasks under it, ticked off
 * right here. Available even while the vault is locked — a task list is no secret worth an
 * unlock every time.
 */
@Composable
internal fun OverlayTodoSheet(
    maxListHeight: Dp,
    onInteraction: () -> Unit,
    onOpenApp: (String?) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val actions = remember { TaskActions(container.scope, container.taskRepository) }
    val open by remember { container.taskRepository.observeOpen() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val now = rememberNow()

    var text by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf<QuickWhen?>(null) }
    var lastDone by remember { mutableStateOf<Pair<String, TaskUndo>?>(null) }
    var added by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lastDone) {
        if (lastDone != null) {
            delay(4_000)
            lastDone = null
        }
    }
    LaunchedEffect(added) {
        if (added != null) {
            delay(2_500)
            added = null
        }
    }

    val parsed = remember(text) { if (text.isBlank()) null else TaskTextParser.parse(text) }

    fun add() {
        val p = parsed ?: return
        val title = p.title.ifBlank { text.trim() }
        if (title.isBlank()) return
        val dueAt = preset?.at ?: p.dueAt
        val hasTime = preset?.hasTime ?: p.hasTime
        actions.save(
            null,
            TaskDraft(
                title = title,
                note = "",
                kind = com.ezzy.vault.tasks.TaskKind.TASK,
                priority = p.priority ?: TaskPriority.default,
                dueAt = dueAt,
                hasTime = hasTime && dueAt != null,
                remind = hasTime && dueAt != null,
                repeat = com.ezzy.vault.tasks.TaskRepeat.NONE,
            ),
        )
        added = title
        text = ""
        preset = null
        onInteraction()
    }

    // Late and today's first, then the rest by date; someday last.
    val tomorrow = TaskTime.addDays(TaskTime.startOfDay(now), 1)
    val sorted = remember(open, now) {
        open.sortedWith(
            compareBy<com.ezzy.vault.data.db.TaskEntity>(
                { if (it.dueAt == null) 2 else if (it.dueAt < tomorrow) 0 else 1 },
                { it.dueAt ?: Long.MAX_VALUE },
                { it.priority },
            )
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        PanelHeader(
            title = "To Do",
            showBack = false,
            onBack = {},
            onOpenApp = { onOpenApp(Routes.TODO) },
            onClose = onClose,
        )

        Column(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onInteraction()
                },
                placeholder = { Text("Quick add… \"Bill kal 5pm\"", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { add() }),
                trailingIcon = {
                    IconButton(onClick = { add() }, enabled = text.isNotBlank()) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Add task",
                            tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            val found = parsed?.takeIf { it.foundSomething && preset == null }
            when {
                added != null -> Text(
                    text = "Added: $added",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp),
                )

                found != null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = buildString {
                            found.dueAt?.let {
                                append(TaskTime.dayLabel(it, now))
                                if (found.hasTime) append(" ").append(TaskTime.timeLabel(it))
                            }
                            found.priority?.let { if (isNotEmpty()) append(" · "); append(it.label) }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (text.isNotBlank()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickWhens(now), key = { it.label }) { option ->
                        EzzyChip(
                            text = option.label,
                            selected = preset?.label == option.label,
                            onClick = {
                                preset = if (preset?.label == option.label) null else option
                                onInteraction()
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.size(6.dp))

        if (sorted.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.TaskAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                Spacer(Modifier.size(6.dp))
                Text("Nothing to do", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Type above to add one",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val late = sorted.count { isOverdue(it, now) }
            if (late > 0) {
                Text(
                    text = if (late == 1) "1 late" else "$late late",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = com.ezzy.vault.ui.todo.overdueColor(),
                    modifier = Modifier.padding(start = 14.dp, bottom = 4.dp),
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (maxListHeight - 150.dp).coerceAtLeast(160.dp)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 8.dp, end = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(sorted, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        now = now,
                        onToggle = {
                            onInteraction()
                            actions.complete(task.id) { undo ->
                                if (undo != null) lastDone = task.title to undo
                            }
                        },
                        onClick = { onOpenApp(Routes.TODO) },
                    )
                }
            }
        }

        lastDone?.let { (title, undo) ->
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        "Done: $title",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        actions.undo(undo)
                        lastDone = null
                        onInteraction()
                    }) {
                        Text("Undo", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.inversePrimary)
                    }
                }
            }
        }
    }
}
