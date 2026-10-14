package com.raycoll.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.raycoll.app.R
import com.raycoll.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val WEEKS = 20
private const val DAYS_PER_WEEK = 7
private val CELL = 14.dp
private val GAP = 3.dp
private val CORNER = 3.dp
private val TODAY_BORDER = 1.5.dp

private fun heatmapStartMonday(today: LocalDate): LocalDate =
    today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks((WEEKS - 1).toLong())

internal fun heatmapDaysShown(today: LocalDate): Int =
    ChronoUnit.DAYS.between(heatmapStartMonday(today), today).toInt() + 1

/** Siatka aktywności: kolumny tygodni (Pn..Nd) do bieżącego tygodnia, dziś obramowane. */
@Composable
fun ActivityHeatmap(heatmap: Map<String, Int>, streak: Int, today: LocalDate) {
    val startMonday = heatmapStartMonday(today)
    val description =
        stringResource(R.string.stats_heatmap_cd, heatmap.values.sum(), heatmap.count { it.value > 0 }, streak)
    val active = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val todayBorder = MaterialTheme.colorScheme.onSurface

    val scrollState = rememberScrollState()
    // najnowszy tydzień jest po prawej — pokaż go od razu
    LaunchedEffect(scrollState.maxValue) { scrollState.scrollTo(scrollState.maxValue) }

    Box(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = Spacing.xs)
    ) {
        Canvas(
            Modifier
                .size(
                    width = CELL * WEEKS + GAP * (WEEKS - 1),
                    height =
                        CELL * DAYS_PER_WEEK + GAP * (DAYS_PER_WEEK - 1)
                )
                .semantics { contentDescription = description }
        ) {
            val cell = CELL.toPx()
            val step = cell + GAP.toPx()
            val corner = CornerRadius(CORNER.toPx())
            val stroke = TODAY_BORDER.toPx()
            for (week in 0 until WEEKS) {
                for (day in 0 until DAYS_PER_WEEK) {
                    val date = startMonday.plusDays((week * DAYS_PER_WEEK + day).toLong())
                    if (date.isAfter(today)) continue
                    val count = heatmap[date.toString()] ?: 0
                    val color = when {
                        count <= 0 -> empty
                        count <= 2 -> active.copy(alpha = 0.35f)
                        count <= 5 -> active.copy(alpha = 0.65f)
                        else -> active
                    }
                    val topLeft = Offset(week * step, day * step)
                    drawRoundRect(color, topLeft, Size(cell, cell), corner)
                    if (date == today) {
                        drawRoundRect(
                            color = todayBorder,
                            topLeft = topLeft + Offset(stroke / 2, stroke / 2),
                            size = Size(cell - stroke, cell - stroke),
                            cornerRadius = corner,
                            style = Stroke(stroke)
                        )
                    }
                }
            }
        }
    }
}
