package com.nikibonev.tempo.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nikibonev.tempo.data.model.AppSettings
import com.nikibonev.tempo.data.model.AppTheme
import com.nikibonev.tempo.data.model.WeekStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tempoDataStore by preferencesDataStore(name = "tempo_settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.tempoDataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[Keys.THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            showTimerNotification = prefs[Keys.TIMER_NOTIFICATION] ?: true,
            weekStart = prefs[Keys.WEEK_START]?.let { runCatching { WeekStart.valueOf(it) }.getOrNull() } ?: WeekStart.MONDAY,
            use24HourTime = prefs[Keys.USE_24_HOUR] ?: true,
            staleTimerHours = (prefs[Keys.STALE_TIMER_HOURS] ?: 10).coerceIn(2, 48),
        )
    }

    suspend fun setTheme(theme: AppTheme) = context.tempoDataStore.edit { it[Keys.THEME] = theme.name }
    suspend fun setDynamicColor(enabled: Boolean) = context.tempoDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    suspend fun setTimerNotification(enabled: Boolean) = context.tempoDataStore.edit { it[Keys.TIMER_NOTIFICATION] = enabled }
    suspend fun setWeekStart(weekStart: WeekStart) = context.tempoDataStore.edit { it[Keys.WEEK_START] = weekStart.name }
    suspend fun setUse24HourTime(enabled: Boolean) = context.tempoDataStore.edit { it[Keys.USE_24_HOUR] = enabled }
    suspend fun setStaleTimerHours(hours: Int) = context.tempoDataStore.edit { it[Keys.STALE_TIMER_HOURS] = hours.coerceIn(2, 48) }

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val TIMER_NOTIFICATION = booleanPreferencesKey("timer_notification")
        val WEEK_START = stringPreferencesKey("week_start")
        val USE_24_HOUR = booleanPreferencesKey("use_24_hour")
        val STALE_TIMER_HOURS = intPreferencesKey("stale_timer_hours")
    }
}
