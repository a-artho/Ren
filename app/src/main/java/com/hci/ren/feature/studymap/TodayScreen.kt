package com.hci.ren.feature.studymap

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.pluralStringResource
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
import com.hci.ren.ui.motion.RenMotionDurationMillis
import com.hci.ren.ui.motion.RenMotionEasing
import com.hci.ren.ui.motion.isReducedMotionEnabled
import com.hci.ren.ui.motion.renFadeThroughTransform
import com.hci.ren.ui.theme.RenContextMenuSurface
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
    val reducedMotion = isReducedMotionEnabled()
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
    var showMovedLaterInfo by rememberSaveable(today) { mutableStateOf(false) }
    var showDoneTodayInfo by rememberSaveable(today) { mutableStateOf(false) }
    var showWontFitTodayInfo by rememberSaveable(today) { mutableStateOf(false) }
    var showRemovedFromPlanInfo by rememberSaveable(today) { mutableStateOf(false) }
    var isUseExtraTimeExpanded by rememberSaveable(today) { mutableStateOf(true) }
    var isMovedLaterExpanded by rememberSaveable(today) { mutableStateOf(true) }
    var isDoneTodayExpanded by rememberSaveable(today) { mutableStateOf(true) }
    var isWontFitTodayExpanded by rememberSaveable(today) { mutableStateOf(true) }
    var isRemovedFromPlanExpanded by rememberSaveable(today) { mutableStateOf(true) }
    var visibleWrapUpMessage by rememberSaveable(today) { mutableStateOf<String?>(null) }
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
    val sourceDocuments = project.plan.sourceDocuments
    val showUpNextSource = sourceDocuments.size > 1
    val upNextPlanTasks = todayPlan.doTodayTasks + todayPlan.pulledInTasks
    val upNextPlanIds = upNextPlanTasks.map { it.id }
    val pulledInTaskIds = todayPlan.pulledInTasks.mapTo(mutableSetOf()) { it.id }
    var isUpNextReorderMode by rememberSaveable(today) { mutableStateOf(false) }
    var upNextOrderIds by rememberSaveable(today) { mutableStateOf(upNextPlanIds) }
    var draggedUpNextTaskId by remember(today) { mutableStateOf<String?>(null) }
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

    LaunchedEffect(isUpNextReorderMode) {
        if (!isUpNextReorderMode) {
            draggedUpNextTaskId = null
        }
    }

    LaunchedEffect(wrapUpResultMessage) {
        if (wrapUpResultMessage != null) {
            visibleWrapUpMessage = wrapUpResultMessage
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
            reducedMotion = reducedMotion,
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
    if (showMovedLaterInfo) {
        MovedLaterInfoDialog(onDismiss = { showMovedLaterInfo = false })
    }
    if (showDoneTodayInfo) {
        DoneTodayInfoDialog(onDismiss = { showDoneTodayInfo = false })
    }
    if (showWontFitTodayInfo) {
        WontFitTodayInfoDialog(onDismiss = { showWontFitTodayInfo = false })
    }
    if (showRemovedFromPlanInfo) {
        RemovedFromPlanInfoDialog(onDismiss = { showRemovedFromPlanInfo = false })
    }

    LazyColumn(
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
        item(key = "today-header") {
            TodayHeader(
                onTimeBudgetClick = { showTimeBudgetDialog = true },
                modifier = Modifier.animateItem(),
            )
        }
        item(key = "today-budget") {
            TodayBudgetCard(
                todayPlan = todayPlan,
                reducedMotion = reducedMotion,
                modifier = Modifier.animateItem(),
            )
        }
        if (startHereTask != null) {
            item(key = "today-start-here") {
                AnimatedContent(
                    targetState = startHereTask.id,
                    transitionSpec = { renFadeThroughTransform(reducedMotion = reducedMotion) },
                    contentKey = { it },
                    label = "today-start-task",
                    modifier = Modifier.animateItem(),
                ) { taskId ->
                    val animatedTask = visibleUpNextTasks.firstOrNull { it.id == taskId } ?: startHereTask
                    val isPulledIn = animatedTask.id in pulledInTaskIds
                    TodayStartHereCard(
                        task = animatedTask,
                        sourceDocuments = sourceDocuments,
                        actions = upNextTaskActions(
                            today = today,
                            task = animatedTask,
                            isPulledIn = isPulledIn,
                            onTaskAction = onTaskAction,
                            includeMarkDone = false,
                            onRemoveFromPlan = { pendingRemovalTask = it },
                        ),
                        onStartFocus = { onStartFocusTask(animatedTask.id) },
                        onMarkDone = { onTaskAction(today, animatedTask.id, TodaySessionTaskAction.MarkDone) },
                        reducedMotion = reducedMotion,
                    )
                }
            }
        }
        if (visibleNotice != null) {
            item(key = "today-notice") {
                TodayNoticeCard(
                    message = visibleNotice.orEmpty(),
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item(key = "today-wrap-action") {
            if (todayPlan.hasPendingChanges || isTodayClosed) {
                TodayRebalancedRow(
                    title = if (isTodayClosed) {
                        stringResource(R.string.today_wrapped_up_button)
                    } else {
                        stringResource(R.string.today_rebalanced)
                    },
                    message = if (isTodayClosed) {
                        visibleWrapUpMessage ?: stringResource(R.string.today_time_closed_message)
                    } else {
                        impactPreview?.message() ?: todayPlan.replanFeedbackMessage()
                    },
                    actionText = wrapUpButtonText,
                    showAction = canWrapUpToday,
                    onActionClick = { showWrapUpDialog = true },
                    reducedMotion = reducedMotion,
                    modifier = Modifier.animateItem(),
                )
            } else {
                TodayWrapUpButton(
                    text = wrapUpButtonText,
                    enabled = canWrapUpToday,
                    onClick = { showWrapUpDialog = true },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (
            upNextPlanTasks.isEmpty() &&
            todayPlan.doneTodayTasks.isEmpty() &&
            todayPlan.wontFitTodayTasks.isEmpty() &&
            todayPlan.movedLaterTasks.isEmpty() &&
            todayPlan.removedFromPlanTasks.isEmpty() &&
            todayPlan.pullInCandidates.isEmpty()
        ) {
            if (!isTodayClosed) {
                item(key = "today-empty") {
                    EmptyTodayCard(
                        state = emptyState,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        } else {
            if (listedUpNextTasks.isNotEmpty()) {
                item(key = "today-up-next-header") {
                    TodaySectionHeader(
                        title = stringResource(R.string.up_next),
                        reducedMotion = reducedMotion,
                        modifier = Modifier.animateItem(),
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
                itemsIndexed(listedUpNextTasks, key = { _, task -> "up-next-${task.id}" }) { index, task ->
                    val isPulledIn = task.id in pulledInTaskIds
                    val isActivelyDragged = draggedUpNextTaskId == task.id
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
                        position = if (isUpNextReorderMode) index + 1 else null,
                        indicatorIcon = Icons.AutoMirrored.Filled.ArrowForward,
                        indicatorContentDescription = stringResource(R.string.up_next),
                        showSource = showUpNextSource,
                        supportingText = if (isPulledIn) stringResource(R.string.pulled_in_today_message) else null,
                        actions = actions,
                        isReorderMode = isUpNextReorderMode,
                        canMoveUp = index > 0,
                        canMoveDown = index < visibleUpNextTasks.lastIndex,
                        onMoveUp = { moveUpNextTask(task.id, -1) },
                        onMoveDown = { moveUpNextTask(task.id, 1) },
                        onDragStateChanged = { isDragging ->
                            draggedUpNextTaskId = task.id.takeIf { isDragging }
                        },
                        reducedMotion = reducedMotion,
                        compact = true,
                        modifier = if (isActivelyDragged) {
                            Modifier.zIndex(10f)
                        } else {
                            Modifier.animateItem()
                        },
                    )
                }
            }
            if (todayPlan.wontFitTodayTasks.isNotEmpty()) {
                item(key = "today-wont-fit-section") {
                    Column(
                        modifier = Modifier.animateItem(),
                    ) {
                        TodaySectionHeader(
                            title = stringResource(R.string.wont_fit_today),
                            reducedMotion = reducedMotion,
                            countLabel = collapsedSectionTaskCountLabel(
                                count = if (isWontFitTodayExpanded) 0 else todayPlan.wontFitTodayTasks.size,
                            ),
                            trailingActions = listOf(
                                TodaySectionAction(
                                    label = "",
                                    icon = Icons.Outlined.Info,
                                    onClick = { showWontFitTodayInfo = true },
                                    contentDescription = stringResource(R.string.wont_fit_today_info),
                                ),
                                TodaySectionAction(
                                    label = if (isWontFitTodayExpanded) {
                                        stringResource(R.string.collapse_section)
                                    } else {
                                        stringResource(R.string.expand_section)
                                    },
                                    icon = if (isWontFitTodayExpanded) {
                                        Icons.Default.ExpandLess
                                    } else {
                                        Icons.Default.ExpandMore
                                    },
                                    onClick = { isWontFitTodayExpanded = !isWontFitTodayExpanded },
                                ),
                            ),
                        )
                        TodayCollapsibleSectionContent(
                            expanded = isWontFitTodayExpanded,
                            reducedMotion = reducedMotion,
                            compact = true,
                        ) {
                            todayPlan.wontFitTodayTasks.forEach { task ->
                                key(task.id) {
                                    TodayTaskRow(
                                        task = task,
                                        sourceDocuments = sourceDocuments,
                                        indicatorIcon = Icons.Default.WarningAmber,
                                        indicatorContentDescription = stringResource(R.string.wont_fit_today),
                                        showSource = false,
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
                                        reducedMotion = reducedMotion,
                                        compact = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (todayPlan.movedLaterTasks.isNotEmpty()) {
                item(key = "today-moved-later-section") {
                    Column(
                        modifier = Modifier.animateItem(),
                    ) {
                        TodaySectionHeader(
                            title = stringResource(R.string.moved_later),
                            reducedMotion = reducedMotion,
                            countLabel = collapsedSectionTaskCountLabel(
                                count = if (isMovedLaterExpanded) 0 else todayPlan.movedLaterTasks.size,
                            ),
                            trailingActions = listOf(
                                TodaySectionAction(
                                    label = "",
                                    icon = Icons.Outlined.Info,
                                    onClick = { showMovedLaterInfo = true },
                                    contentDescription = stringResource(R.string.moved_later_info),
                                ),
                                TodaySectionAction(
                                    label = if (isMovedLaterExpanded) {
                                        stringResource(R.string.collapse_section)
                                    } else {
                                        stringResource(R.string.expand_section)
                                    },
                                    icon = if (isMovedLaterExpanded) {
                                        Icons.Default.ExpandLess
                                    } else {
                                        Icons.Default.ExpandMore
                                    },
                                    onClick = { isMovedLaterExpanded = !isMovedLaterExpanded },
                                ),
                            ),
                        )
                        TodayCollapsibleSectionContent(
                            expanded = isMovedLaterExpanded,
                            reducedMotion = reducedMotion,
                            compact = true,
                        ) {
                            todayPlan.movedLaterTasks.forEach { task ->
                                key(task.id) {
                                    TodayTaskRow(
                                        task = task,
                                        sourceDocuments = sourceDocuments,
                                        indicatorIcon = Icons.Default.Schedule,
                                        indicatorContentDescription = stringResource(R.string.moved_later),
                                        showSource = false,
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
                                        reducedMotion = reducedMotion,
                                        compact = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (todayPlan.pullInCandidates.isNotEmpty()) {
                item(key = "today-pull-in-section") {
                    Column(
                        modifier = Modifier.animateItem(),
                    ) {
                        TodaySectionHeader(
                            title = stringResource(R.string.pull_ahead_suggestions),
                            reducedMotion = reducedMotion,
                            countLabel = collapsedSectionTaskCountLabel(
                                count = if (isUseExtraTimeExpanded) 0 else todayPlan.pullInCandidates.size,
                            ),
                            trailingActions = listOf(
                                TodaySectionAction(
                                    label = "",
                                    icon = Icons.Outlined.Info,
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
                                    onClick = { isUseExtraTimeExpanded = !isUseExtraTimeExpanded },
                                ),
                            ),
                        )
                        TodayCollapsibleSectionContent(
                            expanded = isUseExtraTimeExpanded,
                            reducedMotion = reducedMotion,
                            compact = true,
                        ) {
                            todayPlan.pullInCandidates.forEach { task ->
                                key(task.id) {
                                    TodayTaskRow(
                                        task = task,
                                        sourceDocuments = sourceDocuments,
                                        indicatorIcon = Icons.Default.Timer,
                                        indicatorContentDescription = stringResource(R.string.pull_ahead_suggestions),
                                        showSource = false,
                                        primaryAction = TodayTaskActionSpec(
                                            label = stringResource(R.string.pull_in),
                                            onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.PullIn) },
                                        ),
                                        reducedMotion = reducedMotion,
                                        compact = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (todayPlan.removedFromPlanTasks.isNotEmpty()) {
                item(key = "today-removed-section") {
                    Column(
                        modifier = Modifier.animateItem(),
                    ) {
                        TodaySectionHeader(
                            title = stringResource(R.string.removed_from_plan),
                            reducedMotion = reducedMotion,
                            countLabel = collapsedSectionTaskCountLabel(
                                count = if (isRemovedFromPlanExpanded) 0 else todayPlan.removedFromPlanTasks.size,
                            ),
                            trailingActions = listOf(
                                TodaySectionAction(
                                    label = "",
                                    icon = Icons.Outlined.Info,
                                    onClick = { showRemovedFromPlanInfo = true },
                                    contentDescription = stringResource(R.string.removed_from_plan_info),
                                ),
                                TodaySectionAction(
                                    label = if (isRemovedFromPlanExpanded) {
                                        stringResource(R.string.collapse_section)
                                    } else {
                                        stringResource(R.string.expand_section)
                                    },
                                    icon = if (isRemovedFromPlanExpanded) {
                                        Icons.Default.ExpandLess
                                    } else {
                                        Icons.Default.ExpandMore
                                    },
                                    onClick = { isRemovedFromPlanExpanded = !isRemovedFromPlanExpanded },
                                ),
                            ),
                        )
                        TodayCollapsibleSectionContent(
                            expanded = isRemovedFromPlanExpanded,
                            reducedMotion = reducedMotion,
                            compact = true,
                        ) {
                            todayPlan.removedFromPlanTasks.forEach { task ->
                                key(task.id) {
                                    TodayTaskRow(
                                        task = task,
                                        sourceDocuments = sourceDocuments,
                                        indicatorIcon = Icons.Default.Close,
                                        indicatorContentDescription = stringResource(R.string.removed_from_plan),
                                        indicatorColor = MaterialTheme.colorScheme.error,
                                        showSource = false,
                                        actions = listOf(
                                            TodayTaskActionSpec(
                                                label = stringResource(R.string.restore),
                                                onClick = { onTaskAction(today, task.id, TodaySessionTaskAction.RestoreRemoved) },
                                            ),
                                        ),
                                        reducedMotion = reducedMotion,
                                        compact = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (todayPlan.doneTodayTasks.isNotEmpty()) {
                item(key = "today-done-section") {
                    Column(
                        modifier = Modifier.animateItem(),
                    ) {
                        TodaySectionHeader(
                            title = stringResource(R.string.done_today),
                            reducedMotion = reducedMotion,
                            countLabel = collapsedSectionTaskCountLabel(
                                count = if (isDoneTodayExpanded) 0 else todayPlan.doneTodayTasks.size,
                            ),
                            trailingActions = listOf(
                                TodaySectionAction(
                                    label = "",
                                    icon = Icons.Outlined.Info,
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
                                    onClick = { isDoneTodayExpanded = !isDoneTodayExpanded },
                                ),
                            ),
                        )
                        TodayCollapsibleSectionContent(
                            expanded = isDoneTodayExpanded,
                            reducedMotion = reducedMotion,
                            compact = true,
                        ) {
                            todayPlan.doneTodayTasks.forEach { task ->
                                key(task.id) {
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
                                        showSource = false,
                                        showDuration = false,
                                        actions = actions,
                                        reducedMotion = reducedMotion,
                                        compact = true,
                                    )
                                }
                            }
                        }
                    }
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
            contentColor = Color.White,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.58f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.time_budget),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun TodayBudgetCard(
    todayPlan: TodaySessionPlan,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val pressureMinutes = maxOf(todayPlan.overPlannedMinutes, todayPlan.overflowMinutes)
    val animatedAvailableMinutes by animateIntAsState(
        targetValue = todayPlan.availableMinutes,
        animationSpec = todayMotionSpec(reducedMotion),
        label = "today-budget-available",
    )
    val animatedPlannedMinutes by animateIntAsState(
        targetValue = todayPlan.plannedMinutes,
        animationSpec = todayMotionSpec(reducedMotion),
        label = "today-budget-planned",
    )
    val animatedRemainingOrOverMinutes by animateIntAsState(
        targetValue = if (pressureMinutes > 0) pressureMinutes else todayPlan.remainingMinutes,
        animationSpec = todayMotionSpec(reducedMotion),
        label = "today-budget-remaining",
    )
    val animatedCompletedMinutes by animateIntAsState(
        targetValue = todayPlan.completedMinutes,
        animationSpec = todayMotionSpec(reducedMotion),
        label = "today-budget-completed",
    )
    val animatedMovingLaterMinutes by animateIntAsState(
        targetValue = todayPlan.overflowMinutes + todayPlan.movedLaterMinutes,
        animationSpec = todayMotionSpec(reducedMotion),
        label = "today-budget-moving-later",
    )
    val animatedRemovedMinutes by animateIntAsState(
        targetValue = todayPlan.removedMinutes,
        animationSpec = todayMotionSpec(reducedMotion),
        label = "today-budget-removed",
    )
    val remainingOrOverLabel = if (pressureMinutes > 0) {
        stringResource(R.string.over_budget_metric_label)
    } else {
        stringResource(R.string.remaining_metric_label)
    }
    val targetRemainingOrOverColor = if (pressureMinutes > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val remainingOrOverColor by animateColorAsState(
        targetValue = targetRemainingOrOverColor,
        animationSpec = todayMotionSpec(reducedMotion),
        label = "today-budget-remaining-color",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = todayMotionSpec(reducedMotion)),
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
                    value = formatMinutes(animatedAvailableMinutes),
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
                    value = formatMinutes(animatedPlannedMinutes),
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
                    value = formatMinutes(animatedRemainingOrOverMinutes),
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
                    value = formatMinutes(animatedCompletedMinutes),
                    icon = Icons.Default.CheckCircle,
                    iconTint = MaterialTheme.colorScheme.primary,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider(
                    modifier = Modifier
                        .height(30.dp)
                        .padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                TodayBudgetMetric(
                    label = stringResource(R.string.moved_later_metric_label),
                    value = formatMinutes(animatedMovingLaterMinutes),
                    icon = Icons.Default.Schedule,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider(
                    modifier = Modifier
                        .height(30.dp)
                        .padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                TodayBudgetMetric(
                    label = stringResource(R.string.removed_metric_label),
                    value = formatMinutes(animatedRemovedMinutes),
                    icon = Icons.Default.Close,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    compact = true,
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
    compact: Boolean = false,
) {
    val horizontalPadding = if (compact) 4.dp else 6.dp
    val contentSpacing = if (compact) 2.dp else 4.dp
    val labelSpacing = if (compact) 4.dp else 6.dp
    val iconSize = if (compact) 12.dp else 14.dp
    val valueStyle = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium
    val valueWeight = if (compact) FontWeight.SemiBold else FontWeight.Bold

    Column(
        modifier = modifier.padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(contentSpacing),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(labelSpacing),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(iconSize),
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
            style = valueStyle,
            fontWeight = valueWeight,
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
    reducedMotion: Boolean,
) {
    val hasTimeOverride = availableMinutes != baseAvailableMinutes
    val animatedAvailableMinutes by animateIntAsState(
        targetValue = availableMinutes,
        animationSpec = todayMotionSpec(reducedMotion),
        label = "today-dialog-available-minutes",
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = todayMotionSpec(reducedMotion)),
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
                        text = formatMinutes(animatedAvailableMinutes),
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
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = todayMotionSpec(reducedMotion)),
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
                AnimatedContent(
                    targetState = title to message,
                    transitionSpec = { renFadeThroughTransform(reducedMotion = reducedMotion) },
                    label = "today-rebalanced-copy",
                ) { (targetTitle, targetMessage) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = targetTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = targetMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
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
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    countLabel: String? = null,
    trailingAction: TodaySectionAction? = null,
    trailingActions: List<TodaySectionAction> = emptyList(),
) {
    val actions = listOfNotNull(trailingAction) + trailingActions
    Row(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = todayMotionSpec(reducedMotion)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val titleModifier = if (countLabel == null) {
                Modifier
            } else {
                Modifier
                    .weight(1f, fill = false)
                    .alignByBaseline()
            }
            Text(
                text = title,
                modifier = titleModifier,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (countLabel != null) {
                Text(
                    text = countLabel,
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (actions.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                actions.forEach { action ->
                    if (action.label.isBlank()) {
                        TodaySectionIconAction(action = action)
                    } else {
                        TextButton(
                            onClick = action.onClick,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 6.dp,
                                end = 2.dp,
                                top = 4.dp,
                                bottom = 4.dp,
                            ),
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.contentDescription,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(4.dp))
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
}

@Composable
private fun collapsedSectionTaskCountLabel(count: Int): String? {
    if (count <= 0) return null
    return pluralStringResource(R.plurals.today_collapsed_section_task_count, count, count)
}

@Composable
private fun TodayCollapsibleSectionContent(
    expanded: Boolean,
    reducedMotion: Boolean,
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    val topPadding = if (compact) 8.dp else 12.dp
    val itemSpacing = if (compact) 8.dp else 10.dp
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(todayMotionSpec(reducedMotion)) + expandVertically(todayMotionSpec(reducedMotion)),
        exit = fadeOut(todayMotionSpec(reducedMotion)) + shrinkVertically(todayMotionSpec(reducedMotion)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            content()
        }
    }
}

@Composable
private fun TodaySectionIconAction(
    action: TodaySectionAction,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        IconButton(
            onClick = action.onClick,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.contentDescription,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                modifier = Modifier.size(16.dp),
            )
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
private fun TodayNoticeCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
private fun EmptyTodayCard(
    state: TodayEmptyState,
    modifier: Modifier = Modifier,
) {
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
        modifier = modifier.fillMaxWidth(),
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
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember(task.id) { mutableStateOf(false) }
    var isRingVisible by remember(task.id) { mutableStateOf(false) }
    LaunchedEffect(task.id) {
        isRingVisible = true
    }
    val sourceText = todayTaskSourceText(task, sourceDocuments)
    val durationValue = if (task.likelyStudyMinutes < 60) {
        task.likelyStudyMinutes.toString()
    } else {
        formatMinutes(task.likelyStudyMinutes)
    }
    val durationUnit = if (task.likelyStudyMinutes < 60) "min" else null
    val startLabelShape = RoundedCornerShape(8.dp)
    val startLabelGlowDp by animateDpAsState(
        targetValue = if (isRingVisible) 10.dp else 0.dp,
        animationSpec = todayEmphasisMotionSpec(reducedMotion),
        label = "today-start-label-glow",
    )
    val startLabelGlowElevation = with(LocalDensity.current) { startLabelGlowDp.toPx() }
    val startLabelGlowColor = MaterialTheme.colorScheme.primary
    val ringProgress by animateFloatAsState(
        targetValue = if (isRingVisible) 1f else 0f,
        animationSpec = todayEmphasisMotionSpec(reducedMotion),
        label = "today-start-duration-ring",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = todayMotionSpec(reducedMotion)),
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
                        containerColor = RenContextMenuSurface,
                        tonalElevation = 0.dp,
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
                    progress = ringProgress,
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
    progress: Float,
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
                val segmentProgress = (progress * ringSegments - segmentIndex).coerceIn(0f, 1f)
                drawArc(
                    color = primary.copy(alpha = 0.16f),
                    startAngle = startAngle,
                    sweepAngle = sweepDegrees,
                    useCenter = false,
                    topLeft = ringTopLeft,
                    size = ringSize,
                    style = Stroke(width = glowStrokeWidth, cap = StrokeCap.Round),
                )
                if (segmentProgress > 0f) {
                    drawArc(
                        color = primary.copy(alpha = 0.92f),
                        startAngle = startAngle,
                        sweepAngle = sweepDegrees * segmentProgress,
                        useCenter = false,
                        topLeft = ringTopLeft,
                        size = ringSize,
                        style = Stroke(width = segmentStrokeWidth, cap = StrokeCap.Round),
                    )
                }
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
    indicatorColor: Color? = null,
    showDuration: Boolean = true,
    showTaskType: Boolean = true,
    showSource: Boolean = true,
    supportingText: String? = null,
    primaryAction: TodayTaskActionSpec? = null,
    actions: List<TodayTaskActionSpec> = emptyList(),
    isReorderMode: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Boolean = { false },
    onMoveDown: () -> Boolean = { false },
    onDragStateChanged: (Boolean) -> Unit = {},
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var menuExpanded by remember(task.id) { mutableStateOf(false) }
    var dragOffsetY by remember(task.id) { mutableStateOf(0f) }
    var isDragging by remember(task.id) { mutableStateOf(false) }
    val canMoveUpState = rememberUpdatedState(canMoveUp)
    val canMoveDownState = rememberUpdatedState(canMoveDown)
    val onMoveUpState = rememberUpdatedState(onMoveUp)
    val onMoveDownState = rememberUpdatedState(onMoveDown)
    val moveStepPx = with(LocalDensity.current) { if (compact) 56.dp.toPx() else 72.dp.toPx() }
    val draggedShadowElevationPx = with(LocalDensity.current) { 16.dp.toPx() }
    val moveStepPxState = rememberUpdatedState(moveStepPx)
    val sourceText = if (showSource) todayTaskSourceText(task, sourceDocuments) else null
    val supportingTextValue = supportingText.orEmpty()
    val metadataText = todayTaskMetadataText(
        task = task,
        showDuration = showDuration,
        showTaskType = showTaskType,
    )
    val trailingMode = when {
        isReorderMode -> TodayTaskTrailingMode.Reorder
        primaryAction != null -> TodayTaskTrailingMode.PrimaryAction
        actions.isNotEmpty() -> TodayTaskTrailingMode.Menu
        else -> TodayTaskTrailingMode.Empty
    }
    val shape = if (compact) 14.dp else 16.dp
    val rowStartPadding = if (compact) 12.dp else 14.dp
    val rowEndPadding = if (compact) 6.dp else 8.dp
    val rowVerticalPadding = if (compact) 8.dp else 12.dp
    val rowSpacing = if (compact) 9.dp else 12.dp
    val contentSpacing = if (compact) 2.dp else 3.dp
    val titleStyle = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
    val metaStyle = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    val actionSpacerWidth = if (compact) 8.dp else 12.dp
    val cardScale by animateFloatAsState(
        targetValue = if (isDragging) 1.012f else 1f,
        animationSpec = todayMotionSpec(reducedMotion),
        label = "today-task-row-scale",
    )
    val cardElevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        animationSpec = todayMotionSpec(reducedMotion),
        label = "today-task-row-elevation",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isDragging) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)
        },
        animationSpec = todayMotionSpec(reducedMotion),
        label = "today-task-row-border",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = todayMotionSpec(reducedMotion))
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
                shadowElevation = if (isDragging) draggedShadowElevationPx else 0f
            }
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .zIndex(if (isDragging || dragOffsetY != 0f) 10f else 0f),
        shape = RoundedCornerShape(shape),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)),
        border = BorderStroke(width = 1.dp, color = borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
    ) {
        Row(
            modifier = Modifier.padding(
                start = rowStartPadding,
                end = rowEndPadding,
                top = rowVerticalPadding,
                bottom = rowVerticalPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            TodayTaskIndicator(
                position = position,
                icon = indicatorIcon,
                contentDescription = indicatorContentDescription,
                color = indicatorColor,
                compact = compact,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(contentSpacing),
            ) {
                Text(
                    text = task.title,
                    style = titleStyle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (metadataText != null) {
                    Text(
                        text = metadataText,
                        style = metaStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (sourceText != null) {
                    Text(
                        text = sourceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AnimatedVisibility(
                    visible = supportingText != null,
                    enter = fadeIn(todayMotionSpec(reducedMotion)) + expandVertically(todayMotionSpec(reducedMotion)),
                    exit = fadeOut(todayMotionSpec(reducedMotion)) + shrinkVertically(todayMotionSpec(reducedMotion)),
                ) {
                    Text(
                        text = supportingTextValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AnimatedContent(
                targetState = trailingMode,
                transitionSpec = { renFadeThroughTransform(reducedMotion = reducedMotion) },
                label = "today-task-row-action",
            ) { mode ->
                when (mode) {
                    TodayTaskTrailingMode.Reorder -> {
                        ReorderTaskHandle(
                            isDragging = isDragging,
                            reducedMotion = reducedMotion,
                            compact = compact,
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .pointerInput(task.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            isDragging = true
                                            onDragStateChanged(true)
                                        },
                                        onDragEnd = {
                                            isDragging = false
                                            dragOffsetY = 0f
                                            onDragStateChanged(false)
                                        },
                                        onDragCancel = {
                                            isDragging = false
                                            dragOffsetY = 0f
                                            onDragStateChanged(false)
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
                    }
                    TodayTaskTrailingMode.PrimaryAction -> {
                        primaryAction?.let { action ->
                            TextButton(
                                onClick = action.onClick,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = if (compact) 8.dp else 12.dp,
                                    vertical = if (compact) 4.dp else 8.dp,
                                ),
                            ) {
                                Text(action.label)
                            }
                        } ?: Spacer(Modifier.width(actionSpacerWidth))
                    }
                    TodayTaskTrailingMode.Menu -> {
                        Box {
                            if (compact) {
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    IconButton(
                                        onClick = { menuExpanded = true },
                                        modifier = Modifier.size(34.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = stringResource(R.string.today_task_actions),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            } else {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.today_task_actions),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                containerColor = RenContextMenuSurface,
                                tonalElevation = 0.dp,
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
                    TodayTaskTrailingMode.Empty -> Spacer(Modifier.width(actionSpacerWidth))
                }
            }
        }
    }
}

@Composable
private fun TodayTaskIndicator(
    position: Int?,
    icon: ImageVector?,
    contentDescription: String?,
    color: Color? = null,
    compact: Boolean = false,
) {
    val containerSize = if (compact) 34.dp else 40.dp
    val iconSize = if (compact) 18.dp else 22.dp
    val numberStyle = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall
    val indicatorColor = color ?: MaterialTheme.colorScheme.primary
    val containerColor = if (color == null) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f)
    } else {
        indicatorColor.copy(alpha = 0.12f)
    }
    val borderColor = indicatorColor.copy(alpha = if (color == null) 0.34f else 0.32f)
    Surface(
        modifier = Modifier.size(containerSize),
        shape = CircleShape,
        color = containerColor,
        contentColor = indicatorColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                position != null -> Text(
                    text = position.toString(),
                    style = numberStyle,
                    fontWeight = FontWeight.Bold,
                )
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
private fun ReorderTaskHandle(
    isDragging: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val handleAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0.72f,
        animationSpec = todayMotionSpec(reducedMotion),
        label = "today-reorder-handle-alpha",
    )
    Surface(
        modifier = modifier
            .size(if (compact) 34.dp else 40.dp)
            .alpha(handleAlpha),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = stringResource(R.string.drag_to_reorder_task),
                modifier = Modifier.size(if (compact) 18.dp else 22.dp),
            )
        }
    }
}

@Composable
private fun todayTaskMetadataText(
    task: GeneratedStudyBlock,
    showDuration: Boolean,
    showTaskType: Boolean,
): String? {
    val parts = buildList {
        if (showDuration) add(formatMinutes(task.likelyStudyMinutes))
        if (showTaskType) add(taskTypeLabel(task.taskType))
    }
    return parts.joinToString(" · ").takeIf { it.isNotBlank() }
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
private fun WontFitTodayInfoDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.wont_fit_today_info_title)) },
        text = {
            Text(
                text = stringResource(R.string.wont_fit_today_info_message),
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
private fun RemovedFromPlanInfoDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.removed_from_plan_info_title)) },
        text = {
            Text(
                text = stringResource(R.string.removed_from_plan_info_message),
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
private fun UseExtraTimeInfoDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Info,
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
private fun MovedLaterInfoDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.moved_later_info_title)) },
        text = {
            Text(
                text = stringResource(R.string.moved_later_info_message),
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
                imageVector = Icons.Outlined.Info,
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

private enum class TodayTaskTrailingMode {
    Reorder,
    PrimaryAction,
    Menu,
    Empty,
}

private fun <T> todayMotionSpec(reducedMotion: Boolean) = tween<T>(
    durationMillis = if (reducedMotion) 0 else RenMotionDurationMillis,
    easing = RenMotionEasing,
)

private fun <T> todayEmphasisMotionSpec(reducedMotion: Boolean) = tween<T>(
    durationMillis = if (reducedMotion) 0 else 650,
    easing = RenMotionEasing,
)

private const val TimeAdjustmentStepMinutes = 15
