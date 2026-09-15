package com.nikibonev.tempo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nikibonev.tempo.data.model.IntervalType
import com.nikibonev.tempo.data.model.TimeInterval

@Composable
fun SessionTimeline(
    intervals: List<TimeInterval>,
    projectColor: Color,
    now: Long,
    modifier: Modifier = Modifier,
) {
    val visible = intervals.filterNot { it.deleted }.filter { it.durationMillis(now) > 0 }
    if (visible.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        visible.forEach { interval ->
            val duration = interval.durationMillis(now).coerceAtLeast(1L)
            Box(
                Modifier
                    .weight(duration.toFloat().coerceAtMost(86_400_000f))
                    .height(8.dp)
                    .background(if (interval.type == IntervalType.WORK) projectColor else Color.Gray.copy(alpha = 0.35f)),
            )
        }
    }
}
