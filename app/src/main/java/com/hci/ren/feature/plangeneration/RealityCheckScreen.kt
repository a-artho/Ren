package com.hci.ren.feature.plangeneration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.hci.ren.R

@Composable
fun RealityCheckScreen(
    result: FeasibilityResult,
    topics: List<StudyTopic>,
    onPrioritise: () -> Unit,
    onExtendDeadline: (studyDays: Int, intensive: Boolean) -> Unit,
    onCustomDeadline: (epochMillis: Long) -> Unit,
    onReduceGoal: (StudyScopeGoal) -> Unit,
    onFocusTopics: (Set<String>) -> Unit,
    onContinueAnyway: () -> Unit,
    onBack: () -> Unit,
) {
    var showGoalChoices by remember { mutableStateOf(false) }
    var showTopicChoices by remember { mutableStateOf(false) }
    var showDeadlineChoices by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedTopicIds by remember { mutableStateOf(emptySet<String>()) }
    BackHandler(onBack = onBack)
    if (showGoalChoices) {
        AlertDialog(
            onDismissRequest = { showGoalChoices = false },
            title = { Text(stringResource(R.string.reduce_goal)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        StudyScopeGoal.PassExam to R.string.goal_pass_exam,
                        StudyScopeGoal.ReviseOnly to R.string.goal_revise_only,
                        StudyScopeGoal.Fundamentals to R.string.goal_fundamentals,
                        StudyScopeGoal.SkimEverything to R.string.goal_skim_everything,
                        StudyScopeGoal.CompleteEverything to R.string.goal_complete_everything,
                    ).forEach { (goal, label) ->
                        TextButton(
                            onClick = { showGoalChoices = false; onReduceGoal(goal) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(label), modifier = Modifier.fillMaxWidth()) }
                    }
                    TextButton(
                        onClick = { showGoalChoices = false; showTopicChoices = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.goal_selected_chapters), modifier = Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showGoalChoices = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showDeadlineChoices) {
        AlertDialog(
            onDismissRequest = { showDeadlineChoices = false },
            title = { Text(stringResource(R.string.choose_better_deadline)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeadlineChoice(
                        title = stringResource(R.string.balanced),
                        description = pluralStringResource(R.plurals.balanced_option_description, result.recommendedDaysBalanced, result.recommendedDaysBalanced),
                        onClick = { showDeadlineChoices = false; onExtendDeadline(result.recommendedDaysBalanced, false) },
                    )
                    DeadlineChoice(
                        title = stringResource(R.string.intensive),
                        description = pluralStringResource(R.plurals.intensive_option_description, result.recommendedDaysIntensive, result.recommendedDaysIntensive),
                        onClick = { showDeadlineChoices = false; onExtendDeadline(result.recommendedDaysIntensive, true) },
                    )
                    DeadlineChoice(
                        title = stringResource(R.string.custom_deadline),
                        description = stringResource(R.string.pick_your_own_date),
                        onClick = { showDeadlineChoices = false; showDatePicker = true },
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showDeadlineChoices = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
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
    if (showTopicChoices) {
        AlertDialog(
            onDismissRequest = { showTopicChoices = false },
            title = { Text(stringResource(R.string.goal_selected_chapters)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    topics.forEach { topic ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(
                                checked = topic.id in selectedTopicIds,
                                onCheckedChange = { checked ->
                                    selectedTopicIds = if (checked) selectedTopicIds + topic.id else selectedTopicIds - topic.id
                                },
                            )
                            Text(topic.title, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedTopicIds.isNotEmpty(),
                    onClick = { showTopicChoices = false; onFocusTopics(selectedTopicIds) },
                ) { Text(stringResource(R.string.apply)) }
            },
            dismissButton = { TextButton(onClick = { showTopicChoices = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
        ) {
            Text(stringResource(R.string.reality_check), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.reality_check_subtitle), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                if (result.availableMinutes == 0) stringResource(R.string.no_available_study_time) else {
                    stringResource(R.string.reality_check_body, formatMinutes(result.totalRequiredMinutes), formatMinutes(result.availableMinutes))
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RealityStat(stringResource(R.string.required_time), formatMinutes(result.totalRequiredMinutes))
                    RealityStat(stringResource(R.string.available_time), formatMinutes(result.availableMinutes))
                    RealityStat(stringResource(R.string.current_coverage), stringResource(R.string.about_percent, result.estimatedCoveragePercent))
                    RealityStat(stringResource(R.string.shortage), formatMinutes(result.shortageMinutes))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.reality_check_explanation), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onPrioritise, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.prioritise_matters)) }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.prioritise_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { showDeadlineChoices = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.extend_deadline)) }
            Text(
                pluralStringResource(R.plurals.balanced_study_days, result.recommendedDaysBalanced, result.recommendedDaysBalanced) + " " +
                    pluralStringResource(R.plurals.intensive_study_days, result.recommendedDaysIntensive, result.recommendedDaysIntensive),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { showGoalChoices = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reduce_goal)) }
            Text(stringResource(R.string.reduce_goal_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onContinueAnyway, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.continue_anyway)) }
            Text(stringResource(R.string.continue_anyway_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeadlineChoice(title: String, description: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RealityStat(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

private fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} hr"
    else -> "${minutes / 60} hr ${minutes % 60} min"
}
