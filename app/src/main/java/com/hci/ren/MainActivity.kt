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
import com.hci.ren.feature.studymap.StudyMapDetailRoute
import com.hci.ren.feature.studymap.StudyMapDetailViewModel
import com.hci.ren.feature.studymap.StudyMapLibraryScreen
import com.hci.ren.feature.studymap.StudyMapLibraryViewModel
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
                var selectedStudyProjectId by rememberSaveable { mutableStateOf("") }
                val pdfUploadViewModel: PdfUploadViewModel = viewModel()
                val planSetupViewModel: PlanSetupViewModel = viewModel()
                val planGenerationViewModel: PlanGenerationViewModel = viewModel()
                val studyMapLibraryViewModel: StudyMapLibraryViewModel = viewModel()
                val studyMapDetailViewModel: StudyMapDetailViewModel = viewModel()
                val generationState by planGenerationViewModel.uiState.collectAsState()
                val studyMapLibraryState by studyMapLibraryViewModel.uiState.collectAsState()
                var forward by rememberSaveable { mutableStateOf(true) }

                LaunchedEffect(generationState.planId, generationState.plan, generationState.feasibility, generationState.originalGoalDoesNotFit) {
                    if (generationState.plan != null) {
                        forward = true
                        selectedStudyProjectId = generationState.plan!!.id
                        screen = ScreenStudyMapDetail
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
                                screen = ScreenStudyMapLibrary
                            }
                        },
                        onUploadPdf = {
                            if (!transition.isRunning) {
                                forward = true
                                planGenerationViewModel.reset()
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

                    ScreenStudyMapLibrary -> StudyMapLibraryScreen(
                        state = studyMapLibraryState,
                        onQueryChange = studyMapLibraryViewModel::updateQuery,
                        onFilterChange = studyMapLibraryViewModel::updateFilter,
                        onSortChange = studyMapLibraryViewModel::updateSort,
                        onClearSearchAndFilter = studyMapLibraryViewModel::clearSearchAndFilter,
                        onOpenProject = { projectId ->
                            selectedStudyProjectId = projectId
                            forward = true
                            screen = ScreenStudyMapDetail
                        },
                        onDeleteProject = studyMapLibraryViewModel::deleteProject,
                        onRetry = studyMapLibraryViewModel::retry,
                        onConsumeMessage = studyMapLibraryViewModel::consumeMessage,
                        onHome = {
                            if (!transition.isRunning) {
                                forward = false
                                screen = ScreenHome
                            }
                        },
                        onCreateProject = {
                            if (!transition.isRunning) {
                                forward = true
                                planGenerationViewModel.reset()
                                pdfUploadViewModel.beginNewSession()
                                setupStartedForUploadSession = false
                                openPickerOnStart = true
                                screen = ScreenPdfUpload
                            }
                        },
                        onInsights = {},
                    )

                    ScreenStudyMapDetail -> StudyMapDetailRoute(
                        projectId = selectedStudyProjectId,
                        viewModel = studyMapDetailViewModel,
                        onBack = {
                            if (!transition.isRunning) {
                                forward = false
                                screen = ScreenStudyMapLibrary
                            }
                        },
                        onHome = {
                            if (!transition.isRunning) {
                                forward = false
                                screen = ScreenHome
                            }
                        },
                        onInsights = {},
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
private const val ScreenStudyMapLibrary = "study_map_library"
private const val ScreenStudyMapDetail = "study_map_detail"
