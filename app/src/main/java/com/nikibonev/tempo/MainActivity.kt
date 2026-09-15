package com.nikibonev.tempo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nikibonev.tempo.data.model.ActiveTimer
import com.nikibonev.tempo.data.model.SessionOutcome
import com.nikibonev.tempo.data.model.TimeDataSnapshot
import com.nikibonev.tempo.ui.components.SelectablePill
import com.nikibonev.tempo.ui.screens.DashboardScreen
import com.nikibonev.tempo.ui.screens.HistoryScreen
import com.nikibonev.tempo.ui.screens.InsightsScreen
import com.nikibonev.tempo.ui.screens.ProjectEditorDialog
import com.nikibonev.tempo.ui.screens.ProjectsScreen
import com.nikibonev.tempo.ui.screens.SettingsScreen
import com.nikibonev.tempo.ui.theme.TempoTheme
import com.nikibonev.tempo.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val graph = (application as TempoApplication).graph
            TempoRoot(graph = graph, activity = this)
        }
    }
}

private enum class MainTab(val label: String, val icon: ImageVector) {
    TODAY("Focus", Icons.Default.Home),
    PROJECTS("Projects", Icons.Default.Workspaces),
    HISTORY("History", Icons.Default.History),
    INSIGHTS("Insights", Icons.Default.Insights),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
private fun TempoRoot(graph: AppGraph, activity: ComponentActivity) {
    val settings by graph.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = com.nikibonev.tempo.data.model.AppSettings())
    val account by graph.authRepository.state.collectAsStateWithLifecycle()
    val syncState by graph.cloudSyncRepository.state.collectAsStateWithLifecycle()
    val snapshot by graph.timeRepository.observeSnapshot(graph.ownerId).collectAsStateWithLifecycle(initialValue = TimeDataSnapshot())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    var tab by remember { mutableStateOf(MainTab.TODAY) }
    var createProject by remember { mutableStateOf(false) }
    var editActive by remember { mutableStateOf(false) }
    var finishActive by remember { mutableStateOf(false) }

    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(snapshot.activeTimer?.session?.id, settings.showTimerNotification) {
        if (snapshot.activeTimer != null && settings.showTimerNotification && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                activity.contentResolver.openInputStream(uri)?.use { graph.backupRepository.importJson(account.ownerId, it) }
                    ?: error("Could not read that file.")
            }
            snackbar.showSnackbar(result.fold(
                { "Imported ${it.sessions} sessions and ${it.projects} projects." },
                { "Import failed: ${it.message ?: "unknown error"}" },
            ))
        }
    }

    fun launch(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.onFailure { snackbar.showSnackbar(it.message ?: "Something went wrong.") }
        }
    }

    TempoTheme(settings) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    MainTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(item.icon, item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { padding ->
            androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                when (tab) {
                    MainTab.TODAY -> DashboardScreen(
                        snapshot = snapshot,
                        settings = settings,
                        now = now,
                        onStartFocus = { project, intention, planned -> launch {
                            val target = project ?: snapshot.projects.firstOrNull {
                                !it.deleted && !it.archived && it.name.equals("General", ignoreCase = true)
                            } ?: graph.timeRepository.createProject(
                                ownerId = account.ownerId,
                                name = "General",
                                colorArgb = com.nikibonev.tempo.util.ProjectColorPalette[5],
                                icon = "•",
                                weeklyGoalMinutes = 0,
                            )
                            graph.timeRepository.startSession(account.ownerId, target.id, intention, planned)
                        } },
                        onPause = { launch { graph.timeRepository.pause(account.ownerId) } },
                        onResume = { launch { graph.timeRepository.resume(account.ownerId) } },
                        onFinish = { finishActive = true },
                        onEditActive = { editActive = true },
                        onCreateProject = { createProject = true },
                        onCreateTemplate = { name, color, icon, goal -> launch {
                            graph.timeRepository.createProject(account.ownerId, name, color, icon, goal)
                            snackbar.showSnackbar("$name project created.")
                        } },
                    )
                    MainTab.PROJECTS -> ProjectsScreen(
                        snapshot = snapshot,
                        onCreate = { name, color, icon, goal -> launch { graph.timeRepository.createProject(account.ownerId, name, color, icon, goal) } },
                        onUpdate = { project -> launch { graph.timeRepository.updateProject(project) } },
                        onArchive = { project, archived -> launch { graph.timeRepository.setProjectArchived(project.id, archived) } },
                        onDelete = { project, history -> launch { graph.timeRepository.deleteProject(project.id, history) } },
                    )
                    MainTab.HISTORY -> HistoryScreen(
                        snapshot = snapshot,
                        now = now,
                        use24Hour = settings.use24HourTime,
                        onAddManual = { project, start, end, note, intention -> launch { graph.timeRepository.addManualSession(account.ownerId, project.id, start, end, note, intention) } },
                        onUpdateSession = { id, intention, note, planned, outcome, quality -> launch { graph.timeRepository.updateSession(id, intention, note, planned, outcome, quality) } },
                        onUpdateInterval = { id, start, end -> launch { graph.timeRepository.updateInterval(id, start, end) } },
                        onDeleteSession = { id -> launch { graph.timeRepository.deleteSession(id) } },
                    )
                    MainTab.INSIGHTS -> InsightsScreen(snapshot, settings, now)
                    MainTab.SETTINGS -> SettingsScreen(
                        settings = settings,
                        account = account,
                        syncState = syncState,
                        onTheme = { launch { graph.settingsRepository.setTheme(it) } },
                        onDynamicColor = { launch { graph.settingsRepository.setDynamicColor(it) } },
                        onNotifications = { enabled -> launch { graph.settingsRepository.setTimerNotification(enabled) } },
                        onWeekStart = { launch { graph.settingsRepository.setWeekStart(it) } },
                        onUse24Hour = { launch { graph.settingsRepository.setUse24HourTime(it) } },
                        onStaleHours = { launch { graph.settingsRepository.setStaleTimerHours(it) } },
                        onSignInEmail = { email, password -> launch { graph.authRepository.signInWithEmail(email, password).getOrThrow() } },
                        onCreateAccount = { email, password -> launch { graph.authRepository.createAccount(email, password).getOrThrow(); snackbar.showSnackbar("Account created. Check your inbox for the verification email.") } },
                        onResetPassword = { email -> launch { graph.authRepository.sendPasswordReset(email).getOrThrow(); snackbar.showSnackbar("Password reset email sent.") } },
                        onSignInGoogle = { launch { graph.authRepository.signInWithGoogle(activity).getOrThrow() } },
                        onSignOut = { launch { graph.authRepository.signOut().getOrThrow() } },
                        onSyncNow = { account.uid?.let { uid -> launch { graph.cloudSyncRepository.reconcile(uid).getOrThrow() } } },
                        onExportJson = { launch { shareFile(activity, graph.backupRepository.exportJson(account.ownerId), "application/json") } },
                        onExportCsv = { launch { shareFile(activity, graph.backupRepository.exportCsv(account.ownerId), "text/csv") } },
                        onImport = { importLauncher.launch("application/json") },
                        onDeleteAccount = { account.uid?.let { uid -> launch {
                            graph.cloudSyncRepository.deleteCloudData(uid).getOrThrow()
                            graph.authRepository.deleteFirebaseAccount().getOrThrow()
                            graph.timeRepository.deleteLocalAccountData(uid)
                            snackbar.showSnackbar("Tempo account deleted.")
                        } } },
                        onDeleteLocalData = { launch { graph.timeRepository.deleteLocalAccountData(account.ownerId) } },
                    )
                }
            }
        }

        if (createProject) {
            ProjectEditorDialog(null, onDismiss = { createProject = false }) { name, color, icon, goal ->
                createProject = false
                launch { graph.timeRepository.createProject(account.ownerId, name, color, icon, goal) }
            }
        }
        if (editActive) snapshot.activeTimer?.let { active ->
            EditActiveDialog(active, onDismiss = { editActive = false }) { intention, planned ->
                editActive = false
                launch {
                    val s = active.session
                    graph.timeRepository.updateSession(s.id, intention, s.note, planned, s.outcome, s.quality)
                }
            }
        }
        if (finishActive) snapshot.activeTimer?.let { active ->
            FinishSessionDialog(active, now, onDismiss = { finishActive = false }) { note, outcome, quality ->
                finishActive = false
                launch { graph.timeRepository.finish(account.ownerId, note, outcome, quality) }
            }
        }
    }
}

