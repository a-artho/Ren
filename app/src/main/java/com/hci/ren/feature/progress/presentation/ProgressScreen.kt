package com.hci.ren.feature.progress.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hci.ren.feature.progress.BestRhythmBucket
import com.hci.ren.feature.progress.BestRhythmSummary
import com.hci.ren.feature.progress.StudyConsistencyDay
import com.hci.ren.feature.progress.StudyConsistencySummary
import com.hci.ren.R
import com.hci.ren.feature.progress.WeeklyFocusDay
import com.hci.ren.feature.progress.WeeklyFocusSummary
import com.hci.ren.feature.progress.ProgressChartAxis
import com.hci.ren.feature.progress.buildBestRhythmSummary
import com.hci.ren.feature.progress.buildStudyConsistencySummary
import com.hci.ren.feature.progress.buildWeeklyFocusSummary
import com.hci.ren.feature.studymap.StudyProject
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(
    project: StudyProject,
    modifier: Modifier = Modifier,
    today: String? = null,
) {
    val weeklySummary = remember(project, today) {
        if (today == null) buildWeeklyFocusSummary(project) else buildWeeklyFocusSummary(project, today)
    }
    val consistencySummary = remember(project, today) {
        if (today == null) buildStudyConsistencySummary(project) else buildStudyConsistencySummary(project, today)
    }
    val bestRhythmSummary = remember(project) {
        buildBestRhythmSummary(project)
    }

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
            WeeklyFocusCard(summary = weeklySummary)
        }
        item(key = "study-consistency") {
            StudyConsistencyCard(summary = consistencySummary)
        }
        item(key = "best-rhythm") {
            BestRhythmCard(summary = bestRhythmSummary)
        }
    }
}

@Composable
private fun WeeklyFocusCard(
    summary: WeeklyFocusSummary,
    modifier: Modifier = Modifier,
) {
    ProgressCard(modifier = modifier) {
        WeeklyFocusHeader(summary = summary)
        WeeklyFocusChart(summary = summary)
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
    val chartAxis = summary.chartAxis
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
            ChartAxisLabels(
                axis = chartAxis,
                topInset = ChartValueLabelSlot,
                bottomInset = ChartWeekdayLabelSlot,
                label = { minutes -> focusDurationLabel(minutes) },
            )
            WeeklyBars(
                days = summary.days,
                dayLabels = dayLabels,
                axis = chartAxis,
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
private fun ChartAxisLabels(
    axis: ProgressChartAxis,
    topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp,
    label: @Composable (Int) -> String,
) {
    val values = axis.ticks.asReversed()
    Layout(
        content = {
            values.forEach { value ->
                Text(
                    text = label(value),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        },
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val topInsetPx = topInset.roundToPx()
        val bottomInsetPx = bottomInset.roundToPx()
        val plotHeight = (height - topInsetPx - bottomInsetPx).coerceAtLeast(0)
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val tick = values[index]
                val tickCenterY = topInsetPx + (plotHeight * (1f - axis.ratio(tick))).roundToInt()
                placeable.placeRelative(
                    x = width - placeable.width,
                    y = tickCenterY - placeable.height / 2,
                )
            }
        }
    }
}

@Composable
private fun WeeklyBars(
    days: List<WeeklyFocusDay>,
    dayLabels: Array<String>,
    axis: ProgressChartAxis,
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
            val goalRatio = axis.ratio(goalMinutes)
            val axisColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)
            val goalColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.54f)
            val dash = with(density) { PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx())) }

            Canvas(modifier = Modifier.matchParentSize()) {
                val labelSlotPx = labelSlot.toPx()
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
            }
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
                        axis = axis,
                        maxBarHeight = barAreaHeight,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Canvas(modifier = Modifier.matchParentSize()) {
                val labelSlotPx = labelSlot.toPx()
                val chartHeight = size.height - labelSlotPx
                val goalY = labelSlotPx + chartHeight * (1f - goalRatio.coerceIn(0f, 1f))
                drawLine(
                    color = goalColor,
                    start = Offset(0f, goalY),
                    end = Offset(size.width, goalY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash,
                )
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
    axis: ProgressChartAxis,
    maxBarHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val barHeight = maxBarHeight * axis.ratio(day.focusMinutes)
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
private fun StudyConsistencyCard(
    summary: StudyConsistencySummary,
    modifier: Modifier = Modifier,
) {
    val mostConsistentLabel = summary.mostConsistentWeeksAgo?.let {
        consistencyWeekLabel(weeksAgo = it, sentenceCase = true)
    } ?: stringResource(R.string.progress_consistency_no_data)
    val streakDescription = pluralStringResource(
        R.plurals.progress_consistency_day_streak_description,
        summary.currentStreakDays,
        summary.currentStreakDays,
    )
    val activeDaysDescription = pluralStringResource(
        R.plurals.progress_study_day_count,
        summary.activeDays,
        summary.activeDays,
    )
    val weekCountDescription = pluralStringResource(
        R.plurals.progress_week_count,
        summary.weekCount,
        summary.weekCount,
    )
    val chartDescription = stringResource(
        R.string.progress_consistency_chart_description,
        streakDescription,
        mostConsistentLabel,
        activeDaysDescription,
        weekCountDescription,
    )
    ProgressCard(
        modifier = modifier,
        testTag = "study-consistency-card",
        contentDescription = chartDescription,
    ) {
        StudyConsistencyHeader()
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= 520.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StudyConsistencyStats(
                        summary = summary,
                        mostConsistentLabel = mostConsistentLabel,
                        modifier = Modifier.width(164.dp),
                    )
                    StudyConsistencyHeatmap(
                        summary = summary,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    StudyConsistencyStats(
                        summary = summary,
                        mostConsistentLabel = mostConsistentLabel,
                    )
                    StudyConsistencyHeatmap(summary = summary)
                }
            }
        }
    }
}

