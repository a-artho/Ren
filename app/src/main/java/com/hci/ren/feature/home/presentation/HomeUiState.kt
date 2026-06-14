package com.hci.ren.feature.home.presentation

data class HomeUiState(
    val userName: String,
    val greeting: String,
    val content: HomeContent,
    val isAddMaterialSheetVisible: Boolean = false,
)

sealed interface HomeContent {
    data object Empty : HomeContent

    data class Active(
        val nextSession: NextSessionUiModel?,
        val progress: DailyProgressUiModel,
        val materials: List<MaterialUiModel>,
    ) : HomeContent
}

data class NextSessionUiModel(
    val title: String,
    val pageRange: String,
    val duration: String,
)

data class DailyProgressUiModel(
    val focusTime: String,
    val completedSessions: String,
    val reviewsAdded: String,
)

data class MaterialUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val supportingText: String,
    val progressPercent: Int,
)
