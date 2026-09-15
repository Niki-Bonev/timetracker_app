package com.nikibonev.tempo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.nikibonev.tempo.data.model.Project
import com.nikibonev.tempo.data.model.SessionDetails
import com.nikibonev.tempo.data.model.SessionOutcome
import com.nikibonev.tempo.data.model.TimeDataSnapshot
import com.nikibonev.tempo.data.repository.AnalyticsEngine
import com.nikibonev.tempo.ui.components.EmptyState
import com.nikibonev.tempo.ui.components.ProjectIdentity
import com.nikibonev.tempo.ui.components.SectionHeader
import com.nikibonev.tempo.ui.components.SelectablePill
import com.nikibonev.tempo.util.ProjectColorPalette
import com.nikibonev.tempo.util.ProjectIconPalette
import com.nikibonev.tempo.util.formatDuration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

@Composable
fun ProjectsScreen(
    snapshot: TimeDataSnapshot,
    now: Long,
    use24Hour: Boolean,
    weekStartsMonday: Boolean,
    onCreate: (String, Long, String, Int, String?, Int, Long?, Long?) -> Unit,
    onUpdate: (Project) -> Unit,
    onArchive: (Project, Boolean) -> Unit,
    onDelete: (Project, Boolean) -> Unit,
    onStart: (Project) -> Unit,
    onAddManual: (Project, Long, Long, String, String) -> Unit,
    onUpdateSession: (String, String, String, Int?, SessionOutcome, Int?) -> Unit,
    onUpdateInterval: (String, Long, Long?) -> Unit,
    onDeleteSession: (String) -> Unit,
) {
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<Project?>(null) }
    var creatingParent by remember { mutableStateOf(false) }
    var creatingChildFor by remember { mutableStateOf<Project?>(null) }
    var deleting by remember { mutableStateOf<Project?>(null) }
    var manualFor by remember { mutableStateOf<Project?>(null) }
    var selectedSession by remember { mutableStateOf<SessionDetails?>(null) }

    val visibleProjects = snapshot.projects.filter { !it.deleted }
    val usedColors = visibleProjects.map { it.colorArgb }.toSet()
    val selectedProject = selectedProjectId?.let { id -> visibleProjects.firstOrNull { it.id == id } }

    if (selectedProject == null) {
        ProjectLibrary(
            snapshot = snapshot,
            now = now,
            onOpen = { selectedProjectId = it.id },
            onCreate = { creatingParent = true },
            onStart = onStart,
            onEdit = { editor = it },
        )
    } else {
        ProjectDetail(
            project = selectedProject,
            snapshot = snapshot,
            now = now,
            use24Hour = use24Hour,
            weekStartsMonday = weekStartsMonday,
            onBack = { selectedProjectId = null },
            onStart = { onStart(selectedProject) },
            onEdit = { editor = selectedProject },
            onArchive = { onArchive(selectedProject, !selectedProject.archived) },
            onDelete = { deleting = selectedProject },
            onAddChild = { creatingChildFor = selectedProject },
            onAddManual = { manualFor = selectedProject },
            onEditSession = { selectedSession = it },
            onStartChild = onStart,
            onEditChild = { editor = it },
        )
    }

    if (creatingParent) {
        ProjectEditorDialog(
            project = null,
            parentProjectId = null,
            usedColors = usedColors,
            onDismiss = { creatingParent = false },
        ) { name, color, icon, weekly, parentId, goalMinutes, goalStart, goalEnd ->
            creatingParent = false
            onCreate(name, color, icon, weekly, parentId, goalMinutes, goalStart, goalEnd)
        }
    }

    creatingChildFor?.let { parent ->
        ProjectEditorDialog(
            project = null,
            parentProjectId = parent.id,
            usedColors = usedColors,
            onDismiss = { creatingChildFor = null },
        ) { name, color, icon, weekly, parentId, goalMinutes, goalStart, goalEnd ->
            creatingChildFor = null
            onCreate(name, color, icon, weekly, parentId, goalMinutes, goalStart, goalEnd)
        }
    }

    editor?.let { project ->
        ProjectEditorDialog(
            project = project,
            parentProjectId = project.parentProjectId,
            usedColors = usedColors - project.colorArgb,
            onDismiss = { editor = null },
        ) { name, color, icon, weekly, parentId, goalMinutes, goalStart, goalEnd ->
            editor = null
            onUpdate(project.copy(
                name = name,
                colorArgb = color,
                icon = icon,
                parentProjectId = parentId,
                weeklyGoalMinutes = weekly,
                goalMinutes = goalMinutes,
                goalStartAt = goalStart,
                goalEndAt = goalEnd,
            ))
        }
    }

    deleting?.let { project ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${project.name}?") },
            text = { Text("Keep its tracked history, or delete its sessions too. Deleting a main project also removes its subprojects from the library.") },
            confirmButton = { TextButton(onClick = { deleting = null; selectedProjectId = null; onDelete(project, false) }) { Text("Keep history") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { deleting = null }) { Text("Cancel") }
                    TextButton(onClick = { deleting = null; selectedProjectId = null; onDelete(project, true) }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }

    manualFor?.let { project ->
        val choices = listOf(project) + visibleProjects.filter { it.parentProjectId == project.id && !it.archived }
        ManualDialog(
            projects = choices,
            now = now,
            initialProject = project,
            onDismiss = { manualFor = null },
            onSave = { target, start, end, note, intention ->
                manualFor = null
                onAddManual(target, start, end, note, intention)
            },
        )
    }

    selectedSession?.let { details ->
        SessionDialog(
            details = details,
            now = now,
            onDismiss = { selectedSession = null },
            onSave = { intention, note, planned, outcome, quality ->
                selectedSession = null
                onUpdateSession(details.session.id, intention, note, planned, outcome, quality)
            },
            onDelete = { selectedSession = null; onDeleteSession(details.session.id) },
            onAdjustInterval = onUpdateInterval,
        )
    }
}

@Composable
private fun ProjectLibrary(
    snapshot: TimeDataSnapshot,
    now: Long,
    onOpen: (Project) -> Unit,
    onCreate: () -> Unit,
    onStart: (Project) -> Unit,
    onEdit: (Project) -> Unit,
) {
    val parents = snapshot.projects.filter { !it.deleted && it.parentProjectId == null && !it.archived }
    val archived = snapshot.projects.filter { !it.deleted && it.parentProjectId == null && it.archived }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Projects", style = MaterialTheme.typography.headlineLarge)
                    Text("Your library: tasks, history, goals and stats live inside each project.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onCreate) { Icon(Icons.Outlined.Add, null); Text(" New") }
            }
        }
        if (parents.isEmpty()) {
            item { EmptyState("No projects yet", "Create a project here, or start from Focus and Tempo can use General.", "Create project", onAction = onCreate) }
        } else {
            items(parents, key = { it.id }) { project ->
                val children = snapshot.projects.filter { !it.deleted && it.parentProjectId == project.id }
                val ids = (children.map { it.id } + project.id).toSet()
                val workMillis = snapshot.sessions.filter { !it.session.deleted && it.session.projectId in ids }.sumOf { it.workMillis(now) }
                ProjectLibraryCard(
                    project = project,
                    workMillis = workMillis,
                    childCount = children.size,
                    isRunning = snapshot.activeTimer?.let { it.project.id in ids } == true,
                    canStart = snapshot.activeTimer == null && !project.archived,
                    onOpen = { onOpen(project) },
                    onStart = { onStart(project) },
                    onEdit = { onEdit(project) },
                )
            }
        }
        if (archived.isNotEmpty()) {
            item { SectionHeader("Archived", "Hidden from Focus, history preserved") }
            items(archived, key = { it.id }) { project ->
                val children = snapshot.projects.filter { !it.deleted && it.parentProjectId == project.id }
                val ids = (children.map { it.id } + project.id).toSet()
                val workMillis = snapshot.sessions.filter { !it.session.deleted && it.session.projectId in ids }.sumOf { it.workMillis(now) }
                ProjectLibraryCard(project, workMillis, children.size, false, false, { onOpen(project) }, {}, { onEdit(project) })
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun ProjectLibraryCard(
    project: Project,
    workMillis: Long,
    childCount: Int,
    isRunning: Boolean,
    canStart: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit,
) {
    val base = Color(project.colorArgb)
    val content = if (base.luminance() > 0.48f) Color.Black else Color.White
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = base, contentColor = content),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(project.icon, style = MaterialTheme.typography.headlineMedium)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(project.name, style = MaterialTheme.typography.headlineSmall, color = content)
                    Text(
                        when {
                            isRunning -> "Timer active"
                            childCount == 1 -> "1 subproject"
                            childCount > 1 -> "$childCount subprojects"
                            else -> "No subprojects yet"
                        },
                        color = content.copy(alpha = 0.78f),
                    )
                }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit", tint = content) }
            }
            Text(formatDuration(workMillis), style = MaterialTheme.typography.headlineMedium, color = content)
            Text("tracked all time", color = content.copy(alpha = 0.78f))
            projectGoalLabel(project)?.let { Text(it, color = content.copy(alpha = 0.9f)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Open") }
                Button(onClick = onStart, enabled = canStart, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.PlayArrow, null)
                    Text("Start")
                }
            }
        }
    }
}

