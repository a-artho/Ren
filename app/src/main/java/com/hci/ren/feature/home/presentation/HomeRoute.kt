package com.hci.ren.feature.home.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun HomeRoute() {
    var state by remember { mutableStateOf(HomePreviewData.active) }

    HomeScreen(
        state = state,
        onAction = { action ->
            state = when (action) {
                HomeAction.AddMaterialClicked -> state.copy(
                    isAddMaterialSheetVisible = true,
                )

                HomeAction.AddMaterialDismissed,
                HomeAction.UploadPdfClicked,
                HomeAction.UseSampleMaterialClicked,
                -> state.copy(isAddMaterialSheetVisible = false)

                else -> state
            }
        },
    )
}
