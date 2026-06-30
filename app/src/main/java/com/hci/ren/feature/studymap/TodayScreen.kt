package com.hci.ren.feature.studymap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hci.ren.R
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.StudyTaskType
import com.hci.ren.feature.plangeneration.likelyStudyMinutes

@Composable
fun TodayScreen(
    project: StudyProject,
    session: TodaySessionState?,
    onAvailableTimeChanged: (date: String, minutes: Int?) -> Unit,
    onTaskAction: (date: String, taskId: String, action: TodaySessionTaskAction) -> Unit,
    onWrapUpToday: (date: String) -> Unit,
    modifier: Modifier = Modifier,
    wrapUpResultMessage: String? = null,
    onConsumeWrapUpResult: () -> Unit = {},
    changeMessage: String? = null,
    onConsumeMessage: () -> Unit = {},
) {
    val data = buildStudyMapData(
        plan = project.plan,
        preferences = project.preferences,
        dailyMinutesOverride = project.dailyMinutesOverride,
        dailyAvailableMinutesByDate = project.dailyAvailableMinutesByDate,
        taskStateById = project.taskStateById,
    )
    val today = currentStudyCalendar(project.preferences).toStudyDate()
    val baseAvailableMinutes = todayBaseAvailableMinutes(project, data, today)
    val isTodayClosed = project.dailyAvailableMinutesByDate[today] == 0
    val todaySession = session?.takeIf { it.date == today }
    val availableMinutes = todaySession
        ?.availableMinutes
        ?: baseAvailableMinutes
    val hasAvailabilityOverride = todaySession?.availableMinutes != null && availableMinutes != baseAvailableMinutes
    val todayPlan = TodaySessionPlanner().plan(
        data = data,
        date = today,
        availableMinutes = availableMinutes,
        session = todaySession,
        hasAvailabilityOverride = hasAvailabilityOverride,
    )
    var showWrapUpDialog by remember(today, todayPlan) { mutableStateOf(false) }
    var visibleWrapUpResult by rememberSaveable(today) { mutableStateOf<String?>(null) }
    var visibleNotice by rememberSaveable(today) { mutableStateOf<String?>(null) }
    val emptyState = todayPlan.emptyState(data, isTodayClosed)
    val canWrapUpToday = todayPlan.canWrapUpToday(isTodayClosed)
    val impactPreviewService = remember { TodayImpactPreviewService() }
    val impactPreview = if (canWrapUpToday) {
        impactPreviewService.preview(project, today, todaySession)
    } else {
        null
    }
    val wrapUpButtonText = when {
        canWrapUpToday -> stringResource(R.string.wrap_up_today)
        isTodayClosed -> stringResource(R.string.today_wrapped_up_button)
        else -> stringResource(R.string.nothing_to_wrap_up)
    }
    fun updateAvailableMinutes(minutes: Int) {
        val normalized = minutes.coerceIn(0, MaxTodaySessionMinutes)
        onAvailableTimeChanged(today, normalized.takeUnless { it == baseAvailableMinutes })
    }

    LaunchedEffect(wrapUpResultMessage) {
        if (wrapUpResultMessage != null) {
            visibleWrapUpResult = wrapUpResultMessage
            onConsumeWrapUpResult()
        }
    }

    LaunchedEffect(changeMessage) {
        if (changeMessage != null) {
            visibleNotice = changeMessage
            onConsumeMessage()
        }
    }

    if (showWrapUpDialog) {
        WrapUpTodayDialog(
            todayPlan = todayPlan,
            impactPreview = impactPreview,
            onDismiss = { showWrapUpDialog = false },
            onConfirm = {
                showWrapUpDialog = false
                onWrapUpToday(today)
            },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 22.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TodayBudgetCard(todayPlan, isTodayClosed)
        }
        if (isTodayClosed && visibleWrapUpResult != null) {
            item {
                TodayWrapUpResultCard(message = visibleWrapUpResult.orEmpty())
            }
        }
        if (visibleNotice != null) {
            item {
                TodayNoticeCard(message = visibleNotice.orEmpty())
            }
        }
        item {
            AvailableTimeCard(
                availableMinutes = todayPlan.availableMinutes,
                baseAvailableMinutes = todayPlan.baseAvailableMinutes,
                onDecrease = {
                    updateAvailableMinutes(todayPlan.availableMinutes - TimeAdjustmentStepMinutes)
                },
                onIncrease = {
                    updateAvailableMinutes(todayPlan.availableMinutes + TimeAdjustmentStepMinutes)
                },
                onReset = { onAvailableTimeChanged(today, null) },
            )
        }
        if (todayPlan.hasPendingChanges) {
            item {
                PendingTodayChangesCard(
                    message = todayPlan.replanFeedbackMessage(),
                    impactMessage = impactPreview?.message(),
                )
            }
        }
        item {
            Button(
                onClick = { showWrapUpDialog = true },
                enabled = canWrapUpToday,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(wrapUpButtonText)
            }
        }
        if (
            todayPlan.doTodayTasks.isEmpty() &&
            todayPlan.pulledInTasks.isEmpty() &&
            todayPlan.doneTodayTasks.isEmpty() &&
            todayPlan.wontFitTodayTasks.isEmpty() &&
            todayPlan.movedLaterTasks.isEmpty() &&
            todayPlan.removedFromPlanTasks.isEmpty() &&
            todayPlan.pullInCandidates.isEmpty()
        ) {
            item { EmptyTodayCard(emptyState) }
        } else {
            if (todayPlan.doTodayTasks.isNotEmpty()) {
                item {
                    TodaySectionTitle(stringResource(R.string.today_tasks))
                }
                items(todayPlan.doTodayTasks, key = { "today-${it.id}" }) { task ->
                    TodayTaskRow(
                        task = task,
                        actions = listOf(
                            TodayTaskActionSpec(
                                label = stringResource(R.string.mark_done),
                                onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.MarkDone) },
                            ),
                            TodayTaskActionSpec(
                                label = stringResource(R.string.move_later),
                                onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.MoveLater) },
                            ),
                            TodayTaskActionSpec(
                                label = stringResource(R.string.remove_from_plan),
                                onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.RemoveFromPlan) },
                            ),
                        ),
                    )
                }
            }
            if (todayPlan.pulledInTasks.isNotEmpty()) {
                item {
                    TodaySectionTitle(stringResource(R.string.pulled_in_today))
                }
                items(todayPlan.pulledInTasks, key = { "pulled-${it.id}" }) { task ->
                    TodayTaskRow(
                        task = task,
                        supportingText = stringResource(R.string.pulled_in_today_message),
                        actions = listOf(
                            TodayTaskActionSpec(
                                label = stringResource(R.string.mark_done),
                                onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.MarkDone) },
                            ),
                            TodayTaskActionSpec(
                                label = stringResource(R.string.undo),
                                onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.UndoPullIn) },
                            ),
                            TodayTaskActionSpec(
                                label = stringResource(R.string.remove_from_plan),
                                onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.RemoveFromPlan) },
                            ),
                        ),
                    )
                }
            }
            if (todayPlan.wontFitTodayTasks.isNotEmpty()) {
                item {
                    TodaySectionTitle(stringResource(R.string.wont_fit_today))
                }
                items(todayPlan.wontFitTodayTasks, key = { "wont-fit-${it.id}" }) { task ->
                    TodayTaskRow(
                        task = task,
                        supportingText = stringResource(R.string.move_later_at_wrap_up),
                        actions = listOf(
                            TodayTaskActionSpec(
                                label = stringResource(R.string.move_later),
                                onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.MoveLater) },
                            ),
                            TodayTaskActionSpec(
                                label = stringResource(R.string.remove_from_plan),
                                onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.RemoveFromPlan) },
                            ),
                        ),
                    )
                }
            }
            if (todayPlan.movedLaterTasks.isNotEmpty()) {
                item {
                    TodaySectionTitle(stringResource(R.string.moved_later))
                }
                items(todayPlan.movedLaterTasks, key = { "moved-${it.id}" }) { task ->
                    TodayTaskRow(
                        task = task,
                        supportingText = if (todayPlan.remainingMinutes > 0) {
                            stringResource(R.string.moved_later_can_restore_message)
                        } else {
                            stringResource(R.string.moved_later_message)
                        },
                        actions = listOf(
                            TodayTaskActionSpec(
                                label = stringResource(R.string.restore),
                                onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.RestoreMovedLater) },
                            ),
                            TodayTaskActionSpec(
                                label = stringResource(R.string.remove_from_plan),
                                onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.RemoveFromPlan) },
                            ),
                        ),
                    )
                }
            }
            if (todayPlan.pullInCandidates.isNotEmpty()) {
                item {
                    TodaySectionTitle(stringResource(R.string.pull_ahead_suggestions))
                }
                items(todayPlan.pullInCandidates, key = { "pull-${it.id}" }) { task ->
                    TodayTaskRow(
                        task = task,
                        supportingText = stringResource(R.string.pull_in_if_time_remains),
                        actions = listOf(
                            TodayTaskActionSpec(
                                label = stringResource(R.string.pull_in),
                                onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.PullIn) },
                            ),
                        ),
                    )
                }
            }
            if (todayPlan.removedFromPlanTasks.isNotEmpty()) {
                item {
                    TodaySectionTitle(stringResource(R.string.removed_from_plan))
                }
                items(todayPlan.removedFromPlanTasks, key = { "removed-${it.id}" }) { task ->
                    TodayTaskRow(
                        task = task,
                        supportingText = stringResource(R.string.removed_from_plan_message),
                        actions = listOf(
                            TodayTaskActionSpec(
                                label = stringResource(R.string.restore),
                                onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.RestoreRemoved) },
                            ),
                        ),
                    )
                }
            }
            if (todayPlan.doneTodayTasks.isNotEmpty()) {
                item {
                    TodaySectionTitle(stringResource(R.string.done_today))
                }
                items(todayPlan.doneTodayTasks, key = { "done-${it.id}" }) { task ->
                    val isTemporaryDone = task.id in todaySession?.doneTodayTaskIds.orEmpty()
                    val actions = if (isTemporaryDone) {
                        listOf(
                            TodayTaskActionSpec(
                                label = stringResource(R.string.undo),
                                onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.UndoDone) },
                            ),
                        )
                    } else {
                        emptyList()
                    }
                    TodayTaskRow(
                        task = task,
                        supportingText = if (isTemporaryDone) {
                            stringResource(R.string.done_today_message)
                        } else {
                            stringResource(R.string.already_done_today_message)
                        },
                        actions = actions,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayBudgetCard(
    todayPlan: TodaySessionPlan,
    isTodayClosed: Boolean,
) {
    val pressureMinutes = maxOf(todayPlan.overPlannedMinutes, todayPlan.overflowMinutes)
    val title = when {
        isTodayClosed -> stringResource(R.string.today_time_closed)
        pressureMinutes > 0 -> stringResource(
            R.string.today_time_overflow,
            formatTodayMinutes(pressureMinutes),
        )
        todayPlan.remainingMinutes > 0 -> stringResource(
            R.string.today_time_free,
            formatTodayMinutes(todayPlan.remainingMinutes),
        )
        else -> stringResource(R.string.today_time_exact)
    }
    val message = when {
        isTodayClosed -> stringResource(R.string.today_time_closed_message)
        pressureMinutes > 0 -> stringResource(R.string.today_time_overflow_message)
        todayPlan.remainingMinutes > 0 -> stringResource(R.string.today_time_free_message)
        else -> stringResource(R.string.today_time_exact_message)
    }
    val summary = when {
        isTodayClosed -> stringResource(
            R.string.today_budget_summary_closed,
            formatTodayMinutes(todayPlan.availableMinutes),
        )
        pressureMinutes > 0 -> stringResource(
            R.string.today_budget_summary_overflow,
            formatTodayMinutes(todayPlan.availableMinutes),
            formatTodayMinutes(todayPlan.plannedMinutes),
            formatTodayMinutes(pressureMinutes),
        )
        todayPlan.remainingMinutes > 0 -> stringResource(
            R.string.today_budget_summary_free,
            formatTodayMinutes(todayPlan.availableMinutes),
            formatTodayMinutes(todayPlan.plannedMinutes),
            formatTodayMinutes(todayPlan.remainingMinutes),
        )
        else -> stringResource(
            R.string.today_budget_summary_exact,
            formatTodayMinutes(todayPlan.availableMinutes),
            formatTodayMinutes(todayPlan.plannedMinutes),
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.today),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.today_time_budget_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.today_budget_method_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TodayBudgetMetric(
                        label = stringResource(R.string.available_metric_label),
                        value = formatTodayMinutes(todayPlan.availableMinutes),
                        modifier = Modifier.weight(1f),
                    )
                    TodayBudgetMetric(
                        label = stringResource(R.string.planned_metric_label),
                        value = formatTodayMinutes(todayPlan.plannedMinutes),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TodayBudgetMetric(
                        label = stringResource(R.string.completed),
                        value = formatTodayMinutes(todayPlan.completedMinutes),
                        modifier = Modifier.weight(1f),
                    )
                    TodayBudgetMetric(
                        label = if (pressureMinutes > 0) {
                            stringResource(R.string.overflow_metric_label)
                        } else {
                            stringResource(R.string.remaining_metric_label)
                        },
                        value = formatTodayMinutes(maxOf(todayPlan.remainingMinutes, pressureMinutes)),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TodayBudgetMetric(
                        label = stringResource(R.string.moving_later_metric_label),
                        value = formatTodayMinutes(todayPlan.overflowMinutes + todayPlan.movedLaterMinutes),
                        modifier = Modifier.weight(1f),
                    )
                    TodayBudgetMetric(
                        label = stringResource(R.string.removed_metric_label),
                        value = formatTodayMinutes(todayPlan.removedMinutes),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayBudgetMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AvailableTimeCard(
    availableMinutes: Int,
    baseAvailableMinutes: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onReset: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Timer, contentDescription = null)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.today_available_time),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.today_available_time_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(
                    onClick = onDecrease,
                    enabled = availableMinutes > 0,
                ) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.decrease_available_time))
                }
                Text(
                    text = formatTodayMinutes(availableMinutes),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(
                    onClick = onIncrease,
                    enabled = availableMinutes < MaxTodaySessionMinutes,
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.increase_available_time))
                }
            }
            if (availableMinutes != baseAvailableMinutes) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reset_to_plan_time))
                }
            }
        }
    }
}

