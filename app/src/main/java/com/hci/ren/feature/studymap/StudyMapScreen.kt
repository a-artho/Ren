package com.hci.ren.feature.studymap

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hci.ren.R
import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.isSelectableDeadlineUtc
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.plangeneration.StudyBlockDifficulty
import com.hci.ren.feature.plangeneration.StudySourceDocument
import com.hci.ren.feature.plangeneration.StudySourceRef
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.StudyTaskType
import com.hci.ren.feature.plangeneration.likelyStudyMinutes
import com.hci.ren.feature.plangeneration.reservedStudyMinutes
import com.hci.ren.ui.components.PlanFlowCircleAction
import com.hci.ren.ui.components.PlanFlowControlHeight
import com.hci.ren.ui.components.PlanLandingScaffold
import com.hci.ren.ui.motion.RenEmphasizedEasing
import com.hci.ren.ui.motion.RenMotionDurationMillis
import com.hci.ren.ui.motion.isReducedMotionEnabled
import com.hci.ren.ui.motion.renFadeThroughTransform
import com.hci.ren.ui.theme.RenContextMenuSurface
import com.hci.ren.ui.theme.RenGreenDark
import com.hci.ren.ui.theme.renCardBorderColor
import com.hci.ren.ui.theme.renCardBorderStroke
import com.hci.ren.ui.theme.renCardContainerColor
import com.hci.ren.ui.theme.renMutedIconColor
import com.hci.ren.ui.theme.renSelectedBorderColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class AdjustmentSheet { PlanEdit, Continue }
private enum class PlanEditPage { Menu, Rename, DailyTime, Scope }
private enum class PlanEditDailyTimeChoice { FitTwoLeaves, FitFourLeaves, Custom }

private const val UnscheduledAutoCollapseLeafThreshold = 3

private val PlanEditSheetMenuHeight = 444.dp
private val PlanEditSheetDefaultHeight = 468.dp
private val PlanEditSheetTopicHeight = 640.dp
private val PlanEditMenuHeaderHeight = 76.dp
private val PlanEditActionRowHeight = 68.dp
private val PlanEditActionTextStartPadding = 56.dp
private val PlanEditSheetSurface = RenContextMenuSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyMapScreen(
    plan: GeneratedStudyPlan?,
    preferences: PlanSetupSubmission?,
    modifier: Modifier = Modifier,
    dailyMinutesOverride: Int? = null,
    dailyAvailableMinutesByDate: Map<String, Int> = emptyMap(),
    taskStateById: Map<String, StudyTaskState> = emptyMap(),
    acceptedTightPlan: Boolean = false,
    changeMessage: String? = null,
    navigationResetKey: Int = 0,
    onBack: () -> Unit,
    onCreateProject: () -> Unit,
    onOpenToday: () -> Unit,
    onRenamePlan: (String) -> Unit,
    onDeletePlan: () -> Unit,
    onConsumeMessage: () -> Unit,
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
    var autoCloseExpandedDays by rememberSaveable { mutableStateOf(true) }
    var collapseScheduleKey by rememberSaveable { mutableIntStateOf(0) }

    fun showAdjustment(sheet: AdjustmentSheet) {
        adjustment = sheet
    }

    fun dismissAdjustment() {
        adjustment = null
    }

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
        taskStateById = taskStateById,
    )
    val materialGroups = remember(data.plan) { materialGroups(data.plan) }
    val materialGroupStateKey = materialGroups.joinToString(separator = "|") { it.id }
    var expandedMaterialGroupId by rememberSaveable(materialGroupStateKey, navigationResetKey) {
        mutableStateOf(if (navigationResetKey == 0) materialGroups.firstOrNull()?.id else null)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            StudyMapHeader(
                projectName = plan.projectName,
                deadline = relativeDeadlineLabel(preferences),
                completedTasks = data.completedTasks,
                totalTasks = data.requiredTasks.size,
                autoCloseExpandedDays = autoCloseExpandedDays,
                onEditPlan = { showAdjustment(AdjustmentSheet.PlanEdit) },
                onToggleAutoCloseExpandedDays = { autoCloseExpandedDays = !autoCloseExpandedDays },
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
            if (!acceptedTightPlan && data.realism.status != PlanRealismStatus.OnTrack) {
                item {
                    RealismWarningPanel(
                        realism = data.realism,
                        onAction = { showAdjustment(it) },
                    )
                }
            }
            item(key = "study-map-view-switcher") {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 8.dp),
                    ) {
                        StudyMapViewSwitcher(
                            selected = selectedView,
                            onSelected = { view ->
                                if (view != selectedView) {
                                    selectedViewName = view.name
                                    expandedMaterialGroupId = null
                                    collapseScheduleKey++
                                }
                            },
                        )
                    }
                }
            }
            when (selectedView) {
                StudyMapView.Schedule -> scheduleItems(
                    data = data,
                    onOpenToday = onOpenToday,
                    onEditPlan = { showAdjustment(AdjustmentSheet.PlanEdit) },
                    autoCloseExpandedDays = autoCloseExpandedDays,
                    collapseKey = collapseScheduleKey + navigationResetKey,
                )
                StudyMapView.Topics -> materialItems(
                    groups = materialGroups,
                    expandedGroupId = expandedMaterialGroupId,
                    onToggleGroup = { groupId ->
                        expandedMaterialGroupId = if (expandedMaterialGroupId == groupId) null else groupId
                    },
                )
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
            currentName = plan.projectName,
            resetOffsetHours = preferences.studyDayResetOffsetHours,
            currentDailyMinutes = data.dailyMinutes,
            leafMinutes = suggestedLeafMinutes(plan.blocks),
            plan = data.plan,
            onDismiss = { dismissAdjustment() },
            onRename = { name ->
                onRenamePlan(name)
                dismissAdjustment()
            },
            onApplyDeadline = { millis ->
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(millis))
                onApplyDeadline(date); dismissAdjustment()
            },
            onApplyDailyTime = { onIncreaseDailyTime(it); dismissAdjustment() },
            onApplyScope = { strategy, topics -> onReduceScope(strategy, topics); dismissAdjustment() },
            onDeletePlan = {
                dismissAdjustment()
                deleteDialogOpen = true
            },
        )
        AdjustmentSheet.Continue -> ContinueAnywayDialog(
            onDismiss = { dismissAdjustment() },
            onContinue = { onContinueAnyway(); dismissAdjustment() },
        )
        null -> Unit
    }
}

@Composable
private fun StudyMapHeader(
    projectName: String,
    deadline: String? = null,
    completedTasks: Int,
    totalTasks: Int,
    autoCloseExpandedDays: Boolean,
    onEditPlan: (() -> Unit)? = null,
    onToggleAutoCloseExpandedDays: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasPlanActions = onEditPlan != null
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
                if (hasPlanActions) {
                    StudyPlanTitleMenu(
                        title = title,
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = it },
                        autoCloseExpandedDays = autoCloseExpandedDays,
                        onEditPlan = onEditPlan,
                        onToggleAutoCloseExpandedDays = onToggleAutoCloseExpandedDays,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    StudyMapHeaderTitle(
                        title = title,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (totalTasks > 0) {
                    Spacer(Modifier.width(10.dp))
                    HeaderLeafProgress(
                        completedTasks = completedTasks,
                        totalTasks = totalTasks,
                    )
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
private fun HeaderLeafProgress(
    completedTasks: Int,
    totalTasks: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Eco,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
        )
        Text(
            text = stringResource(R.string.leaf_progress_count, completedTasks, totalTasks),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun StudyPlanTitleMenu(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    autoCloseExpandedDays: Boolean,
    onEditPlan: (() -> Unit)?,
    onToggleAutoCloseExpandedDays: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        BoxWithConstraints {
            val titleMaxWidth = maxOf(0.dp, maxWidth - 25.dp)
            Surface(
                onClick = { onExpandedChange(true) },
                color = Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StudyMapHeaderTitle(
                        title = title,
                        modifier = Modifier.widthIn(max = titleMaxWidth),
                    )
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.study_plan_options),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            offset = DpOffset(x = 0.dp, y = 8.dp),
            modifier = Modifier.width(238.dp),
            shape = RoundedCornerShape(18.dp),
            containerColor = RenContextMenuSurface,
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.64f)),
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                StudyPlanMenuActionRow(
                    title = stringResource(R.string.edit_plan),
                    icon = Icons.Default.Edit,
                    onClick = {
                        onExpandedChange(false)
                        onEditPlan?.invoke()
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f),
                )
                StudyPlanMenuToggleRow(
                    title = stringResource(R.string.auto_close_days),
                    checked = autoCloseExpandedDays,
                    onClick = {
                        onToggleAutoCloseExpandedDays()
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

@Composable
private fun StudyPlanMenuActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = renMutedIconColor(),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StudyPlanMenuToggleRow(
    title: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
        } else {
            Color.Transparent
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.UnfoldLess,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
                },
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(12.dp))
            StudyPlanCompactToggle(checked = checked)
        }
    }
}

@Composable
private fun StudyPlanCompactToggle(checked: Boolean) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
        },
        animationSpec = tween(RenMotionDurationMillis, easing = RenEmphasizedEasing),
        label = "study-plan-menu-toggle-track",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 19.dp else 3.dp,
        animationSpec = tween(RenMotionDurationMillis, easing = RenEmphasizedEasing),
        label = "study-plan-menu-toggle-thumb",
    )

    Surface(
        modifier = Modifier
            .width(40.dp)
            .height(24.dp),
        shape = CircleShape,
        color = trackColor,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Surface(
                modifier = Modifier
                    .offset { IntOffset(x = thumbOffset.roundToPx(), y = 0) }
                    .size(18.dp),
                shape = CircleShape,
                color = if (checked) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f)
                },
            ) {}
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryMetric(
                label = stringResource(R.string.planned_metric_label),
                value = formatMinutes(data.remainingReservedMinutes),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                OutlinedStatusPill(
                    label = realismLabel(data.realism.status),
                    color = realismPillColor(data.realism.status),
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
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            textAlign = textAlign,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
            fontWeight = FontWeight.SemiBold,
            textAlign = textAlign,
        )
    }
}

