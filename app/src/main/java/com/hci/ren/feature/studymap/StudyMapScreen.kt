package com.hci.ren.feature.studymap

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hci.ren.R
import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.isSelectableDeadlineUtc
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.plangeneration.StudyBlockDifficulty
import com.hci.ren.feature.plangeneration.StudySourceDocument
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.StudyTaskType
import com.hci.ren.ui.components.PlanFlowCircleAction
import com.hci.ren.ui.components.PlanLandingScaffold
import com.hci.ren.ui.motion.isReducedMotionEnabled
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class AdjustmentSheet { PlanEdit, Rename, Deadline, DailyTime, Scope, Continue }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyMapScreen(
    plan: GeneratedStudyPlan?,
    preferences: PlanSetupSubmission?,
    modifier: Modifier = Modifier,
    dailyMinutesOverride: Int? = null,
    dailyAvailableMinutesByDate: Map<String, Int> = emptyMap(),
    taskProgressById: Map<String, StudyTaskProgress> = emptyMap(),
    acceptedTightPlan: Boolean = false,
    changeMessage: String? = null,
    suggestedDeadline: String? = null,
    recommendedDaysBalanced: Int = 0,
    recommendedDaysIntensive: Int = 0,
    onBack: () -> Unit,
    onCreateProject: () -> Unit,
    onOpenToday: () -> Unit,
    onRenamePlan: (String) -> Unit,
    onDeletePlan: () -> Unit,
    onConsumeMessage: () -> Unit,
    onExtendDeadline: (studyDays: Int, intensive: Boolean) -> Unit = { _, _ -> },
    onApplyDeadline: (String) -> Unit,
    onIncreaseDailyTime: (Int) -> Unit,
    onReduceScope: (ScopeReduction, Set<String>) -> Unit,
    onContinueAnyway: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var selectedViewName by rememberSaveable { mutableStateOf(StudyMapView.Schedule.name) }
    val selectedView = StudyMapView.valueOf(selectedViewName)
    var adjustment by remember { mutableStateOf<AdjustmentSheet?>(null) }
    var deleteDialogOpen by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    LaunchedEffect(changeMessage) {
        if (changeMessage != null) {
            snackbar.showSnackbar(changeMessage)
            onConsumeMessage()
        }
    }

    if (plan == null || preferences == null) {
        EmptyStudyMap(
            onCreateProject = onCreateProject,
            snackbar = snackbar,
            modifier = modifier,
        )
        return
    }

    val data = buildStudyMapData(
        plan = plan,
        preferences = preferences,
        dailyMinutesOverride = dailyMinutesOverride,
        dailyAvailableMinutesByDate = dailyAvailableMinutesByDate,
        taskProgressById = taskProgressById,
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            StudyMapHeader(
                projectName = plan.projectName,
                deadline = relativeDeadlineLabel(preferences),
                progress = data.progress,
                completedTasks = data.completedTasks,
                totalTasks = data.activeTasks.size,
                realismStatus = data.realism.status,
                onEditPlan = { adjustment = AdjustmentSheet.PlanEdit },
                onDeletePlan = { deleteDialogOpen = true },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ProjectSummaryCard(data) }
            if (!acceptedTightPlan && data.realism.status in setOf(PlanRealismStatus.Tight, PlanRealismStatus.Unrealistic)) {
                item {
                    RealismWarningPanel(
                        realism = data.realism,
                        onAction = { adjustment = it },
                    )
                }
            }
            item {
                StudyMapViewSwitcher(
                    selected = selectedView,
                    onSelected = { selectedViewName = it.name },
                )
            }
            when (selectedView) {
                StudyMapView.Schedule -> scheduleItems(
                    data = data,
                    onOpenToday = onOpenToday,
                )
                StudyMapView.Topics -> topicItems(data)
            }
            if (plan.blocks.isEmpty()) {
                item { EmptyTasksCard(onCreateProject) }
            }
        }
    }

    if (deleteDialogOpen) {
        DeletePlanDialog(
            onDismiss = { deleteDialogOpen = false },
            onDelete = {
                onDeletePlan()
                deleteDialogOpen = false
            },
        )
    }

    when (adjustment) {
        AdjustmentSheet.PlanEdit -> PlanEditSheet(
            onDismiss = { adjustment = null },
            onAction = { adjustment = it },
        )
        AdjustmentSheet.Rename -> RenamePlanDialog(
            currentName = plan.projectName,
            onDismiss = { adjustment = null },
            onRename = { name ->
                onRenamePlan(name)
                adjustment = null
            },
        )
        AdjustmentSheet.Deadline -> DeadlineSheet(
            current = deadlineLabel(preferences),
            recommendedDaysBalanced = recommendedDaysBalanced,
            recommendedDaysIntensive = recommendedDaysIntensive,
            resetOffsetHours = preferences.studyDayResetOffsetHours,
            onDismiss = { adjustment = null },
            onExtendDeadline = { days, intensive -> onExtendDeadline(days, intensive); adjustment = null },
            onCustomDeadline = { millis ->
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(millis))
                onApplyDeadline(date); adjustment = null
            },
        )
        AdjustmentSheet.DailyTime -> DailyTimeSheet(
            currentMinutes = data.dailyMinutes,
            blockMinutes = suggestedBlockMinutes(plan.blocks),
            onDismiss = { adjustment = null },
            onApply = { onIncreaseDailyTime(it); adjustment = null },
        )
        AdjustmentSheet.Scope -> ScopeSheet(
            plan = plan,
            onDismiss = { adjustment = null },
            onApply = { strategy, topics -> onReduceScope(strategy, topics); adjustment = null },
        )
        AdjustmentSheet.Continue -> ContinueAnywayDialog(
            onDismiss = { adjustment = null },
            onContinue = { onContinueAnyway(); adjustment = null },
        )
        null -> Unit
    }
}

