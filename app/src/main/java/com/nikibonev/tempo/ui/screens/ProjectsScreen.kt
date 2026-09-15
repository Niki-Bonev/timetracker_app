package com.nikibonev.tempo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nikibonev.tempo.data.model.Project
import com.nikibonev.tempo.data.model.TimeDataSnapshot
import com.nikibonev.tempo.ui.components.EmptyState
import com.nikibonev.tempo.ui.components.ProjectIdentity
import com.nikibonev.tempo.ui.components.SectionHeader
import com.nikibonev.tempo.ui.components.SelectablePill
import com.nikibonev.tempo.util.ProjectColorPalette
import com.nikibonev.tempo.util.ProjectIconPalette
import com.nikibonev.tempo.util.formatDuration

@Composable
fun ProjectsScreen(
    snapshot: TimeDataSnapshot,
    onCreate: (String, Long, String, Int) -> Unit,
    onUpdate: (Project) -> Unit,
    onArchive: (Project, Boolean) -> Unit,
    onDelete: (Project, Boolean) -> Unit,
) {
    var editor by remember { mutableStateOf<Project?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Project?>(null) }
    val activeId = snapshot.activeTimer?.project?.id
    val active = snapshot.projects.filter { !it.deleted && !it.archived }
    val archived = snapshot.projects.filter { !it.deleted && it.archived }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Projects", style = MaterialTheme.typography.headlineLarge)
            Text("Color-code the kinds of work you care about and give them realistic weekly budgets.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionHeader("Active", trailing = {
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Outlined.Add, null)
                    Text("New")
                }
            })
        }
        if (active.isEmpty()) {
            item { EmptyState("No projects yet", "Create one, or simply start from Focus and Tempo will create General for you.", "Create project") { creating = true } }
        } else {
            items(active, key = { it.id }) { project ->
                ProjectCard(
                    project = project,
                    workMillis = snapshot.sessions.filter { !it.session.deleted && it.session.projectId == project.id }.sumOf { it.workMillis() },
                    isRunning = activeId == project.id,
                    onEdit = { editor = project },
                    onArchive = { onArchive(project, true) },
                    onDelete = { deleting = project },
                )
            }
        }
        if (archived.isNotEmpty()) {
            item { SectionHeader("Archived", "Hidden from quick start, history preserved") }
            items(archived, key = { it.id }) { project ->
                ProjectCard(
                    project = project,
                    workMillis = snapshot.sessions.filter { !it.session.deleted && it.session.projectId == project.id }.sumOf { it.workMillis() },
                    isRunning = false,
                    onEdit = { editor = project },
                    onArchive = { onArchive(project, false) },
                    onDelete = { deleting = project },
                )
            }
        }
    }

    if (creating) {
        ProjectEditorDialog(null, onDismiss = { creating = false }) { name, color, icon, goal ->
            creating = false
            onCreate(name, color, icon, goal)
        }
    }
    editor?.let { project ->
        ProjectEditorDialog(project, onDismiss = { editor = null }) { name, color, icon, goal ->
            editor = null
            onUpdate(project.copy(name = name, colorArgb = color, icon = icon, weeklyGoalMinutes = goal))
        }
    }
    deleting?.let { project ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${project.name}?") },
            text = { Text("You can delete only the project, leaving its past sessions visible, or delete the project and its history together.") },
            confirmButton = { TextButton(onClick = { deleting = null; onDelete(project, false) }) { Text("Keep history") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { deleting = null }) { Text("Cancel") }
                    TextButton(onClick = { deleting = null; onDelete(project, true) }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    workMillis: Long,
    isRunning: Boolean,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProjectIdentity(project, Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit") }
                IconButton(onClick = onArchive) { Icon(if (project.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, "Archive") }
                IconButton(onClick = onDelete, enabled = !isRunning) { Icon(Icons.Outlined.Delete, "Delete") }
            }
            Text("${formatDuration(workMillis, true)} tracked all time", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (project.weeklyGoalMinutes > 0) Text("Weekly target: ${project.weeklyGoalMinutes / 60}h ${project.weeklyGoalMinutes % 60}m")
            if (isRunning) Text("Timer active", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun ProjectEditorDialog(
    project: Project?,
    onDismiss: () -> Unit,
    onSave: (String, Long, String, Int) -> Unit,
) {
    var name by remember(project?.id) { mutableStateOf(project?.name.orEmpty()) }
    var color by remember(project?.id) { mutableStateOf(project?.colorArgb ?: ProjectColorPalette.first()) }
    var icon by remember(project?.id) { mutableStateOf(project?.icon ?: ProjectIconPalette.first()) }
    var goal by remember(project?.id) { mutableStateOf(project?.weeklyGoalMinutes?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (project == null) "New project" else "Edit project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(50) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Color", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProjectColorPalette.take(8).forEach { value ->
                        Box(
                            Modifier.size(if (color == value) 30.dp else 24.dp).clip(CircleShape).background(Color(value)).clickable { color = value },
                        )
                    }
                }
                Text("Symbol", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProjectIconPalette.take(6).forEach { value -> SelectablePill(value, icon == value) { icon = value } }
                }
                OutlinedTextField(
                    goal,
                    { goal = it.filter(Char::isDigit).take(5) },
                    label = { Text("Weekly target (minutes)") },
                    supportingText = { Text("Optional. Example: 600 = 10 hours/week") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(name.ifBlank { "Untitled project" }, color, icon, goal.toIntOrNull() ?: 0) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
