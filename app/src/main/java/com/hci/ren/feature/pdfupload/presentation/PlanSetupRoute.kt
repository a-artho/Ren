package com.hci.ren.feature.pdfupload.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PlanSetupRoute(
    documentUris: List<String>,
    onExit: () -> Unit,
    onGeneratePlan: (PlanSetupSubmission) -> Unit,
    viewModel: PlanSetupViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(documentUris) {
        viewModel.setDocuments(documentUris)
    }

    val navigateBack = {
        if (!viewModel.goBack()) {
            onExit()
        }
    }
    BackHandler(onBack = navigateBack)

    PlanSetupScreen(
        state = state,
        onBack = navigateBack,
        onPlanTitleChanged = viewModel::updatePlanTitle,
        onDeadlineSelected = viewModel::selectDeadline,
        onDateSelected = viewModel::selectCustomDate,
        onDailyTimeSelected = viewModel::selectDailyTime,
        onCustomHoursChanged = viewModel::updateCustomHours,
        onCustomMinutesChanged = viewModel::updateCustomMinutes,
        onDayToggled = viewModel::toggleStudyDay,
        onShortcutSelected = viewModel::selectShortcut,
        onStudyDayResetOffsetSelected = viewModel::updateStudyDayResetOffset,
        onNext = viewModel::goNext,
        onGeneratePlan = {
            viewModel.generatePlan()?.let(onGeneratePlan)
        },
    )
}

