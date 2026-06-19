package com.hci.ren

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hci.ren.feature.home.presentation.HomeRoute
import com.hci.ren.feature.pdfupload.presentation.PlanSetupRoute
import com.hci.ren.feature.pdfupload.presentation.PlanSetupViewModel
import com.hci.ren.feature.pdfupload.presentation.PdfUploadRoute
import com.hci.ren.ui.theme.RenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RenTheme(dynamicColor = false) {
                var screen by rememberSaveable { mutableStateOf(ScreenHome) }
                var setupDocumentUri by rememberSaveable { mutableStateOf("") }
                var openPickerOnStart by rememberSaveable { mutableStateOf(false) }
                val planSetupViewModel: PlanSetupViewModel = viewModel()

                when (screen) {
                    ScreenHome -> HomeRoute(
                        onUploadPdf = {
                            openPickerOnStart = true
                            screen = ScreenPdfUpload
                        },
                    )

                    ScreenPdfUpload -> PdfUploadRoute(
                        openPickerOnStart = openPickerOnStart,
                        onBack = { screen = ScreenHome },
                        onContinue = { documentUri ->
                            setupDocumentUri = documentUri
                            openPickerOnStart = false
                            screen = ScreenPdfSetup
                        },
                    )

                    ScreenPdfSetup -> PlanSetupRoute(
                        documentUri = setupDocumentUri,
                        viewModel = planSetupViewModel,
                        onExit = {
                            openPickerOnStart = false
                            screen = ScreenPdfUpload
                        },
                        onGeneratePlan = {
                            // The real plan-generation destination has not been selected yet.
                        },
                    )
                }
            }
        }
    }
}

private const val ScreenHome = "home"
private const val ScreenPdfUpload = "pdf_upload"
private const val ScreenPdfSetup = "pdf_setup"
