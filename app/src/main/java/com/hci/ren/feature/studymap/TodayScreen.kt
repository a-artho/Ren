package com.hci.ren.feature.studymap

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
import com.hci.ren.ui.theme.RenSelectedCardSurface
import kotlinx.coroutines.delay

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
    var clockNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(project.preferences.studyDayResetOffsetHours) {
        while (true) {
            clockNowMillis = System.currentTimeMillis()
            delay(60_000L)
        }
    }
    val minutesUntilReset = minutesUntilStudyDayReset(
        nowMillis = clockNowMillis,
        resetOffsetHours = project.preferences.studyDayResetOffsetHours,
    )
    val baseAvailableMinutes = effectiveTodayAvailableMinutes(
        requestedMinutes = todayBaseAvailableMinutes(project, data, today),
        minutesUntilReset = minutesUntilReset,
    )
    val isTodayClosed = project.dailyAvailableMinutesByDate[today] == 0
    val todaySession = session?.takeIf { it.date == today }
    val availableMinutes = effectiveTodayAvailableMinutes(
        requestedMinutes = todaySession?.availableMinutes
            ?: baseAvailableMinutes,
        minutesUntilReset = minutesUntilReset,
    )
    val hasAvailabilityOverride = todaySession?.availableMinutes != null && availableMinutes != baseAvailableMinutes
    val todayPlan = TodaySessionPlanner().plan(
        data = data,
        date = today,
        availableMinutes = availableMinutes,
        session = todaySession,
        hasAvailabilityOverride = hasAvailabilityOverride,
    )
    val clockPressure = todayClockPressure(
        activeWorkMinutes = todayPlan.activeWorkMinutes,
        minutesUntilReset = minutesUntilReset,
    )
    var showWrapUpDialog by remember(today, todayPlan) { mutableStateOf(false) }
    var showTimeBudgetDialog by remember(today) { mutableStateOf(false) }
    var pendingRemovalTask by remember(today) { mutableStateOf<GeneratedStudyBlock?>(null) }
    var showUseExtraTimeInfo by rememberSaveable(today) { mutableStateOf(false) }
    var showMovedLaterInfo by rememberSaveable(today) { mutableStateOf(false) }
    var showDoneTodayInfo by rememberSaveable(today) { mutableStateOf(false) }
    var showWontFitTodayInfo by rememberSaveable(today) { mutableStateOf(false) }
    var showRemovedFromPlanInfo by rememberSaveable(today) { mutableStateOf(false) }
    var isUseExtraTimeExpanded by rememberSaveable(today) { mutableStateOf(false) }
    var isMovedLaterExpanded by rememberSaveable(today) { mutableStateOf(true) }
    var isDoneTodayExpanded by rememberSaveable(today) { mutableStateOf(true) }
    var isWontFitTodayExpanded by rememberSaveable(today) { mutableStateOf(true) }
    var isRemovedFromPlanExpanded by rememberSaveable(today) { mutableStateOf(true) }
    var visibleNotice by rememberSaveable(today) { mutableStateOf<String?>(null) }
    val emptyState = todayPlan.emptyState(data, isTodayClosed)
    val canWrapUpToday = todayPlan.canWrapUpToday(isTodayClosed)
    val wrapUpButtonText = when {
        canWrapUpToday -> stringResource(R.string.wrap_up_today)
        isTodayClosed -> stringResource(R.string.today_wrapped_up_button)
        else -> stringResource(R.string.nothing_to_wrap_up)
    }
    val sourceDocuments = project.plan.sourceDocuments
    val showUpNextSource = sourceDocuments.size > 1
    val upNextPlanTasks = todayPlan.doTodayTasks + todayPlan.pulledInTasks
    val pulledInTaskIds = todayPlan.pulledInTasks.mapTo(mutableSetOf()) { it.id }
    var isNextExpanded by rememberSaveable(today) { mutableStateOf(false) }
    val visibleUpNextTasks = upNextPlanTasks
    val startHereTask = visibleUpNextTasks.firstOrNull()
    val listedUpNextTasks = if (startHereTask == null) {
        visibleUpNextTasks
    } else {
        visibleUpNextTasks.drop(1)
    }
    val visibleListedUpNextTasks = if (isNextExpanded) {
        listedUpNextTasks
    } else {
        listedUpNextTasks.take(2)
    }
    fun updateAvailableMinutes(minutes: Int) {
        val normalized = effectiveTodayAvailableMinutes(
            requestedMinutes = minutes,
            minutesUntilReset = minutesUntilReset,
        )
        onAvailableTimeChanged(today, normalized.takeUnless { it == baseAvailableMinutes })
    }

    LaunchedEffect(wrapUpResultMessage) {
        if (wrapUpResultMessage != null) {
            visibleNotice = wrapUpResultMessage
            onConsumeWrapUpResult()
        }
    }

    LaunchedEffect(changeMessage) {
        if (changeMessage != null) {
            visibleNotice = changeMessage
            onConsumeMessage()
        }
    }

    LaunchedEffect(visibleNotice) {
        if (visibleNotice != null) {
            delay(3_200L)
            visibleNotice = null
        }
    }

    if (showWrapUpDialog) {
        WrapUpTodayDialog(
            todayPlan = todayPlan,
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
            maxAvailableMinutes = minutesUntilReset.coerceIn(0, MaxTodaySessionMinutes),
            onDecrease = {
                updateAvailableMinutes(todayPlan.availableMinutes - TimeAdjustmentStepMinutes)
            },
            onIncrease = {
                updateAvailableMinutes(todayPlan.availableMinutes + TimeAdjustmentStepMinutes)
            },
            onSetAvailableMinutes = { minutes -> updateAvailableMinutes(minutes) },
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

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
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
            val headerMessage = todayPlan.headerMessage(clockPressure, isTodayClosed)
            Column(
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TodayHeader(
                    todayPlan = todayPlan,
                    clockPressure = clockPressure,
                    isTodayClosed = isTodayClosed,
                    onAdjustClick = { showTimeBudgetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                AnimatedVisibility(
                    visible = headerMessage != null,
                    enter = fadeIn(todayMotionSpec(reducedMotion)) +
                        expandVertically(todayMotionSpec(reducedMotion), expandFrom = Alignment.Top),
                    exit = fadeOut(todayMotionSpec(reducedMotion)) +
                        shrinkVertically(todayMotionSpec(reducedMotion), shrinkTowards = Alignment.Top),
                ) {
                    if (headerMessage != null) {
                        TodayHeaderSubtitle(
                            message = headerMessage,
                            color = todayPlan.headerColor(clockPressure, isTodayClosed),
                        )
                    }
                }
            }
        }
        item(key = "today-budget") {
            TodayBudgetCard(
                todayPlan = todayPlan,
                reducedMotion = reducedMotion,
                modifier = Modifier.animateItem(),
            )
        }
        if (startHereTask != null) {
            item(key = "today-do-now-section") {
                Column(
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = todayMotionSpec(reducedMotion),
                            placementSpec = todayMotionSpec(reducedMotion),
                            fadeOutSpec = todayMotionSpec(reducedMotion),
                        )
                        .padding(top = 2.dp, bottom = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    TodayDoNowSectionLabel()
                }
            }
            item(key = "today-do-now") {
                AnimatedContent(
                    targetState = startHereTask.id,
                    transitionSpec = { renFadeThroughTransform(reducedMotion = reducedMotion) },
                    contentKey = { it },
                    label = "today-do-now-task",
                    modifier = Modifier.animateItem(
                        fadeInSpec = todayMotionSpec(reducedMotion),
                        placementSpec = todayMotionSpec(reducedMotion),
                        fadeOutSpec = todayMotionSpec(reducedMotion),
                    ),
                ) { taskId ->
                    val animatedTask = visibleUpNextTasks.firstOrNull { it.id == taskId } ?: startHereTask
                    val isPulledIn = animatedTask.id in pulledInTaskIds
                    TodayDoNowCard(
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
                        countLabel = leafCountLabel(listedUpNextTasks.size),
                        countIcon = Icons.Default.Eco,
                        trailingActions = buildList {
                            if (listedUpNextTasks.size > 1) {
                                add(
                                    TodaySectionAction(
                                        label = if (isNextExpanded) {
                                            stringResource(R.string.collapse_section)
                                        } else {
                                            stringResource(R.string.expand_section)
                                        },
                                        icon = if (isNextExpanded) {
                                            Icons.Default.ExpandLess
                                        } else {
                                            Icons.Default.ExpandMore
                                        },
                                        onClick = { isNextExpanded = !isNextExpanded },
                                    ),
                                )
                            }
                        },
                    )
                }
                items(visibleListedUpNextTasks, key = { task -> "up-next-${task.id}" }) { task ->
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
                        indicatorIcon = Icons.AutoMirrored.Filled.ArrowForward,
                        indicatorContentDescription = stringResource(R.string.up_next),
                        showSource = showUpNextSource,
                        supportingText = if (isPulledIn) stringResource(R.string.pulled_in_today_message) else null,
                        actions = actions,
                        reducedMotion = reducedMotion,
                        compact = true,
                        indicatorCompact = true,
                        plainIndicator = true,
                        modifier = Modifier.animateItem(
                            fadeInSpec = todayMotionSpec(reducedMotion),
                            placementSpec = todayMotionSpec(reducedMotion),
                            fadeOutSpec = todayMotionSpec(reducedMotion),
                        ),
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
                                        indicatorCompact = true,
                                        plainIndicator = true,
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
                                        indicatorCompact = true,
                                        plainIndicator = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "today-wrap-up-section") {
                TodaySectionAnchor(
                    label = stringResource(R.string.wrap_up_today_confirm),
                    icon = Icons.Default.Flag,
                    modifier = Modifier
                        .animateItem()
                        .padding(top = 8.dp, bottom = 6.dp),
                )
            }
            item(key = "today-wrap-up-action") {
                Column(
                    modifier = Modifier
                        .animateItem()
                        .animateContentSize(animationSpec = todayMotionSpec(reducedMotion)),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TodayWrapUpAction(
                        wrapUpText = wrapUpButtonText,
                        wrapUpEnabled = canWrapUpToday,
                        doMoreEnabled = todayPlan.pullInCandidates.isNotEmpty(),
                        onDoMoreClick = { isUseExtraTimeExpanded = true },
                        onWrapUpClick = { showWrapUpDialog = true },
                    )
                    TodayTomorrowReveal(
                        expanded = todayPlan.pullInCandidates.isNotEmpty() && isUseExtraTimeExpanded,
                        tasks = todayPlan.pullInCandidates,
                        sourceDocuments = sourceDocuments,
                        onInfoClick = { showUseExtraTimeInfo = true },
                        onCollapseClick = { isUseExtraTimeExpanded = false },
                        onPullIn = { taskId -> onTaskAction(today, taskId, TodaySessionTaskAction.PullIn) },
                        reducedMotion = reducedMotion,
                    )
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
    AnimatedVisibility(
        visible = visibleNotice != null,
        enter = fadeIn(todayMotionSpec(reducedMotion)) +
            slideInVertically(todayMotionSpec(reducedMotion)) { -it / 2 },
        exit = fadeOut(todayMotionSpec(reducedMotion)) +
            slideOutVertically(todayMotionSpec(reducedMotion)) { -it / 2 },
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        TodayTopNotification(message = visibleNotice.orEmpty())
    }
}
}

@Composable
private fun TodayHeader(
    todayPlan: TodaySessionPlan,
    clockPressure: TodayClockPressure,
    isTodayClosed: Boolean,
    onAdjustClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = todayPlan.headerTitle(clockPressure, isTodayClosed)
    val dayBoundary = todayPlan.headerDayBoundary(clockPressure, isTodayClosed)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (dayBoundary != null) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Button(
                    onClick = onAdjustClick,
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .size(32.dp),
                    shape = CircleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RenSelectedCardSurface,
                        contentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.adjust_time),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = dayBoundary,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TodayHeaderSubtitle(
    message: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = color.copy(alpha = 0.86f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TodaySessionPlan.headerTitle(
    clockPressure: TodayClockPressure,
    isTodayClosed: Boolean,
): String = when {
    isTodayClosed -> "Wrapped"
    outsideTodayBudgetMinutes > 0 -> "Over budget"
    activeWorkMinutes == 0 -> "All clear"
    else -> clockPressure.headerTitle()
}

@Composable
private fun TodayClockPressure.headerTitle(): String = when (status) {
    TodayClockPressureStatus.Clear -> "All clear"
    TodayClockPressureStatus.Plenty -> "You're okay"
    TodayClockPressureStatus.StartSoon -> "Start soon"
    TodayClockPressureStatus.ActNow,
    TodayClockPressureStatus.DoesNotFit,
    -> "Start now"
}

@Composable
private fun TodaySessionPlan.headerMessage(
    clockPressure: TodayClockPressure,
    isTodayClosed: Boolean,
): String? = when {
    isTodayClosed -> "Today is closed."
    outsideTodayBudgetMinutes > 0 -> {
        val outsideLeaves = wontFitTodayTasks.size
        if (outsideLeaves == 1) {
            "1 leaf needs moving or more time."
        } else {
            "$outsideLeaves leaves need moving or more time."
        }
    }
    activeWorkMinutes == 0 -> "Nothing waiting for today."
    else -> clockPressure.headerMessage()
}

@Composable
private fun TodayClockPressure.headerMessage(): String? = when (status) {
    TodayClockPressureStatus.Clear -> "Nothing waiting for today."
    TodayClockPressureStatus.Plenty,
    TodayClockPressureStatus.StartSoon,
    TodayClockPressureStatus.ActNow,
    -> null
    TodayClockPressureStatus.DoesNotFit -> "Short by ${formatMinutes(-bufferMinutes)}."
}

private fun TodaySessionPlan.headerDayBoundary(
    clockPressure: TodayClockPressure,
    isTodayClosed: Boolean,
): String? = when {
    isTodayClosed -> null
    else -> "Day ends in ${formatMinutes(clockPressure.minutesUntilReset)}."
}

@Composable
private fun TodaySessionPlan.headerColor(
    clockPressure: TodayClockPressure,
    isTodayClosed: Boolean,
): Color = when {
    isTodayClosed -> MaterialTheme.colorScheme.onSurfaceVariant
    outsideTodayBudgetMinutes > 0 -> MaterialTheme.colorScheme.error
    else -> clockPressure.headerColor()
}

private val TodaySessionPlan.outsideTodayBudgetMinutes: Int
    get() = maxOf(overflowMinutes, overPlannedMinutes)

@Composable
private fun TodayClockPressure.headerColor(): Color = when (status) {
    TodayClockPressureStatus.DoesNotFit,
    TodayClockPressureStatus.ActNow,
    -> MaterialTheme.colorScheme.error
    TodayClockPressureStatus.StartSoon -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
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
    val animatedRemainingLeaves by animateIntAsState(
        targetValue = todayPlan.doTodayTasks.size + todayPlan.pulledInTasks.size,
        animationSpec = todayMotionSpec(reducedMotion),
        label = "today-budget-remaining-leaves",
    )
    val animatedDoneLeaves by animateIntAsState(
        targetValue = todayPlan.doneTodayTasks.size,
        animationSpec = todayMotionSpec(reducedMotion),
        label = "today-budget-done-leaves",
    )
    val animatedMovedLeaves by animateIntAsState(
        targetValue = todayPlan.wontFitTodayTasks.size + todayPlan.movedLaterTasks.size,
        animationSpec = todayMotionSpec(reducedMotion),
        label = "today-budget-moved-leaves",
    )
    val balanceValueColor by animateColorAsState(
        targetValue = if (pressureMinutes > 0) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.86f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
        },
        animationSpec = todayMotionSpec(reducedMotion),
        label = "today-budget-balance-color",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = todayMotionSpec(reducedMotion))
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TodayBalanceToken(
                label = "Day",
                value = formatMinutes(animatedAvailableMinutes),
                icon = Icons.Default.Timer,
                modifier = Modifier.weight(1f),
            )
            TodayBalanceToken(
                label = "Planned",
                value = formatMinutes(animatedPlannedMinutes),
                icon = Icons.AutoMirrored.Filled.Assignment,
                modifier = Modifier.weight(1f),
            )
            TodayBalanceToken(
                label = if (pressureMinutes > 0) "Over" else "Free",
                value = formatMinutes(animatedRemainingOrOverMinutes),
                icon = if (pressureMinutes > 0) Icons.Default.WarningAmber else Icons.Default.Schedule,
                valueColor = balanceValueColor,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TodayBalanceToken(
                label = "Left",
                value = leafCountLabel(animatedRemainingLeaves),
                icon = Icons.Default.Eco,
                modifier = Modifier.weight(1f),
            )
            TodayBalanceToken(
                label = "Done",
                value = leafCountLabel(animatedDoneLeaves),
                icon = Icons.Default.CheckCircle,
                modifier = Modifier.weight(1f),
            )
            TodayBalanceToken(
                label = "Moved",
                value = leafCountLabel(animatedMovedLeaves),
                icon = Icons.Default.RestartAlt,
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))
    }
}

@Composable
private fun TodayBalanceToken(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                modifier = Modifier.size(13.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun leafCountLabel(count: Int): String =
    pluralStringResource(R.plurals.study_leaf_count, count, count)

@Composable
private fun TodayWrapUpAction(
    wrapUpText: String,
    wrapUpEnabled: Boolean,
    doMoreEnabled: Boolean,
    onDoMoreClick: () -> Unit,
    onWrapUpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullContainer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f)
    val pullContent = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    val pullBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)
    val wrapContainer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
    val wrapContent = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
    val wrapBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
    val disabledContainer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f)
    val disabledContent = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TodayWrapUpBranchDiagram()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onDoMoreClick,
                enabled = doMoreEnabled,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, pullBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = pullContainer,
                    contentColor = pullContent,
                    disabledContainerColor = disabledContainer,
                    disabledContentColor = disabledContent,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Eco,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.pull_in_next_task),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onWrapUpClick,
                enabled = wrapUpEnabled,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, wrapBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = wrapContainer,
                    contentColor = wrapContent,
                    disabledContainerColor = disabledContainer,
                    disabledContentColor = disabledContent,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = wrapUpText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodayTomorrowReveal(
    expanded: Boolean,
    tasks: List<GeneratedStudyBlock>,
    sourceDocuments: List<StudySourceDocument>,
    onInfoClick: () -> Unit,
    onCollapseClick: () -> Unit,
    onPullIn: (String) -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val firstLeafBringIntoViewRequester = remember { BringIntoViewRequester() }
    val firstTaskId = tasks.firstOrNull()?.id

    LaunchedEffect(expanded, firstTaskId) {
        if (expanded && firstTaskId != null) {
            delay(if (reducedMotion) 0L else RenMotionDurationMillis.toLong())
            firstLeafBringIntoViewRequester.bringIntoView()
        }
    }

    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(todayMotionSpec(reducedMotion)) +
            expandVertically(
                animationSpec = todayMotionSpec(reducedMotion),
                expandFrom = Alignment.Top,
            ),
        exit = fadeOut(todayMotionSpec(reducedMotion)) +
            shrinkVertically(
                animationSpec = todayMotionSpec(reducedMotion),
                shrinkTowards = Alignment.Top,
            ),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = todayMotionSpec(reducedMotion)),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Eco,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.64f),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.pull_ahead_suggestions),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        IconButton(
                            onClick = onInfoClick,
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = stringResource(R.string.use_extra_time_info),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        IconButton(
                            onClick = onCollapseClick,
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExpandLess,
                                contentDescription = stringResource(R.string.collapse_section),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
                tasks.forEachIndexed { index, task ->
                    TomorrowLeafRow(
                        task = task,
                        sourceDocuments = sourceDocuments,
                        onPullIn = { onPullIn(task.id) },
                        modifier = if (index == 0) {
                            Modifier.bringIntoViewRequester(firstLeafBringIntoViewRequester)
                        } else {
                            Modifier
                        },
                    )
                    if (index < tasks.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TomorrowLeafRow(
    task: GeneratedStudyBlock,
    sourceDocuments: List<StudySourceDocument>,
    onPullIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadataText = todayTaskMetadataText(
        task = task,
        showDuration = true,
        showTaskType = true,
    )
    val sourceText = todayTaskSourceText(task, sourceDocuments)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Eco,
            contentDescription = stringResource(R.string.pull_ahead_suggestions),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.64f),
            modifier = Modifier.size(16.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (metadataText != null) {
                Text(
                    text = metadataText,
                    style = MaterialTheme.typography.bodySmall,
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
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            TextButton(
                onClick = onPullIn,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                ),
            ) {
                Text(
                    text = stringResource(R.string.pull_in),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TodayWrapUpBranchDiagram(
    modifier: Modifier = Modifier,
) {
    val branchColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
    ) {
        val centerX = size.width / 2f
        val splitY = size.height * 0.36f
        val endY = size.height - 1.dp.toPx()
        val leftEndX = size.width * 0.25f
        val rightEndX = size.width * 0.75f
        val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)

        drawLine(
            color = branchColor,
            start = androidx.compose.ui.geometry.Offset(centerX, 0f),
            end = androidx.compose.ui.geometry.Offset(centerX, splitY),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )

        val leftPath = Path().apply {
            moveTo(centerX, splitY)
            cubicTo(
                centerX - size.width * 0.10f,
                splitY,
                leftEndX + size.width * 0.08f,
                endY,
                leftEndX,
                endY,
            )
        }
        val rightPath = Path().apply {
            moveTo(centerX, splitY)
            cubicTo(
                centerX + size.width * 0.10f,
                splitY,
                rightEndX - size.width * 0.08f,
                endY,
                rightEndX,
                endY,
            )
        }
        drawPath(path = leftPath, color = branchColor, style = stroke)
        drawPath(path = rightPath, color = branchColor, style = stroke)
    }
}

@Composable
private fun TimeBudgetDialog(
    availableMinutes: Int,
    baseAvailableMinutes: Int,
    maxAvailableMinutes: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onSetAvailableMinutes: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    reducedMotion: Boolean,
) {
    val hasTimeOverride = availableMinutes != baseAvailableMinutes

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = todayMotionSpec(reducedMotion)),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.today_time_budget_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close_time_budget),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f))

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = stringResource(R.string.today_available_time),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = stringResource(R.string.today_available_time_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TodaySetupStepButton(
                        icon = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.decrease_available_time),
                        enabled = availableMinutes > 0,
                        onClick = onDecrease,
                    )
                    TodayTimeValueEditor(
                        availableMinutes = availableMinutes,
                        maxAvailableMinutes = maxAvailableMinutes,
                        onMinutesChange = onSetAvailableMinutes,
                        modifier = Modifier.weight(1f),
                    )
                    TodaySetupStepButton(
                        icon = Icons.Default.Add,
                        contentDescription = stringResource(R.string.increase_available_time),
                        enabled = availableMinutes < maxAvailableMinutes,
                        onClick = onIncrease,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasTimeOverride) {
                        TextButton(
                            onClick = onReset,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.reset_to_plan_time))
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(stringResource(R.string.done_time_budget))
                    }
                }
            }
        }
    }
}