@Composable
private fun PendingTodayChangesCard(
    message: String,
    impactMessage: String?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (impactMessage != null) {
                Text(
                    text = impactMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun TodaySessionPlan.replanFeedbackMessage(): String = when {
    hasAvailabilityOverride && overflowMinutes > 0 -> stringResource(R.string.today_replanned_overflow_message)
    hasAvailabilityOverride && remainingMinutes > 0 -> stringResource(R.string.today_replanned_extra_time_message)
    hasAvailabilityOverride -> stringResource(R.string.today_replanned_message)
    else -> stringResource(R.string.today_pending_changes_message)
}

@Composable
private fun TodayImpactPreview.message(): String = when (status) {
    TodayImpactStatus.Fits -> stringResource(R.string.today_impact_fits)
    TodayImpactStatus.WorkMovesForward -> stringResource(R.string.today_impact_moves_forward)
    TodayImpactStatus.Tight -> stringResource(R.string.today_impact_tight)
    TodayImpactStatus.DoesNotFit -> stringResource(R.string.today_impact_does_not_fit)
}

@Composable
private fun TodayWrapUpResultCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.today_wrap_up_result_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.wrap_up_source_material_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun TodayNoticeCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodaySectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun EmptyTodayCard(state: TodayEmptyState) {
    val title = when (state) {
        TodayEmptyState.Closed -> stringResource(R.string.today_closed_empty_title)
        TodayEmptyState.NoAvailableTime -> stringResource(R.string.today_no_available_time_title)
        TodayEmptyState.Complete -> stringResource(R.string.today_complete_empty_title)
        TodayEmptyState.NoScheduledTasks -> stringResource(R.string.no_tasks_today)
    }
    val message = when (state) {
        TodayEmptyState.Closed -> stringResource(R.string.today_closed_empty_message)
        TodayEmptyState.NoAvailableTime -> stringResource(R.string.today_no_available_time_message)
        TodayEmptyState.Complete -> stringResource(R.string.today_complete_empty_message)
        TodayEmptyState.NoScheduledTasks -> stringResource(R.string.no_tasks_today_message)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TodayTaskRow(
    task: GeneratedStudyBlock,
    supportingText: String? = null,
    actions: List<TodayTaskActionSpec> = emptyList(),
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatTodayMinutes(task.likelyStudyMinutes)} - ${taskTypeLabel(task.taskType)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (actions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.forEach { action ->
                        TextButton(onClick = action.onClick) {
                            Text(action.label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WrapUpTodayDialog(
    todayPlan: TodaySessionPlan,
    impactPreview: TodayImpactPreview?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val summaryItems = listOf(
        WrapUpSummaryItem(
            label = stringResource(R.string.completed),
            count = todayPlan.doneTodayTasks.size,
            minutes = todayPlan.completedMinutes,
        ),
        WrapUpSummaryItem(
            label = stringResource(R.string.moving_forward_metric_label),
            count = todayPlan.unfinishedWorkForwardTasks.size,
            minutes = todayPlan.unfinishedWorkForwardMinutes,
        ),
        WrapUpSummaryItem(
            label = stringResource(R.string.removed_metric_label),
            count = todayPlan.removedFromPlanTasks.size,
            minutes = todayPlan.removedMinutes,
        ),
    ).filter { it.count > 0 || it.minutes > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wrap_up_today_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.wrap_up_today_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                WrapUpNoteRow(
                    label = stringResource(R.string.wrap_up_source_material_label),
                    text = stringResource(R.string.wrap_up_source_material_message),
                )
                impactPreview?.let { preview ->
                    WrapUpNoteRow(
                        label = stringResource(R.string.wrap_up_plan_preview_label),
                        text = preview.dialogMessage(),
                    )
                }
                if (summaryItems.isEmpty()) {
                    Text(
                        text = stringResource(R.string.wrap_up_today_no_task_changes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    summaryItems.forEach { item ->
                        WrapUpSummaryRow(
                            label = item.label,
                            count = item.count,
                            minutes = item.minutes,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.wrap_up_today_closes_day),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.wrap_up_today_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private data class WrapUpSummaryItem(
    val label: String,
    val count: Int,
    val minutes: Int,
)

@Composable
private fun WrapUpNoteRow(
    label: String,
    text: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WrapUpSummaryRow(
    label: String,
    count: Int,
    minutes: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.wrap_up_summary_value, count, formatTodayMinutes(minutes)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodayImpactPreview.dialogMessage(): String = when (status) {
    TodayImpactStatus.Fits -> stringResource(R.string.wrap_up_impact_fits)
    TodayImpactStatus.WorkMovesForward -> stringResource(R.string.wrap_up_impact_moves_forward)
    TodayImpactStatus.Tight -> stringResource(R.string.wrap_up_impact_tight)
    TodayImpactStatus.DoesNotFit -> stringResource(R.string.wrap_up_impact_does_not_fit)
}

private enum class TodayEmptyState {
    Closed,
    NoAvailableTime,
    Complete,
    NoScheduledTasks,
}

private fun TodaySessionPlan.emptyState(
    data: StudyMapData,
    isTodayClosed: Boolean,
): TodayEmptyState = when {
    isTodayClosed -> TodayEmptyState.Closed
    availableMinutes == 0 -> TodayEmptyState.NoAvailableTime
    data.activeTasks.isNotEmpty() && data.activeTasks.all { it.status == StudyTaskStatus.Completed } -> TodayEmptyState.Complete
    else -> TodayEmptyState.NoScheduledTasks
}

private data class TodayTaskActionSpec(
    val label: String,
    val onClick: () -> Unit,
)

@Composable
private fun taskTypeLabel(type: StudyTaskType): String = when (type) {
    StudyTaskType.Concept -> stringResource(R.string.task_type_concept)
    StudyTaskType.Practice -> stringResource(R.string.task_type_practice)
    StudyTaskType.Review -> stringResource(R.string.task_type_review)
    StudyTaskType.Quiz,
    StudyTaskType.MockTest,
    -> stringResource(R.string.task_type_mock_test)
    StudyTaskType.Memorization -> stringResource(R.string.task_type_memorization)
    StudyTaskType.Reading -> stringResource(R.string.task_type_reading)
    StudyTaskType.Summary -> stringResource(R.string.task_type_summary)
    StudyTaskType.MistakeReview -> stringResource(R.string.task_type_mistake_review)
    StudyTaskType.Custom -> stringResource(R.string.task_type_custom)
}

private fun formatTodayMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

private const val TimeAdjustmentStepMinutes = 15
