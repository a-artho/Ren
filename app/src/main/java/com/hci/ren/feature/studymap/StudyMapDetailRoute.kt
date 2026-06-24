package com.hci.ren.feature.studymap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
    projectId: String,
    viewModel: StudyMapDetailViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onInsights: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(projectId) { viewModel.loadProject(projectId) }
    val project = state.project
    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        project == null -> Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.errorMessage ?: stringResource(R.string.study_map_unavailable), style = MaterialTheme.typography.titleLarge)
                Button(onClick = onBack) { Text(stringResource(R.string.back_to_study_maps)) }
            }
        }
        else -> StudyMapScreen(
            plan = project.plan,
            preferences = project.preferences,
            dailyMinutesOverride = project.dailyMinutesOverride,
            acceptedTightPlan = project.acceptedTightPlan,
            changeMessage = state.userMessage,
            suggestedDeadline = state.suggestedDeadline,
            recommendedDaysBalanced = state.recommendedDaysBalanced,
            recommendedDaysIntensive = state.recommendedDaysIntensive,
            onBack = onBack,
            onHome = onHome,
            onCreateProject = {},
            onInsights = onInsights,
            onConsumeMessage = viewModel::consumeMessage,
            onExtendDeadline = viewModel::extendDeadline,
            onApplyDeadline = viewModel::applyDeadline,
            onIncreaseDailyTime = viewModel::increaseDailyTime,
            onReduceScope = viewModel::reduceScope,
            onContinueAnyway = viewModel::continueAnyway,
            onTaskStatusChange = viewModel::updateTaskStatus,
            onTaskDurationChange = viewModel::updateTaskDuration,
            onExcludeTask = viewModel::excludeTask,
            onRestoreTask = viewModel::restoreTask,
            modifier = modifier,
        )
    }
}
