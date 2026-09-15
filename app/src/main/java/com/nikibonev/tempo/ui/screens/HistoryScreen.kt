package com.nikibonev.tempo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nikibonev.tempo.data.model.Project
import com.nikibonev.tempo.data.model.SessionDetails
import com.nikibonev.tempo.data.model.SessionOutcome
import com.nikibonev.tempo.data.model.TimeDataSnapshot
import com.nikibonev.tempo.data.model.TimeInterval
import com.nikibonev.tempo.ui.components.DurationPair
import com.nikibonev.tempo.ui.components.EmptyState
import com.nikibonev.tempo.ui.components.ProjectIdentity
import com.nikibonev.tempo.ui.components.SelectablePill
import com.nikibonev.tempo.ui.components.SessionTimeline
import com.nikibonev.tempo.util.formatClock
import com.nikibonev.tempo.util.formatDate

@Composable
fun HistoryScreen(
    snapshot: TimeDataSnapshot,
    now: Long,
    use24Hour: Boolean,
    onAddManual: (Project, Long, Long, String, String) -> Unit,
    onUpdateSession: (String, String, String, Int?, SessionOutcome, Int?) -> Unit,
    onUpdateInterval: (String, Long, Long?) -> Unit,
    onDeleteSession: (String) -> Unit,
) {
    var selected by remember { mutableStateOf<SessionDetails?>(null) }
    var manual by remember { mutableStateOf(false) }
    val sessions = snapshot.sessions.filterNot { it.session.deleted }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("History", style = MaterialTheme.typography.headlineLarge)
                    Text("Session history now lives inside Projects; this view is retained internally for reuse.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { manual = true }, enabled = snapshot.projects.any { !it.deleted }) {
                    Icon(Icons.Outlined.Add, null)
                    Text("Manual")
                }
            }
        }
        if (sessions.isEmpty()) {
            item { EmptyState("No sessions yet", "Your completed focus sessions will appear with their break timeline.") }
        } else {
            items(sessions, key = { it.session.id }) { details ->
                SessionHistoryCard(details, now, use24Hour) { selected = details }
            }
        }
    }

    selected?.let { details ->
        SessionDialog(
            details = details,
            now = now,
            onDismiss = { selected = null },
            onSave = { intention, note, planned, outcome, quality ->
                selected = null
                onUpdateSession(details.session.id, intention, note, planned, outcome, quality)
            },
            onDelete = { selected = null; onDeleteSession(details.session.id) },
            onAdjustInterval = onUpdateInterval,
        )
    }
    if (manual) {
        ManualDialog(
            projects = snapshot.projects.filter { !it.deleted },
            now = now,
            initialProject = null,
            onDismiss = { manual = false },
            onSave = { project, start, end, note, intention ->
                manual = false
                onAddManual(project, start, end, note, intention)
            },
        )
    }
}

