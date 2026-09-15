package com.nikibonev.tempo.data.repository

import android.content.Context
import com.nikibonev.tempo.data.local.TempoDatabase
import com.nikibonev.tempo.data.model.IntervalType
import com.nikibonev.tempo.data.model.Project
import com.nikibonev.tempo.data.model.Session
import com.nikibonev.tempo.data.model.SessionOutcome
import com.nikibonev.tempo.data.model.SessionState
import com.nikibonev.tempo.data.model.TimeInterval
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class BackupRepository(
    private val context: Context,
    private val database: TempoDatabase,
    private val timeRepository: TimeRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun exportJson(ownerId: String): File = withContext(ioDispatcher) {
        val root = JSONObject().apply {
            put("schemaVersion", 2)
            put("exportedAt", System.currentTimeMillis())
            put("projects", JSONArray(database.getProjects(ownerId, includeArchived = true, includeDeleted = false).map { it.toJson() }))
            put("sessions", JSONArray(database.getSessions(ownerId, includeDeleted = false).map { it.toJson() }))
            put("intervals", JSONArray(database.getIntervals(ownerId, includeDeleted = false).map { it.toJson() }))
        }
        exportFile("tempo-backup", "json").apply { writeText(root.toString(2)) }
    }

    suspend fun exportCsv(ownerId: String): File = withContext(ioDispatcher) {
        val projects = database.getProjects(ownerId, includeArchived = true).associateBy { it.id }
        val sessions = database.getSessions(ownerId).sortedBy { it.startedAt }
        val lines = mutableListOf("session_id,project,subproject,start,end,work_minutes,break_minutes,intention,note,outcome,quality")
        sessions.forEach { session ->
            val intervals = database.getIntervalsForSession(session.id)
            val workMinutes = intervals.filter { it.type == IntervalType.WORK }.sumOf { it.durationMillis(session.endedAt ?: System.currentTimeMillis()) } / 60_000.0
            val breakMinutes = intervals.filter { it.type == IntervalType.BREAK }.sumOf { it.durationMillis(session.endedAt ?: System.currentTimeMillis()) } / 60_000.0
            val project = projects[session.projectId]
            val parent = project?.parentProjectId?.let(projects::get)
            lines += listOf(
                session.id,
                parent?.name ?: project?.name.orEmpty(),
                if (parent != null) project.name else "",
                iso(session.startedAt),
                session.endedAt?.let(::iso).orEmpty(),
                "%.2f".format(java.util.Locale.ROOT, workMinutes),
                "%.2f".format(java.util.Locale.ROOT, breakMinutes),
                session.intention,
                session.note,
                session.outcome.name,
                session.quality?.toString().orEmpty(),
            ).joinToString(",") { csvEscape(it) }
        }
        exportFile("tempo-sessions", "csv").apply { writeText(lines.joinToString("\n")) }
    }

    suspend fun importJson(ownerId: String, input: InputStream): ImportSummary = withContext(ioDispatcher) {
        val text = input.bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val schema = root.optInt("schemaVersion", -1)
        require(schema in 1..2) { "This Tempo backup uses an unsupported schema version." }
        val projects = root.getJSONArray("projects").objects().map { it.toProject(ownerId) }
        val sessions = root.getJSONArray("sessions").objects().map { it.toSession(ownerId) }
        val intervals = root.getJSONArray("intervals").objects().map { it.toInterval(ownerId) }

        projects.forEach(database::upsertProject)
        sessions.forEach(database::upsertSession)
        intervals.forEach(database::upsertInterval)
        timeRepository.notifyDataChanged(triggerSync = true)
        ImportSummary(projects.size, sessions.size, intervals.size)
    }

    private fun exportFile(prefix: String, extension: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, "$prefix-${System.currentTimeMillis()}.$extension")
    }

    private fun Project.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("colorArgb", colorArgb); put("icon", icon)
        putNullable("parentProjectId", parentProjectId)
        put("weeklyGoalMinutes", weeklyGoalMinutes); put("goalMinutes", goalMinutes)
        putNullable("goalStartAt", goalStartAt); putNullable("goalEndAt", goalEndAt)
        put("archived", archived); put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    private fun Session.toJson() = JSONObject().apply {
        put("id", id); put("projectId", projectId); put("intention", intention); put("note", note)
        putNullable("plannedMinutes", plannedMinutes); put("outcome", outcome.name); putNullable("quality", quality)
        put("state", state.name); put("startedAt", startedAt); putNullable("endedAt", endedAt)
        put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    private fun TimeInterval.toJson() = JSONObject().apply {
        put("id", id); put("sessionId", sessionId); put("type", type.name); put("startedAt", startedAt)
        putNullable("endedAt", endedAt); put("updatedAt", updatedAt)
    }

    private fun JSONObject.toProject(ownerId: String) = Project(
        id = getString("id"),
        ownerId = ownerId,
        name = getString("name"),
        colorArgb = getLong("colorArgb"),
        icon = optString("icon", "●"),
        parentProjectId = nullableString("parentProjectId"),
        weeklyGoalMinutes = optInt("weeklyGoalMinutes", 0),
        goalMinutes = optInt("goalMinutes", 0),
        goalStartAt = nullableLong("goalStartAt"),
        goalEndAt = nullableLong("goalEndAt"),
        archived = optBoolean("archived", false),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis()),
    )

    private fun JSONObject.toSession(ownerId: String) = Session(
        id = getString("id"), ownerId = ownerId, projectId = getString("projectId"), intention = optString("intention", ""),
        note = optString("note", ""), plannedMinutes = nullableInt("plannedMinutes"),
        outcome = enumValueOrDefault(optString("outcome"), SessionOutcome.NONE), quality = nullableInt("quality"),
        state = enumValueOrDefault(optString("state"), SessionState.COMPLETED), startedAt = getLong("startedAt"),
        endedAt = nullableLong("endedAt"), createdAt = optLong("createdAt", getLong("startedAt")), updatedAt = optLong("updatedAt", System.currentTimeMillis()),
    )

    private fun JSONObject.toInterval(ownerId: String) = TimeInterval(
        id = getString("id"), ownerId = ownerId, sessionId = getString("sessionId"),
        type = enumValueOrDefault(optString("type"), IntervalType.WORK), startedAt = getLong("startedAt"),
        endedAt = nullableLong("endedAt"), updatedAt = optLong("updatedAt", System.currentTimeMillis()),
    )

    private fun JSONObject.putNullable(key: String, value: Any?) { if (value == null) put(key, JSONObject.NULL) else put(key, value) }
    private fun JSONObject.nullableLong(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
    private fun JSONObject.nullableInt(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)
    private fun JSONObject.nullableString(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, fallback: T): T = enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '\n' || it == '\r' || it == '"' }) "\"$escaped\"" else escaped
    }

    private fun iso(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

data class ImportSummary(val projects: Int, val sessions: Int, val intervals: Int)