@Composable
private fun TodaySetupSection(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            content()
        }
    }
}

@Composable
private fun TodayTimeValueEditor(
    availableMinutes: Int,
    maxAvailableMinutes: Int,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draftHours by rememberSaveable { mutableStateOf((availableMinutes / 60).toString()) }
    var draftMinutes by rememberSaveable { mutableStateOf((availableMinutes % 60).toString()) }
    val focusManager = LocalFocusManager.current
    fun syncDraft(minutes: Int) {
        draftHours = (minutes / 60).toString()
        draftMinutes = (minutes % 60).toString()
    }
    fun applyDraft(hoursText: String = draftHours, minutesText: String = draftMinutes) {
        val hours = hoursText.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val minutes = minutesText.toIntOrNull()?.coerceIn(0, 59) ?: 0
        onMinutesChange((hours * 60 + minutes).coerceIn(0, maxAvailableMinutes))
    }
    fun finishEditing() {
        applyDraft()
        focusManager.clearFocus()
    }

    LaunchedEffect(availableMinutes) {
        syncDraft(availableMinutes)
    }

    Surface(
        modifier = modifier.height(88.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TodayTimePartField(
                value = draftHours,
                onValueChange = { value ->
                    draftHours = value.filter(Char::isDigit).take(2)
                    applyDraft(hoursText = draftHours)
                },
                label = stringResource(R.string.today_time_budget_hours_suffix),
                placeholder = stringResource(R.string.today_time_budget_hours_placeholder),
                onDone = ::finishEditing,
                modifier = Modifier.weight(1f),
            )
            Surface(
                modifier = Modifier
                    .width(1.dp)
                    .height(44.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f),
                content = {},
            )
            TodayTimePartField(
                value = draftMinutes,
                onValueChange = { value ->
                    draftMinutes = value.filter(Char::isDigit).take(2)
                    applyDraft(minutesText = draftMinutes)
                },
                label = stringResource(R.string.today_time_budget_minutes_suffix),
                placeholder = stringResource(R.string.today_time_budget_minutes_placeholder),
                onDone = ::finishEditing,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TodayTimePartField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
            maxLines = 1,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            textStyle = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (value.isBlank()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
                                textAlign = TextAlign.Center,
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )
    }
}

@Composable
private fun TodaySetupStepButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = if (enabled) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
            } else {
                Color.Transparent
            },
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.22f else 0.10f),
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
                    },
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun TodayRebalancedRow(
    title: String,
    message: String,
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
                        imageVector = Icons.Default.RestartAlt,
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
        }
    }
}

