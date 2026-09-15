package com.nikibonev.tempo.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class SessionState { RUNNING, PAUSED, COMPLETED }
enum class IntervalType { WORK, BREAK }
enum class SessionOutcome { NONE, DONE, PROGRESS, STUCK }
enum class AppTheme { LIGHT, DARK }
enum class WeekStart { MONDAY, SUNDAY }

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val ownerId: String,
    val name: String,
    val colorArgb: Long,
    val icon: String = "●",
    val parentProjectId: String? = null,
    val weeklyGoalMinutes: Int = 0,
    val goalMinutes: Int = 0,
    val goalStartAt: Long? = null,
    val goalEndAt: Long? = null,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
) {
    val hasDateRangeGoal: Boolean
        get() = goalMinutes > 0 && goalStartAt != null && goalEndAt != null && goalEndAt > goalStartAt
}

data class Session(
    val id: String = UUID.randomUUID().toString(),
    val ownerId: String,
    val projectId: String,
    val intention: String = "",
    val note: String = "",
    val plannedMinutes: Int? = null,
    val outcome: SessionOutcome = SessionOutcome.NONE,
    val quality: Int? = null,
    val state: SessionState = SessionState.RUNNING,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

data class TimeInterval(
    val id: String = UUID.randomUUID().toString(),
    val ownerId: String,
    val sessionId: String,
    val type: IntervalType,
    val startedAt: Long,
    val endedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
) {
    fun durationMillis(now: Long = System.currentTimeMillis()): Long =
        ((endedAt ?: now) - startedAt).coerceAtLeast(0L)
}

data class SessionDetails(
    val session: Session,
    val project: Project?,
    val intervals: List<TimeInterval>,
) {
    fun workMillis(now: Long = System.currentTimeMillis()): Long =
        intervals.filter { !it.deleted && it.type == IntervalType.WORK }.sumOf { it.durationMillis(now) }

    fun breakMillis(now: Long = System.currentTimeMillis()): Long =
        intervals.filter { !it.deleted && it.type == IntervalType.BREAK }.sumOf { it.durationMillis(now) }

    fun longestWorkBlockMillis(now: Long = System.currentTimeMillis()): Long =
        intervals.filter { !it.deleted && it.type == IntervalType.WORK }.maxOfOrNull { it.durationMillis(now) } ?: 0L
}

data class ActiveTimer(
    val session: Session,
    val project: Project,
    val intervals: List<TimeInterval>,
) {
    val isPaused: Boolean get() = session.state == SessionState.PAUSED
    fun workMillis(now: Long = System.currentTimeMillis()): Long =
        intervals.filter { !it.deleted && it.type == IntervalType.WORK }.sumOf { it.durationMillis(now) }
    fun breakMillis(now: Long = System.currentTimeMillis()): Long =
        intervals.filter { !it.deleted && it.type == IntervalType.BREAK }.sumOf { it.durationMillis(now) }
}

data class AppSettings(
    val theme: AppTheme = AppTheme.DARK,
    val dynamicColor: Boolean = true,
    val showTimerNotification: Boolean = true,
    val notificationPermissionAsked: Boolean = false,
    val weekStart: WeekStart = WeekStart.MONDAY,
    val use24HourTime: Boolean = true,
    val staleTimerHours: Int = 10,
)

data class AccountState(
    val uid: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val firebaseAvailable: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
) {
    val isSignedIn: Boolean get() = uid != null
    val ownerId: String get() = uid ?: GUEST_OWNER

    companion object {
        const val GUEST_OWNER = "guest"
    }
}

data class DayTotal(val date: LocalDate, val workMillis: Long)
data class ProjectTotal(val project: Project, val workMillis: Long, val breakMillis: Long)

data class AnalyticsSnapshot(
    val totalWorkMillis: Long = 0L,
    val totalBreakMillis: Long = 0L,
    val sessionCount: Int = 0,
    val averageSessionWorkMillis: Long = 0L,
    val longestWorkBlockMillis: Long = 0L,
    val projectTotals: List<ProjectTotal> = emptyList(),
    val dailyTotals: List<DayTotal> = emptyList(),
    val contextSwitches: Int = 0,
    val currentStreakDays: Int = 0,
    val bestStreakDays: Int = 0,
    val insights: List<String> = emptyList(),
)

data class ExportBundle(
    val schemaVersion: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val projects: List<Project>,
    val sessions: List<Session>,
    val intervals: List<TimeInterval>,
)

fun Long.toLocalDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

data class TimeDataSnapshot(
    val projects: List<Project> = emptyList(),
    val sessions: List<SessionDetails> = emptyList(),
    val activeTimer: ActiveTimer? = null,
)
