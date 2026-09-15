package com.nikibonev.tempo.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.nikibonev.tempo.data.model.IntervalType
import com.nikibonev.tempo.data.model.Project
import com.nikibonev.tempo.data.model.Session
import com.nikibonev.tempo.data.model.SessionOutcome
import com.nikibonev.tempo.data.model.SessionState
import com.nikibonev.tempo.data.model.TimeInterval

class TempoDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(false)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE projects (
                id TEXT PRIMARY KEY NOT NULL,
                owner_id TEXT NOT NULL,
                name TEXT NOT NULL,
                color_argb INTEGER NOT NULL,
                icon TEXT NOT NULL,
                parent_project_id TEXT,
                weekly_goal_minutes INTEGER NOT NULL DEFAULT 0,
                goal_minutes INTEGER NOT NULL DEFAULT 0,
                goal_start_at INTEGER,
                goal_end_at INTEGER,
                archived INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_projects_owner ON projects(owner_id, deleted, archived)")
        db.execSQL("CREATE INDEX idx_projects_parent ON projects(owner_id, parent_project_id, deleted)")

        db.execSQL(
            """
            CREATE TABLE sessions (
                id TEXT PRIMARY KEY NOT NULL,
                owner_id TEXT NOT NULL,
                project_id TEXT NOT NULL,
                intention TEXT NOT NULL DEFAULT '',
                note TEXT NOT NULL DEFAULT '',
                planned_minutes INTEGER,
                outcome TEXT NOT NULL DEFAULT 'NONE',
                quality INTEGER,
                state TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_sessions_owner_started ON sessions(owner_id, deleted, started_at DESC)")
        db.execSQL("CREATE INDEX idx_sessions_project ON sessions(project_id, deleted, started_at DESC)")
        db.execSQL("CREATE INDEX idx_sessions_active ON sessions(owner_id, state, deleted)")

        db.execSQL(
            """
            CREATE TABLE intervals (
                id TEXT PRIMARY KEY NOT NULL,
                owner_id TEXT NOT NULL,
                session_id TEXT NOT NULL,
                type TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                updated_at INTEGER NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_intervals_session ON intervals(session_id, deleted, started_at)")
        db.execSQL("CREATE INDEX idx_intervals_owner ON intervals(owner_id, deleted, updated_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE projects ADD COLUMN parent_project_id TEXT")
            db.execSQL("ALTER TABLE projects ADD COLUMN goal_minutes INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE projects ADD COLUMN goal_start_at INTEGER")
            db.execSQL("ALTER TABLE projects ADD COLUMN goal_end_at INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_projects_parent ON projects(owner_id, parent_project_id, deleted)")
        }
    }

    fun getProjects(ownerId: String, includeArchived: Boolean = true, includeDeleted: Boolean = false): List<Project> =
        readableDatabase.query(
            "projects",
            null,
            buildString {
                append("owner_id = ?")
                if (!includeDeleted) append(" AND deleted = 0")
                if (!includeArchived) append(" AND archived = 0")
            },
            arrayOf(ownerId),
            null,
            null,
            "archived ASC, parent_project_id ASC, name COLLATE NOCASE ASC",
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toProject()) } }

    fun getProject(id: String): Project? =
        readableDatabase.query("projects", null, "id = ?", arrayOf(id), null, null, null, "1")
            .use { cursor -> if (cursor.moveToFirst()) cursor.toProject() else null }

    fun getProjectForOwner(ownerId: String, id: String): Project? =
        readableDatabase.query("projects", null, "owner_id = ? AND id = ?", arrayOf(ownerId, id), null, null, null, "1")
            .use { cursor -> if (cursor.moveToFirst()) cursor.toProject() else null }

    fun upsertProject(project: Project) {
        writableDatabase.insertWithOnConflict("projects", null, project.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getSessions(ownerId: String, includeDeleted: Boolean = false): List<Session> =
        readableDatabase.query(
            "sessions",
            null,
            "owner_id = ?" + if (includeDeleted) "" else " AND deleted = 0",
            arrayOf(ownerId),
            null,
            null,
            "started_at DESC",
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toSession()) } }

    fun getSession(id: String): Session? =
        readableDatabase.query("sessions", null, "id = ?", arrayOf(id), null, null, null, "1")
            .use { cursor -> if (cursor.moveToFirst()) cursor.toSession() else null }

    fun upsertSession(session: Session) {
        writableDatabase.insertWithOnConflict("sessions", null, session.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getIntervalsForSession(sessionId: String, includeDeleted: Boolean = false): List<TimeInterval> =
        readableDatabase.query(
            "intervals",
            null,
            "session_id = ?" + if (includeDeleted) "" else " AND deleted = 0",
            arrayOf(sessionId),
            null,
            null,
            "started_at ASC",
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toInterval()) } }

    fun getIntervals(ownerId: String, includeDeleted: Boolean = false): List<TimeInterval> =
        readableDatabase.query(
            "intervals",
            null,
            "owner_id = ?" + if (includeDeleted) "" else " AND deleted = 0",
            arrayOf(ownerId),
            null,
            null,
            "started_at ASC",
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toInterval()) } }

    fun getInterval(id: String): TimeInterval? =
        readableDatabase.query("intervals", null, "id = ?", arrayOf(id), null, null, null, "1")
            .use { cursor -> if (cursor.moveToFirst()) cursor.toInterval() else null }

    fun upsertInterval(interval: TimeInterval) {
        writableDatabase.insertWithOnConflict("intervals", null, interval.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getActiveSession(ownerId: String): Session? = readableDatabase.query(
        "sessions", null, "owner_id = ? AND deleted = 0 AND state != ?", arrayOf(ownerId, SessionState.COMPLETED.name), null, null, "started_at DESC", "1",
    ).use { if (it.moveToFirst()) it.toSession() else null }

    fun migrateGuestDataToUser(uid: String) {
        writableDatabase.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            writableDatabase.execSQL("UPDATE projects SET owner_id = ?, updated_at = ? WHERE owner_id = 'guest'", arrayOf<Any>(uid, now))
            writableDatabase.execSQL("UPDATE sessions SET owner_id = ?, updated_at = ? WHERE owner_id = 'guest'", arrayOf<Any>(uid, now))
            writableDatabase.execSQL("UPDATE intervals SET owner_id = ?, updated_at = ? WHERE owner_id = 'guest'", arrayOf<Any>(uid, now))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    fun deleteAllForOwner(ownerId: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("intervals", "owner_id = ?", arrayOf(ownerId))
            writableDatabase.delete("sessions", "owner_id = ?", arrayOf(ownerId))
            writableDatabase.delete("projects", "owner_id = ?", arrayOf(ownerId))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    fun ownerHasData(ownerId: String): Boolean =
        readableDatabase.rawQuery(
            "SELECT EXISTS(SELECT 1 FROM projects WHERE owner_id = ? AND deleted = 0 LIMIT 1) OR EXISTS(SELECT 1 FROM sessions WHERE owner_id = ? AND deleted = 0 LIMIT 1)",
            arrayOf(ownerId, ownerId),
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) == 1 }

    fun transaction(block: SQLiteDatabase.() -> Unit) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.block()
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    private fun Cursor.toProject() = Project(
        id = string("id"),
        ownerId = string("owner_id"),
        name = string("name"),
        colorArgb = long("color_argb"),
        icon = string("icon"),
        parentProjectId = nullableString("parent_project_id"),
        weeklyGoalMinutes = int("weekly_goal_minutes"),
        goalMinutes = int("goal_minutes"),
        goalStartAt = nullableLong("goal_start_at"),
        goalEndAt = nullableLong("goal_end_at"),
        archived = bool("archived"),
        createdAt = long("created_at"),
        updatedAt = long("updated_at"),
        deleted = bool("deleted"),
    )

    private fun Cursor.toSession() = Session(
        id = string("id"),
        ownerId = string("owner_id"),
        projectId = string("project_id"),
        intention = string("intention"),
        note = string("note"),
        plannedMinutes = nullableInt("planned_minutes"),
        outcome = SessionOutcome.entries.firstOrNull { it.name == string("outcome") } ?: SessionOutcome.NONE,
        quality = nullableInt("quality"),
        state = SessionState.entries.firstOrNull { it.name == string("state") } ?: SessionState.COMPLETED,
        startedAt = long("started_at"),
        endedAt = nullableLong("ended_at"),
        createdAt = long("created_at"),
        updatedAt = long("updated_at"),
        deleted = bool("deleted"),
    )

    private fun Cursor.toInterval() = TimeInterval(
        id = string("id"),
        ownerId = string("owner_id"),
        sessionId = string("session_id"),
        type = IntervalType.entries.firstOrNull { it.name == string("type") } ?: IntervalType.WORK,
        startedAt = long("started_at"),
        endedAt = nullableLong("ended_at"),
        updatedAt = long("updated_at"),
        deleted = bool("deleted"),
    )

    private fun Project.toValues() = ContentValues().apply {
        put("id", id); put("owner_id", ownerId); put("name", name); put("color_argb", colorArgb); put("icon", icon)
        if (parentProjectId == null) putNull("parent_project_id") else put("parent_project_id", parentProjectId)
        put("weekly_goal_minutes", weeklyGoalMinutes); put("goal_minutes", goalMinutes)
        if (goalStartAt == null) putNull("goal_start_at") else put("goal_start_at", goalStartAt)
        if (goalEndAt == null) putNull("goal_end_at") else put("goal_end_at", goalEndAt)
        put("archived", archived.asDb()); put("created_at", createdAt); put("updated_at", updatedAt); put("deleted", deleted.asDb())
    }

    private fun Session.toValues() = ContentValues().apply {
        put("id", id); put("owner_id", ownerId); put("project_id", projectId); put("intention", intention); put("note", note)
        if (plannedMinutes == null) putNull("planned_minutes") else put("planned_minutes", plannedMinutes)
        put("outcome", outcome.name)
        if (quality == null) putNull("quality") else put("quality", quality)
        put("state", state.name); put("started_at", startedAt)
        if (endedAt == null) putNull("ended_at") else put("ended_at", endedAt)
        put("created_at", createdAt); put("updated_at", updatedAt); put("deleted", deleted.asDb())
    }

    private fun TimeInterval.toValues() = ContentValues().apply {
        put("id", id); put("owner_id", ownerId); put("session_id", sessionId); put("type", type.name); put("started_at", startedAt)
        if (endedAt == null) putNull("ended_at") else put("ended_at", endedAt)
        put("updated_at", updatedAt); put("deleted", deleted.asDb())
    }

    private fun Cursor.string(name: String) = getString(getColumnIndexOrThrow(name))
    private fun Cursor.nullableString(name: String): String? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.long(name: String) = getLong(getColumnIndexOrThrow(name))
    private fun Cursor.int(name: String) = getInt(getColumnIndexOrThrow(name))
    private fun Cursor.bool(name: String) = int(name) != 0
    private fun Cursor.nullableLong(name: String): Long? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getLong(it) }
    private fun Cursor.nullableInt(name: String): Int? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getInt(it) }
    private fun Boolean.asDb() = if (this) 1 else 0

    companion object {
        private const val DB_NAME = "tempo.db"
        private const val DB_VERSION = 2
    }
}
