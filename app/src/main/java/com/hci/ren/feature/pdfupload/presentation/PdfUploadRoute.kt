package com.hci.ren.feature.pdfupload.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PdfUploadRoute(
    onBack: () -> Unit,
    onContinue: (String) -> Unit,
    openPickerOnStart: Boolean,
    viewModel: PdfUploadViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.selectDocument(uri)
            }
        },
    )
    var hasOpenedPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openPickerOnStart) {
        if (openPickerOnStart && !hasOpenedPicker) {
            hasOpenedPicker = true
            launcher.launch(arrayOf("application/pdf"))
        }
    }

    PdfUploadScreen(
        state = state,
        onBack = onBack,
        onPickPdf = { launcher.launch(arrayOf("application/pdf")) },
        onContinue = {
            viewModel.documentReference()?.let(onContinue)
        },
        onPageSelected = viewModel::selectPage,
        onPageRequested = viewModel::requestPage,
    )
}
