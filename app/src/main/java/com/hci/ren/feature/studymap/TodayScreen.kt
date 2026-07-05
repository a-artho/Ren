package com.hci.ren.feature.studymap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.hci.ren.R
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.StudySourceDocument
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.StudyTaskType
import com.hci.ren.feature.plangeneration.likelyStudyMinutes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TodayScreen(
    project: StudyProject,
    session: TodaySessionState?,
    onAvailableTimeChanged: (date: String, minutes: Int?) -> Unit,
    onTaskAction: (date: String, taskId: String, action: TodaySessionTaskAction) -> Unit,
    onWrapUpToday: (date: String) -> Unit,
    onStartFocusTask: (taskId: String) -> Unit = {},
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
    var showTimeBudgetDialog by remember(today) { mutableStateOf(false) }
    var pendingRemovalTask by remember(today) { mutableStateOf<GeneratedStudyBlock?>(null) }
    var showUseExtraTimeInfo by rememberSaveable(today) { mutableStateOf(false) }
    var showDoneTodayInfo by rememberSaveable(today) { mutableStateOf(false) }
    var isUseExtraTimeExpanded by rememberSaveable(today) { mutableStateOf(true) }
    var isDoneTodayExpanded by rememberSaveable(today) { mutableStateOf(true) }
    var visibleWrapUpResult by rememberSaveable(today) { mutableStateOf<String?>(null) }
    var visibleNotice by rememberSaveable(today) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
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
    val sourceDocuments = project.plan.sourceDocuments
    val upNextPlanTasks = todayPlan.doTodayTasks + todayPlan.pulledInTasks
    val upNextPlanIds = upNextPlanTasks.map { it.id }
    val pulledInTaskIds = todayPlan.pulledInTasks.mapTo(mutableSetOf()) { it.id }
    var isUpNextReorderMode by rememberSaveable(today) { mutableStateOf(false) }
    var upNextOrderIds by rememberSaveable(today) { mutableStateOf(upNextPlanIds) }
    val orderedUpNextIds = upNextOrderIds.ifEmpty { upNextPlanIds }
    val upNextTasksById = upNextPlanTasks.associateBy { it.id }
    val visibleUpNextTasks = orderedUpNextIds
        .mapNotNull { upNextTasksById[it] } +
        upNextPlanTasks.filterNot { it.id in orderedUpNextIds }
    val startHereTask = visibleUpNextTasks.firstOrNull().takeUnless { isUpNextReorderMode }
    val listedUpNextTasks = if (startHereTask == null) {
        visibleUpNextTasks
    } else {
        visibleUpNextTasks.drop(1)
    }
    fun updateAvailableMinutes(minutes: Int) {
        val normalized = minutes.coerceIn(0, MaxTodaySessionMinutes)
        onAvailableTimeChanged(today, normalized.takeUnless { it == baseAvailableMinutes })
    }
    fun moveUpNextTask(taskId: String, offset: Int): Boolean {
        val current = upNextOrderIds.ifEmpty { upNextPlanIds }
        val currentIndex = current.indexOf(taskId)
        if (currentIndex == -1) return false
        val targetIndex = (currentIndex + offset).coerceIn(current.indices)
        if (targetIndex == currentIndex) return false
        val updated = current.toMutableList()
        val moved = updated.removeAt(currentIndex)
        updated.add(targetIndex, moved)
        upNextOrderIds = updated
        return true
    }
    fun scrollToSection(index: Int) {
        coroutineScope.launch {
            listState.animateScrollToItem(index)
        }
    }

    LaunchedEffect(upNextPlanIds) {
        val retainedIds = upNextOrderIds.filter { it in upNextPlanIds }
        val addedIds = upNextPlanIds.filterNot { it in retainedIds }
        val mergedIds = retainedIds + addedIds
        if (mergedIds != upNextOrderIds) {
            upNextOrderIds = mergedIds
        }
        if (upNextPlanIds.size <= 1) {
            isUpNextReorderMode = false
        }
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
    if (showTimeBudgetDialog) {
        TimeBudgetDialog(
            availableMinutes = todayPlan.availableMinutes,
            baseAvailableMinutes = todayPlan.baseAvailableMinutes,
            onDecrease = {
                updateAvailableMinutes(todayPlan.availableMinutes - TimeAdjustmentStepMinutes)
            },
            onIncrease = {
                updateAvailableMinutes(todayPlan.availableMinutes + TimeAdjustmentStepMinutes)
            },
            onReset = { onAvailableTimeChanged(today, null) },
            onDismiss = { showTimeBudgetDialog = false },
        )
    }
    pendingRemovalTask?.let { task ->
        ConfirmRemoveFromTodayDialog(
            task = task,
            onDismiss = { pendingRemovalTask = null },
            onConfirm = {
                pendingRemovalTask = null
                onTaskAction(today, task.id, TodaySessionTaskAction.RemoveFromPlan)
            },
        )
    }
    if (showUseExtraTimeInfo) {
        UseExtraTimeInfoDialog(onDismiss = { showUseExtraTimeInfo = false })
    }
    if (showDoneTodayInfo) {
        DoneTodayInfoDialog(onDismiss = { showDoneTodayInfo = false })
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 10.dp,
            bottom = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        var nextItemIndex = 0
        item {
            TodayHeader(
                onTimeBudgetClick = { showTimeBudgetDialog = true },
            )
        }
        nextItemIndex += 1
        item {
            TodayBudgetCard(todayPlan, isTodayClosed)
        }
        nextItemIndex += 1
        if (startHereTask != null) {
            item {
                val isPulledIn = startHereTask.id in pulledInTaskIds
                TodayStartHereCard(
                    task = startHereTask,
                    sourceDocuments = sourceDocuments,
                    actions = upNextTaskActions(
                        today = today,
                        task = startHereTask,
                        isPulledIn = isPulledIn,
                        onTaskAction = onTaskAction,
                        includeMarkDone = false,
                        onRemoveFromPlan = { pendingRemovalTask = it },
                    ),
                    onStartFocus = { onStartFocusTask(startHereTask.id) },
                    onMarkDone = { onTaskAction(today, startHereTask.id, TodaySessionTaskAction.MarkDone) },
                )
            }
            nextItemIndex += 1
        }
        if (isTodayClosed && visibleWrapUpResult != null) {
            item {
                TodayWrapUpResultCard(message = visibleWrapUpResult.orEmpty())
            }
            nextItemIndex += 1
        }
        if (visibleNotice != null) {
            item {
                TodayNoticeCard(message = visibleNotice.orEmpty())
            }
            nextItemIndex += 1
        }
        item {
            if (todayPlan.hasPendingChanges || isTodayClosed) {
                TodayRebalancedRow(
                    title = if (isTodayClosed) {
                        stringResource(R.string.today_wrapped_up_button)
                    } else {
                        stringResource(R.string.today_rebalanced)
                    },
                    message = if (isTodayClosed) {
                        stringResource(R.string.today_time_closed_message)
                    } else {
                        impactPreview?.message() ?: todayPlan.replanFeedbackMessage()
                    },
                    actionText = wrapUpButtonText,
                    showAction = canWrapUpToday,
                    onActionClick = { showWrapUpDialog = true },
                )
            } else {
                TodayWrapUpButton(
                    text = wrapUpButtonText,
                    enabled = canWrapUpToday,
                    onClick = { showWrapUpDialog = true },
                )
            }
        }
        nextItemIndex += 1
        if (
            upNextPlanTasks.isEmpty() &&
            todayPlan.doneTodayTasks.isEmpty() &&
            todayPlan.wontFitTodayTasks.isEmpty() &&
            todayPlan.movedLaterTasks.isEmpty() &&
            todayPlan.removedFromPlanTasks.isEmpty() &&
            todayPlan.pullInCandidates.isEmpty()
        ) {
            item { EmptyTodayCard(emptyState) }
            nextItemIndex += 1
        } else {
            if (listedUpNextTasks.isNotEmpty()) {
                item {
                    TodaySectionHeader(
                        title = stringResource(R.string.up_next),
                        count = listedUpNextTasks.size,
                        trailingAction = if (visibleUpNextTasks.size > 1) {
                            TodaySectionAction(
                                label = if (isUpNextReorderMode) {
                                    stringResource(R.string.done_reordering)
                                } else {
                                    stringResource(R.string.reorder)
                                },
                                icon = Icons.Default.DragIndicator,
                                onClick = { isUpNextReorderMode = !isUpNextReorderMode },
                            )
                        } else {
                            null
                        },
                    )
                }
                nextItemIndex += 1
                itemsIndexed(listedUpNextTasks, key = { _, task -> "up-next-${task.id}" }) { index, task ->
                    val isPulledIn = task.id in pulledInTaskIds
                    val actions = upNextTaskActions(
                        today = today,
                        task = task,
                        isPulledIn = isPulledIn,
                        onTaskAction = onTaskAction,
                        onRemoveFromPlan = { pendingRemovalTask = it },
                    )
                    TodayTaskRow(
                        task = task,
                        sourceDocuments = sourceDocuments,
                        position = index + 1,
                        supportingText = if (isPulledIn) stringResource(R.string.pulled_in_today_message) else null,
                        actions = actions,
                        isReorderMode = isUpNextReorderMode,
                        canMoveUp = index > 0,
                        canMoveDown = index < visibleUpNextTasks.lastIndex,
                        onMoveUp = { moveUpNextTask(task.id, -1) },
                        onMoveDown = { moveUpNextTask(task.id, 1) },
                    )
                }
                nextItemIndex += listedUpNextTasks.size
            }
            if (todayPlan.wontFitTodayTasks.isNotEmpty()) {
                item {
                    TodaySectionHeader(
                        title = stringResource(R.string.wont_fit_today),
                        count = todayPlan.wontFitTodayTasks.size,
                    )
                }
                nextItemIndex += 1
                items(todayPlan.wontFitTodayTasks, key = { "wont-fit-${it.id}" }) { task ->
                    TodayTaskRow(
                        task = task,
                        sourceDocuments = sourceDocuments,
                        indicatorIcon = Icons.Default.WarningAmber,
                        indicatorContentDescription = stringResource(R.string.wont_fit_today),
                        supportingText = stringResource(R.string.move_later_at_wrap_up),
                        actions = listOf(
                            TodayTaskActionSpec(
                                label = stringResource(R.string.move_later),
                                onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.MoveLater) },
                            ),
                            TodayTaskActionSpec(
                                label = stringResource(R.string.remove_from_plan),
                                onClick = { pendingRemovalTask = task },
                            ),
                        ),
                    )
                }
                nextItemIndex += todayPlan.wontFitTodayTasks.size
            }
            if (todayPlan.movedLaterTasks.isNotEmpty()) {
                item {
                    TodaySectionHeader(
                        title = stringResource(R.string.moved_later),
                        count = todayPlan.movedLaterTasks.size,
                    )
                }
                nextItemIndex += 1
                items(todayPlan.movedLaterTasks, key = { "moved-${it.id}" }) { task ->
                    TodayTaskRow(
                        task = task,
                        sourceDocuments = sourceDocuments,
                        indicatorIcon = Icons.Default.Schedule,
                        indicatorContentDescription = stringResource(R.string.moved_later),
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
                                onClick = { pendingRemovalTask = task },
                            ),
                        ),
                    )
                }
                nextItemIndex += todayPlan.movedLaterTasks.size
            }
            if (todayPlan.pullInCandidates.isNotEmpty()) {
                val useExtraTimeHeaderIndex = nextItemIndex
                item {
                    TodaySectionHeader(
                        title = stringResource(R.string.pull_ahead_suggestions),
                        count = todayPlan.pullInCandidates.size,
                        trailingActions = listOf(
                            TodaySectionAction(
                                label = "",
                                icon = Icons.Default.Info,
                                onClick = { showUseExtraTimeInfo = true },
                                contentDescription = stringResource(R.string.use_extra_time_info),
                            ),
                            TodaySectionAction(
                                label = if (isUseExtraTimeExpanded) {
                                    stringResource(R.string.collapse_section)
                                } else {
                                    stringResource(R.string.expand_section)
                                },
                                icon = if (isUseExtraTimeExpanded) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                                onClick = {
                                    isUseExtraTimeExpanded = !isUseExtraTimeExpanded
                                    scrollToSection(useExtraTimeHeaderIndex)
                                },
                            ),
                        ),
                    )
                }
                nextItemIndex += 1
                if (isUseExtraTimeExpanded) {
                    itemsIndexed(todayPlan.pullInCandidates, key = { _, task -> "pull-${task.id}" }) { index, task ->
                        TodayTaskRow(
                            task = task,
                            sourceDocuments = sourceDocuments,
                            position = index + 1,
                            primaryAction = TodayTaskActionSpec(
                                label = stringResource(R.string.pull_in),
                                onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.PullIn) },
                            ),
                        )
                    }
                    nextItemIndex += todayPlan.pullInCandidates.size
                }
            }
            if (todayPlan.removedFromPlanTasks.isNotEmpty()) {
                item {
                    TodaySectionHeader(
                        title = stringResource(R.string.removed_from_plan),
                        count = todayPlan.removedFromPlanTasks.size,
                    )
                }
                nextItemIndex += 1
                items(todayPlan.removedFromPlanTasks, key = { "removed-${it.id}" }) { task ->
                    TodayTaskRow(
                        task = task,
                        sourceDocuments = sourceDocuments,
                        indicatorIcon = Icons.Default.Close,
                        indicatorContentDescription = stringResource(R.string.removed_from_plan),
                        supportingText = stringResource(R.string.removed_from_plan_message),
                        actions = listOf(
                            TodayTaskActionSpec(
                                label = stringResource(R.string.restore),
                                onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.RestoreRemoved) },
                            ),
                        ),
                    )
                }
                nextItemIndex += todayPlan.removedFromPlanTasks.size
            }
            if (todayPlan.doneTodayTasks.isNotEmpty()) {
                val doneTodayHeaderIndex = nextItemIndex
                item {
                    TodaySectionHeader(
                        title = stringResource(R.string.done_today),
                        count = todayPlan.doneTodayTasks.size,
                        trailingActions = listOf(
                            TodaySectionAction(
                                label = "",
                                icon = Icons.Default.Info,
                                onClick = { showDoneTodayInfo = true },
                                contentDescription = stringResource(R.string.done_today_info),
                            ),
                            TodaySectionAction(
                                label = if (isDoneTodayExpanded) {
                                    stringResource(R.string.collapse_section)
                                } else {
                                    stringResource(R.string.expand_section)
                                },
                                icon = if (isDoneTodayExpanded) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                                onClick = {
                                    isDoneTodayExpanded = !isDoneTodayExpanded
                                    scrollToSection(doneTodayHeaderIndex)
                                },
                            ),
                        ),
                    )
                }
                nextItemIndex += 1
                if (isDoneTodayExpanded) {
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
                            sourceDocuments = sourceDocuments,
                            indicatorIcon = Icons.Default.CheckCircle,
                            indicatorContentDescription = stringResource(R.string.done_today),
                            actions = actions,
                        )
                    }
                    nextItemIndex += todayPlan.doneTodayTasks.size
                }
            }
        }
    }
}