@Composable
private fun TodaySectionHeader(
    title: String,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    countLabel: String? = null,
    countIcon: ImageVector? = null,
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
                Row(
                    modifier = Modifier.alignByBaseline(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (countIcon != null) {
                        Icon(
                            imageVector = countIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Text(
                        text = countLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            ),
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
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
private fun TodayTopNotification(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
private fun TodayDoNowCard(
    task: GeneratedStudyBlock,
    sourceDocuments: List<StudySourceDocument>,
    actions: List<TodayTaskActionSpec>,
    onStartFocus: () -> Unit,
    onMarkDone: () -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember(task.id) { mutableStateOf(false) }
    val sourceText = todayTaskSourceText(task, sourceDocuments)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = todayMotionSpec(reducedMotion)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)),
    ) {
        Column(
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                        ),
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
                                contentDescription = stringResource(R.string.today_task_actions),
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onStartFocus,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RenSelectedCardSurface,
                        contentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.start_focus),
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
private fun TodayDoNowSectionLabel(
    modifier: Modifier = Modifier,
) {
    TodaySectionAnchor(
        label = stringResource(R.string.today_do_now),
        icon = Icons.Default.Eco,
        modifier = modifier,
    )
}

@Composable
private fun TodaySectionAnchor(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.68f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
            maxLines = 1,
        )
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
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    indicatorCompact: Boolean = false,
    plainIndicator: Boolean = false,
) {
    var menuExpanded by remember(task.id) { mutableStateOf(false) }
    val sourceText = if (showSource) todayTaskSourceText(task, sourceDocuments) else null
    val supportingTextValue = supportingText.orEmpty()
    val metadataText = todayTaskMetadataText(
        task = task,
        showDuration = showDuration,
        showTaskType = showTaskType,
    )
    val trailingMode = when {
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
    val trailingControlSize = if (compact) 34.dp else 40.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = todayMotionSpec(reducedMotion)),
        shape = RoundedCornerShape(shape),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f)),
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)),
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
                extraCompact = indicatorCompact,
                plain = plainIndicator,
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
            Box(
                modifier = if (trailingMode == TodayTaskTrailingMode.PrimaryAction) {
                    Modifier
                } else {
                    Modifier.size(trailingControlSize)
                },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = trailingMode,
                    transitionSpec = { renFadeThroughTransform(reducedMotion = reducedMotion) },
                    label = "today-task-row-action",
                ) { mode ->
                    when (mode) {
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
}

@Composable
private fun TodayTaskIndicator(
    position: Int?,
    icon: ImageVector?,
    contentDescription: String?,
    color: Color? = null,
    compact: Boolean = false,
    extraCompact: Boolean = false,
    plain: Boolean = false,
) {
    val containerSize = when {
        extraCompact -> 28.dp
        compact -> 34.dp
        else -> 40.dp
    }
    val iconSize = when {
        extraCompact -> 15.dp
        compact -> 18.dp
        else -> 22.dp
    }
    val numberStyle = when {
        extraCompact -> MaterialTheme.typography.labelMedium
        compact -> MaterialTheme.typography.labelLarge
        else -> MaterialTheme.typography.titleSmall
    }
    val indicatorColor = color ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.64f)
    val containerColor = if (color == null) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f)
    } else {
        indicatorColor.copy(alpha = 0.12f)
    }
    val borderColor = indicatorColor.copy(alpha = if (color == null) 0.24f else 0.32f)
    if (plain) {
        Box(
            modifier = Modifier.size(containerSize),
            contentAlignment = Alignment.Center,
        ) {
            when {
                position != null -> Text(
                    text = position.toString(),
                    style = numberStyle,
                    fontWeight = FontWeight.Bold,
                    color = indicatorColor,
                )
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = indicatorColor,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
        return
    }
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
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val closingRemainingMinutes = (todayPlan.availableMinutes - todayPlan.untrackedCompletedMinutes)
        .coerceAtLeast(0)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.74f),
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(R.string.wrap_up_today_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.wrap_up_today_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WrapUpDecisionRow(
                        icon = Icons.Default.CheckCircle,
                        label = stringResource(R.string.completed),
                        value = wrapUpSummaryValue(
                            count = todayPlan.doneTodayTasks.size,
                            minutes = todayPlan.completedMinutes,
                        ),
                    )
                    WrapUpDecisionRow(
                        icon = Icons.Default.Eco,
                        label = stringResource(R.string.wrap_up_carry_forward_label),
                        value = wrapUpSummaryValue(
                            count = todayPlan.unfinishedWorkForwardTasks.size,
                            minutes = todayPlan.unfinishedWorkForwardMinutes,
                        ),
                    )
                    if (todayPlan.removedFromPlanTasks.isNotEmpty() || todayPlan.removedMinutes > 0) {
                        WrapUpDecisionRow(
                            icon = Icons.Default.Close,
                            label = stringResource(R.string.removed_metric_label),
                            value = wrapUpSummaryValue(
                                count = todayPlan.removedFromPlanTasks.size,
                                minutes = todayPlan.removedMinutes,
                            ),
                        )
                    }
                    WrapUpDecisionRow(
                        icon = Icons.Default.Timer,
                        label = stringResource(R.string.wrap_up_unused_time_label),
                        value = formatMinutes(closingRemainingMinutes),
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))

                Text(
                    text = stringResource(R.string.wrap_up_today_closes_day),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        ),
                    ) {
                        Text(stringResource(R.string.wrap_up_keep_open))
                    }
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(stringResource(R.string.wrap_up_end_day))
                    }
                }
            }
        }
    }
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
private fun WrapUpDecisionRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun wrapUpSummaryValue(
    count: Int,
    minutes: Int,
): String {
    val leaves = leafCountLabel(count)
    return if (minutes > 0) {
        "$leaves, ${formatMinutes(minutes)}"
    } else {
        leaves
    }
}

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
    PrimaryAction,
    Menu,
    Empty,
}

private fun <T> todayMotionSpec(reducedMotion: Boolean) = tween<T>(
    durationMillis = if (reducedMotion) 0 else RenMotionDurationMillis,
    easing = RenMotionEasing,
)

private const val TimeAdjustmentStepMinutes = 30
