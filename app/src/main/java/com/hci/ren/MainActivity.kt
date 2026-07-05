package com.hci.ren

import android.os.Bundle
import androidx.compose.animation.AnimatedContent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hci.ren.feature.pdfupload.presentation.PlanSetupRoute
import com.hci.ren.feature.pdfupload.presentation.PlanSetupViewModel
import com.hci.ren.feature.pdfupload.presentation.PdfUploadRoute
import com.hci.ren.feature.pdfupload.presentation.PdfUploadViewModel
import com.hci.ren.feature.plangeneration.PlanGenerationScreen
import com.hci.ren.feature.plangeneration.PlanGenerationViewModel
import com.hci.ren.feature.studymap.StudyMapDetailRoute
import com.hci.ren.feature.studymap.StudyMapDetailViewModel
import com.hci.ren.feature.studymap.TodayScreen
import com.hci.ren.ui.theme.RenTheme
import com.hci.ren.ui.motion.isReducedMotionEnabled
import com.hci.ren.ui.motion.renScreenTransform
import com.hci.ren.ui.motion.renTabSwitchTransform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferHighRefreshRate()
        enableEdgeToEdge()
        setContent {
            RenTheme(dynamicColor = false) {
                var screen by rememberSaveable { mutableStateOf(ScreenStudyMapDetail) }
                var setupDocumentUris by rememberSaveable { mutableStateOf("") }
                var openPickerOnStart by rememberSaveable { mutableStateOf(false) }
                var setupStartedForUploadSession by rememberSaveable { mutableStateOf(false) }
                var studyPlanNavigationResetKey by rememberSaveable { mutableIntStateOf(0) }
                val pdfUploadViewModel: PdfUploadViewModel = viewModel()
                val planSetupViewModel: PlanSetupViewModel = viewModel()
                val planGenerationViewModel: PlanGenerationViewModel = viewModel()
                val studyMapDetailViewModel: StudyMapDetailViewModel = viewModel()
                val generationState by planGenerationViewModel.uiState.collectAsState()
                val studyMapState by studyMapDetailViewModel.uiState.collectAsState()
                var forward by rememberSaveable { mutableStateOf(true) }
                val hasActiveStudyPlan = studyMapState.project != null
                val showBottomBar = hasActiveStudyPlan && screen in tabScreens

                LaunchedEffect(generationState.planId, generationState.plan) {
                    if (generationState.plan != null) {
                        forward = true
                        studyMapDetailViewModel.loadActiveProject(force = true)
                        screen = ScreenStudyMapDetail
                    } else if (generationState.planId != null && screen in tabScreens) {
                        forward = true
                        screen = ScreenPlanProcessing
                    }
                }

                LaunchedEffect(hasActiveStudyPlan, screen) {
                    if ((!hasActiveStudyPlan && screen in lockedPlanTabs) || (!TodayNavigationEnabled && screen == ScreenToday)) {
                        forward = false
                        screen = ScreenStudyMapDetail
                    }
                }

                val reducedMotion = isReducedMotionEnabled()
                val transition = updateTransition(screen, label = "app-screen")
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0.dp),
                    bottomBar = {
                        if (showBottomBar) {
                            val selectedTab = tabIndexForScreen(screen)
                            AppNavigationBar(
                                selectedTab = selectedTab,
                                hasActiveStudyPlan = hasActiveStudyPlan,
                                onTabSelected = { tab ->
                                    if (!hasActiveStudyPlan && tab != 0) return@AppNavigationBar
                                    if (!TodayNavigationEnabled && tab == TodayTabIndex) return@AppNavigationBar
                                    val targetScreen = when (tab) {
                                        0 -> ScreenStudyMapDetail
                                        1 -> ScreenToday
                                        2 -> ScreenProgress
                                        else -> ScreenStudyMapDetail
                                    }
                                    if (targetScreen != screen && screen in tabScreens && targetScreen in tabScreens) {
                                        studyPlanNavigationResetKey++
                                    }
                                    forward = tab >= selectedTab
                                    screen = targetScreen
                                },
                            )
                        }
                    },
                ) { scaffoldPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        transition.AnimatedContent(
                            transitionSpec = {
                                if (initialState in tabScreens && targetState in tabScreens) {
                                    renTabSwitchTransform(reducedMotion)
                                } else {
                                    renScreenTransform(forward, reducedMotion)
                                }
                            },
                            contentKey = { it },
                        ) { currentScreen ->
                            Box(Modifier.fillMaxSize()) {
                                when (currentScreen) {
                        ScreenPdfUpload -> PdfUploadRoute(
                            openPickerOnStart = openPickerOnStart,
                            viewModel = pdfUploadViewModel,
                            onDocumentsChanged = planGenerationViewModel::prepareDocuments,
                            onBack = {
                                if (!transition.isRunning) {
                                    forward = false
                                    openPickerOnStart = false
                                    screen = ScreenStudyMapDetail
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

                        ScreenStudyMapDetail -> StudyMapDetailRoute(
                            viewModel = studyMapDetailViewModel,
                            navigationResetKey = studyPlanNavigationResetKey,
                            modifier = if (hasActiveStudyPlan) {
                                Modifier.padding(scaffoldPadding)
                            } else {
                                Modifier
                            },
                            onBack = {
                                if (!transition.isRunning) {
                                    forward = false
                                }
                            },
                            onCreateProject = {
                                if (!transition.isRunning) {
                                    forward = true
                                    planGenerationViewModel.reset()
                                    pdfUploadViewModel.beginNewSession()
                                    setupStartedForUploadSession = false
                                    openPickerOnStart = false
                                    screen = ScreenPdfUpload
                                }
                            },
                            onOpenToday = {
                                if (!transition.isRunning && TodayNavigationEnabled) {
                                    forward = true
                                    screen = ScreenToday
                                }
                            },
                        )

                        ScreenToday -> studyMapState.project?.let { project ->
                            TodayScreen(
                                project = project,
                                session = studyMapState.todaySession,
                                wrapUpResultMessage = studyMapState.todayWrapUpMessage,
                                onAvailableTimeChanged = studyMapDetailViewModel::updateTodayAvailableTime,
                                onTaskAction = studyMapDetailViewModel::updateTodayTaskAction,
                                onWrapUpToday = studyMapDetailViewModel::wrapUpToday,
                                onConsumeWrapUpResult = studyMapDetailViewModel::consumeTodayWrapUpMessage,
                                changeMessage = studyMapState.userMessage,
                                onConsumeMessage = studyMapDetailViewModel::consumeMessage,
                                modifier = Modifier.padding(scaffoldPadding),
                            )
                        } ?: PlaceholderTabScreen(
                            title = stringResource(R.string.today),
                            message = stringResource(R.string.today_placeholder_message),
                            modifier = Modifier.padding(scaffoldPadding),
                        )

                        ScreenProgress -> PlaceholderTabScreen(
                            title = stringResource(R.string.progress),
                            message = stringResource(R.string.progress_placeholder_message),
                            modifier = Modifier.padding(scaffoldPadding),
                        )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        preferHighRefreshRate()
    }

    @Suppress("DEPRECATION")
    private fun preferHighRefreshRate() {
        val preferredMode = windowManager.defaultDisplay.supportedModes
            .maxWithOrNull(compareBy({ it.refreshRate }, { it.physicalWidth * it.physicalHeight }))
            ?: return

        val attributes = window.attributes
        attributes.preferredRefreshRate = preferredMode.refreshRate
        attributes.preferredDisplayModeId = preferredMode.modeId
        window.attributes = attributes
    }
}

private data class Tab(val labelRes: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(R.string.study_plan_tab, Icons.Default.Route),
    Tab(R.string.today, Icons.Default.Timer),
    Tab(R.string.progress, Icons.Default.BarChart),
)

private val tabScreens = setOf(ScreenStudyMapDetail, ScreenToday, ScreenProgress)
private val lockedPlanTabs = setOf(ScreenToday, ScreenProgress)
private const val TodayTabIndex = 1
private const val TodayNavigationEnabled = true

private fun tabIndexForScreen(screen: String): Int = when (screen) {
    ScreenStudyMapDetail -> 0
    ScreenToday -> TodayTabIndex
    ScreenProgress -> 2
    else -> 0
}

@Composable
private fun PlaceholderTabScreen(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppNavigationBar(
    selectedTab: Int,
    hasActiveStudyPlan: Boolean,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        tabs.forEachIndexed { index, tab ->
            val enabled = when (index) {
                0 -> true
                TodayTabIndex -> hasActiveStudyPlan && TodayNavigationEnabled
                else -> hasActiveStudyPlan
            }
            val selected = index == selectedTab
            NavigationBarItem(
                selected = selected,
                enabled = enabled,
                onClick = { onTabSelected(index) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
                    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
                ),
            )
        }
    }
}

private const val ScreenPdfUpload = "pdf_upload"
private const val ScreenPdfSetup = "pdf_setup"
private const val ScreenPlanProcessing = "plan_processing"
private const val ScreenStudyMapDetail = "study_map_detail"
private const val ScreenToday = "today"
private const val ScreenProgress = "progress"