private val SparklesIcon: ImageVector = ImageVector.Builder(
    name = "Sparkles",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(10f, 4f)
        quadTo(10f, 13f, 19f, 13f)
        quadTo(10f, 13f, 10f, 22f)
        quadTo(10f, 13f, 1f, 13f)
        quadTo(10f, 13f, 10f, 4f)
        close()
    }
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(18f, 1f)
        quadTo(18f, 5f, 22f, 5f)
        quadTo(18f, 5f, 18f, 9f)
        quadTo(18f, 5f, 14f, 5f)
        quadTo(18f, 5f, 18f, 1f)
        close()
    }
}.build()

@Composable
private fun TodayHeader(
    onTimeBudgetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.adjusted_for_your_pace),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = SparklesIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Surface(
            onClick = onTimeBudgetClick,
            shape = RoundedCornerShape(50),
            color = Color.Black,
            contentColor = MaterialTheme.colorScheme.primary,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.58f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.time_budget),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
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
    val remainingOrOverLabel = if (pressureMinutes > 0) {
        stringResource(R.string.over_budget_metric_label)
    } else {
        stringResource(R.string.remaining_metric_label)
    }
    val remainingOrOverValue = if (pressureMinutes > 0) {
        formatMinutes(pressureMinutes)
    } else {
        formatMinutes(todayPlan.remainingMinutes)
    }
    val remainingOrOverColor = if (pressureMinutes > 0) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 18.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TodayBudgetMetric(
                    label = stringResource(R.string.available_metric_label),
                    value = formatMinutes(todayPlan.availableMinutes),
                    icon = Icons.Default.Timer,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider(
                    modifier = Modifier
                        .height(36.dp)
                        .padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                TodayBudgetMetric(
                    label = stringResource(R.string.planned_metric_label),
                    value = formatMinutes(todayPlan.plannedMinutes),
                    icon = Icons.Default.CalendarMonth,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider(
                    modifier = Modifier
                        .height(36.dp)
                        .padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                TodayBudgetMetric(
                    label = remainingOrOverLabel,
                    value = remainingOrOverValue,
                    icon = Icons.Default.WarningAmber,
                    iconTint = remainingOrOverColor,
                    valueColor = remainingOrOverColor,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TodayBudgetMetric(
                    label = stringResource(R.string.completed),
                    value = formatMinutes(todayPlan.completedMinutes),
                    icon = Icons.Default.CheckCircle,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider(
                    modifier = Modifier
                        .height(36.dp)
                        .padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                TodayBudgetMetric(
                    label = stringResource(R.string.moving_later_metric_label),
                    value = formatMinutes(todayPlan.overflowMinutes + todayPlan.movedLaterMinutes),
                    icon = Icons.Default.Schedule,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider(
                    modifier = Modifier
                        .height(36.dp)
                        .padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                TodayBudgetMetric(
                    label = stringResource(R.string.removed_metric_label),
                    value = formatMinutes(todayPlan.removedMinutes),
                    icon = Icons.Default.Close,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TodayBudgetMetric(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier = modifier.padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TimeBudgetDialog(
    availableMinutes: Int,
    baseAvailableMinutes: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hasTimeOverride = availableMinutes != baseAvailableMinutes

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.today_time_budget_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_time_budget))
                    }
                }
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
                        text = formatMinutes(availableMinutes),
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onReset,
                        enabled = hasTimeOverride,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reset_to_plan_time))
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.done_time_budget))
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayRebalancedRow(
    title: String,
    message: String,
    actionText: String,
    showAction: Boolean,
    onActionClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = SparklesIcon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showAction) {
                OutlinedButton(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(actionText)
                }
            }
        }
    }
}

@Composable
private fun TodayWrapUpButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.72f else 0.28f)),
    ) {
        Icon(
            imageVector = Icons.Default.Timer,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun TodaySectionHeader(
    title: String,
    count: Int,
    trailingAction: TodaySectionAction? = null,
    trailingActions: List<TodaySectionAction> = emptyList(),
) {
    val actions = listOfNotNull(trailingAction) + trailingActions
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
            contentColor = MaterialTheme.colorScheme.primary,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            actions.forEach { action ->
                if (action.label.isBlank()) {
                    IconButton(onClick = action.onClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.contentDescription,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    TextButton(onClick = action.onClick) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.contentDescription,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = action.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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
    TodayImpactStatus.Crammed -> stringResource(R.string.today_impact_crammed)
    TodayImpactStatus.Overloaded -> stringResource(R.string.today_impact_overloaded)
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
private fun TodayStartHereCard(
    task: GeneratedStudyBlock,
    sourceDocuments: List<StudySourceDocument>,
    actions: List<TodayTaskActionSpec>,
    onStartFocus: () -> Unit,
    onMarkDone: () -> Unit,
) {
    var menuExpanded by remember(task.id) { mutableStateOf(false) }
    val sourceText = todayTaskSourceText(task, sourceDocuments)
    val durationValue = if (task.likelyStudyMinutes < 60) {
        task.likelyStudyMinutes.toString()
    } else {
        formatMinutes(task.likelyStudyMinutes)
    }
    val durationUnit = if (task.likelyStudyMinutes < 60) "min" else null
    val startLabelShape = RoundedCornerShape(8.dp)
    val startLabelGlowElevation = with(LocalDensity.current) { 10.dp.toPx() }
    val startLabelGlowColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.58f)),
    ) {
        Column(
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.graphicsLayer {
                        shadowElevation = startLabelGlowElevation
                        shape = startLabelShape
                        clip = false
                        ambientShadowColor = startLabelGlowColor
                        spotShadowColor = startLabelGlowColor
                    },
                    shape = startLabelShape,
                    color = Color.Black,
                    contentColor = MaterialTheme.colorScheme.primary,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = SparklesIcon,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = stringResource(R.string.today_start_here),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(30.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.today_start_task_actions),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        actions.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.label) },
                                onClick = {
                                    menuExpanded = false
                                    action.onClick()
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TodayStartMetaRow(
                        icon = taskTypeIcon(task.taskType),
                        text = taskTypeLabel(task.taskType),
                    )
                    if (sourceText != null) {
                        TodayStartMetaRow(
                            icon = Icons.AutoMirrored.Filled.Assignment,
                            text = sourceText,
                        )
                    }
                }
                TodayDurationRing(
                    durationValue = durationValue,
                    durationUnit = durationUnit,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onStartFocus,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.start_focus),
                            color = Color.Black,
                        )
                    }
                }
                OutlinedButton(
                    onClick = onMarkDone,
                    modifier = Modifier.weight(0.72f),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mark_done))
                }
            }
        }
    }
}