@Composable
fun SessionHistoryCard(details: SessionDetails, now: Long, use24Hour: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                details.project?.let { ProjectIdentity(it, Modifier.weight(1f)) }
                Text(formatDate(details.session.startedAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (details.session.intention.isNotBlank()) Text(details.session.intention, style = MaterialTheme.typography.titleMedium)
            Text(
                "${formatClock(details.session.startedAt, use24Hour)} – ${details.session.endedAt?.let { formatClock(it, use24Hour) } ?: "now"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SessionTimeline(details.intervals, Color(details.project?.colorArgb ?: 0xFF8B7CFF), now)
            DurationPair(details.workMillis(now), details.breakMillis(now))
        }
    }
}

@Composable
fun SessionDialog(
    details: SessionDetails,
    now: Long,
    onDismiss: () -> Unit,
    onSave: (String, String, Int?, SessionOutcome, Int?) -> Unit,
    onDelete: () -> Unit,
    onAdjustInterval: (String, Long, Long?) -> Unit,
) {
    var intention by remember(details.session.id) { mutableStateOf(details.session.intention) }
    var note by remember(details.session.id) { mutableStateOf(details.session.note) }
    var plannedHours by remember(details.session.id) { mutableStateOf(((details.session.plannedMinutes ?: 0) / 60).takeIf { it > 0 }?.toString().orEmpty()) }
    var plannedMinutes by remember(details.session.id) { mutableStateOf(((details.session.plannedMinutes ?: 0) % 60).takeIf { it > 0 }?.toString().orEmpty()) }
    var outcome by remember(details.session.id) { mutableStateOf(details.session.outcome) }
    var quality by remember(details.session.id) { mutableStateOf(details.session.quality) }
    var adjust by remember { mutableStateOf<TimeInterval?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(details.project?.name ?: "Session") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { DurationPair(details.workMillis(now), details.breakMillis(now)) }
                item { OutlinedTextField(intention, { intention = it.take(120) }, label = { Text("Task / intention") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(note, { note = it.take(1000) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth()) }
                item {
                    Text("Planned duration", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(plannedHours, { plannedHours = it.filter(Char::isDigit).take(3) }, label = { Text("Hours") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(plannedMinutes, { plannedMinutes = it.filter(Char::isDigit).take(2) }, label = { Text("Minutes") }, modifier = Modifier.weight(1f))
                    }
                }
                item {
                    Text("Outcome", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(SessionOutcome.DONE, SessionOutcome.PROGRESS, SessionOutcome.STUCK).forEach { value ->
                            SelectablePill(value.name.lowercase().replaceFirstChar(Char::uppercase), outcome == value) { outcome = value }
                        }
                    }
                }
                item {
                    Text("Focus quality", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        (1..5).forEach { score -> SelectablePill(score.toString(), quality == score) { quality = if (quality == score) null else score } }
                    }
                }
                item {
                    Text("Intervals", style = MaterialTheme.typography.labelLarge)
                    details.intervals.filterNot { it.deleted }.forEach { interval ->
                        TextButton(onClick = { adjust = interval }) {
                            Text("${interval.type.name.lowercase().replaceFirstChar(Char::uppercase)} · ${com.nikibonev.tempo.util.formatDuration(interval.durationMillis(now))}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val planned = (plannedHours.toIntOrNull() ?: 0) * 60 + (plannedMinutes.toIntOrNull() ?: 0)
                onSave(intention, note, planned.takeIf { it > 0 }, outcome, quality)
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )

    adjust?.let { interval ->
        var minutes by remember(interval.id) { mutableStateOf((interval.durationMillis(now) / 60_000L).coerceAtLeast(1L).toString()) }
        AlertDialog(
            onDismissRequest = { adjust = null },
            title = { Text("Adjust ${interval.type.name.lowercase()} interval") },
            text = { OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, label = { Text("Duration in minutes") }) },
            confirmButton = {
                Button(onClick = {
                    val duration = (minutes.toLongOrNull() ?: 1L).coerceAtLeast(1L) * 60_000L
                    adjust = null
                    onAdjustInterval(interval.id, interval.startedAt, interval.startedAt + duration)
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { adjust = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ManualDialog(
    projects: List<Project>,
    now: Long,
    initialProject: Project?,
    onDismiss: () -> Unit,
    onSave: (Project, Long, Long, String, String) -> Unit,
) {
    var project by remember(projects, initialProject?.id) { mutableStateOf(initialProject ?: projects.firstOrNull()) }
    var menuOpen by remember { mutableStateOf(false) }
    var hours by remember { mutableStateOf("1") }
    var minutes by remember { mutableStateOf("0") }
    var intention by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add time manually") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Project", style = MaterialTheme.typography.labelLarge)
                Box {
                    OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(project?.let { "${it.icon}  ${it.name}" } ?: "Choose project")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        projects.forEach { p ->
                            val parent = p.parentProjectId?.let { id -> projects.firstOrNull { it.id == id } }
                            DropdownMenuItem(
                                text = { Text(if (parent == null) p.name else "${parent.name} › ${p.name}") },
                                onClick = { project = p; menuOpen = false },
                            )
                        }
                    }
                }
                Text("Duration", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(hours, { hours = it.filter(Char::isDigit).take(3) }, label = { Text("Hours") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(2) }, label = { Text("Minutes") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(intention, { intention = it.take(120) }, label = { Text("What did you work on?") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it.take(1000) }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                Text("This entry ends now. You can fine-tune its intervals afterward.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = {
                val p = project ?: return@Button
                val durationMinutes = ((hours.toLongOrNull() ?: 0L) * 60L + (minutes.toLongOrNull() ?: 0L)).coerceAtLeast(1L)
                onSave(p, now - durationMinutes * 60_000L, now, note, intention)
            }, enabled = project != null) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
