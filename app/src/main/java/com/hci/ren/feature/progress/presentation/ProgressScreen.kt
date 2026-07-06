package com.hci.ren.feature.progress.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hci.ren.R
import com.hci.ren.feature.progress.WeeklyFocusDay
import com.hci.ren.feature.progress.WeeklyFocusSummary
import com.hci.ren.feature.progress.buildWeeklyFocusSummary
import com.hci.ren.feature.studymap.StudyProject

@Composable
fun ProgressScreen(
    project: StudyProject,
    modifier: Modifier = Modifier,
) {
    val summary = remember(project) { buildWeeklyFocusSummary(project) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 18.dp,
            bottom = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "progress-header") {
            Text(
                text = stringResource(R.string.progress_focus_patterns),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item(key = "weekly-focus") {
            WeeklyFocusCard(summary = summary)
        }
    }
}

@Composable
private fun WeeklyFocusCard(
    summary: WeeklyFocusSummary,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.84f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            WeeklyFocusHeader(summary = summary)
            WeeklyFocusChart(summary = summary)
        }
    }
}

@Composable
private fun WeeklyFocusHeader(summary: WeeklyFocusSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FocusChartIcon()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.weekly_focus),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.focus_minutes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressMetric(
                value = focusDurationLabel(summary.totalFocusMinutes),
                label = stringResource(R.string.progress_this_week),
                modifier = Modifier.weight(1f),
            )
            ProgressMetric(
                value = summary.studyDays.toString(),
                label = stringResource(R.string.progress_study_days),
                modifier = Modifier.weight(1f),
            )
            GoalPill(goalMinutes = summary.goalMinutesPerDay)
        }
    }
}

@Composable
private fun FocusChartIcon() {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.BarChart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun ProgressMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun GoalPill(goalMinutes: Int) {
    Column(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.68f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.progress_goal),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.minutes_per_day, focusDurationLabel(goalMinutes)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

@Composable
private fun WeeklyFocusChart(summary: WeeklyFocusSummary) {
    val dayLabels = stringArrayResource(R.array.progress_weekday_short_labels)
    val chartDescription = stringResource(
        R.string.progress_weekly_chart_description,
        focusDurationLabel(summary.totalFocusMinutes),
        pluralStringResource(
            R.plurals.progress_study_day_count,
            summary.studyDays,
            summary.studyDays,
        ),
        focusDurationLabel(summary.goalMinutesPerDay),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = chartDescription },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChartAxisLabels(maxMinutes = summary.maxChartMinutes)
            WeeklyBars(
                days = summary.days,
                dayLabels = dayLabels,
                maxMinutes = summary.maxChartMinutes,
                goalMinutes = summary.goalMinutesPerDay,
                modifier = Modifier.weight(1f),
            )
        }
        if (summary.totalFocusMinutes == 0) {
            Text(
                text = stringResource(R.string.progress_weekly_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChartAxisLabels(maxMinutes: Int) {
    val axisValues = chartAxisValues(maxMinutes)
    BoxWithConstraints(
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight()
            .padding(
                top = ChartValueLabelSlot,
                bottom = ChartWeekdayLabelSlot,
            ),
    ) {
        val chartHeight = maxHeight
        axisValues.forEach { minutes ->
            Text(
                text = focusDurationLabel(minutes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        val ratio = if (maxMinutes == 0) 0f else minutes.toFloat() / maxMinutes.toFloat()
                        val y = chartHeight * (1f - ratio.coerceIn(0f, 1f))
                        translationY = (y - AxisLabelHalfHeight)
                            .coerceIn(0.dp, chartHeight - AxisLabelHeight)
                            .toPx()
                    },
            )
        }
    }
}

private fun chartAxisValues(maxMinutes: Int): List<Int> {
    val step = ChartAxisMinuteStep
    return generateSequence(maxMinutes) { value -> (value - step).takeIf { it > 0 } }
        .toList() + 0
}

@Composable
private fun WeeklyBars(
    days: List<WeeklyFocusDay>,
    dayLabels: Array<String>,
    maxMinutes: Int,
    goalMinutes: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxHeight()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val density = LocalDensity.current
            val labelSlot = ChartValueLabelSlot
            val barAreaHeight = maxHeight - labelSlot
            val goalRatio = if (maxMinutes == 0) 0f else goalMinutes.toFloat() / maxMinutes.toFloat()
            val goalOffset = labelSlot + barAreaHeight * (1f - goalRatio.coerceIn(0f, 1f))
            val axisColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)
            val goalColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
            val dash = with(density) { PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx())) }

            Canvas(modifier = Modifier.matchParentSize()) {
                val labelSlotPx = labelSlot.toPx()
                val chartHeight = size.height - labelSlotPx
                val goalY = labelSlotPx + chartHeight * (1f - goalRatio.coerceIn(0f, 1f))
                drawLine(
                    color = axisColor,
                    start = Offset(0f, labelSlotPx),
                    end = Offset(0f, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                drawLine(
                    color = axisColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                drawLine(
                    color = goalColor,
                    start = Offset(0f, goalY),
                    end = Offset(size.width, goalY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash,
                )
            }
            Text(
                text = stringResource(R.string.progress_goal_line, focusDurationLabel(goalMinutes)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        translationY = with(density) { (goalOffset - 18.dp).toPx() }
                    },
                maxLines = 1,
            )
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = 10.dp, end = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { day ->
                    FocusBar(
                        day = day,
                        maxMinutes = maxMinutes,
                        maxBarHeight = barAreaHeight,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartWeekdayLabelSlot)
                .padding(start = 10.dp, end = 10.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            dayLabels.take(days.size).forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FocusBar(
    day: WeeklyFocusDay,
    maxMinutes: Int,
    maxBarHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val barHeight = maxBarHeight * if (maxMinutes == 0) {
        0f
    } else {
        (day.focusMinutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
    }
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (day.focusMinutes > 0) {
            Text(
                text = focusDurationLabel(day.focusMinutes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Spacer(Modifier.height(5.dp))
        }
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(barHeight.coerceAtLeast(if (day.focusMinutes > 0) 8.dp else 0.dp))
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun focusDurationLabel(minutes: Int): String {
    val normalized = minutes.coerceAtLeast(0)
    val hours = normalized / 60
    val remainingMinutes = normalized % 60
    return when {
        hours > 0 && remainingMinutes == 0 -> stringResource(R.string.progress_hours, hours)
        hours > 0 -> stringResource(R.string.progress_hours_minutes, hours, remainingMinutes)
        else -> stringResource(R.string.progress_minutes, remainingMinutes)
    }
}

private val ChartValueLabelSlot = 28.dp
private val ChartWeekdayLabelSlot = 28.dp
private val AxisLabelHeight = 16.dp
private val AxisLabelHalfHeight = 8.dp
private const val ChartAxisMinuteStep = 120
