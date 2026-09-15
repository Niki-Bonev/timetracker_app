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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nikibonev.tempo.data.model.AccountState
import com.nikibonev.tempo.data.model.AppSettings
import com.nikibonev.tempo.data.model.AppTheme
import com.nikibonev.tempo.data.model.WeekStart
import com.nikibonev.tempo.data.repository.SyncState
import com.nikibonev.tempo.ui.components.SectionHeader
import com.nikibonev.tempo.ui.components.SelectablePill
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    settings: AppSettings,
    account: AccountState,
    syncState: SyncState,
    onTheme: (AppTheme) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onWeekStart: (WeekStart) -> Unit,
    onUse24Hour: (Boolean) -> Unit,
    onStaleHours: (Int) -> Unit,
    onSignInEmail: (String, String) -> Unit,
    onCreateAccount: (String, String) -> Unit,
    onResetPassword: (String) -> Unit,
    onSignInGoogle: () -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImport: () -> Unit,
    onDeleteAccount: () -> Unit,
    onDeleteLocalData: () -> Unit,
) {
    var authDialog by remember { mutableStateOf(false) }
    var deleteAccount by remember { mutableStateOf(false) }
    var deleteLocal by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
            Text("Customize the tracker without turning setup into a project of its own.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionHeader("Account & sync")
            Spacer(Modifier.height(8.dp))
            Card {
                if (account.isSignedIn) {
                    ListItem(
                        headlineContent = { Text(account.displayName ?: account.email ?: "Signed in") },
                        supportingContent = { Text(syncLabel(syncState)) },
                        leadingContent = { Icon(Icons.Outlined.Cloud, null) },
                    )
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onSyncNow) { Text("Sync now") }
                        OutlinedButton(onClick = onSignOut) { Icon(Icons.Outlined.Logout, null); Text(" Sign out") }
                    }
                } else {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (account.firebaseAvailable) "Local mode" else "Local mode · cloud not configured", style = MaterialTheme.typography.titleMedium)
                        Text("Tracking works fully offline. Signing in is only for backup and multi-device sync.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (account.firebaseAvailable) {
                            Button(onClick = onSignInGoogle, modifier = Modifier.fillMaxWidth()) { Text("Continue with Google") }
                            OutlinedButton(onClick = { authDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Email sign in") }
                        }
                    }
                }
            }
        }
        item {
            SectionHeader("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppTheme.entries.forEach { theme -> SelectablePill(theme.name.lowercase().replaceFirstChar(Char::uppercase), settings.theme == theme) { onTheme(theme) } }
            }
            ToggleRow("Dynamic color", "Use your device palette on Android 12+", settings.dynamicColor, onDynamicColor)
        }
        item {
            SectionHeader("Tracking")
            ToggleRow("Timer notification", "Pause, resume and finish from the notification shade", settings.showTimerNotification, onNotifications)
            ToggleRow("24-hour time", "Use 18:30 instead of 6:30 PM", settings.use24HourTime, onUse24Hour)
            Text("Week starts", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WeekStart.entries.forEach { value -> SelectablePill(value.name.lowercase().replaceFirstChar(Char::uppercase), settings.weekStart == value) { onWeekStart(value) } }
            }
            Text("Forgotten timer warning threshold", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(6, 10, 14, 24).forEach { hours -> SelectablePill("${hours}h", settings.staleTimerHours == hours) { onStaleHours(hours) } }
            }
        }
        item {
            SectionHeader("Your data", "Portable by design")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExportJson, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Download, null); Text(" Backup") }
                OutlinedButton(onClick = onExportCsv, modifier = Modifier.weight(1f)) { Text("CSV") }
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Icon(Icons.Outlined.Upload, null); Text(" Import Tempo backup") }
            Text("JSON keeps projects, sessions and break intervals. CSV is convenient for spreadsheets and analysis.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        item {
            SectionHeader("Privacy & deletion")
            if (account.isSignedIn) {
                TextButton(onClick = { deleteAccount = true }) { Icon(Icons.Outlined.DeleteForever, null); Text(" Delete account and cloud data") }
            }
            TextButton(onClick = { deleteLocal = true }) { Text("Delete local ${if (account.isSignedIn) "account" else "guest"} data", color = MaterialTheme.colorScheme.error) }
        }
    }

    if (authDialog) AuthDialog(onDismiss = { authDialog = false }, onSignIn = { e, p -> authDialog = false; onSignInEmail(e, p) }, onCreate = { e, p -> authDialog = false; onCreateAccount(e, p) }, onReset = onResetPassword)
    if (deleteAccount) ConfirmDestructiveDialog("Delete Tempo account?", "This deletes synced Tempo data and then the Firebase authentication account. Export a backup first if you want to keep your history.", "Delete account", { deleteAccount = false }) { deleteAccount = false; onDeleteAccount() }
    if (deleteLocal) ConfirmDestructiveDialog("Delete local data?", "Projects, sessions and intervals in the current local account namespace will be permanently removed from this device.", "Delete local data", { deleteLocal = false }) { deleteLocal = false; onDeleteLocalData() }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun AuthDialog(
    onDismiss: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onCreate: (String, String) -> Unit,
    onReset: (String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Email account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(email, { email = it.trim().take(120) }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(password, { password = it.take(100) }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation())
                TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide password" else "Show password") }
                TextButton(onClick = { onReset(email) }, enabled = email.isNotBlank()) { Text("Forgot password?") }
            }
        },
        confirmButton = { Button(onClick = { onSignIn(email, password) }, enabled = email.isNotBlank() && password.isNotBlank()) { Text("Sign in") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onCreate(email, password) }, enabled = email.isNotBlank() && password.length >= 6) { Text("Create account") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ConfirmDestructiveDialog(
    title: String,
    body: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun syncLabel(state: SyncState): String = when (state) {
    SyncState.Unavailable -> "Cloud sync unavailable in this build"
    SyncState.Idle -> "Ready to sync"
    SyncState.Syncing -> "Syncing…"
    is SyncState.Success -> {
        val time = Instant.ofEpochMilli(state.atMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        "Last synced $time"
    }
    is SyncState.Error -> "Sync error: ${state.message}"
}
