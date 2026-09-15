package com.nikibonev.tempo.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test fun `duration formats compactly with seconds`() {
        assertEquals("1h 30m", formatDuration(90 * 60_000L, compact = true))
        assertEquals("45m 0s", formatDuration(45 * 60_000L, compact = true))
        assertEquals("3m 12s", formatDuration(3 * 60_000L + 12_000L, compact = true))
        assertEquals("12s", formatDuration(12_000L, compact = true))
    }

    @Test fun `live duration includes milliseconds`() {
        assertEquals("03:12.345", formatDurationPrecise(3 * 60_000L + 12_345L))
        assertEquals("1:02:03.004", formatDurationPrecise(3_723_004L))
    }

    @Test fun `progress is clamped`() {
        assertEquals(1f, progressFraction(10 * 60 * 60_000L, 60))
        assertEquals(0f, progressFraction(60_000L, 0))
    }
}
