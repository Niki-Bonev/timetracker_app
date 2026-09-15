package com.nikibonev.tempo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.nikibonev.tempo.data.model.AppSettings
import com.nikibonev.tempo.data.model.Project
import com.nikibonev.tempo.data.model.TimeDataSnapshot
import com.nikibonev.tempo.data.repository.AnalyticsEngine
import com.nikibonev.tempo.ui.components.DurationPair
import com.nikibonev.tempo.ui.components.ProjectIdentity
import com.nikibonev.tempo.ui.components.SessionTimeline
import com.nikibonev.tempo.util.formatDuration
import com.nikibonev.tempo.util.formatDurationPrecise
import com.nikibonev.tempo.util.progressFraction
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun DashboardScreen(
    snapshot: TimeDataSnapshot,
    settings: AppSettings,
    now: Long,
    onStartFocus: (Project?, String, Int?) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onEditActive: () -> Unit,
    onCreateProject: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    val weekStart = AnalyticsEngine.weekStartMillis(now, settings.weekStart.name == "MONDAY", zone)
    val today = remember(snapshot.sessions, snapshot.projects, todayStart, now) {
        AnalyticsEngine.calculate(snapshot.projects, snapshot.sessions, todayStart, now, zone, 1)
    }
    val week = remember(snapshot.sessions, snapshot.projects, weekStart, now) {
        AnalyticsEngine.calculate(snapshot.projects, snapshot.sessions, weekStart, now, zone, 7)
    }
    val activeProjects = snapshot.projects.filter { !it.deleted && !it.archived }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Focus", style = MaterialTheme.typography.headlineLarge)
            Text(
                if (snapshot.activeTimer == null) "Start the thing. Tempo can organize the details later."
                else "The clock is running. Stay with the work in front of you.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        snapshot.activeTimer?.let { active ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProjectIdentity(active.project)
                        Text(
                            if (active.isPaused) "Break · ${formatDurationPrecise(active.intervals.lastOrNull()?.durationMillis(now) ?: 0L)}"
                            else formatDurationPrecise(active.workMillis(now)),
                            style = MaterialTheme.typography.displayLarge,
                        )
                        if (active.session.intention.isNotBlank()) {
                            Text(active.session.intention, style = MaterialTheme.typography.titleLarge)
                        }
                        active.session.plannedMinutes?.let { planned ->
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { progressFraction(active.workMillis(now), planned) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "${formatDuration(active.workMillis(now))} of ${planned / 60}h ${planned % 60}m planned",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SessionTimeline(active.intervals, Color(active.project.colorArgb), now)
                        DurationPair(active.workMillis(now), active.breakMillis(now))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = if (active.isPaused) onResume else onPause, modifier = Modifier.weight(1f)) {
                                Text(if (active.isPaused) "Resume" else "Pause")
                            }
                            OutlinedButton(onClick = onFinish, modifier = Modifier.weight(1f)) { Text("Finish") }
                        }
                        TextButton(onClick = onEditActive) { Text("Plan / edit this session") }
                    }
                }
            }
        } ?: run {
            item { FocusLauncher(activeProjects, onStartFocus, onCreateProject) }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("Today", formatDuration(today.totalWorkMillis), Modifier.weight(1f))
                SummaryCard("This week", formatDuration(week.totalWorkMillis), Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun FocusLauncher(
    projects: List<Project>,
    onStartFocus: (Project?, String, Int?) -> Unit,
    onCreateProject: () -> Unit,
) {
    var intention by remember { mutableStateOf("") }
    var selected by remember(projects) { mutableStateOf(projects.firstOrNull { it.parentProjectId == null } ?: projects.firstOrNull()) }
    var menuOpen by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Begin", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = intention,
                onValueChange = { intention = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What are you about to do?") },
                placeholder = { Text("Implement login flow, study Java, leg day…") },
                singleLine = true,
            )
            Box {
                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selected?.let { "${it.icon}  ${it.name}" } ?: "General / no project")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("General / no project") },
                        onClick = { selected = null; menuOpen = false },
                    )
                    projects.forEach { project ->
                        val parent = project.parentProjectId?.let { id -> projects.firstOrNull { it.id == id } }
                        DropdownMenuItem(
                            text = { Text(if (parent == null) "${project.icon} ${project.name}" else "${parent.name}  ›  ${project.name}") },
                            onClick = { selected = project; menuOpen = false },
                        )
                    }
                }
            }
            Button(
                onClick = { onStartFocus(selected, intention, null) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start focus") }
            TextButton(onClick = onCreateProject) { Text("Create a new project") }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
