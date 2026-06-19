package com.hci.ren.feature.pdfupload.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PlanSetupRoute(
    documentUri: String,
    onExit: () -> Unit,
    onGeneratePlan: (PlanSetupSubmission) -> Unit,
    viewModel: PlanSetupViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(documentUri) {
        viewModel.setDocument(documentUri)
    }

    PlanSetupScreen(
        state = state,
        onBack = {
            if (!viewModel.goBack()) {
                onExit()
            }
        },
        onGoalSelected = viewModel::selectGoal,
        onDeadlineSelected = viewModel::selectDeadline,
        onDateSelected = viewModel::selectCustomDate,
        onDailyTimeSelected = viewModel::selectDailyTime,
        onCustomMinutesChanged = viewModel::updateCustomMinutes,
        onDayToggled = viewModel::toggleStudyDay,
        onShortcutSelected = viewModel::selectShortcut,
        onAdvancedControls = viewModel::showAdvancedMessage,
        onNext = viewModel::goNext,
        onGeneratePlan = {
            viewModel.generatePlan()?.let(onGeneratePlan)
        },
    )
}