@Composable
private fun StudyMapHeader(
    projectName: String,
    deadline: String? = null,
    progress: Float? = null,
    completedTasks: Int? = null,
    totalTasks: Int? = null,
    realismStatus: PlanRealismStatus? = null,
    onEditPlan: (() -> Unit)? = null,
    onDeletePlan: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasPlanActions = onEditPlan != null || onDeletePlan != null
    val title = deadline
        ?.let { stringResource(R.string.study_plan_title_with_deadline, projectName.safeStudyProjectTitle(), it) }
        ?: projectName.safeStudyProjectTitle()

    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StudyMapHeaderTitle(
                    title = title,
                    modifier = Modifier.weight(1f),
                )
                if (hasPlanActions) {
                    Spacer(Modifier.width(2.dp))
                    StudyPlanMenu(
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = it },
                        onEditPlan = onEditPlan,
                        onDeletePlan = onDeletePlan,
                        modifier = Modifier.offset(x = 10.dp),
                    )
                }
            }
            if (progress != null && realismStatus != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SubtlePlanProgressBar(
                        progress = progress,
                        modifier = Modifier.weight(1f),
                    )
                    if (completedTasks != null && totalTasks != null) {
                        Text(
                            text = stringResource(
                                R.string.tasks_completed_compact,
                                completedTasks,
                                totalTasks,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyMapHeaderTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StudyPlanMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEditPlan: (() -> Unit)?,
    onDeletePlan: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.study_plan_options),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_plan)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    onEditPlan?.invoke()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_plan)) },
                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    onDeletePlan?.invoke()
                },
            )
        }
    }
}

@Composable
private fun RenamePlanDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by rememberSaveable(currentName) { mutableStateOf(currentName) }
    val trimmedName = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_plan_name)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                label = { Text(stringResource(R.string.plan_name)) },
                singleLine = true,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(
                enabled = trimmedName.isNotBlank(),
                onClick = { onRename(trimmedName) },
            ) {
                Text(stringResource(R.string.apply))
            }
        },
    )
}

@Composable
private fun SubtlePlanProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (safeProgress > 0f) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(safeProgress)
                        .height(4.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.68f),
                ) {}
            }
        }
    }
}

