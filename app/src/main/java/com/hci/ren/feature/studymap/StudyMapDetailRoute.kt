package com.hci.ren.feature.studymap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.hci.ren.R

@Composable
fun StudyMapDetailRoute(
    viewModel: StudyMapDetailViewModel,
    onBack: () -> Unit,
    onCreateProject: () -> Unit,
    onOpenToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadActiveProject() }
    val project = state.project
    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        project == null && state.errorMessage != null -> Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.errorMessage ?: stringResource(R.string.study_map_unavailable), style = MaterialTheme.typography.titleLarge)
            }
        }
        project == null -> StudyMapScreen(
            plan = null,
            preferences = null,
            onBack = onBack,
            onCreateProject = onCreateProject,
            onOpenToday = onOpenToday,
            onRenamePlan = {},
            onDeletePlan = {},
            onConsumeMessage = viewModel::consumeMessage,
            onApplyDeadline = {},
            onIncreaseDailyTime = {},
            onReduceScope = { _, _ -> },
            onContinueAnyway = {},
            modifier = modifier,
        )
        else -> StudyMapScreen(
            plan = project.plan,
            preferences = project.preferences,
            dailyMinutesOverride = project.dailyMinutesOverride,
            dailyAvailableMinutesByDate = project.dailyAvailableMinutesByDate,
            taskProgressById = project.taskProgressById,
            acceptedTightPlan = project.acceptedTightPlan,
            changeMessage = state.userMessage,
            suggestedDeadline = state.suggestedDeadline,
            recommendedDaysBalanced = state.recommendedDaysBalanced,
            recommendedDaysIntensive = state.recommendedDaysIntensive,
            onBack = onBack,
            onCreateProject = onCreateProject,
            onOpenToday = onOpenToday,
            onRenamePlan = viewModel::renamePlan,
            onDeletePlan = viewModel::deletePlan,
            onConsumeMessage = viewModel::consumeMessage,
            onExtendDeadline = viewModel::extendDeadline,
            onApplyDeadline = viewModel::applyDeadline,
            onIncreaseDailyTime = viewModel::increaseDailyTime,
            onReduceScope = viewModel::reduceScope,
            onContinueAnyway = viewModel::continueAnyway,
            modifier = modifier,
        )
    }
}