@Composable
private fun ProjectDetail(
    project: Project,
    snapshot: TimeDataSnapshot,
    now: Long,
    use24Hour: Boolean,
    weekStartsMonday: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onAddChild: () -> Unit,
    onAddManual: () -> Unit,
    onEditSession: (SessionDetails) -> Unit,
    onStartChild: (Project) -> Unit,
    onEditChild: (Project) -> Unit,
) {
    val children = snapshot.projects.filter { !it.deleted && it.parentProjectId == project.id }
    val ids = (children.map { it.id } + project.id).toSet()
    val sessions = snapshot.sessions.filter { !it.session.deleted && it.session.projectId in ids }.sortedByDescending { it.session.startedAt }
    val total = sessions.sumOf { it.workMillis(now) }
    val projectColor = Color(project.colorArgb)
    val projectText = if (projectColor.luminance() > 0.48f) Color.Black else Color.White

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null); Text(" Projects") }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = projectColor, contentColor = projectText)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${project.icon}  ${project.name}", style = MaterialTheme.typography.headlineMedium, color = projectText)
                    Text(formatDuration(total), style = MaterialTheme.typography.displaySmall, color = projectText)
                    Text("total across this project and its subprojects", color = projectText.copy(alpha = 0.78f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStart, enabled = snapshot.activeTimer == null && !project.archived, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.PlayArrow, null); Text("Start")
                        }
                        OutlinedButton(onClick = onAddManual, modifier = Modifier.weight(1f)) { Text("Add time") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, null, tint = projectText); Text("Edit", color = projectText) }
                        TextButton(onClick = onArchive) {
                            Icon(if (project.archived) Icons.Outlined.Restore else Icons.Outlined.Archive, null, tint = projectText)
                            Text(if (project.archived) "Restore" else "Archive", color = projectText)
                        }
                        TextButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, null, tint = projectText); Text("Delete", color = projectText) }
                    }
                }
            }
        }

        item { ProjectGoalCard(project, snapshot, ids, now, weekStartsMonday) }

        item {
            SectionHeader("Subprojects / tasks", "Track separate areas without losing the project-wide view", trailing = {
                TextButton(onClick = onAddChild) { Icon(Icons.Outlined.Add, null); Text("Add") }
            })
        }
        if (children.isEmpty()) {
            item { EmptyState("No subprojects yet", "Example: Coding → Java, Android, Algorithms. You can still track directly to ${project.name}.") }
        } else {
            items(children, key = { it.id }) { child ->
                val childWork = snapshot.sessions.filter { !it.session.deleted && it.session.projectId == child.id }.sumOf { it.workMillis(now) }
                ChildProjectCard(child, childWork, snapshot.activeTimer?.project?.id == child.id, snapshot.activeTimer == null && !child.archived, { onStartChild(child) }, { onEditChild(child) })
            }
        }

        item { SectionHeader("History", "Sessions for ${project.name} and its subprojects") }
        if (sessions.isEmpty()) {
            item { EmptyState("No sessions yet", "Start from here or from Focus. Manual time can be added with the button above.") }
        } else {
            items(sessions, key = { it.session.id }) { details ->
                SessionHistoryCard(details, now, use24Hour) { onEditSession(details) }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun ChildProjectCard(
    project: Project,
    workMillis: Long,
    isRunning: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
) {
    val color = Color(project.colorArgb)
    val content = if (color.luminance() > 0.48f) Color.Black else Color.White
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.88f), contentColor = content)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${project.icon} ${project.name}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), color = content)
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit", tint = content) }
            }
            Text(formatDuration(workMillis), style = MaterialTheme.typography.titleMedium, color = content)
            if (isRunning) Text("Timer active", color = content)
            Button(onClick = onStart, enabled = canStart, modifier = Modifier.fillMaxWidth()) { Text("Start ${project.name}") }
        }
    }
}

