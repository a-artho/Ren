package com.hci.ren.feature.home.presentation

sealed interface HomeAction {
    data object AddMaterialClicked : HomeAction
    data object AddMaterialDismissed : HomeAction
    data object UploadPdfClicked : HomeAction
    data object UseSampleMaterialClicked : HomeAction
    data object StartFocusClicked : HomeAction
    data class MaterialClicked(val materialId: String) : HomeAction
    data object ProfileClicked : HomeAction
    data object NotificationsClicked : HomeAction
    data class NavigationItemClicked(val destination: HomeDestination) : HomeAction
}

enum class HomeDestination {
    Home,
    StudyMap,
    Focus,
    Insights,
}
