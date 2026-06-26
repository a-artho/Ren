package com.hci.ren.feature.pdfupload.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.hci.ren.ui.motion.RenMotionDurationMillis
import com.hci.ren.ui.motion.RenMotionEasing
import com.hci.ren.ui.motion.isReducedMotionEnabled
import com.hci.ren.ui.motion.renContentSpec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfUploadScreen(
    state: PdfUploadUiState,
    onBack: () -> Unit,
    onPickPdf: () -> Unit,
    onAddMorePdf: () -> Unit,
    onContinue: () -> Unit,
    onSelectPdf: (Int) -> Unit,
    onRemovePdf: (Int) -> Unit,
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

            if (state.documentGroup != null && state.loadStatus == PdfLoadStatus.Ready) {
                PdfPreviewPane(
                    state = state,
                    onSelectPdf = onSelectPdf,
                    onRemovePdf = onRemovePdf,
                    onAddMorePdf = onAddMorePdf,
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
                Text(
                    text = "Continue",
                    fontWeight = FontWeight.SemiBold,
                )
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

        val group = state.documentGroup
        if (group == null || group.documents.isEmpty()) {
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
                Text(
                    text = "Select PDF",
                    fontWeight = FontWeight.SemiBold,
                )
            }
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
internal fun PdfFileCard(
    document: PdfDocumentUiModel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface,
        animationSpec = tween(RenMotionDurationMillis, easing = RenMotionEasing),
        label = "file-card-bg",
    )
    Card(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth().testTag("pdf-file-card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
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
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove ${document.fileName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PdfPreviewPane(
    state: PdfUploadUiState,
    onSelectPdf: (Int) -> Unit,
    onRemovePdf: (Int) -> Unit,
    onAddMorePdf: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onPageRequested: (PdfRenderKey, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val group = state.documentGroup ?: return
    val currentDocIndex = group.selectedPdfIndex
    val listState = rememberLazyListState()

    Column(modifier = modifier) {
        if (group.documents.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pdf-file-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(group.documents) { index, doc ->
                        PdfFileCard(
                            document = doc,
                            isSelected = index == currentDocIndex,
                            onSelect = { onSelectPdf(index) },
                            onRemove = { onRemovePdf(index) },
                        )
                    }
                }

                val indicatorState = listState.scrollIndicatorState
                if (indicatorState != null && indicatorState.contentSize > indicatorState.viewportSize) {
                    val thumbFraction = indicatorState.viewportSize.toFloat() / indicatorState.contentSize.toFloat()
                    val scrollRange = (indicatorState.contentSize - indicatorState.viewportSize).coerceAtLeast(1)
                    Canvas(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(4.dp)
                            .align(Alignment.CenterEnd)
                    ) {
                        val thumbHeight = size.height * thumbFraction
                        val thumbOffset = (size.height - thumbHeight) * indicatorState.scrollOffset.toFloat() / scrollRange
                        drawRoundRect(
                            color = Color.Gray.copy(alpha = 0.35f),
                            topLeft = Offset(0f, thumbOffset),
                            size = Size(size.width, thumbHeight),
                            cornerRadius = CornerRadius(size.width / 2f, size.width / 2f),
                        )
                    }
                }
            }
            TextButton(
                onClick = onAddMorePdf,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("+ Add more")
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedContent(
                targetState = state.selectedPageIndex,
                transitionSpec = { fadeIn(renContentSpec()) togetherWith fadeOut(renContentSpec()) },
                label = "selected-page",
                modifier = Modifier.weight(1f).fillMaxHeight().testTag("selected-pdf-page"),
            ) { pageIndex ->
                val key = PdfRenderKey(currentDocIndex, pageIndex, PdfRenderKind.Preview)
                PdfPageImage(key, state.renderedPages[key], 1400, onPageRequested, Modifier.fillMaxSize())
            }

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
                    val key = PdfRenderKey(currentDocIndex, pageIndex, PdfRenderKind.Thumbnail)
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

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(RenMotionDurationMillis, easing = RenMotionEasing),
        label = "page-border",
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .18f) else MaterialTheme.colorScheme.surface,
        animationSpec = tween(RenMotionDurationMillis, easing = RenMotionEasing),
        label = "page-background",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is PdfPageRenderState.Ready -> Crossfade(
                targetState = state.bitmap,
                animationSpec = renContentSpec(),
                label = "rendered-page",
            ) { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page ${key.pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

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
                fontWeight = FontWeight.SemiBold,
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
                documentGroup = DocumentGroup(
                    documents = listOf(
                        PdfDocumentUiModel(uri = "content://ren/doc1", fileName = "Lecture notes.pdf", sizeBytes = 1_572_864, pageCount = 5),
                    ),
                    selectedPdfIndex = 0,
                ),
                loadStatus = PdfLoadStatus.Ready,
            ),
            onBack = {},
            onPickPdf = {},
            onAddMorePdf = {},
            onContinue = {},
            onSelectPdf = {},
            onRemovePdf = {},
            onPageSelected = {},
            onPageRequested = { _, _ -> },
        )
    }
}
