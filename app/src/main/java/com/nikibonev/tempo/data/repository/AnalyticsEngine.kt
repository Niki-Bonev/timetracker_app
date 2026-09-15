package com.nikibonev.tempo.data.repository

import com.nikibonev.tempo.data.model.AnalyticsSnapshot
import com.nikibonev.tempo.data.model.DayTotal
import com.nikibonev.tempo.data.model.IntervalType
import com.nikibonev.tempo.data.model.Project
import com.nikibonev.tempo.data.model.ProjectTotal
import com.nikibonev.tempo.data.model.SessionDetails
import com.nikibonev.tempo.data.model.toLocalDate
import com.nikibonev.tempo.util.formatDuration
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object AnalyticsEngine {
    fun calculate(
        projects: List<Project>,
        sessions: List<SessionDetails>,
        fromMillis: Long,
        toMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        dailyWindowDays: Int = 14,
    ): AnalyticsSnapshot {
        val relevant = sessions.filter { details ->
            !details.session.deleted && details.intervals.any { !it.deleted && overlaps(it.startedAt, it.endedAt ?: toMillis, fromMillis, toMillis) }
        }
        val projectMap = projects.associateBy { it.id }
        val projectBuckets = linkedMapOf<String, Pair<Long, Long>>()
        val dayBuckets = linkedMapOf<LocalDate, Long>()
        val workBlocks = mutableListOf<Long>()
        var workTotal = 0L
        var breakTotal = 0L

        relevant.forEach { details ->
            var sessionWork = 0L
            var sessionBreak = 0L
            details.intervals.filterNot { it.deleted }.forEach intervalLoop@ { interval ->
                val intervalEnd = interval.endedAt ?: toMillis
                if (!overlaps(interval.startedAt, intervalEnd, fromMillis, toMillis)) return@intervalLoop
                val start = maxOf(interval.startedAt, fromMillis)
                val end = minOf(intervalEnd, toMillis)
                val duration = (end - start).coerceAtLeast(0L)
                when (interval.type) {
                    IntervalType.WORK -> {
                        sessionWork += duration
                        workBlocks += duration
                        splitAcrossDays(start, end, zoneId).forEach { (date, millis) -> dayBuckets[date] = (dayBuckets[date] ?: 0L) + millis }
                    }
                    IntervalType.BREAK -> sessionBreak += duration
                }
            }
            workTotal += sessionWork
            breakTotal += sessionBreak
            val existing = projectBuckets[details.session.projectId] ?: (0L to 0L)
            projectBuckets[details.session.projectId] = (existing.first + sessionWork) to (existing.second + sessionBreak)
        }

        val projectTotals = projectBuckets.mapNotNull { (projectId, totals) -> projectMap[projectId]?.let { ProjectTotal(it, totals.first, totals.second) } }.sortedByDescending { it.workMillis }
        val today = Instant.ofEpochMilli(toMillis).atZone(zoneId).toLocalDate()
        val days = (dailyWindowDays - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val dailyTotals = days.map { DayTotal(it, dayBuckets[it] ?: 0L) }
        val workedDatesAll = relevant.flatMap { details ->
            details.intervals.filter { !it.deleted && it.type == IntervalType.WORK }.flatMap { interval ->
                splitAcrossDays(maxOf(interval.startedAt, fromMillis), minOf(interval.endedAt ?: toMillis, toMillis), zoneId)
                    .filter { it.second > 0L }.map { it.first }
            }
        }.distinct().sorted()

        val streaks = streakStats(workedDatesAll, today)
        val contextSwitches = countContextSwitches(relevant, toMillis)
        val sessionWorks = relevant.map { details ->
            details.intervals.filter { !it.deleted && it.type == IntervalType.WORK }.sumOf { interval -> clippedDuration(interval.startedAt, interval.endedAt ?: toMillis, fromMillis, toMillis) }
        }.filter { it > 0L }
        val insights = buildInsights(projectTotals, dailyTotals, workBlocks, workTotal, breakTotal, relevant, contextSwitches)

        return AnalyticsSnapshot(
            totalWorkMillis = workTotal,
            totalBreakMillis = breakTotal,
            sessionCount = sessionWorks.size,
            averageSessionWorkMillis = if (sessionWorks.isEmpty()) 0L else sessionWorks.average().toLong(),
            longestWorkBlockMillis = workBlocks.maxOrNull() ?: 0L,
            projectTotals = projectTotals,
            dailyTotals = dailyTotals,
            contextSwitches = contextSwitches,
            currentStreakDays = streaks.first,
            bestStreakDays = streaks.second,
            insights = insights,
        )
    }

    fun weekStartMillis(now: Long = System.currentTimeMillis(), mondayFirst: Boolean = true, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val day = Instant.ofEpochMilli(now).atZone(zoneId)
        val target = if (mondayFirst) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
        return day.with(TemporalAdjusters.previousOrSame(target)).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun monthStartMillis(now: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(now).atZone(zoneId).withDayOfMonth(1).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()

    private fun buildInsights(projectTotals: List<ProjectTotal>, dailyTotals: List<DayTotal>, workBlocks: List<Long>, workTotal: Long, breakTotal: Long, relevant: List<SessionDetails>, contextSwitches: Int): List<String> {
        if (workTotal <= 0L) return listOf("Track a few sessions and Tempo will build your work rhythm here.")
        val result = mutableListOf<String>()
        projectTotals.firstOrNull()?.let { top ->
            val share = ((top.workMillis.toDouble() / workTotal) * 100).roundToInt().coerceIn(0, 100)
            result += "${top.project.name} is your biggest time investment at $share% of tracked work."
        }
        if (workBlocks.isNotEmpty()) {
            val median = workBlocks.sorted().let { it[it.size / 2] }
            val breakShare = if (workTotal + breakTotal == 0L) 0 else ((breakTotal.toDouble() / (workTotal + breakTotal)) * 100).roundToInt()
            result += "Your rhythm: a typical uninterrupted block is ${formatDuration(median, compact = true)}, with $breakShare% of tracked session time spent on breaks."
        }
        dailyTotals.maxByOrNull { it.workMillis }?.takeIf { it.workMillis > 0L }?.let { best -> result += "Your strongest recent day was ${best.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}: ${formatDuration(best.workMillis, compact = true)} tracked." }
        if (contextSwitches > 0) result += "Tempo spotted $contextSwitches quick project switch${if (contextSwitches == 1) "" else "es"} in this period. Those transitions are visible in your history instead of disappearing into the day."
        val planned = relevant.filter { (it.session.plannedMinutes ?: 0) > 0 }.mapNotNull { details ->
            val actual = details.workMillis(details.session.endedAt ?: System.currentTimeMillis())
            val plannedMillis = (details.session.plannedMinutes ?: return@mapNotNull null) * 60_000L
            if (plannedMillis <= 0L) null else abs(actual - plannedMillis).toDouble() / plannedMillis
        }
        if (planned.size >= 3) {
            val accuracy = (100 - planned.average() * 100).roundToInt().coerceIn(0, 100)
            result += "Your recent time estimates are about $accuracy% aligned with actual tracked work."
        }
        return result.take(4)
    }

    private fun countContextSwitches(sessions: List<SessionDetails>, now: Long): Int {
        val ordered = sessions.sortedBy { it.session.startedAt }
        var count = 0
        ordered.zipWithNext().forEach { (a, b) ->
            if (a.session.projectId == b.session.projectId) return@forEach
            val aEnd = a.session.endedAt ?: now
            val gap = b.session.startedAt - aEnd
            if (gap in 0..30 * 60_000L && a.session.startedAt.toLocalDate() == b.session.startedAt.toLocalDate()) count++
        }
        return count
    }

    private fun streakStats(workedDates: List<LocalDate>, today: LocalDate): Pair<Int, Int> {
        if (workedDates.isEmpty()) return 0 to 0
        val set = workedDates.toSet()
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        workedDates.forEach { date ->
            run = if (previous != null && ChronoUnit.DAYS.between(previous, date) == 1L) run + 1 else 1
            best = maxOf(best, run)
            previous = date
        }
        var current = 0
        var cursor = if (today in set) today else today.minusDays(1)
        while (cursor in set) { current++; cursor = cursor.minusDays(1) }
        return current to best
    }

    private fun splitAcrossDays(startMillis: Long, endMillis: Long, zoneId: ZoneId): List<Pair<LocalDate, Long>> {
        if (endMillis <= startMillis) return emptyList()
        val result = mutableListOf<Pair<LocalDate, Long>>()
        var cursor = Instant.ofEpochMilli(startMillis).atZone(zoneId)
        val end = Instant.ofEpochMilli(endMillis).atZone(zoneId)
        while (cursor.isBefore(end)) {
            val nextDay: ZonedDateTime = cursor.toLocalDate().plusDays(1).atStartOfDay(zoneId)
            val sliceEnd = if (nextDay.isBefore(end)) nextDay else end
            result += cursor.toLocalDate() to ChronoUnit.MILLIS.between(cursor, sliceEnd).coerceAtLeast(0L)
            cursor = sliceEnd
        }
        return result
    }

    private fun overlaps(start: Long, end: Long, from: Long, to: Long): Boolean = start < to && end > from
    private fun clippedDuration(start: Long, end: Long, from: Long, to: Long): Long = (minOf(end, to) - maxOf(start, from)).coerceAtLeast(0L)
}
