package com.hci.ren.feature.pdfupload.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.hci.ren.ui.components.PlanFlowIntro
import com.hci.ren.ui.components.PlanFlowIntroTextGap
import com.hci.ren.ui.components.PlanFlowPrimaryButton
import com.hci.ren.ui.components.PlanFlowScaffold
import com.hci.ren.ui.components.PlanFlowSectionGap
import com.hci.ren.ui.theme.RenTheme
import com.hci.ren.ui.theme.RenSelectedCardSurface
import com.hci.ren.ui.theme.renCardBorderColor
import com.hci.ren.ui.theme.renCardContainerColor
import com.hci.ren.ui.motion.RenMotionDurationMillis
import com.hci.ren.ui.motion.RenMotionEasing
import com.hci.ren.ui.motion.isReducedMotionEnabled
import com.hci.ren.ui.motion.renContentSpec
import kotlin.math.roundToInt

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
    onMovePdf: (String, Int) -> Boolean,
    onPageSelected: (Int) -> Unit,
    onPageRequested: (PdfRenderKey, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var previewOpen by remember(state.sessionId) { mutableStateOf(false) }
    val hasReadyDocuments = state.documentGroup != null && state.loadStatus == PdfLoadStatus.Ready
    val primaryActionLabel = if (hasReadyDocuments) "Continue" else "Add files"
    val primaryActionEnabled = if (hasReadyDocuments) {
        state.canContinue
    } else {
        state.loadStatus != PdfLoadStatus.Loading
    }
    val primaryAction = if (hasReadyDocuments) onContinue else onPickPdf

    PlanFlowScaffold(
        onBack = onBack,
        modifier = modifier,
        progress = MaterialSelectionStepNumber / PlanCreationTotalSteps.toFloat(),
        stepLabel = "$MaterialSelectionStepNumber OF $PlanCreationTotalSteps",
        bottomContent = {
            PlanFlowPrimaryButton(
                label = primaryActionLabel,
                onClick = primaryAction,
                enabled = primaryActionEnabled,
                icon = if (hasReadyDocuments) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Add,
                testTag = if (hasReadyDocuments) "pdf-continue" else "pick-pdf",
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(PlanFlowSectionGap),
        ) {
            PdfPickerHeader(
                state = state,
            )

            if (state.documentGroup != null && state.loadStatus == PdfLoadStatus.Ready) {
                PdfFileListPane(
                    state = state,
                    onSelectPdf = onSelectPdf,
                    onRemovePdf = onRemovePdf,
                    onMovePdf = onMovePdf,
                    onAddMorePdf = onAddMorePdf,
                    onPreviewPdf = { index ->
                        onSelectPdf(index)
                        previewOpen = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyUploadPane(
                        state = state,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

        }
    }

    if (previewOpen && state.documentGroup != null && state.loadStatus == PdfLoadStatus.Ready) {
        PdfPreviewDialog(
            state = state,
            onDismiss = { previewOpen = false },
            onPageSelected = onPageSelected,
            onPageRequested = onPageRequested,
        )
    }
}

@Composable
private fun PdfPickerHeader(
    state: PdfUploadUiState,
) {
    Column {
        PlanFlowIntro(
            title = "Sort study materials",
            subtitle = "Add the notes or slides you want included.",
            titleFontWeight = FontWeight.SemiBold,
        )

        if (state.loadStatus is PdfLoadStatus.Error) {
            Spacer(Modifier.height(PlanFlowIntroTextGap))
            Text(
                text = state.loadStatus.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("pdf-error"),
            )
        }
        if (state.noticeMessage != null) {
            Spacer(Modifier.height(PlanFlowIntroTextGap))
            Text(
                text = state.noticeMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("pdf-notice"),
            )
        }
    }
}

@Composable
internal fun PdfFileCard(
    document: PdfDocumentUiModel,
    orderNumber: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Boolean,
    onMoveDown: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    var dragOffsetY by remember(document.uri) { mutableFloatStateOf(0f) }
    var isDragging by remember(document.uri) { mutableStateOf(false) }
    var cardHeightPx by remember(document.uri) { mutableFloatStateOf(0f) }
    val spacingPx = with(LocalDensity.current) { 8.dp.toPx() }
    val fallbackMoveStepPx = with(LocalDensity.current) { 72.dp.toPx() }
    val moveStepPx = if (cardHeightPx > 0f) cardHeightPx + spacingPx else fallbackMoveStepPx
    val canMoveUpState = rememberUpdatedState(canMoveUp)
    val canMoveDownState = rememberUpdatedState(canMoveDown)
    val onMoveUpState = rememberUpdatedState(onMoveUp)
    val onMoveDownState = rememberUpdatedState(onMoveDown)
    val moveStepPxState = rememberUpdatedState(moveStepPx)
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) RenSelectedCardSurface else renCardContainerColor(),
        animationSpec = tween(RenMotionDurationMillis, easing = RenMotionEasing),
        label = "file-card-bg",
    )
    val cardElevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 1.dp,
        animationSpec = tween(RenMotionDurationMillis, easing = RenMotionEasing),
        label = "file-card-elevation",
    )
    val cardScale by animateFloatAsState(
        targetValue = if (isDragging) 1.012f else 1f,
        animationSpec = tween(RenMotionDurationMillis, easing = RenMotionEasing),
        label = "file-card-scale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isDragging) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.58f)
        } else {
            renCardBorderColor()
        },
        animationSpec = tween(RenMotionDurationMillis, easing = RenMotionEasing),
        label = "file-card-border",
    )
    Card(
        onClick = onSelect,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .onGloballyPositioned { coordinates ->
                cardHeightPx = coordinates.size.height.toFloat()
            }
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .zIndex(if (isDragging || dragOffsetY != 0f) 1f else 0f)
            .pointerInput(document.uri) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        dragOffsetY = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val stepPx = moveStepPxState.value
                        dragOffsetY += dragAmount.y
                        when {
                            dragOffsetY <= -stepPx && canMoveUpState.value -> {
                                if (onMoveUpState.value()) {
                                    dragOffsetY += stepPx
                                } else {
                                    dragOffsetY = 0f
                                }
                            }
                            dragOffsetY >= stepPx && canMoveDownState.value -> {
                                if (onMoveDownState.value()) {
                                    dragOffsetY -= stepPx
                                } else {
                                    dragOffsetY = 0f
                                }
                            }
                            else -> {
                                val minOffset = if (canMoveUpState.value) -stepPx else 0f
                                val maxOffset = if (canMoveDownState.value) stepPx else 0f
                                dragOffsetY = dragOffsetY.coerceIn(minOffset, maxOffset)
                            }
                        }
                    },
                )
            }
            .testTag("pdf-file-card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = orderNumber.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp, end = 10.dp)) {
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
            ReorderGrip(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .alpha(if (isDragging) 0.95f else 0.54f),
            )
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
private fun ReorderGrip(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(2) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) {
                    Surface(
                        modifier = Modifier
                            .size(3.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun PdfFileListPane(
    state: PdfUploadUiState,
    onSelectPdf: (Int) -> Unit,
    onRemovePdf: (Int) -> Unit,
    onMovePdf: (String, Int) -> Boolean,
    onAddMorePdf: () -> Unit,
    onPreviewPdf: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val group = state.documentGroup ?: return

    Column(modifier = modifier) {
        if (group.documents.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("pdf-file-list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                itemsIndexed(
                    items = group.documents,
                    key = { _, doc -> doc.uri },
                ) { index, doc ->
                    PdfFileCard(
                        document = doc,
                        orderNumber = index + 1,
                        canMoveUp = index > 0,
                        canMoveDown = index < group.documents.lastIndex,
                        isSelected = false,
                        onSelect = {
                            onSelectPdf(index)
                            onPreviewPdf(index)
                        },
                        onRemove = { onRemovePdf(index) },
                        onMoveUp = { onMovePdf(doc.uri, -1) },
                        onMoveDown = { onMovePdf(doc.uri, 1) },
                    )
                }
                item {
                    if (state.remainingDocumentSlots > 0) {
                        TextButton(
                            onClick = onAddMorePdf,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Add more")
                        }
                    } else {
                        Text(
                            text = "Maximum of $MaxPdfDocumentCount PDFs selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPreviewDialog(
    state: PdfUploadUiState,
    onDismiss: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onPageRequested: (PdfRenderKey, Int) -> Unit,
) {
    val group = state.documentGroup ?: return
    val currentDocIndex = group.selectedPdfIndex
    val currentDocument = group.documents.getOrNull(currentDocIndex) ?: return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentDocument.fileName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = currentDocument.details,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close preview",
                        )
                    }
                }

                AnimatedContent(
                    targetState = state.selectedPageIndex,
                    transitionSpec = { fadeIn(renContentSpec()) togetherWith fadeOut(renContentSpec()) },
                    label = "selected-page",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("selected-pdf-page"),
                ) { pageIndex ->
                    val key = PdfRenderKey(currentDocIndex, pageIndex, PdfRenderKind.Preview)
                    PdfPageImage(key, state.renderedPages[key], 1400, onPageRequested, Modifier.fillMaxSize())
                }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .testTag("pdf-thumbnails"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 8.dp),
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
                            onClick = { onPageSelected(pageIndex) },
                            modifier = Modifier
                                .width(66.dp)
                                .fillMaxHeight()
                                .testTag("pdf-thumbnail-$pageIndex"),
                        )
                    }
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
    onClick: (() -> Unit)? = null,
) {
    LaunchedEffect(key, targetWidthPx) {
        onPageRequested(key, targetWidthPx)
    }

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else renCardBorderColor(),
        animationSpec = tween(RenMotionDurationMillis, easing = RenMotionEasing),
        label = "page-border",
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .18f) else renCardContainerColor(),
        animationSpec = tween(RenMotionDurationMillis, easing = RenMotionEasing),
        label = "page-background",
    )
    val pageShape = RoundedCornerShape(12.dp)
    val pageBorder = BorderStroke(
        width = if (isSelected) 2.dp else 1.dp,
        color = borderColor,
    )
    val content: @Composable () -> Unit = {
        Box(
            modifier = Modifier.fillMaxSize(),
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

    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = pageShape,
            color = backgroundColor,
            border = pageBorder,
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = pageShape,
            color = backgroundColor,
            border = pageBorder,
            content = content,
        )
    }
}

@Composable
private fun EmptyUploadPane(
    state: PdfUploadUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.loadStatus == PdfLoadStatus.Loading) {
            CircularProgressIndicator(modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Reading your files",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "This should only take a moment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        } else {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "No material yet. Tragic, but fixable.",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Up to $MaxPdfDocumentCount files.",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
            onMovePdf = { _, _ -> false },
            onPageSelected = {},
            onPageRequested = { _, _ -> },
        )
    }
}