@Composable
private fun EditActiveDialog(active: ActiveTimer, onDismiss: () -> Unit, onSave: (String, Int?) -> Unit) {
    var intention by remember(active.session.id) { mutableStateOf(active.session.intention) }
    var planned by remember(active.session.id) { mutableStateOf(active.session.plannedMinutes?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Session details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A short intention makes the history useful later. It never blocks starting the timer.")
                OutlinedTextField(intention, { intention = it.take(120) }, label = { Text("What are you working on?") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(planned, { planned = it.filter(Char::isDigit).take(4) }, label = { Text("Planned minutes (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(intention, planned.toIntOrNull()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FinishSessionDialog(active: ActiveTimer, now: Long, onDismiss: () -> Unit, onFinish: (String, SessionOutcome, Int?) -> Unit) {
    var note by remember(active.session.id) { mutableStateOf(active.session.note) }
    var outcome by remember(active.session.id) { mutableStateOf(SessionOutcome.NONE) }
    var quality by remember(active.session.id) { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish ${active.project.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${formatDuration(active.workMillis(now), true)} work · ${formatDuration(active.breakMillis(now), true)} breaks")
                Text("Outcome")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(SessionOutcome.DONE to "Done", SessionOutcome.PROGRESS to "Progress", SessionOutcome.STUCK to "Stuck").forEach { (value, label) ->
                        SelectablePill(label, outcome == value) { outcome = value }
                    }
                }
                Text("Focus quality (optional)")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { score -> SelectablePill(score.toString(), quality == score) { quality = if (quality == score) null else score } }
                }
                OutlinedTextField(note, { note = it.take(1000) }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onFinish(note, outcome, quality) }) { Text("Finish") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep running") } },
    )
}

private fun shareFile(activity: ComponentActivity, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    activity.startActivity(Intent.createChooser(intent, "Share Tempo export"))
}
