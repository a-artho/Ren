package com.hci.ren.feature.pdfupload.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hci.ren.ui.theme.RenTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfUploadScreen(
    state: PdfUploadUiState,
    onBack: () -> Unit,
    onPickPdf: () -> Unit,
    onContinue: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onPageRequested: (PdfRenderKey, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Upload PDF") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                modifier = Modifier.statusBarsPadding(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PdfPickerHeader(
                state = state,
                onPickPdf = onPickPdf,
            )

            if (state.document != null && state.loadStatus == PdfLoadStatus.Ready) {
                PdfPreviewPane(
                    state = state,
                    onPageSelected = onPageSelected,
                    onPageRequested = onPageRequested,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                EmptyPreviewPane(
                    state = state,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }

            Button(
                onClick = onContinue,
                enabled = state.canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pdf-continue"),
            ) {
                Text("Continue")
            }
            Text(
                text = "Next we'll customize how you want to study.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun PdfPickerHeader(
    state: PdfUploadUiState,
    onPickPdf: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Choose study material",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Select a PDF to preview before setup.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val document = state.document
        if (document == null) {
            OutlinedButton(
                onClick = onPickPdf,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pick-pdf"),
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text("Select PDF")
            }
        } else {
            PdfFileCard(document = document)
        }

        if (state.loadStatus is PdfLoadStatus.Error) {
            Text(
                text = state.loadStatus.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("pdf-error"),
            )
        }
    }
}

@Composable
private fun PdfFileCard(
    document: PdfDocumentUiModel,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pdf-file-card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = document.details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "PDF loaded successfully",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PdfPreviewPane(
    state: PdfUploadUiState,
    onPageSelected: (Int) -> Unit,
    onPageRequested: (PdfRenderKey, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PdfPageImage(
            key = PdfRenderKey(state.selectedPageIndex, PdfRenderKind.Preview),
            state = state.renderedPages[PdfRenderKey(state.selectedPageIndex, PdfRenderKind.Preview)],
            targetWidthPx = 1400,
            onPageRequested = onPageRequested,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("selected-pdf-page"),
        )

        LazyColumn(
            modifier = Modifier
                .width(92.dp)
                .fillMaxHeight()
                .testTag("pdf-thumbnails"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(
                items = state.thumbnailPageIndexes,
                key = { it },
            ) { pageIndex ->
                val key = PdfRenderKey(pageIndex, PdfRenderKind.Thumbnail)
                PdfPageImage(
                    key = key,
                    state = state.renderedPages[key],
                    targetWidthPx = 220,
                    isSelected = pageIndex == state.selectedPageIndex,
                    onPageRequested = onPageRequested,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPageSelected(pageIndex) }
                        .testTag("pdf-thumbnail-$pageIndex"),
                )
            }
        }
    }
}

@Composable
private fun PdfPageImage(
    key: PdfRenderKey,
    state: PdfPageRenderState?,
    targetWidthPx: Int,
    onPageRequested: (PdfRenderKey, Int) -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
) {
    LaunchedEffect(key, targetWidthPx) {
        onPageRequested(key, targetWidthPx)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(12.dp),
            )
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is PdfPageRenderState.Ready -> Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = "Page ${key.pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )

            is PdfPageRenderState.Error -> Text(
                text = "Preview unavailable",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp),
            )

            PdfPageRenderState.Loading,
            null,
            -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun EmptyPreviewPane(
    state: PdfUploadUiState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (state.loadStatus == PdfLoadStatus.Loading) {
            CircularProgressIndicator()
        } else {
            Text(
                text = "PDF preview will appear here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PdfUploadReadyPreview() {
    RenTheme(dynamicColor = false) {
        PdfUploadScreen(
            state = PdfUploadUiState(
                document = PdfDocumentUiModel(
                    uri = "content://ren/document",
                    fileName = "Lecture notes.pdf",
                    sizeBytes = 1_572_864,
                    pageCount = 5,
                ),
                loadStatus = PdfLoadStatus.Ready,
            ),
            onBack = {},
            onPickPdf = {},
            onContinue = {},
            onPageSelected = {},
            onPageRequested = { _, _ -> },
        )
    }
}
