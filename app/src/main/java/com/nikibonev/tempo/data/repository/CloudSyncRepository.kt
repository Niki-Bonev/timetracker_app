package com.nikibonev.tempo.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.nikibonev.tempo.data.local.TempoDatabase
import com.nikibonev.tempo.data.model.IntervalType
import com.nikibonev.tempo.data.model.Project
import com.nikibonev.tempo.data.model.Session
import com.nikibonev.tempo.data.model.SessionOutcome
import com.nikibonev.tempo.data.model.SessionState
import com.nikibonev.tempo.data.model.TimeInterval
import com.nikibonev.tempo.util.awaitResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface SyncState {
    data object Unavailable : SyncState
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Success(val atMillis: Long) : SyncState
    data class Error(val message: String) : SyncState
}

class CloudSyncRepository(
    private val authRepository: AuthRepository,
    private val database: TempoDatabase,
    private val timeRepository: TimeRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val firestore: FirebaseFirestore? = authRepository.firebaseAppOrNull()?.let { FirebaseFirestore.getInstance(it) }
    private val _state = MutableStateFlow<SyncState>(if (firestore == null) SyncState.Unavailable else SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    suspend fun reconcile(uid: String): Result<Unit> = runSync {
        val db = requireFirestore()
        withContext(ioDispatcher) {
            reconcileProjects(db, uid)
            reconcileSessions(db, uid)
            reconcileIntervals(db, uid)
            resolveMultipleActiveSessions(uid)
        }
        timeRepository.notifyDataChanged(triggerSync = false)
    }

    suspend fun pushLocalChanges(uid: String): Result<Unit> = runSync {
        val db = requireFirestore()
        withContext(ioDispatcher) {
            pushDocuments(db, uid, PROJECTS, database.getProjects(uid, includeArchived = true, includeDeleted = true).map(Project::toCloudMap))
            pushDocuments(db, uid, SESSIONS, database.getSessions(uid, includeDeleted = true).map(Session::toCloudMap))
            pushDocuments(db, uid, INTERVALS, database.getIntervals(uid, includeDeleted = true).map(TimeInterval::toCloudMap))
        }
    }

    suspend fun deleteCloudData(uid: String): Result<Unit> = runSync {
        val db = requireFirestore()
        listOf(PROJECTS, SESSIONS, INTERVALS).forEach { collectionName ->
            val docs = db.collection(USERS).document(uid).collection(collectionName).get().awaitResult().documents
            docs.chunked(450).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().awaitResult()
            }
        }
        runCatching { db.collection(USERS).document(uid).delete().awaitResult() }
    }

    private suspend fun reconcileProjects(db: FirebaseFirestore, uid: String) {
        val local = database.getProjects(uid, includeArchived = true, includeDeleted = true).associateBy { it.id }
        val remote = db.collection(USERS).document(uid).collection(PROJECTS).get().awaitResult().documents
            .mapNotNull { it.toProject(uid) }.associateBy { it.id }
        val upload = mutableListOf<Map<String, Any?>>()
        (local.keys + remote.keys).forEach { id ->
            val l = local[id]
            val r = remote[id]
            when {
                l == null && r != null -> database.upsertProject(r)
                l != null && r == null -> upload += l.toCloudMap()
                l != null && r != null && r.updatedAt > l.updatedAt -> database.upsertProject(r)
                l != null && r != null && l.updatedAt > r.updatedAt -> upload += l.toCloudMap()
            }
        }
        pushDocuments(db, uid, PROJECTS, upload)
    }

    private suspend fun reconcileSessions(db: FirebaseFirestore, uid: String) {
        val local = database.getSessions(uid, includeDeleted = true).associateBy { it.id }
        val remote = db.collection(USERS).document(uid).collection(SESSIONS).get().awaitResult().documents
            .mapNotNull { it.toSession(uid) }.associateBy { it.id }
        val upload = mutableListOf<Map<String, Any?>>()
        (local.keys + remote.keys).forEach { id ->
            val l = local[id]
            val r = remote[id]
            when {
                l == null && r != null -> database.upsertSession(r)
                l != null && r == null -> upload += l.toCloudMap()
                l != null && r != null && r.updatedAt > l.updatedAt -> database.upsertSession(r)
                l != null && r != null && l.updatedAt > r.updatedAt -> upload += l.toCloudMap()
            }
        }
        pushDocuments(db, uid, SESSIONS, upload)
    }

    private suspend fun reconcileIntervals(db: FirebaseFirestore, uid: String) {
        val local = database.getIntervals(uid, includeDeleted = true).associateBy { it.id }
        val remote = db.collection(USERS).document(uid).collection(INTERVALS).get().awaitResult().documents
            .mapNotNull { it.toInterval(uid) }.associateBy { it.id }
        val upload = mutableListOf<Map<String, Any?>>()
        (local.keys + remote.keys).forEach { id ->
            val l = local[id]
            val r = remote[id]
            when {
                l == null && r != null -> database.upsertInterval(r)
                l != null && r == null -> upload += l.toCloudMap()
                l != null && r != null && r.updatedAt > l.updatedAt -> database.upsertInterval(r)
                l != null && r != null && l.updatedAt > r.updatedAt -> upload += l.toCloudMap()
            }
        }
        pushDocuments(db, uid, INTERVALS, upload)
    }

    private suspend fun pushDocuments(
        db: FirebaseFirestore,
        uid: String,
        collectionName: String,
        documents: List<Map<String, Any?>>,
    ) {
        if (documents.isEmpty()) return
        documents.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { data ->
                val id = data["id"] as String
                val ref = db.collection(USERS).document(uid).collection(collectionName).document(id)
                batch.set(ref, data, SetOptions.merge())
            }
            batch.commit().awaitResult()
        }
        db.collection(USERS).document(uid).set(
            mapOf("lastSyncedAt" to System.currentTimeMillis(), "schemaVersion" to 1),
            SetOptions.merge(),
        ).awaitResult()
    }

    private fun resolveMultipleActiveSessions(uid: String) {
        val active = database.getSessions(uid, includeDeleted = false)
            .filter { it.state != SessionState.COMPLETED }
            .sortedByDescending { it.updatedAt }
        if (active.size <= 1) return
        val keep = active.first()
        active.drop(1).forEach { old ->
            val safeEnd = minOf(keep.startedAt, System.currentTimeMillis()).coerceAtLeast(old.startedAt)
            database.getIntervalsForSession(old.id).lastOrNull { it.endedAt == null }?.let { interval ->
                database.upsertInterval(interval.copy(endedAt = safeEnd.coerceAtLeast(interval.startedAt), updatedAt = System.currentTimeMillis()))
            }
            database.upsertSession(old.copy(state = SessionState.COMPLETED, endedAt = safeEnd, updatedAt = System.currentTimeMillis()))
        }
    }

    private suspend fun runSync(block: suspend () -> Unit): Result<Unit> {
        if (firestore == null) {
            _state.value = SyncState.Unavailable
            return Result.failure(IllegalStateException("Cloud sync is not configured."))
        }
        _state.value = SyncState.Syncing
        val result = runCatching { block() }
        _state.value = result.fold(
            onSuccess = { SyncState.Success(System.currentTimeMillis()) },
            onFailure = { SyncState.Error(it.localizedMessage ?: "Sync failed") },
        )
        return result
    }

    private fun requireFirestore(): FirebaseFirestore = firestore ?: error("Cloud sync is not configured.")

    private fun Project.toCloudMap(): Map<String, Any?> = mapOf(
        "id" to id, "name" to name, "colorArgb" to colorArgb, "icon" to icon,
        "weeklyGoalMinutes" to weeklyGoalMinutes, "archived" to archived,
        "createdAt" to createdAt, "updatedAt" to updatedAt, "deleted" to deleted,
    )

    private fun Session.toCloudMap(): Map<String, Any?> = mapOf(
        "id" to id, "projectId" to projectId, "intention" to intention, "note" to note,
        "plannedMinutes" to plannedMinutes, "outcome" to outcome.name, "quality" to quality,
        "state" to state.name, "startedAt" to startedAt, "endedAt" to endedAt,
        "createdAt" to createdAt, "updatedAt" to updatedAt, "deleted" to deleted,
    )

    private fun TimeInterval.toCloudMap(): Map<String, Any?> = mapOf(
        "id" to id, "sessionId" to sessionId, "type" to type.name, "startedAt" to startedAt,
        "endedAt" to endedAt, "updatedAt" to updatedAt, "deleted" to deleted,
    )

    private fun DocumentSnapshot.toProject(ownerId: String): Project? = runCatching {
        Project(
            id = string("id") ?: id,
            ownerId = ownerId,
            name = string("name") ?: "Untitled project",
            colorArgb = long("colorArgb") ?: 0xFF8B7CFF,
            icon = string("icon") ?: "●",
            weeklyGoalMinutes = (long("weeklyGoalMinutes") ?: 0L).toInt(),
            archived = boolean("archived") ?: false,
            createdAt = long("createdAt") ?: 0L,
            updatedAt = long("updatedAt") ?: 0L,
            deleted = boolean("deleted") ?: false,
        )
    }.getOrNull()

    private fun DocumentSnapshot.toSession(ownerId: String): Session? = runCatching {
        Session(
            id = string("id") ?: id,
            ownerId = ownerId,
            projectId = requireNotNull(string("projectId")),
            intention = string("intention") ?: "",
            note = string("note") ?: "",
            plannedMinutes = long("plannedMinutes")?.toInt(),
            outcome = enumValueOrDefault(string("outcome"), SessionOutcome.NONE),
            quality = long("quality")?.toInt(),
            state = enumValueOrDefault(string("state"), SessionState.COMPLETED),
            startedAt = long("startedAt") ?: 0L,
            endedAt = long("endedAt"),
            createdAt = long("createdAt") ?: 0L,
            updatedAt = long("updatedAt") ?: 0L,
            deleted = boolean("deleted") ?: false,
        )
    }.getOrNull()

    private fun DocumentSnapshot.toInterval(ownerId: String): TimeInterval? = runCatching {
        TimeInterval(
            id = string("id") ?: id,
            ownerId = ownerId,
            sessionId = requireNotNull(string("sessionId")),
            type = enumValueOrDefault(string("type"), IntervalType.WORK),
            startedAt = long("startedAt") ?: 0L,
            endedAt = long("endedAt"),
            updatedAt = long("updatedAt") ?: 0L,
            deleted = boolean("deleted") ?: false,
        )
    }.getOrNull()

    private fun DocumentSnapshot.string(key: String): String? = getString(key)
    private fun DocumentSnapshot.long(key: String): Long? = getLong(key)
    private fun DocumentSnapshot.boolean(key: String): Boolean? = getBoolean(key)

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    companion object {
        private const val USERS = "users"
        private const val PROJECTS = "projects"
        private const val SESSIONS = "sessions"
        private const val INTERVALS = "intervals"
    }
}
