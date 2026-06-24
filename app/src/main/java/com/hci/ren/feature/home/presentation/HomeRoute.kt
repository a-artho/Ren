package com.hci.ren.feature.home.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.res.stringResource
import com.hci.ren.R
import kotlinx.coroutines.launch

@Composable
fun HomeRoute(
    onUploadPdf: () -> Unit,
) {
    var state by remember { mutableStateOf(HomePreviewData.active) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unavailableMessage = stringResource(R.string.home_feature_unavailable)

    HomeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            state = when (action) {
                HomeAction.AddMaterialClicked -> state.copy(
                    isAddMaterialSheetVisible = true,
                )

                HomeAction.AddMaterialDismissed -> state.copy(isAddMaterialSheetVisible = false)

                HomeAction.UseSampleMaterialClicked -> homeStateAfterSampleSelection()

                HomeAction.UploadPdfClicked -> {
                    onUploadPdf()
                    state.copy(isAddMaterialSheetVisible = false)
                }

                HomeAction.StartFocusClicked,
                is HomeAction.MaterialClicked,
                HomeAction.ProfileClicked,
                HomeAction.NotificationsClicked,
                -> {
                    scope.launch { snackbarHostState.showSnackbar(unavailableMessage) }
                    state
                }

                is HomeAction.NavigationItemClicked -> state
            }
        },
    )
}

internal fun homeStateAfterSampleSelection(): HomeUiState =
    HomePreviewData.active.copy(isAddMaterialSheetVisible = false)
