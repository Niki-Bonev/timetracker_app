package com.nikibonev.tempo.data.repository

import com.nikibonev.tempo.data.local.TempoDatabase
import com.nikibonev.tempo.data.model.ActiveTimer
import com.nikibonev.tempo.data.model.IntervalType
import com.nikibonev.tempo.data.model.Project
import com.nikibonev.tempo.data.model.Session
import com.nikibonev.tempo.data.model.SessionDetails
import com.nikibonev.tempo.data.model.SessionOutcome
import com.nikibonev.tempo.data.model.SessionState
import com.nikibonev.tempo.data.model.TimeDataSnapshot
import com.nikibonev.tempo.data.model.TimeInterval
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

class TimeRepository(
    private val database: TempoDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val revision = MutableStateFlow(0L)
    var onDataChanged: (() -> Unit)? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSnapshot(ownerId: Flow<String>): Flow<TimeDataSnapshot> =
        combine(ownerId, revision) { owner, _ -> owner }
            .mapLatest { owner -> withContext(ioDispatcher) { loadSnapshot(owner) } }

    suspend fun loadSnapshot(ownerId: String): TimeDataSnapshot = withContext(ioDispatcher) {
        val projects = database.getProjects(ownerId)
        val projectMap = projects.associateBy { it.id }
        val sessions = database.getSessions(ownerId).map { session ->
            SessionDetails(session, projectMap[session.projectId] ?: database.getProject(session.projectId), database.getIntervalsForSession(session.id))
        }
        val activeSession = database.getActiveSession(ownerId)
        val active = activeSession?.let { session ->
            val project = projectMap[session.projectId] ?: database.getProject(session.projectId)
            project?.let { ActiveTimer(session, it, database.getIntervalsForSession(session.id)) }
        }
        TimeDataSnapshot(projects = projects, sessions = sessions, activeTimer = active)
    }

    suspend fun createProject(
        ownerId: String,
        name: String,
        colorArgb: Long,
        icon: String,
        weeklyGoalMinutes: Int = 0,
        parentProjectId: String? = null,
        goalMinutes: Int = 0,
        goalStartAt: Long? = null,
        goalEndAt: Long? = null,
    ): Project = write {
        parentProjectId?.let { parentId ->
            val parent = database.getProjectForOwner(ownerId, parentId)
            require(parent != null && !parent.deleted && parent.parentProjectId == null) { "Parent project is not available." }
        }
        val now = System.currentTimeMillis()
        val rangeValid = goalMinutes > 0 && goalStartAt != null && goalEndAt != null && goalEndAt > goalStartAt
        val project = Project(
            ownerId = ownerId,
            name = name.trim().ifBlank { "Untitled project" },
            colorArgb = colorArgb,
            icon = icon.ifBlank { "●" },
            parentProjectId = parentProjectId,
            weeklyGoalMinutes = weeklyGoalMinutes.coerceAtLeast(0),
            goalMinutes = if (rangeValid) goalMinutes.coerceAtLeast(0) else 0,
            goalStartAt = if (rangeValid) goalStartAt else null,
            goalEndAt = if (rangeValid) goalEndAt else null,
            createdAt = now,
            updatedAt = now,
        )
        database.upsertProject(project)
        project
    }

    suspend fun updateProject(project: Project): Project = write {
        val rangeValid = project.goalMinutes > 0 && project.goalStartAt != null && project.goalEndAt != null && project.goalEndAt > project.goalStartAt
        val updated = project.copy(
            name = project.name.trim().ifBlank { "Untitled project" },
            weeklyGoalMinutes = project.weeklyGoalMinutes.coerceAtLeast(0),
            goalMinutes = if (rangeValid) project.goalMinutes.coerceAtLeast(0) else 0,
            goalStartAt = if (rangeValid) project.goalStartAt else null,
            goalEndAt = if (rangeValid) project.goalEndAt else null,
            updatedAt = System.currentTimeMillis(),
        )
        database.upsertProject(updated)
        updated
    }

    suspend fun setProjectArchived(projectId: String, archived: Boolean) = write {
        val project = database.getProject(projectId) ?: return@write
        val now = System.currentTimeMillis()
        database.upsertProject(project.copy(archived = archived, updatedAt = now))
        if (project.parentProjectId == null) {
            database.getProjects(project.ownerId, includeArchived = true, includeDeleted = true)
                .filter { it.parentProjectId == project.id && !it.deleted }
                .forEach { database.upsertProject(it.copy(archived = archived, updatedAt = now)) }
        }
    }

    suspend fun deleteProject(projectId: String, deleteSessions: Boolean = false) = write {
        val project = database.getProject(projectId) ?: return@write
        val now = System.currentTimeMillis()
        val allProjects = database.getProjects(project.ownerId, includeArchived = true, includeDeleted = true)
        val targets = if (project.parentProjectId == null) {
            listOf(project) + allProjects.filter { it.parentProjectId == project.id && !it.deleted }
        } else listOf(project)
        targets.forEach { database.upsertProject(it.copy(deleted = true, updatedAt = now)) }
        if (deleteSessions) {
            val ids = targets.map { it.id }.toSet()
            database.getSessions(project.ownerId, includeDeleted = true)
                .filter { it.projectId in ids && !it.deleted }
                .forEach { softDeleteSessionInternal(it, now) }
        }
    }

    suspend fun startSession(ownerId: String, projectId: String, intention: String = "", plannedMinutes: Int? = null, now: Long = System.currentTimeMillis()): Session = write {
        require(database.getActiveSession(ownerId) == null) { "A timer is already active." }
        val project = database.getProjectForOwner(ownerId, projectId)
        require(project != null && !project.deleted && !project.archived) { "Project is not available." }
        val session = Session(ownerId = ownerId, projectId = projectId, intention = intention.trim(), plannedMinutes = plannedMinutes?.takeIf { it > 0 }, state = SessionState.RUNNING, startedAt = now, createdAt = now, updatedAt = now)
        val interval = TimeInterval(ownerId = ownerId, sessionId = session.id, type = IntervalType.WORK, startedAt = now, updatedAt = now)
        database.upsertSession(session)
        database.upsertInterval(interval)
        session
    }

    suspend fun pause(ownerId: String, now: Long = System.currentTimeMillis()) = write {
        val session = database.getActiveSession(ownerId) ?: return@write
        if (session.state != SessionState.RUNNING) return@write
        database.getIntervalsForSession(session.id).lastOrNull { it.endedAt == null }?.let { open ->
            database.upsertInterval(open.copy(endedAt = now.coerceAtLeast(open.startedAt), updatedAt = now))
        }
        database.upsertInterval(TimeInterval(ownerId = ownerId, sessionId = session.id, type = IntervalType.BREAK, startedAt = now, updatedAt = now))
        database.upsertSession(session.copy(state = SessionState.PAUSED, updatedAt = now))
    }

    suspend fun resume(ownerId: String, now: Long = System.currentTimeMillis()) = write {
        val session = database.getActiveSession(ownerId) ?: return@write
        if (session.state != SessionState.PAUSED) return@write
        database.getIntervalsForSession(session.id).lastOrNull { it.endedAt == null }?.let { open ->
            database.upsertInterval(open.copy(endedAt = now.coerceAtLeast(open.startedAt), updatedAt = now))
        }
        database.upsertInterval(TimeInterval(ownerId = ownerId, sessionId = session.id, type = IntervalType.WORK, startedAt = now, updatedAt = now))
        database.upsertSession(session.copy(state = SessionState.RUNNING, updatedAt = now))
    }

    suspend fun finish(ownerId: String, note: String? = null, outcome: SessionOutcome? = null, quality: Int? = null, now: Long = System.currentTimeMillis()): Session? = write {
        finishInternal(ownerId, note, outcome, quality, now)
    }

    suspend fun switchProject(ownerId: String, projectId: String, intention: String = "", plannedMinutes: Int? = null, now: Long = System.currentTimeMillis()): Session = write {
        finishInternal(ownerId, outcome = SessionOutcome.PROGRESS, now = now)
        val project = database.getProjectForOwner(ownerId, projectId)
        require(project != null && !project.deleted && !project.archived) { "Project is not available." }
        val session = Session(ownerId = ownerId, projectId = projectId, intention = intention.trim(), plannedMinutes = plannedMinutes?.takeIf { it > 0 }, state = SessionState.RUNNING, startedAt = now, createdAt = now, updatedAt = now)
        database.upsertSession(session)
        database.upsertInterval(TimeInterval(ownerId = ownerId, sessionId = session.id, type = IntervalType.WORK, startedAt = now, updatedAt = now))
        session
    }

    suspend fun updateSession(sessionId: String, intention: String, note: String, plannedMinutes: Int?, outcome: SessionOutcome, quality: Int?): Session? = write {
        val session = database.getSession(sessionId) ?: return@write null
        val updated = session.copy(intention = intention.trim(), note = note.trim(), plannedMinutes = plannedMinutes?.takeIf { it > 0 }, outcome = outcome, quality = quality?.coerceIn(1, 5), updatedAt = System.currentTimeMillis())
        database.upsertSession(updated)
        updated
    }

    suspend fun addManualSession(ownerId: String, projectId: String, startedAt: Long, endedAt: Long, note: String = "", intention: String = ""): Session = write {
        require(endedAt > startedAt) { "End time must be after start time." }
        val project = database.getProjectForOwner(ownerId, projectId)
        require(project != null && !project.deleted) { "Project is not available." }
        val now = System.currentTimeMillis()
        val session = Session(ownerId = ownerId, projectId = projectId, intention = intention.trim(), note = note.trim(), state = SessionState.COMPLETED, startedAt = startedAt, endedAt = endedAt, createdAt = now, updatedAt = now)
        database.upsertSession(session)
        database.upsertInterval(TimeInterval(ownerId = ownerId, sessionId = session.id, type = IntervalType.WORK, startedAt = startedAt, endedAt = endedAt, updatedAt = now))
        session
    }

    suspend fun updateInterval(intervalId: String, startedAt: Long, endedAt: Long?): TimeInterval? = write {
        val interval = database.getInterval(intervalId) ?: return@write null
        require(endedAt == null || endedAt >= startedAt) { "Interval end cannot precede its start." }
        val updated = interval.copy(startedAt = startedAt, endedAt = endedAt, updatedAt = System.currentTimeMillis())
        database.upsertInterval(updated)
        updated
    }

    suspend fun deleteSession(sessionId: String) = write {
        val session = database.getSession(sessionId) ?: return@write
        softDeleteSessionInternal(session, System.currentTimeMillis())
    }

    suspend fun restoreSession(sessionId: String) = write {
        val session = database.getSession(sessionId) ?: return@write
        val now = System.currentTimeMillis()
        database.upsertSession(session.copy(deleted = false, updatedAt = now))
        database.getIntervalsForSession(sessionId, includeDeleted = true).forEach { database.upsertInterval(it.copy(deleted = false, updatedAt = now)) }
    }

    suspend fun migrateGuestDataToUser(uid: String): Boolean = write {
        if (!database.ownerHasData("guest") || database.ownerHasData(uid)) return@write false
        database.migrateGuestDataToUser(uid)
        true
    }

    suspend fun deleteLocalAccountData(ownerId: String) = write { database.deleteAllForOwner(ownerId) }

    fun notifyDataChanged(triggerSync: Boolean = false) {
        revision.value += 1L
        if (triggerSync) onDataChanged?.invoke()
    }

    internal fun rawDatabase(): TempoDatabase = database

    private fun finishInternal(ownerId: String, note: String? = null, outcome: SessionOutcome? = null, quality: Int? = null, now: Long): Session? {
        val session = database.getActiveSession(ownerId) ?: return null
        database.getIntervalsForSession(session.id).lastOrNull { it.endedAt == null }?.let { open ->
            database.upsertInterval(open.copy(endedAt = now.coerceAtLeast(open.startedAt), updatedAt = now))
        }
        val finished = session.copy(note = note?.trim() ?: session.note, outcome = outcome ?: session.outcome, quality = quality?.coerceIn(1, 5) ?: session.quality, state = SessionState.COMPLETED, endedAt = now.coerceAtLeast(session.startedAt), updatedAt = now)
        database.upsertSession(finished)
        return finished
    }

    private fun softDeleteSessionInternal(session: Session, now: Long) {
        database.upsertSession(session.copy(deleted = true, updatedAt = now))
        database.getIntervalsForSession(session.id, includeDeleted = true).forEach { database.upsertInterval(it.copy(deleted = true, updatedAt = now)) }
    }

    private suspend fun <T> write(block: suspend () -> T): T {
        val result = withContext(ioDispatcher) { block() }
        revision.value += 1L
        onDataChanged?.invoke()
        return result
    }
}
