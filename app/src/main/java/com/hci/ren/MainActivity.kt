package com.hci.ren

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.hci.ren.feature.home.presentation.HomeRoute
import com.hci.ren.feature.pdfupload.presentation.PdfSetupScreen
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

                    ScreenPdfSetup -> PdfSetupScreen(
                        documentUri = setupDocumentUri,
                        onBack = {
                            openPickerOnStart = false
                            screen = ScreenPdfUpload
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
