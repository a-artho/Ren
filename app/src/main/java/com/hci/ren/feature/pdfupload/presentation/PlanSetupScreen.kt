package com.hci.ren.feature.pdfupload.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hci.ren.ui.components.PlanFlowCircleChoice
import com.hci.ren.ui.components.PlanFlowIntro
import com.hci.ren.ui.components.PlanFlowOptionRow
import com.hci.ren.ui.components.PlanFlowPillChoice
import com.hci.ren.ui.components.PlanFlowPrimaryButton
import com.hci.ren.ui.components.PlanFlowScaffold
import com.hci.ren.ui.components.PlanFlowSectionGap
import com.hci.ren.ui.theme.RenTheme
import com.hci.ren.ui.motion.RenFadeThroughDurationMillis
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
    onNext: () -> Unit,
    onGeneratePlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDatePickerOpen by rememberSaveable { mutableStateOf(false) }
    var isNavigationLocked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selectableDates = remember {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                isSelectableDeadlineUtc(utcTimeMillis, System.currentTimeMillis())
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
) {
    StepIntro(
        title = "Which days can you study?",
        subtitle = "Choose the days you usually have time. You can adjust this later.",
    )

    StudyRhythmCard(
        state = state,
        onDayToggled = onDayToggled,
        onShortcutSelected = onShortcutSelected,
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
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StudyDay.entries.forEach { day ->
                    PlanFlowCircleChoice(
                        label = day.shortLabel,
                        contentDescription = day.label,
                        isSelected = day in state.selectedDays,
                        onClick = { onDayToggled(day) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = selectedDaysLabel(selectedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = selectedDaysLabelColor(selectedCount),
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(24.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f),
            )

            Spacer(Modifier.height(22.dp))

            Text(
                text = "Quick select",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StudyDayShortcut.entries.forEach { shortcut ->
                    PlanFlowPillChoice(
                        label = shortcut.label,
                        isSelected = state.selectedDays == daysForShortcut(shortcut),
                        onClick = { onShortcutSelected(shortcut) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = deadlineError ?: "Plans will be spread only across selected days.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (deadlineError == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
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
            onNext = {},
            onGeneratePlan = {},
        )
    }
}
