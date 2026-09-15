package com.nikibonev.tempo.data.repository

import com.nikibonev.tempo.data.model.IntervalType
import com.nikibonev.tempo.data.model.Project
import com.nikibonev.tempo.data.model.Session
import com.nikibonev.tempo.data.model.SessionDetails
import com.nikibonev.tempo.data.model.SessionState
import com.nikibonev.tempo.data.model.TimeInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AnalyticsEngineTest {
    private val zone = ZoneId.of("UTC")
    private val project = Project(ownerId = "u", name = "Build", colorArgb = 0xFF6557E8)

    @Test
    fun `work and breaks are counted independently`() {
        val start = ZonedDateTime.of(2026, 9, 15, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val session = Session(ownerId = "u", projectId = project.id, state = SessionState.COMPLETED, startedAt = start, endedAt = start + 90 * MINUTE)
        val details = SessionDetails(
            session,
            project,
            listOf(
                TimeInterval(ownerId = "u", sessionId = session.id, type = IntervalType.WORK, startedAt = start, endedAt = start + 50 * MINUTE),
                TimeInterval(ownerId = "u", sessionId = session.id, type = IntervalType.BREAK, startedAt = start + 50 * MINUTE, endedAt = start + 60 * MINUTE),
                TimeInterval(ownerId = "u", sessionId = session.id, type = IntervalType.WORK, startedAt = start + 60 * MINUTE, endedAt = start + 90 * MINUTE),
            ),
        )
        val result = AnalyticsEngine.calculate(listOf(project), listOf(details), start, start + 90 * MINUTE, zone, 1)
        assertEquals(80 * MINUTE, result.totalWorkMillis)
        assertEquals(10 * MINUTE, result.totalBreakMillis)
        assertEquals(50 * MINUTE, result.longestWorkBlockMillis)
        assertEquals(1, result.sessionCount)
    }

    @Test
    fun `work crossing midnight is split between days`() {
        val start = ZonedDateTime.of(2026, 9, 14, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val end = start + 60 * MINUTE
        val session = Session(ownerId = "u", projectId = project.id, state = SessionState.COMPLETED, startedAt = start, endedAt = end)
        val details = SessionDetails(session, project, listOf(TimeInterval(ownerId = "u", sessionId = session.id, type = IntervalType.WORK, startedAt = start, endedAt = end)))
        val result = AnalyticsEngine.calculate(listOf(project), listOf(details), start, end, zone, 2)
        assertEquals(60 * MINUTE, result.totalWorkMillis)
        assertEquals(30 * MINUTE, result.dailyTotals[0].workMillis)
        assertEquals(30 * MINUTE, result.dailyTotals[1].workMillis)
    }

    @Test
    fun `fast switch to another project is visible`() {
        val second = project.copy(id = "p2", name = "Study")
        val base = ZonedDateTime.of(2026, 9, 15, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        fun details(project: Project, start: Long, end: Long): SessionDetails {
            val session = Session(ownerId = "u", projectId = project.id, state = SessionState.COMPLETED, startedAt = start, endedAt = end)
            return SessionDetails(session, project, listOf(TimeInterval(ownerId = "u", sessionId = session.id, type = IntervalType.WORK, startedAt = start, endedAt = end)))
        }
        val first = details(project, base, base + 20 * MINUTE)
        val next = details(second, base + 25 * MINUTE, base + 45 * MINUTE)
        val result = AnalyticsEngine.calculate(listOf(project, second), listOf(first, next), base, base + 60 * MINUTE, zone, 1)
        assertEquals(1, result.contextSwitches)
        assertTrue(result.insights.any { it.contains("switch") })
    }

    private companion object { const val MINUTE = 60_000L }
}
