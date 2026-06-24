package com.hci.ren.feature.studymap

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hci.ren.R
import com.hci.ren.feature.pdfupload.presentation.PlanSetupSubmission
import com.hci.ren.feature.pdfupload.presentation.StudyDeadline
import com.hci.ren.feature.pdfupload.presentation.isSelectableDeadlineUtc
import com.hci.ren.feature.plangeneration.GeneratedStudyBlock
import com.hci.ren.feature.plangeneration.GeneratedStudyPlan
import com.hci.ren.feature.plangeneration.StudyTaskStatus
import com.hci.ren.feature.plangeneration.StudyTaskType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch

private enum class AdjustmentSheet { Deadline, DailyTime, Scope, Continue }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyMapScreen(
    plan: GeneratedStudyPlan?,
    preferences: PlanSetupSubmission?,
    modifier: Modifier = Modifier,
    dailyMinutesOverride: Int? = null,
    acceptedTightPlan: Boolean = false,
    changeMessage: String? = null,
    suggestedDeadline: String? = null,
    recommendedDaysBalanced: Int = 0,
    recommendedDaysIntensive: Int = 0,
    onHome: () -> Unit,
    onCreateProject: () -> Unit,
    onInsights: () -> Unit,
    onConsumeMessage: () -> Unit,
    onExtendDeadline: (studyDays: Int, intensive: Boolean) -> Unit = { _, _ -> },
    onApplyDeadline: (String) -> Unit,
    onIncreaseDailyTime: (Int) -> Unit,
    onReduceScope: (ScopeReduction, Set<String>) -> Unit,
    onContinueAnyway: () -> Unit,
    onTaskStatusChange: (String, StudyTaskStatus) -> Unit,
    onTaskDurationChange: (String, Int) -> Unit,
    onExcludeTask: (String) -> Unit,
    onRestoreTask: (String) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedViewName by rememberSaveable { mutableStateOf(StudyMapView.Schedule.name) }
    val selectedView = StudyMapView.valueOf(selectedViewName)
    var selectedTask by remember { mutableStateOf<GeneratedStudyBlock?>(null) }
    var adjustment by remember { mutableStateOf<AdjustmentSheet?>(null) }
    val unavailable = stringResource(R.string.feature_not_available)

    BackHandler(onBack = onHome)

    LaunchedEffect(changeMessage) {
        if (changeMessage != null) {
            snackbar.showSnackbar(changeMessage)
            onConsumeMessage()
        }
    }

    if (plan == null || preferences == null) {
        EmptyStudyMap(
            onHome = onHome,
            onCreateProject = onCreateProject,
            onInsights = { scope.launch { snackbar.showSnackbar(unavailable) }; onInsights() },
            snackbar = snackbar,
            modifier = modifier,
        )
        return
    }

    val data = buildStudyMapData(plan, preferences, dailyMinutesOverride)
    val allComplete = data.activeTasks.isNotEmpty() && data.activeTasks.all { it.status == StudyTaskStatus.Completed }
    val today = Calendar.getInstance().toStudyDate()
    val todayTasks = data.schedule.days.firstOrNull { it.date == today }?.tasks.orEmpty()
    val ctaTask = todayTasks.firstOrNull(::isAvailableTask) ?: data.nextTask
    val ctaLabel = when {
        allComplete -> stringResource(R.string.view_insights)
        todayTasks.any(::isAvailableTask) -> stringResource(R.string.start_todays_plan)
        data.nextTask != null -> stringResource(R.string.start_next_task)
        data.schedule.unscheduledTasks.isNotEmpty() -> stringResource(R.string.review_unscheduled_tasks)
        else -> stringResource(R.string.start_first_task)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { StudyMapHeader(plan.projectName) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                Column(Modifier.navigationBarsPadding()) {
                    Button(
                        onClick = {
                            when {
                                allComplete -> onInsights()
                                ctaTask != null -> onTaskStatusChange(ctaTask.id, StudyTaskStatus.InProgress)
                                data.schedule.unscheduledTasks.isNotEmpty() -> selectedTask = data.schedule.unscheduledTasks.first()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(52.dp),
                        enabled = allComplete || ctaTask != null || data.schedule.unscheduledTasks.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                    ) {
                        Icon(if (allComplete) Icons.Default.Insights else Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(ctaLabel)
                    }
                    StudyMapNavigation(onHome = onHome, onInsights = { scope.launch { snackbar.showSnackbar(unavailable) }; onInsights() })
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 20.dp),
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
                StudyMapView.Schedule -> scheduleItems(data, onTaskClick = { selectedTask = it }, onStart = {
                    onTaskStatusChange(it.id, StudyTaskStatus.InProgress)
                }, onDone = { onTaskStatusChange(it.id, StudyTaskStatus.Completed) }, onSkip = {
                    onTaskStatusChange(it.id, StudyTaskStatus.Skipped)
                }, onExclude = onExcludeTask)
                StudyMapView.Topics -> topicItems(data, onTaskClick = { selectedTask = it })
            }
            if (plan.blocks.isEmpty()) {
                item { EmptyTasksCard(onCreateProject) }
            }
        }
    }

    selectedTask?.let { task ->
        TaskDetailSheet(
            task = task,
            topicNames = plan.topics.filter { it.id in task.topicIds }.map { it.title },
            onDismiss = { selectedTask = null },
            onStatusChange = { onTaskStatusChange(task.id, it); selectedTask = null },
            onDurationChange = { onTaskDurationChange(task.id, it); selectedTask = null },
            onExclude = { onExcludeTask(task.id); selectedTask = null },
            onRestore = { onRestoreTask(task.id); selectedTask = null },
        )
    }

    when (adjustment) {
        AdjustmentSheet.Deadline -> DeadlineSheet(
            current = deadlineLabel(preferences),
            recommendedDaysBalanced = recommendedDaysBalanced,
            recommendedDaysIntensive = recommendedDaysIntensive,
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
private fun StudyMapHeader(projectName: String) {
    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(stringResource(R.string.study_map), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(projectName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProjectSummaryCard(data: StudyMapData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(46.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(data.plan.projectName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(realismLabel(data.realism.status), style = MaterialTheme.typography.labelLarge, color = realismColor(data.realism.status))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryValue(stringResource(R.string.deadline_label), deadlineLabel(data.preferences), Modifier.weight(1f))
                SummaryValue(stringResource(R.string.total_estimate), formatMinutes(data.totalEstimatedMinutes), Modifier.weight(1f))
                SummaryValue(stringResource(R.string.available_time), data.realism.availableMinutes?.let(::formatMinutes) ?: stringResource(R.string.no_deadline), Modifier.weight(1f))
            }
            LinearProgressIndicator(progress = { data.progress }, modifier = Modifier.fillMaxWidth())
            Text(pluralStringResource(R.plurals.tasks_completed_format, data.activeTasks.size, data.completedTasks, data.activeTasks.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
            AdjustmentAction(R.string.reduce_scope, R.string.reduce_scope_subtitle) { onAction(AdjustmentSheet.Scope) }
            AdjustmentAction(R.string.increase_daily_time, R.string.increase_daily_time_subtitle) { onAction(AdjustmentSheet.DailyTime) }
            AdjustmentAction(R.string.extend_deadline, R.string.extend_deadline_subtitle) { onAction(AdjustmentSheet.Deadline) }
            AdjustmentAction(R.string.continue_anyway, R.string.continue_anyway_short_subtitle) { onAction(AdjustmentSheet.Continue) }
        }
    }
}

@Composable
private fun AdjustmentAction(title: Int, subtitle: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StudyMapViewSwitcher(selected: StudyMapView, onSelected: (StudyMapView) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StudyMapView.entries.forEach { view ->
            Surface(
                modifier = Modifier.weight(1f).clickable(role = Role.Tab) { onSelected(view) },
                shape = RoundedCornerShape(12.dp),
                color = if (view == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    if (view == StudyMapView.Schedule) stringResource(R.string.schedule_view) else stringResource(R.string.topics_view),
                    modifier = Modifier.padding(vertical = 11.dp),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = if (view == selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.scheduleItems(
    data: StudyMapData,
    onTaskClick: (GeneratedStudyBlock) -> Unit,
    onStart: (GeneratedStudyBlock) -> Unit,
    onDone: (GeneratedStudyBlock) -> Unit,
    onSkip: (GeneratedStudyBlock) -> Unit,
    onExclude: (String) -> Unit,
) {
    data.nextTask?.let { task -> item(key = "next-${task.id}") { NextTaskCard(task, { onTaskClick(task) }, { onStart(task) }) } }
    itemsIndexed(data.schedule.days, key = { _, day -> day.date }) { index, day ->
        ScheduleDaySection(day, initiallyExpanded = index == 0, onTaskClick, onStart, onDone, onSkip, onExclude)
    }
    if (data.schedule.unscheduledTasks.isNotEmpty()) {
        item { SectionTitle(stringResource(R.string.unscheduled_tasks), stringResource(R.string.unscheduled_explanation)) }
        items(data.schedule.unscheduledTasks, key = { "unscheduled-${it.id}" }) { task ->
            StudyTaskCard(task, emptySet(), { onTaskClick(task) }, onStart, onDone, onSkip, onExclude)
        }
    }
    if (data.activeTasks.isNotEmpty() && data.activeTasks.all { it.status == StudyTaskStatus.Completed }) {
        item { CompletionCard() }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.topicItems(data: StudyMapData, onTaskClick: (GeneratedStudyBlock) -> Unit) {
    items(data.plan.topics, key = { "topic-${it.id}" }) { topic ->
        val tasks = data.plan.blocks.filter { topic.id in it.topicIds && !it.isExcluded }
        TopicSection(topic.title, tasks, onTaskClick)
    }
}

@Composable
private fun NextTaskCard(task: GeneratedStudyBlock, onClick: () -> Unit, onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.next_up), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            Text(task.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${formatMinutes(task.durationMinutes)} • ${taskTypeLabel(task.taskType)}", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.start_here), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Button(onClick = onStart, colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.start)) }
            }
        }
    }
}

@Composable
private fun ScheduleDaySection(
    day: StudyScheduleDay,
    initiallyExpanded: Boolean,
    onTaskClick: (GeneratedStudyBlock) -> Unit,
    onStart: (GeneratedStudyBlock) -> Unit,
    onDone: (GeneratedStudyBlock) -> Unit,
    onSkip: (GeneratedStudyBlock) -> Unit,
    onExclude: (String) -> Unit,
) {
    var expanded by rememberSaveable(day.date) { mutableStateOf(initiallyExpanded) }
    val completedIds = day.tasks.filter { it.status == StudyTaskStatus.Completed }.mapTo(mutableSetOf()) { it.id }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(dateHeading(day.date), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text(pluralStringResource(R.plurals.task_count_time, day.tasks.size, day.tasks.size, formatMinutes(day.totalScheduledMinutes)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (day.isOverCapacity) StatusPill(stringResource(R.string.over_capacity), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
        }
        if (expanded) day.tasks.forEach { task ->
            StudyTaskCard(task, completedIds, { onTaskClick(task) }, onStart, onDone, onSkip, onExclude)
        }
    }
}

@Composable
private fun StudyTaskCard(
    task: GeneratedStudyBlock,
    completedIds: Set<String>,
    onClick: () -> Unit,
    onStart: (GeneratedStudyBlock) -> Unit,
    onDone: (GeneratedStudyBlock) -> Unit,
    onSkip: (GeneratedStudyBlock) -> Unit,
    onExclude: (String) -> Unit,
) {
    val locked = task.status == StudyTaskStatus.Locked || task.dependencies.any { it !in completedIds }
    val status = if (locked) StudyTaskStatus.Locked else task.status
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = taskTypeColor(task.taskType), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(taskTypeIcon(task.taskType), contentDescription = null, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${formatMinutes(task.durationMinutes)} • ${taskTypeLabel(task.taskType)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusPill(statusLabel(status), statusContainer(status), statusContent(status))
                if (locked) Text(stringResource(R.string.locked_reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!locked && task.status != StudyTaskStatus.Completed) DropdownMenuItem({ Text(stringResource(R.string.start)) }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) }, onClick = { menu = false; onStart(task) })
                    if (task.status != StudyTaskStatus.Completed) DropdownMenuItem({ Text(stringResource(R.string.mark_done)) }, leadingIcon = { Icon(Icons.Default.CheckCircle, null) }, onClick = { menu = false; onDone(task) })
                    if (task.isSkippable) DropdownMenuItem({ Text(stringResource(R.string.skip_task)) }, onClick = { menu = false; onSkip(task) })
                    DropdownMenuItem({ Text(stringResource(R.string.exclude_task)) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, onClick = { menu = false; onExclude(task.id) })
                }
            }
        }
    }
}

@Composable
private fun TopicSection(title: String, tasks: List<GeneratedStudyBlock>, onTaskClick: (GeneratedStudyBlock) -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val progress = TaskProgressCalculator().projectProgress(tasks)
    Card(shape = RoundedCornerShape(16.dp), border = CardDefaults.outlinedCardBorder(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(pluralStringResource(R.plurals.tasks_completed_format, progress.second, progress.first, progress.second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (expanded) {
                HorizontalDivider()
                tasks.forEach { task ->
                    Row(Modifier.fillMaxWidth().clickable { onTaskClick(task) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(taskTypeIcon(task.taskType), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(task.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(formatMinutes(task.durationMinutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(statusLabel(task.status), statusContainer(task.status), statusContent(task.status))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailSheet(
    task: GeneratedStudyBlock,
    topicNames: List<String>,
    onDismiss: () -> Unit,
    onStatusChange: (StudyTaskStatus) -> Unit,
    onDurationChange: (Int) -> Unit,
    onExclude: () -> Unit,
    onRestore: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var minutes by remember(task.id) { mutableStateOf(task.durationMinutes.toString()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.task_details), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(task.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (task.instructions.isNotBlank()) Text(task.instructions, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DetailRow(stringResource(R.string.duration), formatMinutes(task.durationMinutes))
            DetailRow(stringResource(R.string.type), taskTypeLabel(task.taskType))
            DetailRow(stringResource(R.string.status), statusLabel(task.status))
            DetailRow(stringResource(R.string.scheduled_date), task.scheduledDate?.let(::formatDate) ?: stringResource(R.string.not_scheduled))
            if (topicNames.isNotEmpty()) DetailRow(stringResource(R.string.topic), topicNames.joinToString())
            if (task.status == StudyTaskStatus.Locked || task.dependencies.isNotEmpty()) Text(stringResource(R.string.locked_reason), style = MaterialTheme.typography.bodySmall)
            if (editing) {
                OutlinedTextField(value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.duration)) }, suffix = { Text(stringResource(R.string.minute_suffix)) }, singleLine = true)
                Button(onClick = { minutes.toIntOrNull()?.let(onDurationChange) }, enabled = minutes.toIntOrNull() in 1..1_440, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onStatusChange(StudyTaskStatus.InProgress) }, modifier = Modifier.weight(1f), enabled = task.status != StudyTaskStatus.Locked) { Text(stringResource(R.string.start)) }
                    OutlinedButton(onClick = { onStatusChange(StudyTaskStatus.Completed) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.mark_done)) }
                }
                TextButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.edit_duration)) }
                if (task.isSkippable) TextButton(onClick = { onStatusChange(StudyTaskStatus.Skipped) }) { Text(stringResource(R.string.skip_task)) }
                if (task.isExcluded || task.isOptional) TextButton(onClick = onRestore) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.restore_task)) }
                else TextButton(onClick = onExclude) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.exclude_task)) }
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
                    isSelectableDeadlineUtc(utcTimeMillis, System.currentTimeMillis())
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
                Surface(Modifier.fillMaxWidth().clickable { selected = minutes }, shape = RoundedCornerShape(12.dp), color = if (selected == minutes) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
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
    var selected by remember { mutableStateOf(ScopeReduction.HighPriorityOnly) }
    val topics = remember { mutableStateListOf<String>() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.reduce_scope_sheet_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            previews.forEach { preview ->
                val title = when (preview.strategy) {
                    ScopeReduction.HighPriorityOnly -> R.string.keep_high_priority
                    ScopeReduction.RemoveOptionalReviews -> R.string.remove_optional_reviews
                    ScopeReduction.ReducePractice -> R.string.reduce_practice_volume
                    ScopeReduction.ChooseTopics -> R.string.choose_topics_manually
                }
                Surface(Modifier.fillMaxWidth().clickable { selected = preview.strategy }, shape = RoundedCornerShape(12.dp), color = if (selected == preview.strategy) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(14.dp)) {
                        Text(stringResource(title), fontWeight = FontWeight.SemiBold)
                        if (preview.savedMinutes > 0) Text(stringResource(R.string.saves_about, formatMinutes(preview.savedMinutes)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (selected == ScopeReduction.ChooseTopics) {
                Text(stringResource(R.string.select_topics), fontWeight = FontWeight.SemiBold)
                plan.topics.forEach { topic ->
                    Row(Modifier.fillMaxWidth().clickable { if (topic.id in topics) topics.remove(topic.id) else topics.add(topic.id) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = topic.id in topics, onCheckedChange = { checked -> if (checked) topics.add(topic.id) else topics.remove(topic.id) })
                        Text(topic.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Button(onClick = { onApply(selected, topics.toSet()) }, enabled = selected != ScopeReduction.ChooseTopics || topics.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.apply_changes)) }
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
private fun StudyMapNavigation(onHome: () -> Unit, onInsights: () -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        listOf(
            Triple(stringResource(R.string.home), Icons.Default.Home, onHome),
            Triple(stringResource(R.string.study_map), Icons.Default.Map, {}),
            Triple(stringResource(R.string.insights), Icons.Default.Insights, onInsights),
        ).forEachIndexed { index, (label, icon, action) ->
            NavigationBarItem(selected = index == 1, onClick = action, icon = { Icon(icon, label) }, label = { Text(label) })
        }
    }
}

@Composable
private fun EmptyStudyMap(onHome: () -> Unit, onCreateProject: () -> Unit, onInsights: () -> Unit, snackbar: SnackbarHostState, modifier: Modifier) {
    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbar) }, topBar = { StudyMapHeader(stringResource(R.string.no_study_project_selected)) }, bottomBar = { Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) { StudyMapNavigation(onHome, onInsights) } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Map, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) } }
                Text(stringResource(R.string.no_study_project_selected), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.no_study_project_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onCreateProject) { Text(stringResource(R.string.create_project)) }
            }
        }
    }
}

@Composable private fun EmptyTasksCard(onGenerate: () -> Unit) { Card { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.no_tasks_yet), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Button(onClick = onGenerate) { Text(stringResource(R.string.generate_tasks)) } } } }
@Composable private fun CompletionCard() { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer); Text(stringResource(R.string.finished_study_map), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(stringResource(R.string.all_tasks_complete_message)) } } }
@Composable private fun SectionTitle(title: String, subtitle: String) { Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() }); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun DetailRow(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(12.dp)); Text(value, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) } }
@Composable private fun StatusPill(label: String, container: Color, content: Color) { Surface(shape = RoundedCornerShape(50), color = container) { Text(label, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = content, fontWeight = FontWeight.SemiBold) } }

@Composable private fun realismLabel(status: PlanRealismStatus) = when (status) { PlanRealismStatus.OnTrack -> stringResource(R.string.on_track); PlanRealismStatus.Tight -> stringResource(R.string.plan_is_tight); PlanRealismStatus.Unrealistic -> stringResource(R.string.unrealistic_plan); PlanRealismStatus.NoDeadline -> stringResource(R.string.no_deadline) }
@Composable private fun realismColor(status: PlanRealismStatus) = when (status) { PlanRealismStatus.OnTrack -> MaterialTheme.colorScheme.primary; PlanRealismStatus.Tight -> MaterialTheme.colorScheme.tertiary; PlanRealismStatus.Unrealistic -> MaterialTheme.colorScheme.error; PlanRealismStatus.NoDeadline -> MaterialTheme.colorScheme.onSurfaceVariant }
@Composable private fun statusLabel(status: StudyTaskStatus) = when (status) { StudyTaskStatus.NotStarted -> stringResource(R.string.not_started); StudyTaskStatus.InProgress -> stringResource(R.string.task_status_in_progress); StudyTaskStatus.Completed -> stringResource(R.string.completed); StudyTaskStatus.Skipped -> stringResource(R.string.skipped); StudyTaskStatus.Locked -> stringResource(R.string.locked); StudyTaskStatus.Overdue -> stringResource(R.string.overdue); StudyTaskStatus.Rescheduled -> stringResource(R.string.rescheduled); StudyTaskStatus.Optional -> stringResource(R.string.optional); StudyTaskStatus.Excluded -> stringResource(R.string.excluded); StudyTaskStatus.Unscheduled -> stringResource(R.string.unscheduled); StudyTaskStatus.OverCapacity -> stringResource(R.string.over_capacity) }
@Composable private fun statusContainer(status: StudyTaskStatus) = when (status) { StudyTaskStatus.InProgress, StudyTaskStatus.Rescheduled -> MaterialTheme.colorScheme.primaryContainer; StudyTaskStatus.Completed -> MaterialTheme.colorScheme.secondaryContainer; StudyTaskStatus.Overdue, StudyTaskStatus.OverCapacity -> MaterialTheme.colorScheme.errorContainer; else -> MaterialTheme.colorScheme.surfaceVariant }
@Composable private fun statusContent(status: StudyTaskStatus) = when (status) { StudyTaskStatus.Overdue, StudyTaskStatus.OverCapacity -> MaterialTheme.colorScheme.onErrorContainer; StudyTaskStatus.InProgress, StudyTaskStatus.Rescheduled -> MaterialTheme.colorScheme.onPrimaryContainer; else -> MaterialTheme.colorScheme.onSurfaceVariant }
@Composable private fun taskTypeColor(type: StudyTaskType) = when (type) { StudyTaskType.Practice, StudyTaskType.Quiz, StudyTaskType.MockExam, StudyTaskType.MockTest -> MaterialTheme.colorScheme.tertiaryContainer; StudyTaskType.Review, StudyTaskType.Summary, StudyTaskType.MistakeReview -> MaterialTheme.colorScheme.secondaryContainer; else -> MaterialTheme.colorScheme.primaryContainer }
private fun taskTypeIcon(type: StudyTaskType): ImageVector = when (type) { StudyTaskType.Practice, StudyTaskType.Quiz, StudyTaskType.MockExam, StudyTaskType.MockTest -> Icons.AutoMirrored.Filled.Assignment; StudyTaskType.Review, StudyTaskType.Summary, StudyTaskType.MistakeReview -> Icons.Default.Refresh; StudyTaskType.Memorization -> Icons.Default.AddTask; StudyTaskType.Reading, StudyTaskType.Learn, StudyTaskType.Concept -> Icons.AutoMirrored.Filled.MenuBook; else -> Icons.Default.School }
@Composable private fun taskTypeLabel(type: StudyTaskType): String = when (type) { StudyTaskType.Learn, StudyTaskType.Concept -> stringResource(R.string.task_type_concept); StudyTaskType.Practice -> stringResource(R.string.task_type_practice); StudyTaskType.Review -> stringResource(R.string.task_type_review); StudyTaskType.Quiz, StudyTaskType.MockExam, StudyTaskType.MockTest -> stringResource(R.string.task_type_mock_test); StudyTaskType.Memorization -> stringResource(R.string.task_type_memorization); StudyTaskType.Skim, StudyTaskType.Reading -> stringResource(R.string.task_type_reading); StudyTaskType.Summary -> stringResource(R.string.task_type_summary); StudyTaskType.MistakeReview -> stringResource(R.string.task_type_mistake_review); StudyTaskType.Custom -> stringResource(R.string.task_type_custom) }
private fun isAvailableTask(task: GeneratedStudyBlock) = task.status !in setOf(StudyTaskStatus.Completed, StudyTaskStatus.Skipped, StudyTaskStatus.Locked, StudyTaskStatus.Excluded)
private fun formatMinutes(minutes: Int): String = when { minutes < 60 -> "$minutes min"; minutes % 60 == 0 -> "${minutes / 60}h"; else -> "${minutes / 60}h ${minutes % 60}m" }
private fun formatDate(date: String): String = date.toStudyCalendar()?.let { SimpleDateFormat("d MMMM", Locale.getDefault()).format(it.time) } ?: date
@Composable private fun dateHeading(date: String): String { val value = date.toStudyCalendar() ?: return date; val today = dayOnly(Calendar.getInstance()); val tomorrow = dayOnly(Calendar.getInstance()).apply { add(Calendar.DAY_OF_MONTH, 1) }; return when (dayOnly(value)) { today -> stringResource(R.string.today); tomorrow -> stringResource(R.string.tomorrow); else -> SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(value.time) } }
@Composable private fun deadlineLabel(preferences: PlanSetupSubmission): String = when (preferences.deadline) { StudyDeadline.NoFixedDeadline -> stringResource(R.string.no_deadline); else -> deadlineDate(preferences, Calendar.getInstance())?.let { SimpleDateFormat("d MMMM", Locale.getDefault()).format(it.time) } ?: stringResource(R.string.no_deadline) }
private fun suggestedBlockMinutes(tasks: List<GeneratedStudyBlock>): Int { val values = tasks.map { it.durationMinutes }.filter { it > 0 }.sorted(); return (values.getOrNull(values.size / 2) ?: 30).coerceIn(15, 120) }
