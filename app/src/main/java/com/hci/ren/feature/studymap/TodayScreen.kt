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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hci.ren.R
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.StudyTaskType

@Composable
fun TodayScreen(
    project: StudyProject,
    session: TodaySessionState?,
    onAvailableTimeChanged: (date: String, minutes: Int?) -> Unit,
    onTaskAction: (date: String, taskId: String, action: TodaySessionTaskAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = buildStudyMapData(
        plan = project.plan,
        preferences = project.preferences,
        dailyMinutesOverride = project.dailyMinutesOverride,
    )
    val today = currentStudyCalendar(project.preferences).toStudyDate()
    val todaySchedule = data.schedule.days.firstOrNull { it.date == today }
    val baseAvailableMinutes = todaySchedule?.capacityMinutes ?: data.dailyMinutes
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
    fun updateAvailableMinutes(minutes: Int) {
        val normalized = minutes.coerceIn(0, MaxTodaySessionMinutes)
        onAvailableTimeChanged(today, normalized.takeUnless { it == baseAvailableMinutes })
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
            TodayHeader(
                plannedMinutes = todayPlan.plannedMinutes,
                completedMinutes = todayPlan.completedMinutes,
                availableMinutes = todayPlan.availableMinutes,
            )
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
                PendingTodayChangesCard()
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
            item { EmptyTodayCard() }
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
                        supportingText = stringResource(R.string.moved_later_message),
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
        }
    }
}

@Composable
private fun TodayHeader(
    plannedMinutes: Int,
    completedMinutes: Int,
    availableMinutes: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.today),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TodayMetric(
                label = stringResource(R.string.available_metric_label),
                value = formatTodayMinutes(availableMinutes),
                modifier = Modifier.weight(1f),
            )
            TodayMetric(
                label = stringResource(R.string.planned_metric_label),
                value = formatTodayMinutes(plannedMinutes),
                modifier = Modifier.weight(1f),
            )
            TodayMetric(
                label = stringResource(R.string.completed),
                value = formatTodayMinutes(completedMinutes),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TodayMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
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
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
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
private fun PendingTodayChangesCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = stringResource(R.string.today_pending_changes_message),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
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
private fun EmptyTodayCard() {
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
                text = stringResource(R.string.no_tasks_today),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.no_tasks_today_message),
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
                text = "${formatTodayMinutes(task.durationMinutes)} - ${taskTypeLabel(task.taskType)}",
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