@Composable
private fun BestRhythmCard(
    summary: BestRhythmSummary,
    modifier: Modifier = Modifier,
) {
    val bestBucket = summary.bestBucket
    val chartDescription = if (bestBucket == null) {
        stringResource(R.string.progress_best_rhythm_empty)
    } else {
        stringResource(
            R.string.progress_best_rhythm_chart_description,
            focusDurationLabel(bestBucket.focusMinutes),
            bestBucket.cleanRatePercent,
        )
    }
    ProgressCard(
        modifier = modifier,
        testTag = "best-rhythm-card",
        contentDescription = chartDescription,
    ) {
        BestRhythmHeader(bestBucket = bestBucket)
        if (summary.hasData) {
            BestRhythmChart(summary = summary)
        } else {
            Text(
                text = stringResource(R.string.progress_best_rhythm_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BestRhythmHeader(bestBucket: BestRhythmBucket?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(23.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.progress_best_rhythm_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.progress_best_rhythm_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (bestBucket != null) {
            BestRhythmBadge(bestBucket = bestBucket)
        }
    }
}

@Composable
private fun BestRhythmBadge(bestBucket: BestRhythmBucket) {
    Column(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.44f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = focusDurationLabel(bestBucket.focusMinutes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = stringResource(R.string.progress_best_rhythm_your_best),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun BestRhythmChart(summary: BestRhythmSummary) {
    val buckets = remember(summary.buckets) {
        summary.buckets.sortedWith(
            compareByDescending<BestRhythmBucket> { it.cleanRate }
                .thenByDescending { it.cleanRounds }
                .thenByDescending { it.focusMinutes },
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            buckets.forEach { bucket ->
                RhythmBulletRow(
                    bucket = bucket,
                    isBest = bucket == summary.bestBucket,
                )
            }
        }
        Text(
            text = stringResource(R.string.progress_best_rhythm_based_on),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RhythmBulletRow(
    bucket: BestRhythmBucket,
    isBest: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeColor = if (isBest) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    }
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.width(70.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = focusDurationLabel(bucket.focusMinutes),
                style = MaterialTheme.typography.titleMedium,
                color = if (isBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isBest) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
            if (isBest) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(BestRhythmTrackHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(trackColor),
        ) {
            val targetFillWidth = maxWidth * bucket.cleanRate.coerceIn(0f, 1f)
            val visibleFillWidth = if (bucket.cleanRatePercent > 0) {
                targetFillWidth.coerceAtLeast(BestRhythmMinimumFillWidth)
            } else {
                0.dp
            }.coerceAtMost(maxWidth)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(visibleFillWidth)
                    .clip(RoundedCornerShape(999.dp))
                    .background(activeColor.copy(alpha = if (isBest) 0.82f else 0.46f)),
            )
        }
        Column(
            modifier = Modifier.width(66.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = stringResource(R.string.progress_percent, bucket.cleanRatePercent),
                style = MaterialTheme.typography.labelLarge,
                color = activeColor,
                fontWeight = if (isBest) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.progress_focus_round_count,
                    bucket.attemptedRounds,
                    bucket.attemptedRounds,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProgressCard(
    modifier: Modifier = Modifier,
    testTag: String? = null,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val taggedModifier = if (testTag == null) {
        modifier
    } else {
        modifier.testTag(testTag)
    }
    val semanticModifier = if (contentDescription == null) {
        taggedModifier
    } else {
        taggedModifier.semantics { this.contentDescription = contentDescription }
    }
    Surface(
        modifier = semanticModifier.fillMaxWidth(),
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
            content = content,
        )
    }
}

@Composable
private fun StudyConsistencyHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(23.dp),
            )
        }
        Text(
            text = stringResource(R.string.progress_consistency_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StudyConsistencyStats(
    summary: StudyConsistencySummary,
    mostConsistentLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(
                text = summary.currentStreakDays.toString(),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.progress_day_streak),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = if (summary.mostConsistentWeeksAgo == null) {
                    stringResource(R.string.progress_consistency_empty)
                } else {
                    stringResource(R.string.progress_most_consistent_period, mostConsistentLabel)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StudyConsistencyHeatmap(
    summary: StudyConsistencySummary,
    modifier: Modifier = Modifier,
) {
    val dayLabels = stringArrayResource(R.array.progress_weekday_short_labels)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val labelWidth = 86.dp
        val rowGap = 9.dp
        val cellGap = 7.dp
        val rawCellSize = (maxWidth - labelWidth - rowGap - (cellGap * 6)) / 7
        val cellSize = when {
            rawCellSize < 10.dp -> 10.dp
            rawCellSize > 34.dp -> 34.dp
            else -> rawCellSize
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(cellGap)) {
                    dayLabels.take(7).forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(cellSize),
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
                Spacer(Modifier.width(labelWidth))
            }
            summary.weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(rowGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(cellGap)) {
                        week.days.forEachIndexed { dayIndex, day ->
                            StudyConsistencyCell(
                                day = day,
                                modifier = Modifier
                                    .size(cellSize)
                                    .testTag("consistency-cell-${week.weeksAgo}-$dayIndex"),
                            )
                        }
                    }
                    Text(
                        text = consistencyWeekLabel(week.weeksAgo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(labelWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyConsistencyCell(
    day: StudyConsistencyDay,
    modifier: Modifier = Modifier,
) {
    val color = if (day.hasFocus) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.34f + (day.completionRatio * 0.54f))
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color),
    )
}

@Composable
private fun consistencyWeekLabel(
    weeksAgo: Int,
    sentenceCase: Boolean = false,
): String = when (weeksAgo) {
    0 -> stringResource(
        if (sentenceCase) {
            R.string.progress_consistency_this_week_sentence
        } else {
            R.string.progress_consistency_this_week
        },
    )
    1 -> stringResource(
        if (sentenceCase) {
            R.string.progress_consistency_last_week_sentence
        } else {
            R.string.progress_consistency_last_week
        },
    )
    else -> pluralStringResource(R.plurals.progress_consistency_weeks_ago, weeksAgo, weeksAgo)
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
private val BestRhythmTrackHeight = 10.dp
private val BestRhythmMinimumFillWidth = 2.dp
