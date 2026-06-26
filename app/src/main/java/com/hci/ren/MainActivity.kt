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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Map
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hci.ren.feature.home.presentation.HomeRoute
import com.hci.ren.feature.pdfupload.presentation.PlanSetupRoute
import com.hci.ren.feature.pdfupload.presentation.PlanSetupViewModel
import com.hci.ren.feature.pdfupload.presentation.PdfUploadRoute
import com.hci.ren.feature.pdfupload.presentation.PdfUploadViewModel
import com.hci.ren.feature.plangeneration.PlanGenerationScreen
import com.hci.ren.feature.plangeneration.PlanGenerationViewModel
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
                var setupDocumentUris by rememberSaveable { mutableStateOf("") }
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
                        val isTab = currentScreen in tabScreens
                        val tabPadding = if (isTab) Modifier.navigationBarsPadding().padding(bottom = TabBarHeight) else Modifier
                        Box(Modifier.fillMaxSize().then(tabPadding)) {
                            when (currentScreen) {
                        ScreenHome -> HomeRoute(
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
                            onContinue = { documentUris ->
                                if (!transition.isRunning) {
                                    forward = true
                                    setupDocumentUris = documentUris.joinToString("|")
                                    if (!setupStartedForUploadSession) {
                                        planSetupViewModel.beginNewSession(documentUris)
                                        setupStartedForUploadSession = true
                                    }
                                    openPickerOnStart = false
                                    screen = ScreenPdfSetup
                                }
                            },
                        )

                        ScreenPdfSetup -> PlanSetupRoute(
                            documentUris = setupDocumentUris.split("|").filter { it.isNotEmpty() },
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
                            onHome = {},
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
                            onHome = {},
                            onInsights = {},
                        )
                            }
                        }
                    }

                    if (screen in tabScreens) {
                        val selectedTab = tabIndexForScreen(screen)
                        AppNavigationBar(
                            selectedTab = selectedTab,
                            onTabSelected = { tab ->
                                forward = tab >= selectedTab
                                screen = when (tab) {
                                    0 -> ScreenHome
                                    1 -> ScreenStudyMapLibrary
                                    else -> ScreenHome
                                }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }
}

private data class Tab(val labelRes: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(R.string.home, Icons.Default.Home),
    Tab(R.string.study_map, Icons.Default.Map),
    Tab(R.string.insights, Icons.Default.Insights),
)

private val tabScreens = setOf(ScreenHome, ScreenStudyMapLibrary, ScreenStudyMapDetail)

private val TabBarHeight = 80.dp

private fun tabIndexForScreen(screen: String): Int = when (screen) {
    ScreenHome -> 0
    ScreenStudyMapLibrary, ScreenStudyMapDetail -> 1
    else -> 0
}

@Composable
private fun AppNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        tabs.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = index == selectedTab,
                onClick = { onTabSelected(index) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}

private const val ScreenHome = "home"
private const val ScreenPdfUpload = "pdf_upload"
private const val ScreenPdfSetup = "pdf_setup"
private const val ScreenPlanProcessing = "plan_processing"
private const val ScreenStudyMapLibrary = "study_map_library"
private const val ScreenStudyMapDetail = "study_map_detail"