@Composable
private fun TodayDurationRing(
    durationValue: String,
    durationUnit: String?,
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.size(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ringSegments = 8
            val gapDegrees = 8f
            val sweepDegrees = 360f / ringSegments - gapDegrees
            val glowStrokeWidth = 12.dp.toPx()
            val segmentStrokeWidth = 5.dp.toPx()
            val ringDiameter = size.minDimension - glowStrokeWidth
            val ringTopLeft = Offset(
                x = (size.width - ringDiameter) / 2f,
                y = (size.height - ringDiameter) / 2f,
            )
            val ringSize = Size(ringDiameter, ringDiameter)
            repeat(ringSegments) { segmentIndex ->
                val startAngle = -90f + segmentIndex * (360f / ringSegments) + gapDegrees / 2f
                drawArc(
                    color = primary.copy(alpha = 0.16f),
                    startAngle = startAngle,
                    sweepAngle = sweepDegrees,
                    useCenter = false,
                    topLeft = ringTopLeft,
                    size = ringSize,
                    style = Stroke(width = glowStrokeWidth, cap = StrokeCap.Round),
                )
                drawArc(
                    color = primary.copy(alpha = 0.92f),
                    startAngle = startAngle,
                    sweepAngle = sweepDegrees,
                    useCenter = false,
                    topLeft = ringTopLeft,
                    size = ringSize,
                    style = Stroke(width = segmentStrokeWidth, cap = StrokeCap.Round),
                )
            }
        }
        Surface(
            modifier = Modifier.size(70.dp),
            shape = CircleShape,
            color = Color.Black,
            contentColor = Color.White,
            border = BorderStroke(1.dp, primary.copy(alpha = 0.20f)),
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = durationValue,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
                if (durationUnit != null) {
                    Text(
                        text = durationUnit,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayStartMetaRow(
    icon: ImageVector,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TodayTaskRow(
    task: GeneratedStudyBlock,
    sourceDocuments: List<StudySourceDocument>,
    position: Int? = null,
    indicatorIcon: ImageVector? = null,
    indicatorContentDescription: String? = null,
    supportingText: String? = null,
    primaryAction: TodayTaskActionSpec? = null,
    actions: List<TodayTaskActionSpec> = emptyList(),
    isReorderMode: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Boolean = { false },
    onMoveDown: () -> Boolean = { false },
) {
    var menuExpanded by remember(task.id) { mutableStateOf(false) }
    var dragOffsetY by remember(task.id) { mutableStateOf(0f) }
    var isDragging by remember(task.id) { mutableStateOf(false) }
    val canMoveUpState = rememberUpdatedState(canMoveUp)
    val canMoveDownState = rememberUpdatedState(canMoveDown)
    val onMoveUpState = rememberUpdatedState(onMoveUp)
    val onMoveDownState = rememberUpdatedState(onMoveDown)
    val moveStepPx = with(LocalDensity.current) { 72.dp.toPx() }
    val moveStepPxState = rememberUpdatedState(moveStepPx)
    val sourceText = todayTaskSourceText(task, sourceDocuments)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = if (isDragging) 1.01f else 1f
                scaleY = if (isDragging) 1.01f else 1f
            }
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .zIndex(if (isDragging || dragOffsetY != 0f) 1f else 0f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDragging) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TodayTaskIndicator(
                position = position,
                icon = indicatorIcon,
                contentDescription = indicatorContentDescription,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatMinutes(task.likelyStudyMinutes)} · ${taskTypeLabel(task.taskType)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sourceText != null) {
                    Text(
                        text = sourceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isReorderMode) {
                ReorderTaskHandle(
                    isDragging = isDragging,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .pointerInput(task.id) {
                            detectDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = {
                                    isDragging = false
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    isDragging = false
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val stepPx = moveStepPxState.value
                                    dragOffsetY += dragAmount.y
                                    when {
                                        dragOffsetY <= -stepPx && canMoveUpState.value -> {
                                            if (onMoveUpState.value()) {
                                                dragOffsetY += stepPx
                                            } else {
                                                dragOffsetY = 0f
                                            }
                                        }
                                        dragOffsetY >= stepPx && canMoveDownState.value -> {
                                            if (onMoveDownState.value()) {
                                                dragOffsetY -= stepPx
                                            } else {
                                                dragOffsetY = 0f
                                            }
                                        }
                                        else -> {
                                            val minOffset = if (canMoveUpState.value) -stepPx else 0f
                                            val maxOffset = if (canMoveDownState.value) stepPx else 0f
                                            dragOffsetY = dragOffsetY.coerceIn(minOffset, maxOffset)
                                        }
                                    }
                                },
                            )
                        },
                )
            } else if (primaryAction != null) {
                TextButton(onClick = primaryAction.onClick) {
                    Text(primaryAction.label)
                }
            } else if (actions.isNotEmpty()) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.today_task_actions),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        actions.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.label) },
                                onClick = {
                                    menuExpanded = false
                                    action.onClick()
                                },
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun TodayTaskIndicator(
    position: Int?,
    icon: ImageVector?,
    contentDescription: String?,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f),
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                position != null -> Text(
                    text = position.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun ReorderTaskHandle(
    isDragging: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(40.dp)
            .alpha(if (isDragging) 1f else 0.72f),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = stringResource(R.string.drag_to_reorder_task),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun todayTaskSourceText(
    task: GeneratedStudyBlock,
    documents: List<StudySourceDocument>,
): String? {
    val documentLabel = taskSourceDocumentLabel(task, documents)
    val pageLabel = taskPageLabel(task)
    return when {
        documentLabel != null && pageLabel != null -> stringResource(
            R.string.today_task_source_label,
            documentLabel,
            pageLabel,
        )
        documentLabel != null -> documentLabel
        pageLabel != null -> pageLabel
        else -> null
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
    val closingRemainingMinutes = (todayPlan.availableMinutes - todayPlan.completedMinutes).coerceAtLeast(0)
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
                WrapUpNoteRow(
                    label = stringResource(R.string.wrap_up_time_after_closing_label),
                    text = stringResource(
                        R.string.wrap_up_today_closes_day,
                        formatMinutes(closingRemainingMinutes),
                    ),
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

@Composable
private fun ConfirmRemoveFromTodayDialog(
    task: GeneratedStudyBlock,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(R.string.remove_today_task_title)) },
        text = {
            Text(
                text = stringResource(R.string.remove_today_task_message, task.title),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.remove_today_task_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun UseExtraTimeInfoDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.use_extra_time_info_title)) },
        text = {
            Text(
                text = stringResource(R.string.use_extra_time_info_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        },
    )
}

@Composable
private fun DoneTodayInfoDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.done_today_info_title)) },
        text = {
            Text(
                text = stringResource(R.string.done_today_info_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
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
            text = stringResource(R.string.wrap_up_summary_value, count, formatMinutes(minutes)),
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
    TodayImpactStatus.Crammed -> stringResource(R.string.wrap_up_impact_crammed)
    TodayImpactStatus.Overloaded -> stringResource(R.string.wrap_up_impact_overloaded)
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

@Composable
private fun upNextTaskActions(
    today: String,
    task: GeneratedStudyBlock,
    isPulledIn: Boolean,
    onTaskAction: (date: String, taskId: String, action: TodaySessionTaskAction) -> Unit,
    includeMarkDone: Boolean = true,
    onRemoveFromPlan: (GeneratedStudyBlock) -> Unit,
): List<TodayTaskActionSpec> {
    val actions = mutableListOf<TodayTaskActionSpec>()
    if (includeMarkDone) {
        actions += TodayTaskActionSpec(
            label = stringResource(R.string.mark_done),
            onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.MarkDone) },
        )
    }
    actions += if (isPulledIn) {
        TodayTaskActionSpec(
            label = stringResource(R.string.undo),
            onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.UndoPullIn) },
        )
    } else {
        TodayTaskActionSpec(
            label = stringResource(R.string.move_later),
            onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.MoveLater) },
        )
    }
    actions += TodayTaskActionSpec(
        label = stringResource(R.string.remove_from_plan),
        onClick = { onRemoveFromPlan(task) },
    )
    return actions
}

private data class TodaySectionAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val contentDescription: String? = null,
)

private data class TodayTaskActionSpec(
    val label: String,
    val onClick: () -> Unit,
)

private const val TimeAdjustmentStepMinutes = 15