@Composable
private fun RealismWarningPanel(realism: PlanRealism, onAction: (AdjustmentSheet) -> Unit) {
    val container = realismContainer(realism.status)
    val content = realismContent(realism.status)
    val accent = realismColor(realism.status)
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(18.dp),
        border = realismWarningBorder(realism.status),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = accent.copy(alpha = 0.14f),
                    contentColor = accent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    realismWarningTitle(realism.status),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = content,
                )
            }
            Text(
                realismWarningMessage(realism),
                style = MaterialTheme.typography.bodyMedium,
                color = content,
            )
            AdjustmentAction(
                title = R.string.edit_plan,
                subtitle = R.string.edit_plan_sheet_subtitle,
                icon = Icons.Default.Edit,
                accent = accent,
            ) { onAction(AdjustmentSheet.PlanEdit) }
            AdjustmentAction(
                title = R.string.continue_anyway,
                subtitle = R.string.continue_anyway_short_subtitle,
                icon = Icons.Default.CheckCircle,
                accent = accent,
            ) { onAction(AdjustmentSheet.Continue) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanEditSheet(
    currentName: String,
    resetOffsetHours: Int,
    currentDailyMinutes: Int,
    leafMinutes: Int,
    plan: GeneratedStudyPlan,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onApplyDeadline: (epochMillis: Long) -> Unit,
    onApplyDailyTime: (Int) -> Unit,
    onApplyScope: (ScopeReduction, Set<String>) -> Unit,
    onDeletePlan: () -> Unit,
) {
    var pageName by rememberSaveable { mutableStateOf(PlanEditPage.Menu.name) }
    var showCustomDatePicker by rememberSaveable { mutableStateOf(false) }
    val page = PlanEditPage.valueOf(pageName)
    val sheetHeight = when (page) {
        PlanEditPage.Menu -> PlanEditSheetMenuHeight
        PlanEditPage.Scope -> PlanEditSheetTopicHeight
        else -> PlanEditSheetDefaultHeight
    }
    val reducedMotion = isReducedMotionEnabled()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PlanEditSheetSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f),
        tonalElevation = 0.dp,
        dragHandle = { RenSheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .padding(horizontal = 20.dp)
                .imePadding()
                .navigationBarsPadding()
                .padding(top = 0.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PlanEditFlowHeader(
                page = page,
                height = PlanEditMenuHeaderHeight,
                currentDailyMinutes = currentDailyMinutes,
                onBack = { pageName = PlanEditPage.Menu.name },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { renFadeThroughTransform(reducedMotion = reducedMotion) },
                    contentKey = { it },
                    modifier = Modifier.fillMaxSize(),
                    label = "plan-edit-page",
                ) { targetPage ->
                    when (targetPage) {
                        PlanEditPage.Menu -> PlanEditMenuContent(
                            modifier = Modifier.fillMaxSize(),
                            onChangeDeadline = { showCustomDatePicker = true },
                            onNavigate = { pageName = it.name },
                            onDeletePlan = onDeletePlan,
                        )
                        PlanEditPage.Rename -> PlanEditRenameContent(
                            currentName = currentName,
                            modifier = Modifier.fillMaxSize(),
                            onRename = onRename,
                        )
                        PlanEditPage.DailyTime -> PlanEditDailyTimeContent(
                            currentMinutes = currentDailyMinutes,
                            leafMinutes = leafMinutes,
                            modifier = Modifier.fillMaxSize(),
                            onApply = onApplyDailyTime,
                        )
                        PlanEditPage.Scope -> PlanEditScopeContent(
                            plan = plan,
                            modifier = Modifier.fillMaxSize(),
                            onApply = onApplyScope,
                        )
                    }
                }
            }
        }
    }
    if (showCustomDatePicker) {
        PlanEditDatePickerDialog(
            resetOffsetHours = resetOffsetHours,
            onDismiss = { showCustomDatePicker = false },
            onApplyDeadline = { millis ->
                showCustomDatePicker = false
                onApplyDeadline(millis)
            },
        )
    }
}

@Composable
private fun PlanEditFlowHeader(
    page: PlanEditPage,
    height: Dp,
    currentDailyMinutes: Int,
    onBack: () -> Unit,
) {
    val reducedMotion = isReducedMotionEnabled()
    AnimatedContent(
        targetState = page,
        transitionSpec = { renFadeThroughTransform(reducedMotion = reducedMotion) },
        contentKey = { it },
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        label = "plan-edit-header",
    ) { targetPage ->
        PlanEditFlowHeaderContent(
            page = targetPage,
            currentDailyMinutes = currentDailyMinutes,
            onBack = onBack,
        )
    }
}

@Composable
private fun PlanEditFlowHeaderContent(
    page: PlanEditPage,
    currentDailyMinutes: Int,
    onBack: () -> Unit,
) {
    val title = planEditPageTitle(page)
    val subtitle = if (page == PlanEditPage.DailyTime) {
        stringResource(
            R.string.current_daily_budget_header,
            stringResource(R.string.minutes_per_day, formatMinutes(currentDailyMinutes)),
        )
    } else {
        planEditPageSubtitle(page)
    }
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (page != PlanEditPage.Menu) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .offset(x = (-8).dp)
                    .size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                )
            }
            Spacer(Modifier.width(2.dp))
        }
        if (page == PlanEditPage.Menu) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                subtitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun planEditPageTitle(page: PlanEditPage): String = when (page) {
    PlanEditPage.Menu -> stringResource(R.string.edit_plan_sheet_title)
    PlanEditPage.Rename -> stringResource(R.string.change_plan_name)
    PlanEditPage.DailyTime -> stringResource(R.string.available_time)
    PlanEditPage.Scope -> stringResource(R.string.choose_material)
}

@Composable
private fun planEditPageSubtitle(page: PlanEditPage): String = when (page) {
    PlanEditPage.Menu -> stringResource(R.string.edit_plan_sheet_subtitle)
    PlanEditPage.Rename -> stringResource(R.string.change_plan_name_subtitle)
    PlanEditPage.DailyTime -> stringResource(R.string.available_time_subtitle)
    PlanEditPage.Scope -> stringResource(R.string.choose_material_subtitle)
}

@Composable
private fun PlanEditMenuContent(
    modifier: Modifier = Modifier,
    onChangeDeadline: () -> Unit,
    onNavigate: (PlanEditPage) -> Unit,
    onDeletePlan: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PlanEditActionRow(
                title = R.string.change_plan_name,
                subtitle = R.string.change_plan_name_subtitle,
                icon = Icons.Default.Edit,
                showDivider = true,
            ) {
                onNavigate(PlanEditPage.Rename)
            }
            PlanEditActionRow(
                title = R.string.change_deadline,
                subtitle = R.string.change_deadline_subtitle,
                icon = Icons.Default.Event,
                showDivider = true,
            ) {
                onChangeDeadline()
            }
            PlanEditActionRow(
                title = R.string.available_time,
                subtitle = R.string.available_time_subtitle,
                icon = Icons.Default.Timer,
                showDivider = true,
            ) {
                onNavigate(PlanEditPage.DailyTime)
            }
            PlanEditActionRow(
                title = R.string.choose_material,
                subtitle = R.string.choose_material_subtitle,
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
            ) {
                onNavigate(PlanEditPage.Scope)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            PlanEditDeleteAction(onClick = onDeletePlan)
        }
    }
}

