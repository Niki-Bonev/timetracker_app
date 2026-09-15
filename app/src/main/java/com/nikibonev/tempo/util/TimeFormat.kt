package com.nikibonev.tempo.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

fun formatDuration(millis: Long, compact: Boolean = false): String {
    val totalSeconds = (millis.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        compact && hours > 0 -> "${hours}h ${minutes}m"
        compact && minutes > 0 -> "${minutes}m ${seconds}s"
        compact -> "${seconds}s"
        hours > 0 -> "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
        else -> "%02d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}

fun formatDurationPrecise(millis: Long): String {
    val safe = millis.coerceAtLeast(0L)
    val totalSeconds = safe / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val ms = safe % 1000L
    return if (hours > 0) {
        "%d:%02d:%02d.%03d".format(Locale.ROOT, hours, minutes, seconds, ms)
    } else {
        "%02d:%02d.%03d".format(Locale.ROOT, minutes, seconds, ms)
    }
}

fun formatHoursDecimal(millis: Long): String = String.format(Locale.ROOT, "%.1fh", millis / 3_600_000.0)

fun formatClock(timestamp: Long, use24Hour: Boolean = true): String {
    val pattern = if (use24Hour) "HH:mm" else "h:mm a"
    return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern))
}

fun formatDate(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, d MMM"))

fun progressFraction(valueMillis: Long, targetMinutes: Int): Float {
    if (targetMinutes <= 0) return 0f
    return (valueMillis / (targetMinutes * 60_000.0)).toFloat().coerceIn(0f, 1f)
}

fun millisToRoundedMinutes(millis: Long): Int = (millis / 60_000.0).roundToInt()