@Composable
private fun ProjectGoalCard(project: Project, snapshot: TimeDataSnapshot, ids: Set<String>, now: Long, weekStartsMonday: Boolean) {
    val zone = remember { ZoneId.systemDefault() }
    val targetMinutes: Int
    val from: Long
    val to: Long
    val label: String
    when {
        project.hasDateRangeGoal -> {
            targetMinutes = project.goalMinutes
            from = project.goalStartAt ?: now
            to = project.goalEndAt ?: now
            label = "Fixed-period target"
        }
        project.weeklyGoalMinutes > 0 -> {
            targetMinutes = project.weeklyGoalMinutes
            from = AnalyticsEngine.weekStartMillis(now, weekStartsMonday, zone)
            to = now
            label = "Weekly target"
        }
        else -> {
            Card { Text("No time target set. Edit the project to add a weekly or fixed-period goal.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return
        }
    }
    val relevantSessions = snapshot.sessions.filter { it.session.projectId in ids }
    val analytics = remember(relevantSessions, from, to, now) {
        AnalyticsEngine.calculate(snapshot.projects, relevantSessions, from, if (project.hasDateRangeGoal) minOf(now, to) else now, zone, 7)
    }
    val targetMillis = targetMinutes * 60_000L
    val fraction = (analytics.totalWorkMillis.toFloat() / targetMillis.coerceAtLeast(1L)).coerceIn(0f, 1f)
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text("${formatDuration(analytics.totalWorkMillis)} / ${formatDuration(targetMillis)}")
            if (project.hasDateRangeGoal) {
                val days = ceil(((project.goalEndAt ?: now) - now).coerceAtLeast(0L) / 86_400_000.0).toInt()
                Text(if (days > 0) "$days days remaining" else "Goal period ended", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ProjectEditorDialog(
    project: Project?,
    parentProjectId: String?,
    usedColors: Set<Long>,
    onDismiss: () -> Unit,
    onSave: (String, Long, String, Int, String?, Int, Long?, Long?) -> Unit,
) {
    enum class GoalMode { NONE, WEEKLY, PERIOD }

    val initialColor = project?.colorArgb ?: ProjectColorPalette.firstOrNull { it !in usedColors } ?: ProjectColorPalette.first()
    var name by remember(project?.id, parentProjectId) { mutableStateOf(project?.name.orEmpty()) }
    var color by remember(project?.id) { mutableStateOf(initialColor) }
    var icon by remember(project?.id) { mutableStateOf(project?.icon ?: ProjectIconPalette.first()) }
    var goalMode by remember(project?.id) {
        mutableStateOf(when {
            project?.hasDateRangeGoal == true -> GoalMode.PERIOD
            (project?.weeklyGoalMinutes ?: 0) > 0 -> GoalMode.WEEKLY
            else -> GoalMode.NONE
        })
    }
    val existingGoalMinutes = when (goalMode) {
        GoalMode.WEEKLY -> project?.weeklyGoalMinutes ?: 0
        GoalMode.PERIOD -> project?.goalMinutes ?: 0
        GoalMode.NONE -> 0
    }
    var goalHours by remember(project?.id) { mutableStateOf((existingGoalMinutes / 60).takeIf { it > 0 }?.toString().orEmpty()) }
    var goalMinutes by remember(project?.id) { mutableStateOf((existingGoalMinutes % 60).takeIf { it > 0 }?.toString().orEmpty()) }
    val existingDays = if (project?.hasDateRangeGoal == true) {
        ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(project.goalStartAt ?: System.currentTimeMillis()).atZone(ZoneId.systemDefault()).toLocalDate(),
            Instant.ofEpochMilli(project.goalEndAt ?: System.currentTimeMillis()).atZone(ZoneId.systemDefault()).toLocalDate(),
        ).coerceAtLeast(1L).toInt()
    } else 30
    var periodDays by remember(project?.id) { mutableStateOf(existingDays.toString()) }
    val colorAvailable = color !in usedColors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (project == null) if (parentProjectId == null) "New project" else "New subproject" else "Edit project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(50) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Color · each active project gets its own identity", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProjectColorPalette.take(8).forEach { value ->
                        val available = value !in usedColors || value == project?.colorArgb
                        Box(
                            Modifier
                                .size(if (color == value) 30.dp else 24.dp)
                                .alpha(if (available) 1f else 0.22f)
                                .clip(CircleShape)
                                .background(Color(value))
                                .clickable(enabled = available) { color = value },
                        )
                    }
                }
                Text("Symbol", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProjectIconPalette.take(6).forEach { value -> SelectablePill(value, icon == value) { icon = value } }
                }
                Text("Time target", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectablePill("None", goalMode == GoalMode.NONE) { goalMode = GoalMode.NONE }
                    SelectablePill("Weekly", goalMode == GoalMode.WEEKLY) { goalMode = GoalMode.WEEKLY }
                    SelectablePill("Fixed period", goalMode == GoalMode.PERIOD) { goalMode = GoalMode.PERIOD }
                }
                if (goalMode != GoalMode.NONE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(goalHours, { goalHours = it.filter(Char::isDigit).take(4) }, label = { Text("Hours") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(goalMinutes, { goalMinutes = it.filter(Char::isDigit).take(2) }, label = { Text("Minutes") }, modifier = Modifier.weight(1f))
                    }
                    if (goalMode == GoalMode.PERIOD) {
                        OutlinedTextField(periodDays, { periodDays = it.filter(Char::isDigit).take(4) }, label = { Text("Period length (days)") }, supportingText = { Text("Starts when you save the goal. Example: 90 days.") }, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (!colorAvailable) Text("Choose a different color; active projects should be visually distinct.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && colorAvailable,
                onClick = {
                    val totalGoal = ((goalHours.toIntOrNull() ?: 0) * 60 + (goalMinutes.toIntOrNull() ?: 0)).coerceAtLeast(0)
                    val weekly = if (goalMode == GoalMode.WEEKLY) totalGoal else 0
                    val start = if (goalMode == GoalMode.PERIOD && totalGoal > 0) System.currentTimeMillis() else null
                    val end = if (start != null) start + (periodDays.toLongOrNull() ?: 30L).coerceAtLeast(1L) * 86_400_000L else null
                    onSave(name.trim(), color, icon, weekly, parentProjectId, if (goalMode == GoalMode.PERIOD) totalGoal else 0, start, end)
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun projectGoalLabel(project: Project): String? = when {
    project.hasDateRangeGoal -> "Target: ${project.goalMinutes / 60}h ${project.goalMinutes % 60}m over a fixed period"
    project.weeklyGoalMinutes > 0 -> "Weekly target: ${project.weeklyGoalMinutes / 60}h ${project.weeklyGoalMinutes % 60}m"
    else -> null
}
