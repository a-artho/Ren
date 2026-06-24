package com.hci.ren.feature.home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMaterialSheet(
    onUploadPdf: () -> Unit,
    onUseSampleMaterial: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Add study material",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Upload a PDF or explore the app with sample material.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onUploadPdf,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sheet-upload-pdf"),
            ) {
                Text(
                    text = "Upload PDF",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(
                onClick = onUseSampleMaterial,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Use sample material",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Cancel",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
