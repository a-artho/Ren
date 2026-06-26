package com.hci.ren.feature.pdfupload.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PdfUploadRoute(
    onBack: () -> Unit,
    onContinue: (List<String>) -> Unit,
    openPickerOnStart: Boolean,
    viewModel: PdfUploadViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    val initialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                uris.forEach { uri ->
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
                viewModel.selectDocuments(uris)
            }
        },
    )

    val addMoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                uris.forEach { uri ->
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
                viewModel.appendDocuments(uris)
            }
        },
    )

    var pickerHandledSessionId by rememberSaveable { mutableStateOf<Long?>(null) }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        viewModel.restoreDocumentIfNeeded()
    }

    LaunchedEffect(openPickerOnStart, state.sessionId) {
        if (openPickerOnStart && pickerHandledSessionId != state.sessionId) {
            pickerHandledSessionId = state.sessionId
            initialLauncher.launch(arrayOf("application/pdf"))
        }
    }

    PdfUploadScreen(
        state = state,
        onBack = onBack,
        onPickPdf = { initialLauncher.launch(arrayOf("application/pdf")) },
        onAddMorePdf = { addMoreLauncher.launch(arrayOf("application/pdf")) },
        onContinue = { viewModel.documentReferences().let(onContinue) },
        onSelectPdf = viewModel::selectPdf,
        onRemovePdf = viewModel::removeDocument,
        onPageSelected = viewModel::selectPage,
        onPageRequested = viewModel::requestPage,
    )
}
