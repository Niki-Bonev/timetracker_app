package com.nikibonev.tempo.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test fun `duration formats compactly`() {
        assertEquals("1h 30m", formatDuration(90 * 60_000L, compact = true))
        assertEquals("45m", formatDuration(45 * 60_000L, compact = true))
    }

    @Test fun `progress is clamped`() {
        assertEquals(1f, progressFraction(10 * 60 * 60_000L, 60))
        assertEquals(0f, progressFraction(60_000L, 0))
    }
}
