package com.hci.ren.feature.pdfupload.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hci.ren.ui.components.PlanFlowIntro
import com.hci.ren.ui.components.PlanFlowOptionRow
import com.hci.ren.ui.components.PlanFlowPrimaryButton
import com.hci.ren.ui.components.PlanFlowScaffold
import com.hci.ren.ui.components.PlanFlowSectionGap
import com.hci.ren.ui.theme.RenTheme
import com.hci.ren.ui.motion.RenFadeThroughDurationMillis
import com.hci.ren.ui.motion.RenMotionDurationMillis
import com.hci.ren.ui.motion.isReducedMotionEnabled
import com.hci.ren.ui.motion.renFadeThroughTransform
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSetupScreen(
    state: PlanSetupUiState,
    onBack: () -> Unit,
    onPlanTitleChanged: (String) -> Unit,
    onDeadlineSelected: (StudyDeadline) -> Unit,
    onDateSelected: (Long) -> Unit,
    onDailyTimeSelected: (DailyStudyTime) -> Unit,
    onCustomHoursChanged: (String) -> Unit,
    onCustomMinutesChanged: (String) -> Unit,
    onDayToggled: (StudyDay) -> Unit,
    onShortcutSelected: (StudyDayShortcut) -> Unit,
    onStudyDayResetOffsetSelected: (Int) -> Unit,
    onNext: () -> Unit,
    onGeneratePlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDatePickerOpen by rememberSaveable { mutableStateOf(false) }
    var isNavigationLocked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selectableDates = remember(state.studyDayResetOffsetHours) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                isSelectableDeadlineUtc(
                    selectedMillis = utcTimeMillis,
                    nowMillis = System.currentTimeMillis(),
                    resetOffsetHours = state.studyDayResetOffsetHours,
                )
        }
    }
    val datePickerState = rememberDatePickerState(selectableDates = selectableDates)
    fun navigateOnce(action: () -> Unit) {
        if (isNavigationLocked) return
        isNavigationLocked = true
        action()
        scope.launch {
            delay(RenFadeThroughDurationMillis.toLong())
            isNavigationLocked = false
        }
    }

    PlanFlowScaffold(
        onBack = { navigateOnce(onBack) },
        modifier = modifier,
        progress = state.progress,
        stepLabel = "${MaterialSelectionStepNumber + state.currentStep.number} OF $PlanCreationTotalSteps",
        bottomContent = {
            PlanSetupPrimaryButton(
                state = state,
                onNext = { navigateOnce(onNext) },
                onGeneratePlan = { navigateOnce(onGeneratePlan) },
            )
        },
    ) {
        val reducedMotion = isReducedMotionEnabled()
        AnimatedContent(
            targetState = state.currentStep,
            transitionSpec = {
                renFadeThroughTransform(reducedMotion = reducedMotion)
            },
            contentKey = { it },
            label = "plan-step",
            modifier = Modifier.fillMaxSize(),
        ) { step ->
            val stepScrollState = rememberScrollState()

            // Auto-scroll to show custom time fields when Custom is selected
            LaunchedEffect(state.selectedDailyTime == DailyStudyTime.Custom) {
                if (state.selectedDailyTime == DailyStudyTime.Custom) {
                    withFrameNanos { }
                    stepScrollState.animateScrollTo(stepScrollState.maxValue)
                }
            }

            Column(Modifier.fillMaxSize().verticalScroll(stepScrollState)) {
                when (step) {
                    PlanSetupStep.PlanTitle -> PlanTitleStep(
                        planTitle = state.planTitle,
                        onPlanTitleChanged = onPlanTitleChanged,
                    )

                    PlanSetupStep.Deadline -> DeadlineStep(
                        state = state,
                        onDeadlineSelected = onDeadlineSelected,
                        onChooseDate = { isDatePickerOpen = true },
                    )

                    PlanSetupStep.DailyTime -> DailyTimeStep(
                        state = state,
                        onDailyTimeSelected = onDailyTimeSelected,
                        onCustomHoursChanged = onCustomHoursChanged,
                        onCustomMinutesChanged = onCustomMinutesChanged,
                    )

                    PlanSetupStep.StudyDays -> StudyDaysStep(
                        state = state,
                        onDayToggled = onDayToggled,
                        onShortcutSelected = onShortcutSelected,
                        onStudyDayResetOffsetSelected = onStudyDayResetOffsetSelected,
                    )
                }
            }
        }
    }

    if (isDatePickerOpen) {
        DatePickerDialog(
            onDismissRequest = { isDatePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let(onDateSelected)
                        isDatePickerOpen = false
                    },
                    enabled = datePickerState.selectedDateMillis != null,
                ) {
                    Text(
                        text = "Choose",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { isDatePickerOpen = false }) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun PlanTitleStep(
    planTitle: String,
    onPlanTitleChanged: (String) -> Unit,
) {
    StepIntro(
        title = "What are we tackling?",
        subtitle = "Use a course, exam, or topic name.",
    )

    OutlinedTextField(
        value = planTitle,
        onValueChange = onPlanTitleChanged,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("plan-title"),
        label = { Text("Course, exam, or topic") },
        placeholder = { Text("HCI final") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = planTextFieldColors(),
    )
}

@Composable
private fun DeadlineStep(
    state: PlanSetupUiState,
    onDeadlineSelected: (StudyDeadline) -> Unit,
    onChooseDate: () -> Unit,
) {
    StepIntro(
        title = "When is it due?",
        subtitle = "This helps spread the work across your study days.",
    )

    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        setupDeadlines.forEach { deadline ->
            PlanFlowOptionRow(
                title = deadline.label,
                subtitle = if (deadline == StudyDeadline.ChooseDate) {
                    state.customDeadlineLabel
                } else {
                    null
                },
                icon = deadline.icon,
                isSelected = state.selectedDeadline == deadline,
                onClick = {
                    if (deadline == StudyDeadline.ChooseDate) {
                        onChooseDate()
                    } else {
                        onDeadlineSelected(deadline)
                    }
                },
            )
        }
    }
}

@Composable
private fun DailyTimeStep(
    state: PlanSetupUiState,
    onDailyTimeSelected: (DailyStudyTime) -> Unit,
    onCustomHoursChanged: (String) -> Unit,
    onCustomMinutesChanged: (String) -> Unit,
) {
    StepIntro(
        title = "How much time can you\nset aside each day?",
        subtitle = "We'll use this to spread the plan. You can adjust each day later.",
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        setupDailyStudyTimes.forEach { time ->
            PlanFlowOptionRow(
                title = time.label,
                icon = null,
                isSelected = state.selectedDailyTime == time,
                onClick = { onDailyTimeSelected(time) },
            )

            if (time == DailyStudyTime.Custom && state.selectedDailyTime == DailyStudyTime.Custom) {
                val isCustomTimeEmpty = state.customHoursText.isBlank() && state.customMinutesText.isBlank()
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = state.customHoursText,
                            onValueChange = onCustomHoursChanged,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("custom-hours"),
                            label = { Text("Hours") },
                            singleLine = true,
                            isError = state.customTimeError != null && !isCustomTimeEmpty,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(16.dp),
                            colors = planTextFieldColors(),
                        )
                        OutlinedTextField(
                            value = state.customMinutesText,
                            onValueChange = onCustomMinutesChanged,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("custom-minutes"),
                            label = { Text("Minutes") },
                            singleLine = true,
                            isError = state.customTimeError != null && !isCustomTimeEmpty,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(16.dp),
                            colors = planTextFieldColors(),
                        )
                    }

                    state.customTimeError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCustomTimeEmpty) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }
        }

        dailyTimeComment(state)?.let { comment ->
            Text(
                text = comment,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StudyDaysStep(
    state: PlanSetupUiState,
    onDayToggled: (StudyDay) -> Unit,
    onShortcutSelected: (StudyDayShortcut) -> Unit,
    onStudyDayResetOffsetSelected: (Int) -> Unit,
) {
    StepIntro(
        title = "When do you study?",
        subtitle = "Pick the days this plan can use.",
    )

    StudyRhythmCard(
        state = state,
        onDayToggled = onDayToggled,
        onShortcutSelected = onShortcutSelected,
    )

    Spacer(Modifier.height(18.dp))

    StudyDayResetOffsetSwitcher(
        selectedOffsetHours = state.studyDayResetOffsetHours,
        onSelected = onStudyDayResetOffsetSelected,
    )

    Spacer(Modifier.height(10.dp))

    Text(
        text = studyDayResetOffsetMessage(state.studyDayResetOffsetHours),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun StudyRhythmCard(
    state: PlanSetupUiState,
    onDayToggled: (StudyDay) -> Unit,
    onShortcutSelected: (StudyDayShortcut) -> Unit,
) {
    val selectedCount = state.selectedDays.size
    val deadlineError = state.studyDaysDeadlineError()

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 20.dp)) {
            StudyWeekPicker(
                selectedDays = state.selectedDays,
                onDayToggled = onDayToggled,
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = selectedDaysLabel(selectedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = selectedDaysLabelColor(selectedCount),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f),
            )

            Spacer(Modifier.height(18.dp))

            QuickSelectRow(
                selectedDays = state.selectedDays,
                onShortcutSelected = onShortcutSelected,
            )

            if (deadlineError != null) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = deadlineError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StudyWeekPicker(
    selectedDays: Set<StudyDay>,
    onDayToggled: (StudyDay) -> Unit,
) {
    val days = StudyDay.entries
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = size.width / days.size
            val centerY = size.height / 2f
            val radius = 22.dp.toPx()
            val firstCenter = step / 2f
            val lastCenter = size.width - step / 2f

            drawLine(
                color = muted,
                start = Offset(firstCenter + radius, centerY),
                end = Offset(lastCenter - radius, centerY),
                strokeWidth = 0.75.dp.toPx(),
                cap = StrokeCap.Round,
            )

            days.dropLast(1).forEachIndexed { index, day ->
                val nextDay = days[index + 1]
                if (day in selectedDays && nextDay in selectedDays) {
                    val startX = step * index + step / 2f + radius + 1.5.dp.toPx()
                    val endX = step * (index + 1) + step / 2f - radius - 1.5.dp.toPx()
                    drawLine(
                        color = primary.copy(alpha = 0.72f),
                        start = Offset(startX, centerY),
                        end = Offset(endX, centerY),
                        strokeWidth = 1.4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            days.forEach { day ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    StudyWeekDayButton(
                        day = day,
                        isSelected = day in selectedDays,
                        onClick = { onDayToggled(day) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyWeekDayButton(
    day: StudyDay,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val targetBackground = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val targetBorder = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    }
    val targetText = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val background by animateColorAsState(targetBackground, tween(RenMotionDurationMillis), label = "study-day-background")
    val border by animateColorAsState(targetBorder, tween(RenMotionDurationMillis), label = "study-day-border")
    val textColor by animateColorAsState(targetText, tween(RenMotionDurationMillis), label = "study-day-text")
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .size(42.dp)
            .toggleable(
                value = isSelected,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = { onClick() },
            )
            .semantics {
                contentDescription = day.label
                selected = isSelected
            },
        shape = CircleShape,
        color = background,
        border = BorderStroke(1.dp, border),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = day.shortLabel,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun QuickSelectRow(
    selectedDays: Set<StudyDay>,
    onShortcutSelected: (StudyDayShortcut) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StudyDayShortcut.entries.forEach { shortcut ->
            QuickSelectChip(
                label = shortcut.label,
                isSelected = selectedDays == daysForShortcut(shortcut),
                onClick = { onShortcutSelected(shortcut) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickSelectChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetBackground = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
    } else {
        Color.Transparent
    }
    val targetBorder = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
    }
    val targetText = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val background by animateColorAsState(targetBackground, tween(RenMotionDurationMillis), label = "quick-select-background")
    val border by animateColorAsState(targetBorder, tween(RenMotionDurationMillis), label = "quick-select-border")
    val textColor by animateColorAsState(targetText, tween(RenMotionDurationMillis), label = "quick-select-text")

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(36.dp)
            .semantics { selected = isSelected },
        shape = CircleShape,
        color = background,
        border = BorderStroke(1.dp, border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyDayResetOffsetSwitcher(
    selectedOffsetHours: Int,
    onSelected: (Int) -> Unit,
) {
    val options = listOf(0 to "Regular", 4 to "Night owl")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (offsetHours, label) ->
            SegmentedButton(
                selected = selectedOffsetHours == offsetHours,
                onClick = { onSelected(offsetHours) },
                modifier = Modifier.weight(1f),
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
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
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

private fun studyDayResetOffsetMessage(offsetHours: Int): String =
    if (offsetHours == 0) {
        "12 AM means a new day. Brutal."
    } else {
        "Before 4 AM is still tonight."
    }

private fun selectedDaysLabel(count: Int): String =
    when (count) {
        0 -> "No days picked yet. Bold strategy."
        1 -> "1 day selected"
        else -> "$count days selected"
    }

@Composable
private fun selectedDaysLabelColor(count: Int): Color =
    if (count == 0) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }

@Composable
private fun planTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f),
    errorBorderColor = MaterialTheme.colorScheme.error,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    errorContainerColor = MaterialTheme.colorScheme.surface,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
)

@Composable
private fun StepIntro(
    title: String,
    subtitle: String,
) {
    PlanFlowIntro(
        title = title,
        subtitle = subtitle,
    )
    Spacer(Modifier.height(PlanFlowSectionGap))
}

@Composable
private fun PlanSetupPrimaryButton(
    state: PlanSetupUiState,
    onNext: () -> Unit,
    onGeneratePlan: () -> Unit,
) {
    val isFinalStep = state.currentStep == PlanSetupStep.StudyDays
    PlanFlowPrimaryButton(
        label = if (isFinalStep) "Create plan" else "Next",
        onClick = if (isFinalStep) onGeneratePlan else onNext,
        enabled = state.canContinue,
        icon = if (isFinalStep) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
        testTag = if (isFinalStep) "generate-plan" else "plan-next",
    )
}

private val setupDeadlines = listOf(
    StudyDeadline.Tomorrow,
    StudyDeadline.InThreeDays,
    StudyDeadline.InOneWeek,
    StudyDeadline.ChooseDate,
)

private val setupDailyStudyTimes = listOf(
    DailyStudyTime.OneHour,
    DailyStudyTime.TwoHours,
    DailyStudyTime.ThreeHours,
    DailyStudyTime.FiveHours,
    DailyStudyTime.Custom,
)

private val StudyDeadline.label: String
    get() = when (this) {
    StudyDeadline.Tomorrow -> "Tomorrow"
    StudyDeadline.InThreeDays -> "In 3 days"
    StudyDeadline.InOneWeek -> "In 1 week"
    StudyDeadline.ChooseDate -> "Choose a date"
}

private val StudyDeadline.icon: ImageVector
    get() = when (this) {
        StudyDeadline.Tomorrow -> Icons.Default.Event
    StudyDeadline.InThreeDays -> Icons.Default.Schedule
    StudyDeadline.InOneWeek -> Icons.Default.PendingActions
    StudyDeadline.ChooseDate -> Icons.Default.CalendarMonth
}

private val DailyStudyTime.label: String
    get() = when (this) {
        DailyStudyTime.OneHour -> "1 hour"
        DailyStudyTime.TwoHours -> "2 hours"
        DailyStudyTime.ThreeHours -> "3 hours"
        DailyStudyTime.FiveHours -> "5 hours"
        DailyStudyTime.Custom -> "Custom"
    }

private fun dailyTimeComment(state: PlanSetupUiState): String? {
    val minutes = state.selectedDailyTime?.minutes ?: state.customMinutes
    return when {
        state.selectedDailyTime == null -> null
        minutes == null -> null
        minutes < 60 -> "Tiny session. Cute. The plan will have to be ruthless."
        minutes == 60 -> "One hour. Respectable, but we may need to be picky."
        minutes >= 300 -> "Five hours. Ah, the exam-season personality has arrived."
        else -> null
    }
}

private val StudyDay.shortLabel: String
    get() = when (this) {
        StudyDay.Monday -> "Mo"
        StudyDay.Tuesday -> "Tu"
        StudyDay.Wednesday -> "We"
        StudyDay.Thursday -> "Th"
        StudyDay.Friday -> "Fr"
        StudyDay.Saturday -> "Sa"
        StudyDay.Sunday -> "Su"
    }

private val StudyDay.label: String
    get() = when (this) {
        StudyDay.Monday -> "Monday"
        StudyDay.Tuesday -> "Tuesday"
        StudyDay.Wednesday -> "Wednesday"
        StudyDay.Thursday -> "Thursday"
        StudyDay.Friday -> "Friday"
        StudyDay.Saturday -> "Saturday"
        StudyDay.Sunday -> "Sunday"
    }

private val StudyDayShortcut.label: String
    get() = when (this) {
        StudyDayShortcut.EveryDay -> "Every day"
        StudyDayShortcut.Weekdays -> "Weekdays"
        StudyDayShortcut.Weekends -> "Weekends"
    }

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PlanSetupGoalPreview() {
    RenTheme(dynamicColor = false) {
        PlanSetupScreen(
            state = PlanSetupUiState(
                planTitle = "HCI final",
            ),
            onBack = {},
            onPlanTitleChanged = {},
            onDeadlineSelected = {},
            onDateSelected = {},
            onDailyTimeSelected = {},
            onCustomHoursChanged = {},
            onCustomMinutesChanged = {},
            onDayToggled = {},
            onShortcutSelected = {},
            onStudyDayResetOffsetSelected = {},
            onNext = {},
            onGeneratePlan = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PlanSetupDaysPreview() {
    RenTheme(dynamicColor = false) {
        PlanSetupScreen(
            state = PlanSetupUiState(
                currentStep = PlanSetupStep.StudyDays,
                selectedDays = daysForShortcut(StudyDayShortcut.Weekdays),
            ),
            onBack = {},
            onPlanTitleChanged = {},
            onDeadlineSelected = {},
            onDateSelected = {},
            onDailyTimeSelected = {},
            onCustomHoursChanged = {},
            onCustomMinutesChanged = {},
            onDayToggled = {},
            onShortcutSelected = {},
            onStudyDayResetOffsetSelected = {},
            onNext = {},
            onGeneratePlan = {},
        )
    }
}
