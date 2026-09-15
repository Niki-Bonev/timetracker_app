package com.nikibonev.tempo

import android.content.Context
import com.nikibonev.tempo.data.local.TempoDatabase
import com.nikibonev.tempo.data.repository.AuthRepository
import com.nikibonev.tempo.data.repository.BackupRepository
import com.nikibonev.tempo.data.repository.CloudSyncRepository
import com.nikibonev.tempo.data.repository.SettingsRepository
import com.nikibonev.tempo.data.repository.TimeRepository
import com.nikibonev.tempo.notifications.TimerNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database = TempoDatabase(appContext)
    val timeRepository = TimeRepository(database)
    val authRepository = AuthRepository(appContext)
    val settingsRepository = SettingsRepository(appContext)
    val cloudSyncRepository = CloudSyncRepository(authRepository, database, timeRepository)
    val backupRepository = BackupRepository(appContext, database, timeRepository)
    val notificationManager = TimerNotificationManager(appContext)

    val ownerId = authRepository.state.map { account -> account.ownerId }.distinctUntilChanged()

    private var syncJob: Job? = null

    init {
        timeRepository.onDataChanged = { scheduleCloudPush() }
        appScope.launch {
            authRepository.state.map { it.uid }.distinctUntilChanged().collect { uid ->
                if (uid != null) {
                    cloudSyncRepository.reconcile(uid)
                    val migrated = runCatching { timeRepository.migrateGuestDataToUser(uid) }.getOrDefault(false)
                    if (migrated) cloudSyncRepository.pushLocalChanges(uid)
                }
            }
        }
        appScope.launch {
            combine(settingsRepository.settings, timeRepository.observeSnapshot(ownerId)) { settings, snapshot -> settings to snapshot.activeTimer }
                .collect { (settings, active) -> notificationManager.render(active, settings) }
        }
    }

    fun scheduleCloudPush() {
        val uid = authRepository.state.value.uid ?: return
        syncJob?.cancel()
        syncJob = appScope.launch {
            delay(1_500)
            cloudSyncRepository.pushLocalChanges(uid)
        }
    }

    fun refreshNotificationSoon() { timeRepository.notifyDataChanged(triggerSync = false) }
}
