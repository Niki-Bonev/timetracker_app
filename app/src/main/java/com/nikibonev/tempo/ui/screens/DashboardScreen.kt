package com.nikibonev.tempo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nikibonev.tempo.data.model.AppSettings
import com.nikibonev.tempo.data.model.Project
import com.nikibonev.tempo.data.model.TimeDataSnapshot
import com.nikibonev.tempo.data.repository.AnalyticsEngine
import com.nikibonev.tempo.ui.components.DurationPair
import com.nikibonev.tempo.ui.components.EmptyState
import com.nikibonev.tempo.ui.components.ProjectIdentity
import com.nikibonev.tempo.ui.components.SectionHeader
import com.nikibonev.tempo.ui.components.SelectablePill
import com.nikibonev.tempo.ui.components.SessionTimeline
import com.nikibonev.tempo.util.ProjectColorPalette
import com.nikibonev.tempo.util.formatDuration
import com.nikibonev.tempo.util.progressFraction
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

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
    onCreateTemplate: (String, Long, String, Int) -> Unit,
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
                if (snapshot.activeTimer == null) "Decide what matters, start the clock, then leave Tempo alone."
                else "Tempo is keeping the record. Stay with the work in front of you.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        snapshot.activeTimer?.let { active ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProjectIdentity(active.project)
                        Text(
                            if (active.isPaused) "Break ${formatDuration(active.intervals.lastOrNull()?.durationMillis(now) ?: 0L)}"
                            else formatDuration(active.workMillis(now)),
                            style = MaterialTheme.typography.displayLarge,
                        )
                        if (active.session.intention.isNotBlank()) Text(active.session.intention, style = MaterialTheme.typography.titleMedium)
                        active.session.plannedMinutes?.let { planned ->
                            LinearProgressIndicator(
                                progress = { progressFraction(active.workMillis(now), planned) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("${formatDuration(active.workMillis(now), true)} of ${planned}m planned", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SessionTimeline(active.intervals, Color(active.project.colorArgb), now)
                        DurationPair(active.workMillis(now), active.breakMillis(now))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = if (active.isPaused) onResume else onPause, modifier = Modifier.weight(1f)) {
                                Text(if (active.isPaused) "Resume" else "Pause")
                            }
                            OutlinedButton(onClick = onFinish, modifier = Modifier.weight(1f)) { Text("Finish") }
                        }
                        TextButton(onClick = onEditActive) { Text("Edit session details") }
                    }
                }
            }
        } ?: run {
            item {
                FocusLauncher(activeProjects, onStartFocus, onCreateProject)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("Today", formatDuration(today.totalWorkMillis, true), Modifier.weight(1f))
                SummaryCard("This week", formatDuration(week.totalWorkMillis, true), Modifier.weight(1f))
            }
        }

        item { SectionHeader("Goal compass", "Weekly goals translated into what remains") }
        val goals = activeProjects.filter { it.weeklyGoalMinutes > 0 }
        if (goals.isEmpty()) {
            item { EmptyState("No weekly goals yet", "Set a target on a project and Tempo will show whether your week is on pace.") }
        } else {
            items(goals, key = { it.id }) { project ->
                val tracked = week.projectTotals.firstOrNull { it.project.id == project.id }?.workMillis ?: 0L
                val targetMillis = project.weeklyGoalMinutes * 60_000L
                val remaining = (targetMillis - tracked).coerceAtLeast(0L)
                val dayOfWeek = LocalDate.now(zone).dayOfWeek.value
                val daysLeft = (8 - dayOfWeek).coerceAtLeast(1)
                val pace = ceil(remaining / daysLeft.toDouble()).toLong()
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProjectIdentity(project)
                        LinearProgressIndicator(progress = { (tracked.toFloat() / targetMillis.coerceAtLeast(1L)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("${formatDuration(tracked, true)} / ${formatDuration(targetMillis, true)} this week")
                        Text(
                            if (remaining == 0L) "Goal reached. Anything extra is bonus time."
                            else "${formatDuration(remaining, true)} left · about ${formatDuration(pace, true)} per remaining day",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (activeProjects.isEmpty()) {
            item {
                SectionHeader("Quick setup", "Start with a useful template, or create your own")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectablePill("Development", false) { onCreateTemplate("Development", ProjectColorPalette[0], "⌘", 600) }
                    SelectablePill("Study", false) { onCreateTemplate("Study", ProjectColorPalette[2], "✎", 480) }
                    SelectablePill("Gym", false) { onCreateTemplate("Gym", ProjectColorPalette[1], "⚡", 180) }
                }
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
    var selected by remember { mutableStateOf<Project?>(projects.firstOrNull()) }
    var planned by remember { mutableStateOf<Int?>(null) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("What are you about to do?", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = intention,
                onValueChange = { intention = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Implement login flow, write paper section, leg day…") },
                singleLine = true,
            )
            if (projects.isNotEmpty()) {
                Text("Project", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    projects.take(3).forEach { p -> SelectablePill(p.name, selected?.id == p.id) { selected = p } }
                }
            }
            Text("Plan (optional)", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(25, 45, 60, 90).forEach { minutes ->
                    SelectablePill("${minutes}m", planned == minutes) { planned = if (planned == minutes) null else minutes }
                }
            }
            Button(onClick = { onStartFocus(selected, intention, planned) }, modifier = Modifier.fillMaxWidth()) { Text("Start focus") }
            TextButton(onClick = onCreateProject) { Text("Create another project") }
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