@Composable
private fun PlanEditRenameContent(
    currentName: String,
    modifier: Modifier = Modifier,
    onRename: (String) -> Unit,
) {
    var name by rememberSaveable(currentName) { mutableStateOf(currentName) }
    val trimmedName = name.trim()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(80) },
            label = { Text(stringResource(R.string.plan_name)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = planEditTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        PlanEditPrimaryAction(
            enabled = trimmedName.isNotBlank() && trimmedName != currentName.trim(),
            onClick = { onRename(trimmedName) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanEditDatePickerDialog(
    resetOffsetHours: Int,
    onDismiss: () -> Unit,
    onApplyDeadline: (epochMillis: Long) -> Unit,
) {
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
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(
            containerColor = RenContextMenuSurface,
        ),
        confirmButton = {
            TextButton(
                enabled = datePickerState.selectedDateMillis != null,
                onClick = { datePickerState.selectedDateMillis?.let(onApplyDeadline) },
            ) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    ) {
        DatePicker(
            state = datePickerState,
            colors = DatePickerDefaults.colors(
                containerColor = RenContextMenuSurface,
            ),
        )
    }
}

@Composable
private fun PlanEditDailyTimeContent(
    currentMinutes: Int,
    leafMinutes: Int,
    modifier: Modifier = Modifier,
    onApply: (Int) -> Unit,
) {
    var selectedChoiceName by rememberSaveable(currentMinutes, leafMinutes) {
        mutableStateOf(PlanEditDailyTimeChoice.FitTwoLeaves.name)
    }
    var customHours by rememberSaveable { mutableStateOf("") }
    var customMinutes by rememberSaveable { mutableStateOf("") }
    val selectedChoice = PlanEditDailyTimeChoice.values()
        .firstOrNull { it.name == selectedChoiceName } ?: PlanEditDailyTimeChoice.FitTwoLeaves
    val hasCustomTime = customHours.isNotBlank() || customMinutes.isNotBlank()
    val customHourValue = customHours.toIntOrNull()
    val customMinuteValue = customMinutes.toIntOrNull()
    val customHoursValid = customHours.isBlank() || customHourValue in 0..24
    val customMinutesValid = customMinutes.isBlank() || customMinuteValue in 0..59
    val customTotal = if (hasCustomTime && customHoursValid && customMinutesValid) {
        ((customHourValue ?: 0) * 60 + (customMinuteValue ?: 0)).takeIf { it in 1..1_440 }
    } else {
        null
    }
    val quickOptions = listOf(
        Triple(
            PlanEditDailyTimeChoice.FitTwoLeaves,
            (currentMinutes + leafMinutes * 2).coerceIn(1, 1_440),
            R.string.fit_two_leaves_per_day,
        ),
        Triple(
            PlanEditDailyTimeChoice.FitFourLeaves,
            (currentMinutes + leafMinutes * 4).coerceIn(1, 1_440),
            R.string.fit_four_leaves_per_day,
        ),
    )
    val quickMinutes = quickOptions.firstOrNull { it.first == selectedChoice }?.second ?: quickOptions.first().second
    val proposedMinutes = if (selectedChoice == PlanEditDailyTimeChoice.Custom) {
        customTotal ?: currentMinutes
    } else {
        quickMinutes
    }
    val canApply = selectedChoice != PlanEditDailyTimeChoice.Custom || customTotal != null
    val customChoiceValue = when {
        customTotal != null -> stringResource(R.string.minutes_per_day, formatMinutes(customTotal))
        hasCustomTime -> stringResource(R.string.invalid_daily_budget)
        else -> stringResource(R.string.custom_daily_time_value)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickOptions.forEach { (choice, minutes, label) ->
                PlanEditDailyTimeChoiceRow(
                    title = stringResource(label),
                    value = stringResource(R.string.minutes_per_day, formatMinutes(minutes)),
                    selected = selectedChoice == choice,
                    onClick = {
                        selectedChoiceName = choice.name
                        customHours = ""
                        customMinutes = ""
                    },
                )
            }
            PlanEditDailyTimeChoiceRow(
                title = stringResource(R.string.custom_daily_time),
                value = customChoiceValue,
                selected = selectedChoice == PlanEditDailyTimeChoice.Custom,
                onClick = { selectedChoiceName = PlanEditDailyTimeChoice.Custom.name },
            )
            if (selectedChoice == PlanEditDailyTimeChoice.Custom) {
                PlanEditCustomDailyTimeFields(
                    customHours = customHours,
                    customMinutes = customMinutes,
                    customHoursValid = customHoursValid,
                    customMinutesValid = customMinutesValid,
                    hasCustomTime = hasCustomTime,
                    onCustomHoursChanged = {
                        selectedChoiceName = PlanEditDailyTimeChoice.Custom.name
                        customHours = it.filter(Char::isDigit).take(2)
                    },
                    onCustomMinutesChanged = {
                        selectedChoiceName = PlanEditDailyTimeChoice.Custom.name
                        customMinutes = it.filter(Char::isDigit).take(2)
                    },
                )
            }
        }

        Spacer(Modifier.weight(1f))
        PlanEditPrimaryAction(
            onClick = { onApply(proposedMinutes.coerceIn(1, 1_440)) },
            enabled = canApply,
        )
    }
}

@Composable
private fun PlanEditDailyTimeChoiceRow(
    title: String,
    value: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val rowSurface = renCardContainerColor()
    val targetBorderColor = if (selected) {
        renSelectedBorderColor()
    } else {
        renCardBorderColor()
    }
    val targetBackground = rowSurface
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(RenMotionDurationMillis, easing = RenEmphasizedEasing),
        label = "plan-edit-daily-choice-border",
    )
    val backgroundColor by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(RenMotionDurationMillis, easing = RenEmphasizedEasing),
        label = "plan-edit-daily-choice-background",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f)
                },
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PlanEditCustomDailyTimeFields(
    customHours: String,
    customMinutes: String,
    customHoursValid: Boolean,
    customMinutesValid: Boolean,
    hasCustomTime: Boolean,
    onCustomHoursChanged: (String) -> Unit,
    onCustomMinutesChanged: (String) -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = customHours,
            onValueChange = onCustomHoursChanged,
            label = { Text(stringResource(R.string.hours_label), style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            isError = hasCustomTime && !customHoursValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp),
            colors = planEditTextFieldColors(),
            textStyle = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .weight(1f)
                .height(54.dp),
        )
        OutlinedTextField(
            value = customMinutes,
            onValueChange = onCustomMinutesChanged,
            label = { Text(stringResource(R.string.minutes_label), style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            isError = hasCustomTime && !customMinutesValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp),
            colors = planEditTextFieldColors(),
            textStyle = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .weight(1f)
                .height(54.dp),
        )
    }
}

@Composable
private fun PlanEditScopeContent(
    plan: GeneratedStudyPlan,
    modifier: Modifier = Modifier,
    onApply: (ScopeReduction, Set<String>) -> Unit,
) {
    val knownTopicIds = remember(plan.topics) { plan.topics.mapTo(mutableSetOf()) { it.id } }
    val activeTopicIds = remember(plan.blocks, knownTopicIds) {
        plan.blocks
            .filterNot { it.status == StudyTaskStatus.ExcludedByUser }
            .flatMap { it.topicIds }
            .filter { it in knownTopicIds }
            .toSet()
    }
    val topics = remember(plan.topics, activeTopicIds) {
        mutableStateListOf<String>().also { selected ->
            selected.addAll(activeTopicIds)
        }
    }
    val topicScrollState = rememberScrollState()
    val canApply = topics.isNotEmpty() && topics.toSet() != activeTopicIds

    fun setTopic(topicId: String, checked: Boolean) {
        if (checked) {
            if (topicId !in topics) topics.add(topicId)
        } else {
            topics.remove(topicId)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.select_topics),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.topic_selection_count, topics.size, plan.topics.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                maxLines = 1,
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            border = renCardBorderStroke(),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 6.dp, top = 6.dp, end = 12.dp, bottom = 6.dp)
                        .verticalScroll(topicScrollState),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    plan.topics.forEach { topic ->
                        val checked = topic.id in topics
                        Surface(
                            onClick = { setTopic(topic.id, !checked) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (checked) {
                                Color.Transparent
                            } else {
                                renCardContainerColor()
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { setTopic(topic.id, it) },
                                )
                                Text(
                                    topic.title,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (checked) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
                                    },
                                    fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                if (topicScrollState.maxValue > 0) {
                    val thumbPreferredHeight = maxHeight * 0.22f
                    val thumbHeight = when {
                        thumbPreferredHeight < 36.dp -> 36.dp
                        thumbPreferredHeight > 76.dp -> 76.dp
                        else -> thumbPreferredHeight
                    }
                    val edgePadding = 12.dp
                    val travel = maxHeight - thumbHeight - edgePadding * 2
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 5.dp)
                            .offset {
                                val scrollProgress = topicScrollState.value.toFloat() /
                                    topicScrollState.maxValue.toFloat()
                                IntOffset(
                                    x = 0,
                                    y = (edgePadding + travel * scrollProgress).roundToPx(),
                                )
                            }
                            .width(2.dp)
                            .height(thumbHeight),
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                    ) {}
                }
            }
        }
        PlanEditPrimaryAction(
            onClick = { onApply(ScopeReduction.ChooseTopics, topics.toSet()) },
            enabled = canApply,
        )
    }
}

