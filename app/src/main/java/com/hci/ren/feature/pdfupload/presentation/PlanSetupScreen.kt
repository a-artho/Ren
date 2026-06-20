package com.hci.ren.feature.pdfupload.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import com.hci.ren.ui.theme.RenTheme
import com.hci.ren.ui.motion.RenMotionDurationMillis
import com.hci.ren.ui.motion.RenMotionEasing
import com.hci.ren.ui.motion.isReducedMotionEnabled
import com.hci.ren.ui.motion.renScreenTransform
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSetupScreen(
    state: PlanSetupUiState,
    onBack: () -> Unit,
    onGoalSelected: (StudyGoal) -> Unit,
    onDeadlineSelected: (StudyDeadline) -> Unit,
    onDateSelected: (Long) -> Unit,
    onDailyTimeSelected: (DailyStudyTime) -> Unit,
    onCustomMinutesChanged: (String) -> Unit,
    onDayToggled: (StudyDay) -> Unit,
    onShortcutSelected: (StudyDayShortcut) -> Unit,
    onAdvancedControls: () -> Unit,
    onNext: () -> Unit,
    onGeneratePlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDatePickerOpen by rememberSaveable { mutableStateOf(false) }
    var isNavigationLocked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val datePickerState = rememberDatePickerState()
    fun navigateOnce(action: () -> Unit) {
        if (isNavigationLocked) return
        isNavigationLocked = true
        action()
        scope.launch {
            delay(RenMotionDurationMillis.toLong())
            isNavigationLocked = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(PlanSetupEdgePadding))

            PlanSetupTopRow(
                state = state,
                onBack = { navigateOnce(onBack) },
            )

            Spacer(Modifier.height(10.dp))

            val reducedMotion = isReducedMotionEnabled()
            AnimatedContent(
                targetState = state.currentStep,
                transitionSpec = {
                    renScreenTransform(
                        forward = targetState.ordinal > initialState.ordinal,
                        reducedMotion = reducedMotion,
                    )
                },
                contentKey = { it },
                label = "plan-step",
                modifier = Modifier
                    .weight(1f),
            ) { step ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (step) {
                    PlanSetupStep.Goal -> GoalStep(
                        selectedGoal = state.selectedGoal,
                        onGoalSelected = onGoalSelected,
                    )

                    PlanSetupStep.Deadline -> DeadlineStep(
                        state = state,
                        onDeadlineSelected = onDeadlineSelected,
                        onChooseDate = { isDatePickerOpen = true },
                    )

                    PlanSetupStep.DailyTime -> DailyTimeStep(
                        state = state,
                        onDailyTimeSelected = onDailyTimeSelected,
                        onCustomMinutesChanged = onCustomMinutesChanged,
                    )

                    PlanSetupStep.StudyDays -> StudyDaysStep(
                        state = state,
                        onDayToggled = onDayToggled,
                        onShortcutSelected = onShortcutSelected,
                        onAdvancedControls = onAdvancedControls,
                    )
                }
                }
            }

            Spacer(Modifier.height(14.dp))

            PlanSetupPrimaryButton(
                state = state,
                onNext = { navigateOnce(onNext) },
                onGeneratePlan = { navigateOnce(onGeneratePlan) },
            )

            Spacer(Modifier.height(PlanSetupEdgePadding))
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
private fun PlanSetupTopRow(
    state: PlanSetupUiState,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PlanSetupControlHeight), // Keeps the 58.dp row height
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            // Offsets the 12.dp internal padding to align the visual icon with the text below
            modifier = Modifier.offset(x = (-12).dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
                // Rely on the standard 24.dp icon size for a cleaner look
            )
        }

        Spacer(Modifier.width(8.dp))

        val animatedProgress by animateFloatAsState(
            targetValue = state.progress,
            animationSpec = tween(RenMotionDurationMillis, easing = RenMotionEasing),
            label = "setup-progress",
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .weight(1f)
                .height(5.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        )

        Spacer(Modifier.width(16.dp))

        Text(
            text = "${state.currentStep.number} OF 4",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun GoalStep(
    selectedGoal: StudyGoal?,
    onGoalSelected: (StudyGoal) -> Unit,
) {
    StepIntro(
        title = "What do you want to\nachieve with this material?",
        subtitle = "Choose the option that fits best.",
    )

    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        StudyGoal.entries.forEach { goal ->
            SelectionRow(
                title = goal.label,
                icon = goal.icon,
                isSelected = selectedGoal == goal,
                onClick = { onGoalSelected(goal) },
            )
        }
    }
}

@Composable
private fun DeadlineStep(
    state: PlanSetupUiState,
    onDeadlineSelected: (StudyDeadline) -> Unit,
    onChooseDate: () -> Unit,
) {
    StepIntro(
        title = "When do you need to finish?",
        subtitle = "Choose a deadline for this material.",
    )

    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        StudyDeadline.entries.forEach { deadline ->
            SelectionRow(
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
    onCustomMinutesChanged: (String) -> Unit,
) {
    StepIntro(
        title = "How much time can you\nstudy each day?",
        subtitle = "We'll automatically divide this into focus sessions and breaks.",
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DailyStudyTime.entries.forEach { time ->
            SelectionRow(
                title = time.label,
                icon = null,
                isSelected = state.selectedDailyTime == time,
                onClick = { onDailyTimeSelected(time) },
            )

            if (time == DailyStudyTime.Custom && state.selectedDailyTime == DailyStudyTime.Custom) {
                OutlinedTextField(
                    value = state.customMinutesText,
                    onValueChange = onCustomMinutesChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom-minutes"),
                    label = { Text("Minutes per day") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
    }
}

@Composable
private fun StudyDaysStep(
    state: PlanSetupUiState,
    onDayToggled: (StudyDay) -> Unit,
    onShortcutSelected: (StudyDayShortcut) -> Unit,
    onAdvancedControls: () -> Unit,
) {
    StepIntro(
        title = "Which days can you study?",
        subtitle = "Select all that apply.",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        StudyDay.entries.forEach { day ->
            DayToggle(
                day = day,
                isSelected = day in state.selectedDays,
                onClick = { onDayToggled(day) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StudyDayShortcut.entries.forEach { shortcut ->
            ShortcutButton(
                shortcut = shortcut,
                isSelected = state.selectedDays == daysForShortcut(shortcut),
                onClick = { onShortcutSelected(shortcut) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Spacer(Modifier.height(40.dp))

    AdvancedControlsLink(
        onClick = onAdvancedControls,
        isMessageVisible = state.isAdvancedMessageVisible,
    )

    Spacer(Modifier.height(18.dp))

    Text(
        text = "You can change this later.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StepIntro(
    title: String,
    subtitle: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        lineHeight = MaterialTheme.typography.headlineSmall.lineHeight,
    )
    Spacer(Modifier.height(14.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun SelectionRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val targetBorderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val targetBackground = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor by animateColorAsState(targetBorderColor, tween(RenMotionDurationMillis), label = "option-border")
    val background by animateColorAsState(targetBackground, tween(RenMotionDurationMillis), label = "option-background")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(10.dp))
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon == null) {
            RadioButton(
                selected = isSelected,
                onClick = null,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }

        Spacer(Modifier.width(18.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected && subtitle != null) {
                    FontWeight.Bold
                } else {
                    FontWeight.SemiBold
                },
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.9f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.9f),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (!isSelected && icon != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
    }
}

@Composable
private fun DayToggle(
    day: StudyDay,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .toggleable(value = isSelected, role = Role.Checkbox, onValueChange = { onClick() })
            .semantics { contentDescription = day.label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.initial,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ShortcutButton(
    shortcut: StudyDayShortcut,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .semantics { selected = isSelected },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            } else {
                Color.Transparent
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Text(
            text = shortcut.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AdvancedControlsLink(
    onClick: () -> Unit,
    isMessageVisible: Boolean,
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Advanced controls",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        if (isMessageVisible) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Coming soon",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlanSetupPrimaryButton(
    state: PlanSetupUiState,
    onNext: () -> Unit,
    onGeneratePlan: () -> Unit,
) {
    val isFinalStep = state.currentStep == PlanSetupStep.StudyDays
    Button(
        onClick = if (isFinalStep) onGeneratePlan else onNext,
        enabled = state.canContinue,
        modifier = Modifier
            .fillMaxWidth()
            .height(PlanSetupControlHeight)
            .testTag(if (isFinalStep) "generate-plan" else "plan-next"),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                com.hci.ren.ui.theme.RenGreenDark
            } else {
                MaterialTheme.colorScheme.primary
            },
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(
            text = if (isFinalStep) "Generate Plan" else "Next",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private val PlanSetupControlHeight = 58.dp
private val PlanSetupEdgePadding = 18.dp

private val StudyGoal.label: String
    get() = when (this) {
        StudyGoal.LearnThoroughly -> "Learn it thoroughly"
        StudyGoal.PrepareForExam -> "Prepare for an exam"
        StudyGoal.ReviseKnown -> "Revise what I already know"
        StudyGoal.FinishQuickly -> "Finish it quickly"
        StudyGoal.OngoingStudy -> "Keep up with ongoing study"
    }

private val StudyGoal.icon: ImageVector
    get() = when (this) {
        StudyGoal.LearnThoroughly -> Icons.AutoMirrored.Filled.MenuBook
        StudyGoal.PrepareForExam -> Icons.Default.AssignmentTurnedIn
        StudyGoal.ReviseKnown -> Icons.Default.Refresh
        StudyGoal.FinishQuickly -> Icons.Default.FlashOn
        StudyGoal.OngoingStudy -> Icons.Default.AutoGraph
    }

private val StudyDeadline.label: String
    get() = when (this) {
        StudyDeadline.Today -> "Today"
        StudyDeadline.InThreeDays -> "In 3 days"
        StudyDeadline.InOneWeek -> "In 1 week"
        StudyDeadline.ChooseDate -> "Choose a date"
        StudyDeadline.NoFixedDeadline -> "No fixed deadline"
    }

private val StudyDeadline.icon: ImageVector
    get() = when (this) {
        StudyDeadline.Today -> Icons.Default.Event
        StudyDeadline.InThreeDays -> Icons.Default.Schedule
        StudyDeadline.InOneWeek -> Icons.Default.PendingActions
        StudyDeadline.ChooseDate -> Icons.Default.CalendarMonth
        StudyDeadline.NoFixedDeadline -> Icons.Default.Timelapse
    }

private val DailyStudyTime.label: String
    get() = when (this) {
        DailyStudyTime.FifteenMinutes -> "15 min"
        DailyStudyTime.ThirtyMinutes -> "30 min"
        DailyStudyTime.FortyFiveMinutes -> "45 min"
        DailyStudyTime.OneHour -> "1 hour"
        DailyStudyTime.TwoHours -> "2 hours"
        DailyStudyTime.Custom -> "Custom"
    }

private val StudyDay.initial: String
    get() = when (this) {
        StudyDay.Monday -> "M"
        StudyDay.Tuesday -> "T"
        StudyDay.Wednesday -> "W"
        StudyDay.Thursday -> "T"
        StudyDay.Friday -> "F"
        StudyDay.Saturday -> "S"
        StudyDay.Sunday -> "S"
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
                selectedGoal = StudyGoal.PrepareForExam,
            ),
            onBack = {},
            onGoalSelected = {},
            onDeadlineSelected = {},
            onDateSelected = {},
            onDailyTimeSelected = {},
            onCustomMinutesChanged = {},
            onDayToggled = {},
            onShortcutSelected = {},
            onAdvancedControls = {},
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
            onGoalSelected = {},
            onDeadlineSelected = {},
            onDateSelected = {},
            onDailyTimeSelected = {},
            onCustomMinutesChanged = {},
            onDayToggled = {},
            onShortcutSelected = {},
            onAdvancedControls = {},
            onNext = {},
            onGeneratePlan = {},
        )
    }
}
