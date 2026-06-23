package com.hci.ren

import android.os.Bundle
import androidx.compose.animation.AnimatedContent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hci.ren.feature.home.presentation.HomeRoute
import com.hci.ren.feature.pdfupload.presentation.PlanSetupRoute
import com.hci.ren.feature.pdfupload.presentation.PlanSetupViewModel
import com.hci.ren.feature.pdfupload.presentation.PdfUploadRoute
import com.hci.ren.feature.pdfupload.presentation.PdfUploadViewModel
import com.hci.ren.feature.plangeneration.PlanGenerationScreen
import com.hci.ren.feature.plangeneration.PlanGenerationViewModel
import com.hci.ren.feature.studymap.StudyMapScreen
import com.hci.ren.ui.theme.RenTheme
import com.hci.ren.ui.motion.isReducedMotionEnabled
import com.hci.ren.ui.motion.renScreenTransform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RenTheme(dynamicColor = false) {
                var screen by rememberSaveable { mutableStateOf(ScreenHome) }
                var setupDocumentUri by rememberSaveable { mutableStateOf("") }
                var openPickerOnStart by rememberSaveable { mutableStateOf(false) }
                var setupStartedForUploadSession by rememberSaveable { mutableStateOf(false) }
                val pdfUploadViewModel: PdfUploadViewModel = viewModel()
                val planSetupViewModel: PlanSetupViewModel = viewModel()
                val planGenerationViewModel: PlanGenerationViewModel = viewModel()
                val generationState by planGenerationViewModel.uiState.collectAsState()
                var forward by rememberSaveable { mutableStateOf(true) }

                LaunchedEffect(generationState.planId, generationState.plan, generationState.feasibility, generationState.originalGoalDoesNotFit) {
                    if (generationState.plan != null) {
                        forward = true
                        screen = ScreenStudyMap
                    } else if (generationState.planId != null && screen == ScreenHome) {
                        forward = true
                        screen = ScreenPlanProcessing
                    }
                }

                val reducedMotion = isReducedMotionEnabled()
                val transition = updateTransition(screen, label = "app-screen")
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    transition.AnimatedContent(
                        transitionSpec = { renScreenTransform(forward, reducedMotion) },
                        contentKey = { it },
                    ) { currentScreen ->
                        when (currentScreen) {
                    ScreenHome -> HomeRoute(
                        onStudyMap = {
                            if (!transition.isRunning) {
                                forward = true
                                screen = ScreenStudyMap
                            }
                        },
                        onUploadPdf = {
                            if (!transition.isRunning) {
                                forward = true
                                pdfUploadViewModel.beginNewSession()
                                setupStartedForUploadSession = false
                                openPickerOnStart = true
                                screen = ScreenPdfUpload
                            }
                        },
                    )

                    ScreenPdfUpload -> PdfUploadRoute(
                        openPickerOnStart = openPickerOnStart,
                        viewModel = pdfUploadViewModel,
                        onBack = {
                            if (!transition.isRunning) {
                                forward = false
                                openPickerOnStart = false
                                screen = ScreenHome
                            }
                        },
                        onContinue = { documentUri ->
                            if (!transition.isRunning) {
                                forward = true
                                setupDocumentUri = documentUri
                                if (!setupStartedForUploadSession) {
                                    planSetupViewModel.beginNewSession(documentUri)
                                    setupStartedForUploadSession = true
                                }
                                openPickerOnStart = false
                                screen = ScreenPdfSetup
                            }
                        },
                    )

                    ScreenPdfSetup -> PlanSetupRoute(
                        documentUri = setupDocumentUri,
                        viewModel = planSetupViewModel,
                        onExit = {
                            if (!transition.isRunning) {
                                forward = false
                                openPickerOnStart = false
                                screen = ScreenPdfUpload
                            }
                        },
                        onGeneratePlan = { submission ->
                            if (!transition.isRunning) {
                                forward = true
                                planGenerationViewModel.start(submission)
                                screen = ScreenPlanProcessing
                            }
                        },
                    )

                    ScreenPlanProcessing -> PlanGenerationScreen(
                        state = generationState,
                        onBack = {
                            if (!transition.isRunning) {
                                forward = false
                                planGenerationViewModel.reset()
                                screen = ScreenPdfSetup
                            }
                        },
                        onRetry = planGenerationViewModel::retry,
                    )

                    ScreenStudyMap -> StudyMapScreen(
                        plan = generationState.plan,
                        preferences = planGenerationViewModel.currentSubmission(),
                        dailyMinutesOverride = generationState.dailyStudyMinutesOverride,
                        acceptedTightPlan = generationState.acceptedTightPlan,
                        changeMessage = generationState.changeMessage,
                        suggestedDeadline = planGenerationViewModel.suggestedDeadline(),
                        onHome = {
                            if (!transition.isRunning) {
                                forward = false
                                screen = ScreenHome
                            }
                        },
                        onCreateProject = {
                            if (!transition.isRunning) {
                                forward = true
                                pdfUploadViewModel.beginNewSession()
                                setupStartedForUploadSession = false
                                openPickerOnStart = true
                                screen = ScreenPdfUpload
                            }
                        },
                        onInsights = {},
                        onConsumeMessage = planGenerationViewModel::consumeChangeMessage,
                        onApplyDeadline = planGenerationViewModel::applyDeadline,
                        onIncreaseDailyTime = planGenerationViewModel::increaseDailyTime,
                        onReduceScope = planGenerationViewModel::reduceScope,
                        onContinueAnyway = planGenerationViewModel::continueAnyway,
                        onTaskStatusChange = planGenerationViewModel::updateTaskStatus,
                        onTaskDurationChange = planGenerationViewModel::updateTaskDuration,
                        onExcludeTask = planGenerationViewModel::excludeTask,
                        onRestoreTask = planGenerationViewModel::restoreTask,
                    )
                        }
                    }
                }
            }
        }
    }
}

private const val ScreenHome = "home"
private const val ScreenPdfUpload = "pdf_upload"
private const val ScreenPdfSetup = "pdf_setup"
private const val ScreenPlanProcessing = "plan_processing"
private const val ScreenStudyMap = "study_map"