@Composable
private fun PlanEditPrimaryAction(
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(PlanFlowControlHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = RenGreenDark,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(
            stringResource(R.string.apply_changes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun planEditTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
    unfocusedBorderColor = renCardBorderColor(),
    errorBorderColor = MaterialTheme.colorScheme.error,
    focusedContainerColor = renCardContainerColor(),
    unfocusedContainerColor = renCardContainerColor(),
    errorContainerColor = renCardContainerColor(),
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun RenSheetHandle() {
    Surface(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 12.dp)
            .width(28.dp)
            .height(4.dp),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
    ) {}
}

@Composable
private fun PlanEditActionRow(
    title: Int,
    subtitle: Int,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
    val iconContainerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
    val iconColor = renMutedIconColor()
    val separatorColor = treeLineColor(0.09f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PlanEditActionRowHeight)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(12.dp),
                color = iconContainerColor,
                contentColor = iconColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = supportingColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = PlanEditActionTextStartPadding, end = 8.dp),
                color = separatorColor,
            )
        }
    }
}

@Composable
private fun PlanEditDeleteAction(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val deleteColor = MaterialTheme.colorScheme.error.copy(alpha = 0.84f)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.32f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.07f),
            contentColor = deleteColor,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Icon(
            imageVector = Icons.Default.DeleteOutline,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.delete_plan),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun AdjustmentAction(
    title: Int,
    subtitle: Int,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = accent,
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                    activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    activeContentColor = MaterialTheme.colorScheme.primary,
                    activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                ),
                icon = {
                    Icon(
                        imageVector = studyMapViewIcon(view),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
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

private fun studyMapViewIcon(view: StudyMapView): ImageVector = when (view) {
    StudyMapView.Schedule -> Icons.Default.Eco
    StudyMapView.Topics -> Icons.AutoMirrored.Filled.LibraryBooks
}

@Composable
private fun treeLineColor(alpha: Float): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

private fun mainTimelineContinuationStroke(strokeWidth: Float): Stroke =
    Stroke(
        width = strokeWidth,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(strokeWidth * 3f, strokeWidth * 9f)),
    )

private fun androidx.compose.foundation.lazy.LazyListScope.scheduleItems(
    data: StudyMapData,
    onOpenToday: () -> Unit,
    onEditPlan: () -> Unit,
    autoCloseExpandedDays: Boolean,
    collapseKey: Int,
) {
    if (data.schedule.days.isNotEmpty()) {
        item(key = "schedule-timeline") {
            StudyScheduleTimeline(
                days = data.schedule.days,
                documents = data.plan.sourceDocuments,
                studyToday = currentStudyCalendar(data.preferences).toStudyDate(),
                onOpenToday = onOpenToday,
                autoCloseExpandedDays = autoCloseExpandedDays,
                collapseKey = collapseKey,
                modifier = Modifier.animateItem(),
            )
        }
    }
    if (data.schedule.unscheduledTasks.isNotEmpty()) {
        item(key = "unscheduled") {
            UnscheduledWorkCard(
                tasks = data.schedule.unscheduledTasks,
                documents = data.plan.sourceDocuments,
                onEditPlan = onEditPlan,
                modifier = Modifier.animateItem(),
            )
        }
    }
    if (data.activeTasks.isNotEmpty() && data.activeTasks.all { it.status == StudyTaskStatus.Completed }) {
        item { CompletionCard() }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.materialItems(
    groups: List<MaterialGroup>,
    expandedGroupId: String?,
    onToggleGroup: (String) -> Unit,
) {
    groups.forEach { group ->
        item(key = "material-${group.id}") {
            MaterialSection(
                group = group,
                orderNumber = group.document?.order,
                expanded = group.id == expandedGroupId,
                onToggleExpanded = { onToggleGroup(group.id) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun StudyScheduleTimeline(
    days: List<StudyScheduleDay>,
    documents: List<StudySourceDocument>,
    studyToday: String,
    onOpenToday: () -> Unit,
    autoCloseExpandedDays: Boolean,
    collapseKey: Int,
    modifier: Modifier = Modifier,
) {
    val dayStateKey = days.joinToString(separator = "|") { it.date }
    var expandedDays by rememberSaveable(dayStateKey, studyToday, collapseKey) {
        mutableStateOf(if (collapseKey == 0) initialExpandedTimelineDays(days, studyToday) else emptyList())
    }

    LaunchedEffect(autoCloseExpandedDays) {
        if (autoCloseExpandedDays && expandedDays.size > 1) {
            expandedDays = expandedDays.takeLast(1)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        val expandedFocusDates = expandedDays
            .filter { date -> date != studyToday }
            .toSet()
        val firstExpandedIndex = days.indexOfFirst { day -> day.date in expandedFocusDates }
        days.forEachIndexed { index, day ->
            val expanded = day.date in expandedFocusDates
            val fadeIncomingLine = firstExpandedIndex >= 0 && index > firstExpandedIndex
            val fadeOutgoingLine = firstExpandedIndex >= 0 && index > firstExpandedIndex
            StudyDayMapCard(
                day = day,
                documents = documents,
                studyToday = studyToday,
                expanded = expanded,
                isFocused = expanded || (day.date == studyToday && expandedFocusDates.isEmpty()),
                isFirst = index == 0,
                isLast = index == days.lastIndex,
                fadeIncomingLine = fadeIncomingLine,
                fadeOutgoingLine = fadeOutgoingLine,
                onOpenToday = onOpenToday,
                onToggleExpanded = {
                    expandedDays = if (day.date in expandedDays) {
                        expandedDays - day.date
                    } else if (autoCloseExpandedDays) {
                        listOf(day.date)
                    } else {
                        expandedDays + day.date
                    }
                },
                onExpandedLeftViewport = {
                    expandedDays = expandedDays - day.date
                },
            )
        }
    }
}

private fun initialExpandedTimelineDays(days: List<StudyScheduleDay>, studyToday: String): List<String> {
    val firstDay = days.firstOrNull()?.date ?: return emptyList()
    return if (firstDay == studyToday) emptyList() else listOf(firstDay)
}

@Composable
private fun StudyDayMapCard(
    day: StudyScheduleDay,
    documents: List<StudySourceDocument>,
    studyToday: String,
    expanded: Boolean,
    isFocused: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    fadeIncomingLine: Boolean,
    fadeOutgoingLine: Boolean,
    onOpenToday: () -> Unit,
    onToggleExpanded: () -> Unit,
    onExpandedLeftViewport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isToday = day.date == studyToday
    val isCompleted = day.tasks.isNotEmpty() && day.tasks.all { it.status == StudyTaskStatus.Completed }
    val view = LocalView.current
    var headerRowHeightPx by remember(day.date, expanded) { mutableIntStateOf(0) }
    val defaultMarkerHeight = if (expanded) 52.dp else 112.dp
    val measuredMarkerHeight = with(LocalDensity.current) { headerRowHeightPx.toDp() }
    val markerHeight = if (headerRowHeightPx == 0) defaultMarkerHeight else maxOf(defaultMarkerHeight, measuredMarkerHeight)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                if (expanded) {
                    val viewportHeight = view.height.toFloat()
                    val bounds = coordinates.boundsInWindow()
                    if (viewportHeight > 0f && (bounds.bottom <= 0f || bounds.top >= viewportHeight)) {
                        onExpandedLeftViewport()
                    }
                }
            }
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { headerRowHeightPx = it.height },
            verticalAlignment = Alignment.Top,
        ) {
            DayTimelineMarker(
                isFirst = isFirst,
                isLast = isLast && !expanded,
                isFocused = isFocused,
                isCompleted = isCompleted,
                fadeIncomingLine = fadeIncomingLine,
                fadeOutgoingLine = fadeOutgoingLine,
                height = markerHeight,
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                    if (isToday) {
                        onOpenToday()
                    } else {
                        onToggleExpanded()
                    }
                    }
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StudyDayCardHeader(
                    day = day,
                    studyToday = studyToday,
                    isToday = isToday,
                    expanded = expanded,
                )

                if (!expanded && day.tasks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
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
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        if (expanded) {
            val timelineItems = remember(day.tasks, documents) {
                day.tasks.toTimelineStudyItems(documents)
            }
            val timelineStateKey = remember(timelineItems) {
                timelineItems.joinToString("|") { it.timelineKey() }
            }
            var expandedItemKey by rememberSaveable(day.date, timelineStateKey) {
                mutableStateOf<String?>(null)
            }
            var previousSourceId: String? = null
            timelineItems.forEachIndexed { index, item ->
                val itemKey = item.timelineKey()
                val sourceDocument = item.sourceDocument
                if (sourceDocument != null && sourceDocument.id != previousSourceId) {
                    TimelineSourceDivider(
                        source = stringResource(R.string.pdf_order_label, sourceDocument.order),
                        continuesChildRail = index > 0,
                        showMainRail = !isLast || index == 0,
                    )
                }
                previousSourceId = sourceDocument?.id
                TimelineStudyItemBranchRow(
                    item = item,
                    meta = timelineStudyItemMeta(item),
                    expanded = expandedItemKey == itemKey,
                    onToggleExpanded = {
                        expandedItemKey = if (expandedItemKey == itemKey) null else itemKey
                    },
                    isFirstBranch = index == 0,
                    isLastBranch = index == timelineItems.lastIndex,
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
    isFocused: Boolean,
    isCompleted: Boolean,
    fadeIncomingLine: Boolean,
    fadeOutgoingLine: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    val railColor = treeLineColor(0.20f)
    val fadedRailColor = treeLineColor(0.08f)
    val nodeBorderColor = if (isFocused) activeColor.copy(alpha = 0.72f) else mutedColor
    val dotSize = if (isFocused) 13.dp else 10.dp
    val dotTop = 19.dp
    Box(
        modifier = modifier
            .width(18.dp)
            .height(height),
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val dotRadius = dotSize.toPx() / 2f
            val dotCenterY = dotTop.toPx() + dotRadius
            if (!isFirst) {
                drawLine(
                    color = if (fadeIncomingLine) fadedRailColor else railColor,
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, dotCenterY - dotRadius),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Butt,
                )
            }
            if (!isLast) {
                drawLine(
                    color = if (fadeOutgoingLine) fadedRailColor else railColor,
                    start = Offset(centerX, dotCenterY + dotRadius),
                    end = Offset(centerX, size.height),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Butt,
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
private fun TimelineStudyItemBranchRow(
    item: TimelineStudyItem,
    meta: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    isFirstBranch: Boolean,
    isLastBranch: Boolean,
    isLastDay: Boolean,
) {
    var rowHeightPx by remember { mutableIntStateOf(0) }
    val rowHeight = with(LocalDensity.current) { rowHeightPx.toDp() }
    val status = item.timelineStatus()
    val grouped = item.tasks.size > 1

    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (rowHeightPx > 0) {
                TaskBranchConnector(
                    isFirstBranch = isFirstBranch,
                    isLastBranch = isLastBranch && !expanded,
                    isLastDay = isLastDay,
                    modifier = Modifier
                        .width(54.dp)
                        .height(rowHeight),
                )
            }

            val rowModifier = if (grouped) {
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .onSizeChanged { rowHeightPx = it.height }
            } else {
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { rowHeightPx = it.height }
            }

            Row(
                modifier = rowModifier,
                verticalAlignment = Alignment.Top,
            ) {
                Spacer(Modifier.width(54.dp))
                TimelineStudyItemTextContent(
                    item = item,
                    meta = meta,
                    showMeta = !expanded || !grouped,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                )
                if (status != StudyTaskStatus.NotStarted) {
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.padding(top = 10.dp)) {
                        StatusPill(statusLabel(status), statusContainer(status), statusContent(status))
                    }
                }
                if (grouped) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    )
                }
            }

            TreeTaskLeafNode(
                status = status,
                isParent = grouped,
                modifier = Modifier.padding(start = 24.dp, top = 3.dp),
            )
        }

        if (grouped && expanded) {
            item.tasks.forEachIndexed { index, task ->
                TimelineNestedTaskBranchRow(
                    task = task,
                    pageLabel = taskPageLabel(task),
                    isFirstLeaf = index == 0,
                    isLastLeaf = index == item.tasks.lastIndex,
                    continuesMainRail = !isLastDay,
                    continuesChildRailAfterGroup = !isLastBranch,
                    onCollapseParent = onToggleExpanded,
                )
            }
        }
    }
}

@Composable
private fun TreeTaskLeafNode(
    status: StudyTaskStatus,
    modifier: Modifier = Modifier,
    isParent: Boolean = false,
) {
    val complete = status == StudyTaskStatus.Completed
    Box(
        modifier = modifier.size(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (complete) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else if (isParent) {
            Surface(
                modifier = Modifier.size(10.dp),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(
                    width = 1.25.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
                ),
            ) {}
        } else {
            Icon(
                Icons.Default.Eco,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
            )
        }
    }
}

@Composable
private fun TimelineNestedTaskBranchRow(
    task: GeneratedStudyBlock,
    pageLabel: String?,
    isFirstLeaf: Boolean,
    isLastLeaf: Boolean,
    continuesMainRail: Boolean,
    continuesChildRailAfterGroup: Boolean,
    onCollapseParent: () -> Unit,
) {
    var rowHeightPx by remember { mutableIntStateOf(0) }
    val rowHeight = with(LocalDensity.current) { rowHeightPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCollapseParent),
    ) {
        if (rowHeightPx > 0) {
            NestedTaskBranchConnector(
                isFirstLeaf = isFirstLeaf,
                isLastLeaf = isLastLeaf,
                continuesMainRail = continuesMainRail,
                continuesChildRailAfterGroup = continuesChildRailAfterGroup,
                modifier = Modifier
                    .width(82.dp)
                    .height(rowHeight),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowHeightPx = it.height },
            verticalAlignment = Alignment.Top,
        ) {
            Spacer(Modifier.width(82.dp))
            TaskRowTextContent(
                task = task,
                pageLabel = pageLabel,
                muted = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            )
            if (task.status != StudyTaskStatus.NotStarted) {
                Spacer(Modifier.width(10.dp))
                Box(Modifier.padding(top = 10.dp)) {
                    StatusPill(statusLabel(task.status), statusContainer(task.status), statusContent(task.status))
                }
            }
        }

        TreeTaskLeafNode(
            status = task.status,
            modifier = Modifier.padding(start = 50.dp, top = 3.dp),
        )
    }
}

@Composable
private fun TimelineSourceDivider(
    source: String,
    continuesChildRail: Boolean,
    showMainRail: Boolean,
    modifier: Modifier = Modifier,
) {
    val trunkRail = treeLineColor(0.20f)
    val fadedTrunkRail = treeLineColor(0.08f)
    val dividerHeight = if (continuesChildRail) 40.dp else 24.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dividerHeight),
    ) {
        Canvas(
            modifier = Modifier
                .width(54.dp)
                .fillMaxHeight(),
        ) {
            val branchX = 9.dp.toPx()
            val nodeCenterX = 39.dp.toPx()
            if (showMainRail) {
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = if (continuesChildRail) fadedTrunkRail else trunkRail,
                    start = Offset(branchX, 0f),
                    end = Offset(branchX, size.height),
                    strokeWidth = strokeWidth,
                    cap = if (continuesChildRail) StrokeCap.Round else StrokeCap.Butt,
                    pathEffect = if (continuesChildRail) {
                        mainTimelineContinuationStroke(strokeWidth).pathEffect
                    } else {
                        null
                    },
                )
            }
            if (continuesChildRail) {
                drawLine(
                    color = trunkRail,
                    start = Offset(nodeCenterX, 0f),
                    end = Offset(nodeCenterX, size.height),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Butt,
                )
            }
        }
        TimelineSourceDividerLabel(
            text = source,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 44.dp, end = 24.dp)
                .align(Alignment.CenterStart),
        )
    }
}

@Composable
private fun TimelineSourceDividerLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        SourceDividerPill(text = text)
    }
}

@Composable
private fun SourceDividerLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val lineColor = treeLineColor(0.09f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceDividerLine(color = lineColor)
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        SourceDividerLine(color = lineColor)
    }
}

@Composable
private fun SourceDividerPill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
        border = BorderStroke(1.dp, treeLineColor(0.08f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowScope.SourceDividerLine(color: Color) {
    Canvas(
        modifier = Modifier
            .weight(1f)
            .height(1.dp),
    ) {
        drawLine(
            color = color,
            start = Offset.Zero,
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Butt,
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
    val trunkColor = treeLineColor(0.20f)
    val fadedTrunkColor = treeLineColor(0.08f)
    Canvas(modifier) {
        val branchX = 9.dp.toPx()
        val nodeCenterX = 39.dp.toPx()
        val nodeCenterY = 18.dp.toPx()
        val nodeGap = 6.dp.toPx()
        val strokeWidth = 1.dp.toPx()
        val exitY = size.height
        val continuationStroke = mainTimelineContinuationStroke(strokeWidth)
        if (isFirstBranch) {
            drawPath(
                path = Path().apply {
                    moveTo(branchX, 0f)
                    lineTo(branchX, nodeCenterY)
                    lineTo(nodeCenterX - nodeGap, nodeCenterY)
                },
                color = trunkColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
        }
        if (!isLastDay) {
            if (isFirstBranch) {
                drawLine(
                    color = fadedTrunkColor,
                    start = Offset(branchX, nodeCenterY + strokeWidth / 2f),
                    end = Offset(branchX, exitY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                    pathEffect = continuationStroke.pathEffect,
                )
            } else {
                drawLine(
                    color = fadedTrunkColor,
                    start = Offset(branchX, 0f),
                    end = Offset(branchX, exitY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                    pathEffect = continuationStroke.pathEffect,
                )
            }
        }

        val upperRailEnd = nodeCenterY - nodeGap
        val lowerRailStart = nodeCenterY + nodeGap
        if (!isFirstBranch && upperRailEnd > 0f) {
            drawLine(
                color = trunkColor,
                start = Offset(nodeCenterX, 0f),
                end = Offset(nodeCenterX, upperRailEnd),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Butt,
            )
        }
        if (!isLastBranch && lowerRailStart < exitY) {
            drawLine(
                color = trunkColor,
                start = Offset(nodeCenterX, lowerRailStart),
                end = Offset(nodeCenterX, exitY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Butt,
            )
        }
    }
}

@Composable
private fun NestedTaskBranchConnector(
    isFirstLeaf: Boolean,
    isLastLeaf: Boolean,
    continuesMainRail: Boolean,
    continuesChildRailAfterGroup: Boolean,
    modifier: Modifier = Modifier,
) {
    val mainRailColor = treeLineColor(0.08f)
    val childRailColor = treeLineColor(0.17f)
    Canvas(modifier) {
        val mainBranchX = 9.dp.toPx()
        val childBranchX = 39.dp.toPx()
        val leafCenterX = 65.dp.toPx()
        val leafCenterY = 18.dp.toPx()
        val nodeGap = 6.dp.toPx()
        val strokeWidth = 1.dp.toPx()

        if (continuesMainRail) {
            val continuationStroke = mainTimelineContinuationStroke(strokeWidth)
            drawLine(
                color = mainRailColor,
                start = Offset(mainBranchX, 0f),
                end = Offset(mainBranchX, size.height),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
                pathEffect = continuationStroke.pathEffect,
            )
        }

        drawPath(
            path = Path().apply {
                moveTo(childBranchX, 0f)
                lineTo(childBranchX, leafCenterY)
                lineTo(leafCenterX - nodeGap, leafCenterY)
            },
            color = childRailColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
        )
        if (!isLastLeaf || continuesChildRailAfterGroup) {
            drawLine(
                color = childRailColor,
                start = Offset(childBranchX, leafCenterY + strokeWidth / 2f),
                end = Offset(childBranchX, size.height),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Butt,
            )
        }
    }
}

@Composable
private fun TimelineDayGap(height: Dp = 28.dp) {
    val mutedColor = treeLineColor(0.08f)
    Canvas(
        modifier = Modifier
            .width(54.dp)
            .height(height),
    ) {
        val branchX = 9.dp.toPx()
        val strokeWidth = 1.dp.toPx()
        val continuationStroke = mainTimelineContinuationStroke(strokeWidth)
        drawLine(
            color = mutedColor,
            start = Offset(branchX, 0f),
            end = Offset(branchX, size.height),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
            pathEffect = continuationStroke.pathEffect,
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
        pluralStringResource(R.plurals.study_leaf_count, day.tasks.size, day.tasks.size),
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
        style = MaterialTheme.typography.titleMedium,
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
    onEditPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timelineItems = remember(tasks, documents) { tasks.toTimelineStudyItems(documents) }
    val timelineStateKey = remember(timelineItems) {
        timelineItems.joinToString("|") { it.timelineKey() }
    }
    var detailsExpanded by rememberSaveable(timelineStateKey) {
        mutableStateOf(tasks.size <= UnscheduledAutoCollapseLeafThreshold)
    }
    var expandedItemKey by rememberSaveable(timelineStateKey) { mutableStateOf<String?>(null) }
    val meta = listOf(
        pluralStringResource(R.plurals.study_leaf_count, tasks.size, tasks.size),
        formatMinutes(tasks.sumOf { it.likelyStudyMinutes }),
    ).joinToString(" \u2022 ")
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) {
                        val nextExpanded = !detailsExpanded
                        detailsExpanded = nextExpanded
                        if (!nextExpanded) expandedItemKey = null
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.unscheduled_tasks),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = meta,
                        modifier = Modifier.widthIn(max = 148.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    )
                }
                Text(
                    text = stringResource(R.string.unscheduled_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (detailsExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    var previousSourceKey: String? = null
                    timelineItems.forEachIndexed { index, item ->
                        val itemKey = item.timelineKey()
                        val sourceDocument = item.sourceDocument
                        val sourceKey = sourceDocument?.id ?: "other"
                        if (sourceKey != previousSourceKey) {
                            if (index > 0) {
                                Spacer(Modifier.height(4.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                SourceDividerPill(
                                    text = sourceDocument
                                        ?.let { stringResource(R.string.pdf_order_label, it.order) }
                                        ?: stringResource(R.string.other_material),
                                )
                            }
                        }
                        previousSourceKey = sourceKey
                        UnscheduledStudyItemRow(
                            item = item,
                            meta = timelineStudyItemMeta(item),
                            expanded = expandedItemKey == itemKey,
                            onToggleExpanded = {
                                expandedItemKey = if (expandedItemKey == itemKey) null else itemKey
                            },
                        )
                    }
                }
            }

            TextButton(
                onClick = onEditPlan,
                modifier = Modifier
                    .align(Alignment.End)
                    .height(38.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.make_room))
            }
        }
    }
}

@Composable
private fun UnscheduledStudyItemRow(
    item: TimelineStudyItem,
    meta: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val grouped = item.tasks.size > 1
    val rowModifier = if (grouped) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded)
    } else {
        Modifier.fillMaxWidth()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Row(
            modifier = rowModifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TreeTaskLeafNode(
                status = item.timelineStatus(),
                isParent = grouped,
            )
            Spacer(Modifier.width(8.dp))
            TimelineStudyItemTextContent(
                item = item,
                meta = meta,
                modifier = Modifier.weight(1f),
            )
            if (grouped) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
        }

        if (grouped && expanded) {
            item.tasks.forEach { task ->
                UnscheduledLeafRow(
                    task = task,
                    pageLabel = taskPageLabel(task),
                )
            }
        }
    }
}

@Composable
private fun UnscheduledLeafRow(
    task: GeneratedStudyBlock,
    pageLabel: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 30.dp, top = 3.dp, bottom = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TreeTaskLeafNode(status = task.status)
        Spacer(Modifier.width(8.dp))
        TaskRowTextContent(
            task = task,
            pageLabel = pageLabel,
            muted = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MaterialTaskRowContent(
    task: GeneratedStudyBlock,
    meta: String,
    connectBefore: Boolean,
    connectAfter: Boolean,
) {
    var rowHeightPx by remember { mutableIntStateOf(0) }
    val rowHeight = with(LocalDensity.current) { rowHeightPx.toDp() }
    val connectorColor = treeLineColor(0.20f)
    val nodeContainerSize = 20.dp
    val nodeSize = 10.dp
    val nodeClearance = 8.dp
    val rowVerticalPadding = 8.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { rowHeightPx = it.height },
    ) {
        if (rowHeightPx > 0 && (connectBefore || connectAfter)) {
            Canvas(
                modifier = Modifier
                    .width(20.dp)
                    .height(rowHeight),
            ) {
                val centerX = size.width / 2f
                val centerY = rowVerticalPadding.toPx() + nodeContainerSize.toPx() / 2f
                val nodeGap = nodeClearance.toPx()
                if (connectBefore) {
                    val endY = centerY - nodeGap
                    drawLine(
                        color = connectorColor,
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, endY.coerceAtLeast(0f)),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Butt,
                    )
                }
                if (connectAfter) {
                    val startY = centerY + nodeGap
                    drawLine(
                        color = connectorColor,
                        start = Offset(centerX, startY.coerceAtMost(size.height)),
                        end = Offset(centerX, size.height),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Butt,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = rowVerticalPadding),
            verticalAlignment = Alignment.Top,
        ) {
            TaskBullet(
                status = task.status,
                nodeSize = nodeSize,
                completeIconSize = 12.dp,
                borderWidth = 1.25.dp,
                containerSize = nodeContainerSize,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                )
            }
            if (task.status != StudyTaskStatus.NotStarted) {
                Spacer(Modifier.width(8.dp))
                StatusPill(statusLabel(task.status), statusContainer(task.status), statusContent(task.status))
            }
        }
    }
}

@Composable
private fun TimelineStudyItemTextContent(
    item: TimelineStudyItem,
    meta: String,
    modifier: Modifier = Modifier,
    showMeta: Boolean = true,
) {
    val textModifier = if (showMeta) {
        modifier.heightIn(min = 46.dp)
    } else {
        modifier
    }
    Column(
        modifier = textModifier,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (showMeta) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TaskRowTextContent(
    task: GeneratedStudyBlock,
    pageLabel: String?,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val meta = listOfNotNull(
        pageLabel,
        formatMinutes(task.likelyStudyMinutes),
        taskTypeLabel(task.taskType),
    ).joinToString(" \u2022 ")
    val titleColor = if (muted) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.84f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val metaColor = if (muted) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier.heightIn(min = 46.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = task.title,
            style = MaterialTheme.typography.labelLarge,
            color = titleColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = metaColor,
        )
    }
}

@Composable
private fun TaskBullet(
    status: StudyTaskStatus,
    modifier: Modifier = Modifier,
    nodeSize: Dp = 14.dp,
    containerSize: Dp = 30.dp,
    completeIconSize: Dp = 16.dp,
    borderWidth: Dp = 1.5.dp,
    borderColor: Color? = null,
) {
    val complete = status == StudyTaskStatus.Completed
    val defaultBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    Box(modifier = modifier.size(containerSize), contentAlignment = Alignment.Center) {
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
                        color = borderColor ?: defaultBorderColor,
                    ),
                ) {}
            }
        }
    }
}

@Composable
private fun dayLoadLabel(day: StudyScheduleDay): String = when {
    day.isOverCapacity -> stringResource(R.string.over_capacity)
    day.isRisky -> stringResource(R.string.load_tight)
    else -> difficultyLabel(day.load)
}

@Composable
private fun difficultyLabel(difficulty: StudyBlockDifficulty): String = when (difficulty) {
    StudyBlockDifficulty.Light -> stringResource(R.string.load_light)
    StudyBlockDifficulty.Standard -> stringResource(R.string.load_standard)
    StudyBlockDifficulty.Heavy -> stringResource(R.string.load_heavy)
}

internal fun taskSourceDocumentLabel(task: GeneratedStudyBlock, documents: List<StudySourceDocument>): String? {
    val ref = task.sourceRefs.firstOrNull() ?: return null
    return documents
        .firstOrNull { it.matchesSourceDocumentId(ref.documentId) }
        ?.filename
        ?.shortDocumentName()
}

@Composable
internal fun taskPageLabel(task: GeneratedStudyBlock): String? {
    val ref = task.sourceRefs.firstOrNull() ?: return null
    return when {
        ref.startPage != null && ref.endPage != null && ref.endPage != ref.startPage -> stringResource(R.string.source_page_range, ref.startPage, ref.endPage)
        ref.startPage != null -> stringResource(R.string.source_page, ref.startPage)
        else -> null
    }
}

@Composable
private fun timelineStudyItemMeta(item: TimelineStudyItem): String {
    if (item.tasks.size == 1) {
        val task = item.tasks.first()
        return listOfNotNull(
            taskPageLabel(task),
            formatMinutes(task.likelyStudyMinutes),
            taskTypeLabel(task.taskType),
        ).joinToString(" \u2022 ")
    }

    val leafCount = item.tasks.size
    return listOfNotNull(
        timelineStudyItemPageLabel(item),
        pluralStringResource(R.plurals.study_leaf_count, leafCount, leafCount),
        formatMinutes(item.tasks.sumOf { it.likelyStudyMinutes }),
    ).joinToString(" \u2022 ")
}

@Composable
private fun timelineStudyItemPageLabel(item: TimelineStudyItem): String? {
    val refs = item.tasks.mapNotNull { it.sourceRefForTimeline(item.sourceDocument) }
    val start = refs.mapNotNull { it.startPage }.minOrNull()
    val end = refs.mapNotNull { it.endPage ?: it.startPage }.maxOrNull()
    return when {
        start != null && end != null && end != start -> stringResource(R.string.source_page_range, start, end)
        start != null -> stringResource(R.string.source_page, start)
        else -> null
    }
}

@Composable
internal fun taskTypeColor(type: StudyTaskType) = when (type) {
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

internal fun taskTypeIcon(type: StudyTaskType): ImageVector = when (type) {
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
private fun MaterialSection(
    group: MaterialGroup,
    orderNumber: Int?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = TaskProgressCalculator().projectProgress(group.tasks)
    val totalMinutes = group.tasks.sumOf { it.reservedStudyMinutes.coerceAtLeast(0) }
    val pageLabel = group.document?.pageCount?.let { pluralStringResource(R.plurals.source_page_count, it, it) }
    val meta = listOfNotNull(
        "${progress.first} / ${progress.second}",
        formatMinutes(totalMinutes),
        pageLabel,
    ).joinToString(" \u2022 ")
    val title = group.document?.filename?.shortDocumentName() ?: stringResource(R.string.other_material)
    val sourceGroups = materialSourceGroups(group)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = renCardBorderStroke(),
        colors = CardDefaults.cardColors(containerColor = renCardContainerColor()),
    ) {
        Column(Modifier.animateContentSize()) {
            Surface(onClick = onToggleExpanded, color = Color.Transparent) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (orderNumber != null) {
                        MaterialOrderBadge(number = orderNumber)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            if (expanded) {
                HorizontalDivider()
                sourceGroups.forEachIndexed { groupIndex, sourceGroup ->
                    if (groupIndex == 0) {
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Spacer(Modifier.height(12.dp))
                    }
                    val connectsGroup = sourceGroup.tasks.size > 1
                    sourceGroup.tasks.forEachIndexed { taskIndex, task ->
                        Box(Modifier.padding(horizontal = 12.dp)) {
                            MaterialTaskRowContent(
                                task = task,
                                meta = materialTaskMeta(task, group),
                                connectBefore = connectsGroup && taskIndex > 0,
                                connectAfter = connectsGroup && taskIndex < sourceGroup.tasks.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialOrderBadge(number: Int) {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private data class MaterialGroup(
    val id: String,
    val document: StudySourceDocument?,
    val tasks: List<GeneratedStudyBlock>,
)

private data class MaterialSourceGroup(
    val tasks: List<GeneratedStudyBlock>,
)

private data class TimelineStudyItem(
    val sourceDocument: StudySourceDocument?,
    val title: String,
    val tasks: List<GeneratedStudyBlock>,
)

private fun TimelineStudyItem.timelineKey(): String = listOf(
    sourceDocument?.id.orEmpty(),
    title,
    tasks.firstOrNull()?.id.orEmpty(),
    tasks.lastOrNull()?.id.orEmpty(),
    tasks.size.toString(),
).joinToString("|")

private fun List<GeneratedStudyBlock>.toTimelineStudyItems(
    documents: List<StudySourceDocument>,
): List<TimelineStudyItem> {
    val items = mutableListOf<TimelineStudyItem>()
    var currentSource: StudySourceDocument? = null
    var currentTitle: String? = null
    var currentTasks = mutableListOf<GeneratedStudyBlock>()

    fun flushCurrent() {
        if (currentTasks.isEmpty()) return
        items += currentTasks.toTimelineStudyItem(currentSource, currentTitle)
        currentTasks = mutableListOf()
        currentSource = null
        currentTitle = null
    }

    sortedBy { it.order }.forEach { task ->
        val source = task.primarySourceDocument(documents)
        val title = task.sourceRefForTimeline(source)?.materialGroupDisplayTitle()
        if (title == null) {
            flushCurrent()
            items += TimelineStudyItem(
                sourceDocument = source,
                title = task.title,
                tasks = listOf(task),
            )
        } else if (currentTasks.isEmpty() || (source?.id == currentSource?.id && title == currentTitle)) {
            currentSource = source
            currentTitle = title
            currentTasks += task
        } else {
            flushCurrent()
            currentSource = source
            currentTitle = title
            currentTasks += task
        }
    }

    flushCurrent()
    return items
}

private fun List<GeneratedStudyBlock>.toTimelineStudyItem(
    sourceDocument: StudySourceDocument?,
    materialGroupTitle: String?,
): TimelineStudyItem {
    val first = first()
    return TimelineStudyItem(
        sourceDocument = sourceDocument,
        title = if (size == 1) first.title else materialGroupTitle ?: first.title,
        tasks = this,
    )
}

private fun TimelineStudyItem.timelineStatus(): StudyTaskStatus = when {
    tasks.size == 1 -> tasks.first().status
    tasks.all { it.status == StudyTaskStatus.Completed } -> StudyTaskStatus.Completed
    tasks.any { it.status == StudyTaskStatus.OverCapacity } -> StudyTaskStatus.OverCapacity
    tasks.any { it.status == StudyTaskStatus.Unscheduled } -> StudyTaskStatus.Unscheduled
    tasks.any { it.status == StudyTaskStatus.InProgress || it.status == StudyTaskStatus.Completed || it.status == StudyTaskStatus.Rescheduled } -> StudyTaskStatus.InProgress
    tasks.any { it.status == StudyTaskStatus.DeferredByUser } -> StudyTaskStatus.DeferredByUser
    tasks.any { it.status == StudyTaskStatus.ExcludedByUser } -> StudyTaskStatus.ExcludedByUser
    tasks.any { it.status == StudyTaskStatus.Locked } -> StudyTaskStatus.Locked
    else -> StudyTaskStatus.NotStarted
}

private fun materialGroups(plan: GeneratedStudyPlan): List<MaterialGroup> {
    val documents = plan.sourceDocuments.sortedWith(compareBy<StudySourceDocument> { it.order }.thenBy { it.filename })
    val activeTasks = plan.blocks
        .filterNot { it.status == StudyTaskStatus.ExcludedByUser }
        .sortedBy { it.order }
    val assignedTaskIds = mutableSetOf<String>()
    val groups = documents.mapNotNull { document ->
        val tasks = activeTasks.filter { task ->
            task.primarySourceDocument(documents)?.id == document.id
        }
        if (tasks.isEmpty()) {
            null
        } else {
            assignedTaskIds += tasks.map { it.id }
            MaterialGroup(
                id = document.id,
                document = document,
                tasks = tasks,
            )
        }
    }.toMutableList()

    val otherTasks = activeTasks.filterNot { it.id in assignedTaskIds }
    if (otherTasks.isNotEmpty()) {
        groups += MaterialGroup(
            id = "other",
            document = null,
            tasks = otherTasks,
        )
    }
    return groups
}

private fun GeneratedStudyBlock.primarySourceDocument(documents: List<StudySourceDocument>): StudySourceDocument? {
    sourceRefs.forEach { ref ->
        documents.firstOrNull { it.matchesSourceDocumentId(ref.documentId) }?.let { return it }
    }
    return null
}

private fun GeneratedStudyBlock.sourceRefForTimeline(sourceDocument: StudySourceDocument?): StudySourceRef? =
    sourceDocument
        ?.let { document -> sourceRefs.firstOrNull { document.matchesSourceDocumentId(it.documentId) } }
        ?: sourceRefs.firstOrNull()

private fun StudySourceDocument.matchesSourceDocumentId(value: String): Boolean =
    id == value || uploadDocumentId == value

private fun materialSourceGroups(group: MaterialGroup): List<MaterialSourceGroup> {
    val groups = mutableListOf<MaterialSourceGroup>()
    var currentTitle: String? = null
    var currentTasks = mutableListOf<GeneratedStudyBlock>()
    group.tasks.sortedBy { it.order }.forEach { task ->
        val sourceTitle = task.materialSourceRef(group)?.materialGroupDisplayTitle()
        if (sourceTitle == null) {
            if (currentTasks.isNotEmpty()) {
                groups += MaterialSourceGroup(currentTasks)
                currentTitle = null
                currentTasks = mutableListOf()
            }
            groups += MaterialSourceGroup(listOf(task))
        } else if (currentTasks.isEmpty() || sourceTitle == currentTitle) {
            currentTitle = sourceTitle
            currentTasks += task
        } else {
            groups += MaterialSourceGroup(currentTasks)
            currentTitle = sourceTitle
            currentTasks = mutableListOf(task)
        }
    }
    if (currentTasks.isNotEmpty()) {
        groups += MaterialSourceGroup(currentTasks)
    }
    return groups
}

private fun GeneratedStudyBlock.materialSourceRef(group: MaterialGroup): StudySourceRef? =
    group.document
        ?.let { document -> sourceRefs.firstOrNull { document.matchesSourceDocumentId(it.documentId) } }
        ?: sourceRefs.firstOrNull()

private fun StudySourceRef.materialGroupDisplayTitle(): String? =
    materialGroupTitle?.takeIf { it.isNotBlank() }

@Composable
private fun materialTaskMeta(task: GeneratedStudyBlock, group: MaterialGroup): String {
    val ref = task.materialSourceRef(group)
    val pageLabel = when {
        ref?.startPage != null && ref.endPage != null && ref.endPage != ref.startPage -> stringResource(R.string.source_page_range, ref.startPage, ref.endPage)
        ref?.startPage != null -> stringResource(R.string.source_page, ref.startPage)
        else -> null
    }
    return listOfNotNull(
        pageLabel,
        formatMinutes(task.likelyStudyMinutes),
        taskTypeLabel(task.taskType),
    ).joinToString(" \u2022 ")
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
    PlanRealismStatus.Crammed -> stringResource(R.string.plan_is_crammed)
    PlanRealismStatus.Overloaded -> stringResource(R.string.plan_is_overloaded)
}

@Composable
private fun realismWarningTitle(status: PlanRealismStatus) = when (status) {
    PlanRealismStatus.OnTrack -> stringResource(R.string.on_track)
    PlanRealismStatus.Tight -> stringResource(R.string.plan_is_tight)
    PlanRealismStatus.Crammed -> stringResource(R.string.crammed_plan_title)
    PlanRealismStatus.Overloaded -> stringResource(R.string.overloaded_plan_title)
}

@Composable
private fun realismWarningMessage(realism: PlanRealism) = when (realism.status) {
    PlanRealismStatus.OnTrack -> ""
    PlanRealismStatus.Tight -> stringResource(
        R.string.tight_plan_message,
        formatMinutes(realism.remainingMinutes),
        formatMinutes(realism.availableMinutes ?: 0),
    )
    PlanRealismStatus.Crammed -> stringResource(R.string.crammed_plan_message)
    PlanRealismStatus.Overloaded -> stringResource(R.string.overloaded_plan_message)
}

@Composable
private fun realismColor(status: PlanRealismStatus) = when (status) {
    PlanRealismStatus.OnTrack -> MaterialTheme.colorScheme.primary
    PlanRealismStatus.Tight -> MaterialTheme.colorScheme.tertiary
    PlanRealismStatus.Crammed -> MaterialTheme.colorScheme.tertiary
    PlanRealismStatus.Overloaded -> MaterialTheme.colorScheme.error
}

@Composable
private fun realismPillColor(status: PlanRealismStatus) = when (status) {
    PlanRealismStatus.OnTrack -> MaterialTheme.colorScheme.primary
    PlanRealismStatus.Tight -> MaterialTheme.colorScheme.tertiary
    PlanRealismStatus.Crammed -> MaterialTheme.colorScheme.tertiary
    PlanRealismStatus.Overloaded -> MaterialTheme.colorScheme.error
}

@Composable
private fun realismContainer(status: PlanRealismStatus) = when (status) {
    PlanRealismStatus.OnTrack -> MaterialTheme.colorScheme.primaryContainer
    PlanRealismStatus.Tight -> MaterialTheme.colorScheme.tertiaryContainer
    PlanRealismStatus.Crammed -> MaterialTheme.colorScheme.tertiaryContainer
    PlanRealismStatus.Overloaded -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.24f)
}

@Composable
private fun realismContent(status: PlanRealismStatus) = when (status) {
    PlanRealismStatus.OnTrack -> MaterialTheme.colorScheme.onPrimaryContainer
    PlanRealismStatus.Tight -> MaterialTheme.colorScheme.onTertiaryContainer
    PlanRealismStatus.Crammed -> MaterialTheme.colorScheme.onTertiaryContainer
    PlanRealismStatus.Overloaded -> MaterialTheme.colorScheme.onErrorContainer
}

@Composable
private fun realismWarningBorder(status: PlanRealismStatus): BorderStroke? = when (status) {
    PlanRealismStatus.Overloaded -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.38f))
    else -> null
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
internal fun taskTypeLabel(type: StudyTaskType): String = when (type) {
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

internal fun formatMinutes(minutes: Int): String = when {
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
    val remainingMillis = deadline.timeInMillis - today.timeInMillis
    if (remainingMillis <= 0L) {
        return stringResource(R.string.deadline_past)
    }
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

private fun suggestedLeafMinutes(tasks: List<GeneratedStudyBlock>): Int {
    val values = tasks.map { it.likelyStudyMinutes }.filter { it > 0 }.sorted()
    return (values.getOrNull(values.size / 2) ?: 30).coerceIn(15, 120)
}

private const val MillisPerDay = 24 * 60 * 60 * 1000L
