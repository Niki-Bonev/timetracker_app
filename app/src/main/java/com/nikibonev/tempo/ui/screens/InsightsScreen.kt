package com.nikibonev.tempo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nikibonev.tempo.data.model.AppSettings
import com.nikibonev.tempo.data.model.TimeDataSnapshot
import com.nikibonev.tempo.data.repository.AnalyticsEngine
import com.nikibonev.tempo.ui.components.EmptyState
import com.nikibonev.tempo.ui.components.MetricCard
import com.nikibonev.tempo.ui.components.ProjectIdentity
import com.nikibonev.tempo.ui.components.SectionHeader
import com.nikibonev.tempo.ui.components.SelectablePill
import com.nikibonev.tempo.util.formatDuration
import java.time.ZoneId

private enum class InsightPeriod(val label: String) { WEEK("Week"), DAYS_30("30 days"), ALL("All time") }

@Composable
fun InsightsScreen(
    snapshot: TimeDataSnapshot,
    settings: AppSettings,
    now: Long,
) {
    var period by remember { mutableStateOf(InsightPeriod.WEEK) }
    val zone = remember { ZoneId.systemDefault() }
    val earliest = snapshot.sessions.minOfOrNull { it.session.startedAt } ?: now
    val from = when (period) {
        InsightPeriod.WEEK -> AnalyticsEngine.weekStartMillis(now, settings.weekStart.name == "MONDAY", zone)
        InsightPeriod.DAYS_30 -> now - 30L * 24L * 60L * 60L * 1000L
        InsightPeriod.ALL -> earliest
    }
    val analytics = remember(snapshot.sessions, snapshot.projects, from, now, period) {
        AnalyticsEngine.calculate(snapshot.projects, snapshot.sessions, from, now, zone, if (period == InsightPeriod.WEEK) 7 else 14)
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Insights", style = MaterialTheme.typography.headlineLarge)
            Text("Patterns that help you plan better, without turning productivity into a scoreboard.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InsightPeriod.entries.forEach { item -> SelectablePill(item.label, period == item) { period = item } }
            }
        }
        if (analytics.totalWorkMillis == 0L) {
            item { EmptyState("Not enough data yet", "Track a few sessions and Tempo will surface workload, focus and planning patterns here.") }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Focused", formatDuration(analytics.totalWorkMillis, true), "${analytics.sessionCount} sessions", Modifier.weight(1f))
                    MetricCard("Longest block", formatDuration(analytics.longestWorkBlockMillis, true), "without a break", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Avg. session", formatDuration(analytics.averageSessionWorkMillis, true), modifier = Modifier.weight(1f))
                    MetricCard("Current streak", "${analytics.currentStreakDays}d", "best ${analytics.bestStreakDays}d", Modifier.weight(1f))
                }
            }
            item { SectionHeader("Where the time went") }
            items(analytics.projectTotals, key = { it.project.id }) { total ->
                val fraction = (total.workMillis.toFloat() / analytics.totalWorkMillis.coerceAtLeast(1L)).coerceIn(0f, 1f)
                Card {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row {
                            ProjectIdentity(total.project, Modifier.weight(1f))
                            Text(formatDuration(total.workMillis, true))
                        }
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        if (total.breakMillis > 0) Text("${formatDuration(total.breakMillis, true)} breaks inside sessions", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { SectionHeader("What Tempo noticed") }
            items(analytics.insights) { insight ->
                Card { Text(insight, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge) }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Context switching", style = MaterialTheme.typography.titleMedium)
                        Text("${analytics.contextSwitches} quick project switches", style = MaterialTheme.typography.headlineMedium)
                        Text("This is descriptive, not a penalty. Some days genuinely require switching; Tempo simply makes the cost visible.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