@Composable
private fun DeletePlanDialog(
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
        title = { Text(stringResource(R.string.delete_plan_title)) },
        text = { Text(stringResource(R.string.delete_plan_message)) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text(
                    text = stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

@Composable
private fun ProjectSummaryCard(data: StudyMapData) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SummaryMetric(
                label = stringResource(R.string.planned_metric_label),
                value = formatMinutes(data.totalEstimatedMinutes),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                OutlinedStatusPill(
                    label = realismLabel(data.realism.status),
                    color = realismColor(data.realism.status),
                )
            }
            SummaryMetric(
                label = stringResource(R.string.available_metric_label),
                value = formatMinutes(data.realism.availableMinutes),
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    val textAlign = if (horizontalAlignment == Alignment.End) {
        androidx.compose.ui.text.style.TextAlign.End
    } else {
        androidx.compose.ui.text.style.TextAlign.Start
    }
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = textAlign,
        )
    }
}

@Composable
private fun RealismWarningPanel(realism: PlanRealism, onAction: (AdjustmentSheet) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (realism.status == PlanRealismStatus.Tight) stringResource(R.string.plan_is_tight) else stringResource(R.string.unrealistic_plan_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(stringResource(R.string.tight_plan_message, formatMinutes(realism.remainingMinutes), formatMinutes(realism.availableMinutes ?: 0)), style = MaterialTheme.typography.bodyMedium)
            AdjustmentAction(R.string.choose_material, R.string.choose_material_subtitle) { onAction(AdjustmentSheet.Scope) }
            AdjustmentAction(R.string.increase_daily_time, R.string.increase_daily_time_subtitle) { onAction(AdjustmentSheet.DailyTime) }
            AdjustmentAction(R.string.extend_deadline, R.string.extend_deadline_subtitle) { onAction(AdjustmentSheet.Deadline) }
            AdjustmentAction(R.string.continue_anyway, R.string.continue_anyway_short_subtitle) { onAction(AdjustmentSheet.Continue) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanEditSheet(
    onDismiss: () -> Unit,
    onAction: (AdjustmentSheet) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.edit_plan),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.edit_plan_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AdjustmentAction(R.string.change_plan_name, R.string.change_plan_name_subtitle) {
                onAction(AdjustmentSheet.Rename)
            }
            AdjustmentAction(R.string.change_deadline, R.string.change_deadline_subtitle) {
                onAction(AdjustmentSheet.Deadline)
            }
            AdjustmentAction(R.string.available_time, R.string.available_time_subtitle) {
                onAction(AdjustmentSheet.DailyTime)
            }
            AdjustmentAction(R.string.choose_material, R.string.choose_material_subtitle) {
                onAction(AdjustmentSheet.Scope)
            }
        }
    }
}

@Composable
private fun AdjustmentAction(title: Int, subtitle: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyMapViewSwitcher(selected: StudyMapView, onSelected: (StudyMapView) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        StudyMapView.entries.forEachIndexed { index, view ->
            val isSelected = view == selected
            SegmentedButton(
                selected = isSelected,
                onClick = { onSelected(view) },
                modifier = Modifier.weight(1f),
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = StudyMapView.entries.size,
                ),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    activeContentColor = MaterialTheme.colorScheme.primary,
                    activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                ),
                icon = {},
            ) {
                Text(
                    if (view == StudyMapView.Schedule) stringResource(R.string.schedule_view) else stringResource(R.string.topics_view),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.scheduleItems(
    data: StudyMapData,
    onOpenToday: () -> Unit,
) {
    if (data.schedule.days.isNotEmpty()) {
        item(key = "schedule-timeline") {
            StudyScheduleTimeline(
                days = data.schedule.days,
                documents = data.plan.sourceDocuments,
                studyToday = currentStudyCalendar(data.preferences).toStudyDate(),
                onOpenToday = onOpenToday,
                modifier = Modifier.animateItem(),
            )
        }
    }
    if (data.schedule.unscheduledTasks.isNotEmpty()) {
        item(key = "unscheduled") {
            UnscheduledWorkCard(
                tasks = data.schedule.unscheduledTasks,
                documents = data.plan.sourceDocuments,
                modifier = Modifier.animateItem(),
            )
        }
    }
    if (data.activeTasks.isNotEmpty() && data.activeTasks.all { it.status == StudyTaskStatus.Completed }) {
        item { CompletionCard() }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.topicItems(data: StudyMapData) {
    items(data.plan.topics, key = { "topic-${it.id}" }) { topic ->
        val tasks = data.plan.blocks.filter { topic.id in it.topicIds && it.status != StudyTaskStatus.ExcludedByUser }
        TopicSection(topic.title, tasks, Modifier.animateItem())
    }
}

@Composable
private fun StudyScheduleTimeline(
    days: List<StudyScheduleDay>,
    documents: List<StudySourceDocument>,
    studyToday: String,
    onOpenToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        days.forEachIndexed { index, day ->
            StudyDayMapCard(
                day = day,
                documents = documents,
                studyToday = studyToday,
                initiallyExpanded = index == 0,
                isFirst = index == 0,
                isLast = index == days.lastIndex,
                onOpenToday = onOpenToday,
            )
        }
    }
}

@Composable
private fun StudyDayMapCard(
    day: StudyScheduleDay,
    documents: List<StudySourceDocument>,
    studyToday: String,
    initiallyExpanded: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onOpenToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isToday = day.date == studyToday
    val isCompleted = day.tasks.isNotEmpty() && day.tasks.all { it.status == StudyTaskStatus.Completed }
    var manuallyExpanded by remember(day.date) { mutableStateOf(if (isToday) false else initiallyExpanded) }
    val expanded = !isToday && manuallyExpanded

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            DayTimelineMarker(
                isFirst = isFirst,
                isLast = isLast && !expanded,
                isToday = isToday,
                isCompleted = isCompleted,
                expanded = expanded,
            )
            Spacer(Modifier.width(12.dp))
            Surface(
                onClick = {
                    if (isToday) {
                        onOpenToday()
                    } else {
                        manuallyExpanded = !manuallyExpanded
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    StudyDayCardHeader(
                        day = day,
                        studyToday = studyToday,
                        isToday = isToday,
                        expanded = expanded,
                    )

                    if (!expanded && day.tasks.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.next_task_preview, day.tasks.first().title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isToday) {
                                Text(
                                    text = stringResource(R.string.open_today),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (expanded) {
            day.tasks.forEachIndexed { index, task ->
                TimelineTaskBranchRow(
                    task = task,
                    source = taskSourceLabel(task, documents),
                    isFirstBranch = index == 0,
                    isLastBranch = index == day.tasks.lastIndex,
                    isLastDay = isLast,
                )
            }
            if (!isLast) {
                TimelineDayGap()
            }
        }
    }
}

@Composable
private fun DayTimelineMarker(
    isFirst: Boolean,
    isLast: Boolean,
    isToday: Boolean,
    isCompleted: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    val nodeBorderColor = if (isToday) activeColor.copy(alpha = 0.72f) else mutedColor
    val dotSize = if (isToday) 13.dp else 10.dp
    val dotTop = 19.dp
    Box(
        modifier = modifier
            .width(18.dp)
            .height(if (expanded) 58.dp else 88.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val dotRadius = dotSize.toPx() / 2f
            val dotCenterY = dotTop.toPx() + dotRadius
            if (!isFirst) {
                drawLine(
                    color = mutedColor,
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, dotCenterY - dotRadius),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            if (!isLast) {
                drawLine(
                    color = mutedColor,
                    start = Offset(centerX, dotCenterY + dotRadius),
                    end = Offset(centerX, size.height),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Surface(
            modifier = Modifier
                .padding(top = dotTop)
                .size(dotSize),
            shape = CircleShape,
            color = if (isCompleted) activeColor else Color.Transparent,
            border = if (isCompleted) null else BorderStroke(1.4.dp, nodeBorderColor),
        ) {}
    }
}

@Composable
private fun TimelineTaskBranchRow(
    task: GeneratedStudyBlock,
    source: String?,
    isFirstBranch: Boolean,
    isLastBranch: Boolean,
    isLastDay: Boolean,
) {
    var rowHeightPx by remember { mutableIntStateOf(0) }
    val rowHeight = with(LocalDensity.current) { rowHeightPx.toDp() }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (rowHeightPx > 0) {
            TaskBranchConnector(
                isFirstBranch = isFirstBranch,
                isLastBranch = isLastBranch,
                isLastDay = isLastDay,
                modifier = Modifier
                    .width(54.dp)
                    .height(rowHeight),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowHeightPx = it.height },
            verticalAlignment = Alignment.Top,
        ) {
            Spacer(Modifier.width(54.dp))
            TaskRowTextContent(
                task = task,
                source = source,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 7.dp),
            )
            if (task.status != StudyTaskStatus.NotStarted) {
                Spacer(Modifier.width(10.dp))
                Box(Modifier.padding(top = 7.dp)) {
                    StatusPill(statusLabel(task.status), statusContainer(task.status), statusContent(task.status))
                }
            }
        }

        TaskBullet(
            status = task.status,
            nodeSize = 10.dp,
            completeIconSize = 12.dp,
            borderWidth = 1.25.dp,
            modifier = Modifier.padding(start = 24.dp, top = 7.dp),
        )
    }
}

@Composable
private fun TaskBranchConnector(
    isFirstBranch: Boolean,
    isLastBranch: Boolean,
    isLastDay: Boolean,
    modifier: Modifier = Modifier,
) {
    val mutedColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    Canvas(modifier) {
        val branchX = 9.dp.toPx()
        val nodeCenterX = 39.dp.toPx()
        val nodeCenterY = 22.dp.toPx()
        val exitY = size.height
        if (!isLastDay || !isLastBranch) {
            drawLine(
                color = mutedColor,
                start = Offset(branchX, 0f),
                end = Offset(branchX, exitY),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        } else {
            drawLine(
                color = mutedColor,
                start = Offset(branchX, 0f),
                end = Offset(branchX, nodeCenterY),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        if (isFirstBranch) {
            drawLine(
                color = mutedColor,
                start = Offset(branchX, nodeCenterY),
                end = Offset(nodeCenterX, nodeCenterY),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        val childRailStart = if (isFirstBranch) nodeCenterY else 0f
        val childRailEnd = if (isLastBranch) nodeCenterY else exitY
        if (childRailStart < childRailEnd) {
            drawLine(
                color = mutedColor,
                start = Offset(nodeCenterX, childRailStart),
                end = Offset(nodeCenterX, childRailEnd),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun TimelineDayGap(height: Dp = 16.dp) {
    val mutedColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    Canvas(
        modifier = Modifier
            .width(54.dp)
            .height(height),
    ) {
        val branchX = 9.dp.toPx()
        drawLine(
            color = mutedColor,
            start = Offset(branchX, 0f),
            end = Offset(branchX, size.height),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun StudyDayCardHeader(
    day: StudyScheduleDay,
    studyToday: String,
    isToday: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val loadMeta = listOf(
        pluralStringResource(R.plurals.study_day_task_count, day.tasks.size, day.tasks.size),
        formatMinutes(day.totalScheduledMinutes),
        dayLoadLabel(day),
    ).joinToString(" \u2022 ")

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < 300.dp

            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StudyDayTitle(
                            text = dateHeading(day.date, studyToday),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        StudyDayActionIcon(isToday = isToday, expanded = expanded)
                    }
                    Text(
                        text = loadMeta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StudyDayTitle(
                        text = dateHeading(day.date, studyToday),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = loadMeta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    StudyDayActionIcon(isToday = isToday, expanded = expanded)
                }
            }
        }
    }
}

@Composable
private fun StudyDayTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
private fun StudyDayActionIcon(isToday: Boolean, expanded: Boolean) {
    Icon(
        if (isToday) Icons.AutoMirrored.Filled.KeyboardArrowRight else if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = null,
        tint = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun UnscheduledWorkCard(
    tasks: List<GeneratedStudyBlock>,
    documents: List<StudySourceDocument>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.unscheduled_tasks), stringResource(R.string.unscheduled_explanation))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            tasks.forEach { task ->
                MapTaskRow(
                    task = task,
                    source = taskSourceLabel(task, documents),
                    onClick = null,
                )
            }
        }
    }
}

@Composable
private fun MapTaskRow(
    task: GeneratedStudyBlock,
    source: String?,
    onClick: (() -> Unit)?,
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent,
        ) {
            MapTaskRowContent(task = task, source = source)
        }
    } else {
        MapTaskRowContent(task = task, source = source)
    }
}

@Composable
private fun MapTaskRowContent(
    task: GeneratedStudyBlock,
    source: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskBullet(status = task.status)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatMinutes(task.durationMinutes)} • ${taskTypeLabel(task.taskType)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (source != null) {
                Text(
                    text = source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (task.status != StudyTaskStatus.NotStarted) {
            Spacer(Modifier.width(10.dp))
            StatusPill(statusLabel(task.status), statusContainer(task.status), statusContent(task.status))
        }
    }
}

@Composable
private fun TaskRowTextContent(
    task: GeneratedStudyBlock,
    source: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = task.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${formatMinutes(task.durationMinutes)} \u2022 ${taskTypeLabel(task.taskType)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (source != null) {
            Text(
                text = source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TaskBullet(
    status: StudyTaskStatus,
    modifier: Modifier = Modifier,
    nodeSize: Dp = 14.dp,
    completeIconSize: Dp = 16.dp,
    borderWidth: Dp = 1.5.dp,
) {
    val complete = status == StudyTaskStatus.Completed
    Box(modifier = modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Box(contentAlignment = Alignment.Center) {
            if (complete) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(completeIconSize),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Surface(
                    modifier = Modifier.size(nodeSize),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(
                        width = borderWidth,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                    ),
                ) {}
            }
        }
    }
}

@Composable
private fun dayLoadLabel(day: StudyScheduleDay): String = when {
    day.isOverCapacity -> stringResource(R.string.over_capacity)
    else -> difficultyLabel(day.load)
}

@Composable
private fun difficultyLabel(difficulty: StudyBlockDifficulty): String = when (difficulty) {
    StudyBlockDifficulty.Light -> stringResource(R.string.load_light)
    StudyBlockDifficulty.Standard -> stringResource(R.string.load_standard)
    StudyBlockDifficulty.Heavy -> stringResource(R.string.load_heavy)
}

@Composable
private fun taskSourceLabel(task: GeneratedStudyBlock, documents: List<StudySourceDocument>): String? {
    val ref = task.sourceRefs.firstOrNull() ?: return null
    val document = documents.firstOrNull { it.id == ref.documentId || it.uploadDocumentId == ref.documentId }
    val documentName = document?.filename?.shortDocumentName() ?: ref.sectionTitle
    val pageLabel = when {
        ref.startPage != null && ref.endPage != null && ref.endPage != ref.startPage -> stringResource(R.string.source_page_range, ref.startPage, ref.endPage)
        ref.startPage != null -> stringResource(R.string.source_page, ref.startPage)
        else -> null
    }
    return when {
        documentName != null && pageLabel != null -> stringResource(R.string.source_with_page, documentName, pageLabel)
        documentName != null -> documentName
        pageLabel != null -> pageLabel
        else -> null
    }
}

@Composable
private fun taskTypeColor(type: StudyTaskType) = when (type) {
    StudyTaskType.Practice,
    StudyTaskType.Quiz,
    StudyTaskType.MockTest,
    -> MaterialTheme.colorScheme.tertiaryContainer
    StudyTaskType.Review,
    StudyTaskType.Summary,
    StudyTaskType.MistakeReview,
    -> MaterialTheme.colorScheme.secondaryContainer
    else -> MaterialTheme.colorScheme.primaryContainer
}

private fun taskTypeIcon(type: StudyTaskType): ImageVector = when (type) {
    StudyTaskType.Practice,
    StudyTaskType.Quiz,
    StudyTaskType.MockTest,
    -> Icons.AutoMirrored.Filled.Assignment
    StudyTaskType.Review,
    StudyTaskType.Summary,
    StudyTaskType.MistakeReview,
    -> Icons.Default.Refresh
    StudyTaskType.Memorization -> Icons.Default.AddTask
    StudyTaskType.Reading,
    StudyTaskType.Concept,
    -> Icons.AutoMirrored.Filled.MenuBook
    else -> Icons.Default.School
}

@Composable
private fun TopicSection(title: String, tasks: List<GeneratedStudyBlock>, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val progress = TaskProgressCalculator().projectProgress(tasks)
    val totalMinutes = tasks.sumOf { it.durationMinutes.coerceAtLeast(0) }
    val meta = "${progress.first} / ${progress.second} \u2022 ${formatMinutes(totalMinutes)}"
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.animateContentSize()) {
            Surface(onClick = { expanded = !expanded }, color = Color.Transparent) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            if (expanded) {
                HorizontalDivider()
                tasks.forEach { task ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        MapTaskRowContent(
                            task = task,
                            source = null,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineSheet(
    current: String,
    recommendedDaysBalanced: Int,
    recommendedDaysIntensive: Int,
    resetOffsetHours: Int,
    onDismiss: () -> Unit,
    onExtendDeadline: (studyDays: Int, intensive: Boolean) -> Unit,
    onCustomDeadline: (epochMillis: Long) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.choose_better_deadline), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            DetailRow(stringResource(R.string.current_deadline), current)
            DeadlineOption(
                title = stringResource(R.string.balanced),
                description = pluralStringResource(R.plurals.balanced_option_description, recommendedDaysBalanced, recommendedDaysBalanced),
                onClick = { onExtendDeadline(recommendedDaysBalanced, false); onDismiss() },
            )
            DeadlineOption(
                title = stringResource(R.string.intensive),
                description = pluralStringResource(R.plurals.intensive_option_description, recommendedDaysIntensive, recommendedDaysIntensive),
                onClick = { onExtendDeadline(recommendedDaysIntensive, true); onDismiss() },
            )
            DeadlineOption(
                title = stringResource(R.string.custom_deadline),
                description = stringResource(R.string.pick_your_own_date),
                onClick = { showDatePicker = true },
            )
        }
    }
    if (showDatePicker) {
        val selectableDates = remember {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    isSelectableDeadlineUtc(
                        selectedMillis = utcTimeMillis,
                        nowMillis = System.currentTimeMillis(),
                        resetOffsetHours = resetOffsetHours,
                    )
            }
        }
        val datePickerState = rememberDatePickerState(selectableDates = selectableDates)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = datePickerState.selectedDateMillis != null,
                    onClick = {
                        datePickerState.selectedDateMillis?.let(onCustomDeadline)
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.apply)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
private fun DeadlineOption(title: String, description: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyTimeSheet(currentMinutes: Int, blockMinutes: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    var selected by remember { mutableIntStateOf(currentMinutes + blockMinutes) }
    var custom by remember { mutableStateOf("") }
    val options = listOf(currentMinutes + blockMinutes to R.string.add_one_block, currentMinutes + blockMinutes * 2 to R.string.add_two_blocks)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.increase_daily_time), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            DetailRow(stringResource(R.string.current_daily_time), stringResource(R.string.minutes_per_day, formatMinutes(currentMinutes)))
            options.forEach { (minutes, label) ->
                Surface(onClick = { selected = minutes }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = if (selected == minutes) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(label), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(formatMinutes(minutes))
                    }
                }
            }
            OutlinedTextField(value = custom, onValueChange = { custom = it.filter(Char::isDigit).take(4); custom.toIntOrNull()?.let { value -> if (value in 1..1_440) selected = value } }, label = { Text(stringResource(R.string.custom_time)) }, suffix = { Text(stringResource(R.string.minutes_per_day_suffix)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onApply(selected.coerceIn(1, 1_440)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.apply_changes)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeSheet(plan: GeneratedStudyPlan, onDismiss: () -> Unit, onApply: (ScopeReduction, Set<String>) -> Unit) {
    val previews = remember(plan.blocks) { PlanAdjustmentService().scopePreviews(plan.blocks) }
    var selected by remember { mutableStateOf(ScopeReduction.ChooseTopics) }
    val topics = remember { mutableStateListOf<String>() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.choose_material_sheet_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            previews.forEach { preview ->
                val title = when (preview.strategy) {
                    ScopeReduction.ChooseTopics -> R.string.choose_material_to_keep
                }
                Surface(onClick = { selected = preview.strategy }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = if (selected == preview.strategy) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(14.dp)) {
                        Text(stringResource(title), fontWeight = FontWeight.SemiBold)
                        if (preview.savedMinutes > 0) Text(stringResource(R.string.saves_about, formatMinutes(preview.savedMinutes)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(stringResource(R.string.select_topics), fontWeight = FontWeight.SemiBold)
            plan.topics.forEach { topic ->
                Surface(
                    onClick = { if (topic.id in topics) topics.remove(topic.id) else topics.add(topic.id) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = topic.id in topics, onCheckedChange = { checked -> if (checked) topics.add(topic.id) else topics.remove(topic.id) })
                        Text(topic.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Button(onClick = { onApply(selected, topics.toSet()) }, enabled = topics.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.apply_changes)) }
        }
    }
}

@Composable
private fun ContinueAnywayDialog(onDismiss: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.WarningAmber, null) },
        title = { Text(stringResource(R.string.continue_tight_title)) },
        text = { Text(stringResource(R.string.continue_tight_message)) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.go_back)) } },
        confirmButton = { Button(onClick = onContinue) { Text(stringResource(R.string.continue_anyway)) } },
    )
}

@Composable
private fun EmptyStudyMap(onCreateProject: () -> Unit, snackbar: SnackbarHostState, modifier: Modifier) {
    val reducedMotion = isReducedMotionEnabled()
    val introTransition = rememberInfiniteTransition(label = "introLanding")
    val introProgress by introTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "introLandingProgress",
    )
    val effectiveProgress = if (reducedMotion) 0.52f else introProgress

    PlanLandingScaffold(
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                AnimatedEmptyStudyMapHeader(
                    progress = effectiveProgress,
                )
                StudyPlanIntroContent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    message = stringResource(R.string.study_plan_intro_message),
                    actionLabel = stringResource(R.string.start_planning),
                    onAction = onCreateProject,
                    progress = effectiveProgress,
                    reducedMotion = reducedMotion,
                )
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun AnimatedEmptyStudyMapHeader(progress: Float) {
    val baseColor = MaterialTheme.colorScheme.onBackground
    val headerText = stringResource(R.string.empty_study_plan_header)
    Text(
        text = buildAnnotatedString {
            append(headerText)
            repeat(3) { index ->
                withStyle(SpanStyle(color = baseColor.copy(alpha = headlineDotAlpha(progress, index)))) {
                    append(".")
                }
            }
        },
        style = MaterialTheme.typography.headlineSmall,
        color = baseColor,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StudyPlanIntroContent(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    progress: Float,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val actionScale = if (reducedMotion) 1f else introActionPulseScale(progress)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        IntroActionGuide(
            progress = progress,
            reducedMotion = reducedMotion,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.15f)
                .padding(horizontal = 12.dp),
        )
        PlanFlowCircleAction(
            label = actionLabel,
            onClick = onAction,
            modifier = Modifier
                .align(Alignment.End)
                .graphicsLayer {
                    scaleX = actionScale
                    scaleY = actionScale
                },
        )
    }
}

@Composable
private fun IntroActionGuide(
    progress: Float,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val guideColor = MaterialTheme.colorScheme.primary
    val dashPhase = if (reducedMotion) 0f else progress * 28f

    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        val start = Offset(size.width * 0.58f, 12.dp.toPx())
        val control1 = Offset(size.width * 0.9f, size.height * 0.08f)
        val control2 = Offset(size.width * 0.72f, size.height * 0.68f)
        val end = Offset(size.width - 40.dp.toPx(), size.height - 8.dp.toPx())
        val easedProgress = easeOutCubic(progress)
        val path = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(
                control1.x,
                control1.y,
                control2.x,
                control2.y,
                end.x,
                end.y,
            )
        }

        drawPath(
            path = path,
            color = guideColor.copy(alpha = 0.34f),
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(2.dp.toPx(), 10.dp.toPx()),
                    phase = dashPhase,
                ),
            ),
        )
        val lead = cubicPoint(start, control1, control2, end, easedProgress)
        if (!reducedMotion) {
            val absorption = ((progress - 0.74f) / 0.22f).coerceIn(0f, 1f)
            val dotAlpha = 1f - absorption
            val dotRadius = 3.dp.toPx() * (1f - absorption * 0.82f)
            drawCircle(
                color = guideColor.copy(alpha = 0.08f * dotAlpha),
                radius = 13.dp.toPx() * (1f - absorption * 0.55f),
                center = lead,
            )
            drawCircle(
                color = guideColor.copy(alpha = 0.18f * dotAlpha),
                radius = 7.dp.toPx() * (1f - absorption * 0.5f),
                center = lead,
            )
            if (dotAlpha > 0.04f) {
                drawCircle(
                    color = guideColor.copy(alpha = 0.62f * dotAlpha),
                    radius = dotRadius,
                    center = lead,
                )
            }
        } else {
            drawCircle(
                color = guideColor.copy(alpha = 0.52f),
                radius = 3.dp.toPx(),
                center = end,
            )
        }
    }
}

private fun introActionPulseScale(progress: Float): Float {
    val pulseProgress = ((progress - 0.78f) / 0.18f).coerceIn(0f, 1f)
    val pulse = if (pulseProgress <= 0.5f) {
        pulseProgress / 0.5f
    } else {
        (1f - pulseProgress) / 0.5f
    }
    return 1f + pulse.coerceIn(0f, 1f) * 0.055f
}

private fun headlineDotAlpha(progress: Float, index: Int): Float {
    val revealStart = 0.05f + index * 0.12f
    val reveal = ((progress - revealStart) / 0.1f).coerceIn(0f, 1f)
    val absorptionFade = 1f - ((progress - 0.76f) / 0.18f).coerceIn(0f, 1f) * 0.72f
    return 0.18f + reveal * absorptionFade * 0.82f
}

private fun easeOutCubic(progress: Float): Float {
    val inverse = 1f - progress.coerceIn(0f, 1f)
    return 1f - inverse * inverse * inverse
}

private fun cubicPoint(
    start: Offset,
    control1: Offset,
    control2: Offset,
    end: Offset,
    progress: Float,
): Offset {
    val t = progress.coerceIn(0f, 1f)
    val inverse = 1f - t
    val x = inverse * inverse * inverse * start.x +
        3f * inverse * inverse * t * control1.x +
        3f * inverse * t * t * control2.x +
        t * t * t * end.x
    val y = inverse * inverse * inverse * start.y +
        3f * inverse * inverse * t * control1.y +
        3f * inverse * t * t * control2.y +
        t * t * t * end.y
    return Offset(x, y)
}

@Composable
private fun EmptyTasksCard(onGenerate: () -> Unit) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.no_tasks_yet),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = onGenerate) {
                Text(stringResource(R.string.generate_tasks))
            }
        }
    }
}

@Composable
private fun CompletionCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.finished_study_map),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.all_tasks_complete_message))
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OutlinedStatusPill(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = BorderStroke(1.dp, color.copy(alpha = 0.62f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusPill(label: String, container: Color, content: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun realismLabel(status: PlanRealismStatus) = when (status) {
    PlanRealismStatus.OnTrack -> stringResource(R.string.on_track)
    PlanRealismStatus.Tight -> stringResource(R.string.plan_is_tight)
    PlanRealismStatus.Unrealistic -> stringResource(R.string.unrealistic_plan)
}

@Composable
private fun realismColor(status: PlanRealismStatus) = when (status) {
    PlanRealismStatus.OnTrack -> MaterialTheme.colorScheme.primary
    PlanRealismStatus.Tight -> MaterialTheme.colorScheme.tertiary
    PlanRealismStatus.Unrealistic -> MaterialTheme.colorScheme.error
}

@Composable
private fun realismContainer(status: PlanRealismStatus) = when (status) {
    PlanRealismStatus.OnTrack -> MaterialTheme.colorScheme.primaryContainer
    PlanRealismStatus.Tight -> MaterialTheme.colorScheme.tertiaryContainer
    PlanRealismStatus.Unrealistic -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun realismContent(status: PlanRealismStatus) = when (status) {
    PlanRealismStatus.OnTrack -> MaterialTheme.colorScheme.onPrimaryContainer
    PlanRealismStatus.Tight -> MaterialTheme.colorScheme.onTertiaryContainer
    PlanRealismStatus.Unrealistic -> MaterialTheme.colorScheme.onErrorContainer
}

@Composable
private fun statusLabel(status: StudyTaskStatus) = when (status) {
    StudyTaskStatus.NotStarted -> stringResource(R.string.not_started)
    StudyTaskStatus.InProgress -> stringResource(R.string.task_status_in_progress)
    StudyTaskStatus.Completed -> stringResource(R.string.completed)
    StudyTaskStatus.Locked -> stringResource(R.string.locked)
    StudyTaskStatus.Overdue -> stringResource(R.string.overdue)
    StudyTaskStatus.Rescheduled -> stringResource(R.string.rescheduled)
    StudyTaskStatus.DeferredByUser -> stringResource(R.string.deferred_by_user)
    StudyTaskStatus.ExcludedByUser -> stringResource(R.string.excluded_by_user)
    StudyTaskStatus.Unscheduled -> stringResource(R.string.unscheduled)
    StudyTaskStatus.OverCapacity -> stringResource(R.string.over_capacity)
}

@Composable
private fun statusContainer(status: StudyTaskStatus) = when (status) {
    StudyTaskStatus.InProgress,
    StudyTaskStatus.Rescheduled,
    -> MaterialTheme.colorScheme.primaryContainer
    StudyTaskStatus.Completed -> MaterialTheme.colorScheme.secondaryContainer
    StudyTaskStatus.Overdue,
    StudyTaskStatus.OverCapacity,
    -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun statusContent(status: StudyTaskStatus) = when (status) {
    StudyTaskStatus.Overdue,
    StudyTaskStatus.OverCapacity,
    -> MaterialTheme.colorScheme.onErrorContainer
    StudyTaskStatus.InProgress,
    StudyTaskStatus.Rescheduled,
    -> MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun taskTypeLabel(type: StudyTaskType): String = when (type) {
    StudyTaskType.Concept -> stringResource(R.string.task_type_concept)
    StudyTaskType.Practice -> stringResource(R.string.task_type_practice)
    StudyTaskType.Review -> stringResource(R.string.task_type_review)
    StudyTaskType.Quiz,
    StudyTaskType.MockTest,
    -> stringResource(R.string.task_type_mock_test)
    StudyTaskType.Memorization -> stringResource(R.string.task_type_memorization)
    StudyTaskType.Reading,
    -> stringResource(R.string.task_type_reading)
    StudyTaskType.Summary -> stringResource(R.string.task_type_summary)
    StudyTaskType.MistakeReview -> stringResource(R.string.task_type_mistake_review)
    StudyTaskType.Custom -> stringResource(R.string.task_type_custom)
}

private fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

private fun String.shortDocumentName(): String =
    if (lowercase(Locale.ROOT).endsWith(".pdf")) dropLast(4) else this

private fun formatDate(date: String): String =
    date.toStudyCalendar()
        ?.let { SimpleDateFormat("d MMMM", Locale.getDefault()).format(it.time) }
        ?: date

@Composable
private fun dateHeading(date: String, studyToday: String): String {
    val value = date.toStudyCalendar() ?: return date
    val tomorrow = studyToday.toStudyCalendar()?.apply {
        add(Calendar.DAY_OF_MONTH, 1)
    }?.toStudyDate()
    return when (date) {
        studyToday -> stringResource(R.string.today)
        tomorrow -> stringResource(R.string.tomorrow)
        else -> SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(value.time)
    }
}

@Composable
private fun deadlineLabel(preferences: PlanSetupSubmission): String =
    deadlineDate(preferences, currentStudyCalendar(preferences))?.let {
        SimpleDateFormat("d MMMM", Locale.getDefault()).format(it.time)
    } ?: stringResource(R.string.not_scheduled)

@Composable
private fun relativeDeadlineLabel(preferences: PlanSetupSubmission): String =
    relativeDeadlineLabel(preferences, currentStudyCalendar(preferences))

@Composable
private fun relativeDeadlineLabel(preferences: PlanSetupSubmission, today: Calendar): String {
    val deadline = deadlineDate(preferences, today) ?: return stringResource(R.string.not_scheduled)
    val todayOnly = dayOnly(today)
    val deadlineOnly = dayOnly(deadline)
    val days = ((deadlineOnly.timeInMillis - todayOnly.timeInMillis) / MillisPerDay).toInt()
    return when {
        days < 0 -> stringResource(R.string.deadline_past)
        days == 0 -> stringResource(R.string.deadline_today)
        days == 1 -> stringResource(R.string.deadline_tomorrow)
        else -> pluralStringResource(R.plurals.deadline_in_days, days, days)
    }
}

private fun suggestedBlockMinutes(tasks: List<GeneratedStudyBlock>): Int {
    val values = tasks.map { it.durationMinutes }.filter { it > 0 }.sorted()
    return (values.getOrNull(values.size / 2) ?: 30).coerceIn(15, 120)
}

private const val MillisPerDay = 24 * 60 * 60 * 1000L
