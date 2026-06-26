package com.hci.ren.feature.plangeneration

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hci.ren.R
import com.hci.ren.ui.components.PlanFlowScaffold
import com.hci.ren.ui.motion.RenEmphasizedDecelerateEasing
import com.hci.ren.ui.motion.RenEmphasizedEasing
import com.hci.ren.ui.motion.isReducedMotionEnabled
import kotlin.math.abs

@Composable
fun PlanGenerationScreen(state: PlanGenerationUiState, onBack: () -> Unit, onRetry: () -> Unit) {
    var showCancelDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (state.status == PlanStatus.Failed) {
            onBack()
        } else {
            showCancelDialog = true
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(R.string.cancel_generation_title)) },
            text = { Text(stringResource(R.string.cancel_generation_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        onBack()
                    }
                ) {
                    Text(
                        stringResource(R.string.cancel_generation_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.cancel_generation_dismiss))
                }
            }
        )
    }

    PlanFlowScaffold(
        onBack = {
            if (state.status == PlanStatus.Failed) {
                onBack()
            } else {
                showCancelDialog = true
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.status == PlanStatus.Failed) {
                FailedContent(errorMessage = state.errorMessage, onRetry = onRetry)
            } else {
                ProcessingContent(state = state)
            }
        }
    }
}

// region â€” Failed state

@Composable
private fun FailedContent(errorMessage: String?, onRetry: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.size(80.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Error,
                contentDescription = stringResource(R.string.error_icon_description),
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Text(
        text = errorMessage ?: stringResource(R.string.plan_generation_failed),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) { Text(stringResource(R.string.retry)) }
}

// endregion
// region â€” Processing state

@Composable
private fun ProcessingContent(state: PlanGenerationUiState) {
    val reducedMotion = isReducedMotionEnabled()
    val currentIndex = if (state.status == PlanStatus.Completed) {
        processingSteps.size
    } else {
        processingSteps.indexOfFirst { it.status == state.status }.coerceAtLeast(0)
    }
    val activeStepIndex = currentIndex.coerceAtMost(processingSteps.lastIndex)
    val targetProgress = if (state.status == PlanStatus.Completed) 1f else (currentIndex + 1f) / processingSteps.size
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else 900,
            easing = RenEmphasizedDecelerateEasing,
        ),
        label = "plan generation progress",
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxHeight < 680.dp
        val cardHeight = if (compact) 354.dp else 382.dp
        val rowHeight = if (compact) 64.dp else 70.dp
        val titleGap = if (compact) 8.dp else 10.dp
        val progressGap = if (compact) 18.dp else 20.dp
        val timelineGap = if (compact) 28.dp else 32.dp

        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            Spacer(Modifier.height(if (compact) 2.dp else 8.dp))

            Text(
                text = stringResource(R.string.ai_processing),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(titleGap))
            Text(
                text = stringResource(R.string.processing_description),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp,
            )
            Spacer(Modifier.height(progressGap))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .semantics {
                        stateDescription = if (state.status == PlanStatus.Completed) {
                            "Processing completed"
                        } else {
                            "Processing step ${activeStepIndex + 1} of ${processingSteps.size}"
                        }
                    },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
            )

            Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Step ${activeStepIndex + 1} of ${processingSteps.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(timelineGap))

            ProcessingTimelineCard(
                state = state,
                currentIndex = currentIndex,
                isCompleted = state.status == PlanStatus.Completed,
                cardHeight = cardHeight,
                rowHeight = rowHeight,
                compact = compact,
                reducedMotion = reducedMotion,
            )

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.wait_tip_1),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
            Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
        }
    }
}

// endregion
@Composable
private fun ProcessingTimelineCard(
    state: PlanGenerationUiState,
    currentIndex: Int,
    isCompleted: Boolean,
    cardHeight: androidx.compose.ui.unit.Dp,
    rowHeight: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    reducedMotion: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight),
        shape = RoundedCornerShape(if (compact) 24.dp else 28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    top = if (compact) 16.dp else 20.dp,
                    end = 16.dp,
                    bottom = if (compact) 16.dp else 20.dp,
                ),
        ) {
            processingSteps.forEachIndexed { index, step ->
                val visual = when {
                    isCompleted || index < currentIndex -> StepVisual.Complete
                    index == currentIndex -> StepVisual.Current
                    else -> StepVisual.Upcoming
                }
                ProcessingTimelineRow(
                    label = stringResource(step.labelRes),
                    subtitle = stepSubtitle(step, index, currentIndex, state),
                    visual = visual,
                    isFirst = index == 0,
                    isLast = index == processingSteps.lastIndex,
                    rowHeight = rowHeight,
                    compact = compact,
                    reducedMotion = reducedMotion,
                )
            }
        }
    }
}

@Composable
private fun ProcessingTimelineRow(
    label: String,
    subtitle: String?,
    visual: StepVisual,
    isFirst: Boolean,
    isLast: Boolean,
    rowHeight: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    reducedMotion: Boolean,
) {
    val rowAlpha by animateFloatAsState(
        targetValue = if (visual == StepVisual.Upcoming) 0.66f else 1f,
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else 420,
            easing = RenEmphasizedDecelerateEasing,
        ),
        label = "processing-row-alpha",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimelineIndicator(
            visual = visual,
            isFirst = isFirst,
            isLast = isLast,
            compact = compact,
            reducedMotion = reducedMotion,
            modifier = Modifier
                .width(if (compact) 38.dp else 42.dp)
                .fillMaxHeight(),
        )

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (visual == StepVisual.Upcoming) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = rowAlpha)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                if (visual == StepVisual.Current) {
                    ActiveStepSignal(reducedMotion = reducedMotion)
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (!isLast) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun TimelineIndicator(
    visual: StepVisual,
    isFirst: Boolean,
    isLast: Boolean,
    compact: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val green = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)
    val indicatorSize = if (compact) 24.dp else 26.dp
    val upcomingSize = if (compact) 24.dp else 26.dp
    val circleRadiusDp = indicatorSize / 2f
    val upperLineColor by animateColorAsState(
        targetValue = when (visual) {
            StepVisual.Complete, StepVisual.Current -> green
            StepVisual.Upcoming -> muted
        },
        animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing),
        label = "timeline-upper-line",
    )
    val lowerLineColor by animateColorAsState(
        targetValue = when (visual) {
            StepVisual.Complete -> green
            StepVisual.Current, StepVisual.Upcoming -> muted
        },
        animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing),
        label = "timeline-lower-line",
    )
    val upperLineProgress by animateFloatAsState(
        targetValue = if (visual == StepVisual.Upcoming) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else 820,
            easing = RenEmphasizedDecelerateEasing,
        ),
        label = "timeline-upper-progress",
    )
    val lowerLineProgress by animateFloatAsState(
        targetValue = if (visual == StepVisual.Complete) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else 820,
            easing = RenEmphasizedDecelerateEasing,
        ),
        label = "timeline-lower-progress",
    )
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val circleRadius = circleRadiusDp.toPx()
            if (!isFirst) {
                val lineEndY = centerY - circleRadius
                val trackEnd = Offset(centerX, lineEndY)
                val activeEnd = Offset(centerX, lineEndY * upperLineProgress)
                drawLine(
                    color = muted.copy(alpha = 0.32f),
                    start = Offset(centerX, 0f),
                    end = trackEnd,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = upperLineColor,
                    start = Offset(centerX, 0f),
                    end = activeEnd,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            if (!isLast) {
                val lineStartY = centerY + circleRadius
                val trackEndY = size.height
                val activeEndY = lineStartY + (size.height - lineStartY) * lowerLineProgress
                val start = Offset(centerX, lineStartY)
                val trackEnd = Offset(centerX, trackEndY)
                val activeEnd = Offset(centerX, activeEndY)
                drawLine(
                    color = muted.copy(alpha = 0.32f),
                    start = start,
                    end = trackEnd,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = lowerLineColor,
                    start = start,
                    end = activeEnd,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        when (visual) {
            StepVisual.Complete -> CompletedCheckmark(
                reducedMotion = reducedMotion,
                modifier = Modifier.size(indicatorSize),
            )
            StepVisual.Current -> ActiveCurrentIndicator(
                reducedMotion = reducedMotion,
                modifier = Modifier.size(indicatorSize),
            )
            StepVisual.Upcoming -> Surface(
                modifier = Modifier.size(upcomingSize),
                shape = CircleShape,
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, muted),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Intentionally empty: upcoming steps use shape/status only, not numbers.
                }
            }
        }
    }
}

// region â€” Step data

private enum class StepVisual { Complete, Current, Upcoming }

private data class Step(
    val status: PlanStatus,
    val labelRes: Int,
    val subtitleRes: Int,
)

private val processingSteps = listOf(
    Step(PlanStatus.Uploading, R.string.uploading_document, R.string.step_subtitle_uploading),
    Step(PlanStatus.Analyzing, R.string.reading_material, R.string.step_subtitle_analyzing),
    Step(PlanStatus.IdentifyingTopics, R.string.identifying_topics, R.string.step_subtitle_identifying),
    Step(PlanStatus.CreatingBlocks, R.string.creating_blocks, R.string.step_subtitle_creating),
    Step(PlanStatus.Finalizing, R.string.finalizing_plan, R.string.step_subtitle_finalizing),
)

@Composable
private fun stepSubtitle(
    step: Step,
    stepIndex: Int,
    currentIndex: Int,
    state: PlanGenerationUiState,
): String? {
    if (stepIndex != currentIndex) return null

    return if (
        step.status == PlanStatus.Uploading &&
        state.uploadingDocumentIndex > 0 &&
        state.uploadingDocumentTotal > 1
    ) {
        stringResource(
            R.string.uploading_pdf_progress,
            state.uploadingDocumentIndex,
            state.uploadingDocumentTotal,
        )
    } else {
        stringResource(step.subtitleRes)
    }
}

// endregion

// region â€” ProcessingStep composable

// region â€” Status indicator composables (Canvas-drawn, no emojis)

@Composable
private fun CompletedCheckmark(
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val green = MaterialTheme.colorScheme.primary
    val checkColor = MaterialTheme.colorScheme.onPrimary
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible || reducedMotion) 1f else 0.74f,
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else 420,
            easing = RenEmphasizedDecelerateEasing,
        ),
        label = "completed-check-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible || reducedMotion) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else 260,
            easing = RenEmphasizedEasing,
        ),
        label = "completed-check-alpha",
    )

    Canvas(modifier = modifier.scale(scale).alpha(alpha)) {
        val r = size.minDimension / 2f
        drawCircle(color = green, radius = r)
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val cx = center.x
        val cy = center.y
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx - r * 0.30f, cy + r * 0.02f)
            lineTo(cx - r * 0.05f, cy + r * 0.28f)
            lineTo(cx + r * 0.35f, cy - r * 0.22f)
        }
        drawPath(path, color = checkColor, style = stroke)
    }
}

@Composable
private fun ActiveCurrentIndicator(
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val green = MaterialTheme.colorScheme.primary
    val pulse = if (reducedMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "current-step-breathe")
        val animatedPulse by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1650, easing = RenEmphasizedEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "current-step-pulse",
        )
        animatedPulse
    }
    Canvas(modifier) {
        val baseRadius = size.minDimension / 2f - 3.dp.toPx()
        val pulseRadius = baseRadius + (if (reducedMotion) 0f else pulse * 3.dp.toPx())
        drawCircle(
            color = green.copy(alpha = if (reducedMotion) 0.18f else 0.08f + pulse * 0.12f),
            radius = pulseRadius,
        )
        drawCircle(
            color = green,
            radius = baseRadius,
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(
            color = green,
            radius = 5.5.dp.toPx(),
        )
    }
}

@Composable
private fun ActiveStepSignal(reducedMotion: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    val phase = if (reducedMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "active-step-signal")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "active-step-signal-phase",
        )
        animatedPhase
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val shiftedPhase = (phase + index * 0.22f) % 1f
            val wave = 1f - abs(shiftedPhase * 2f - 1f)
            val dotSize = if (reducedMotion) 5.dp else (4.5f + wave * 1.7f).dp
            Surface(
                modifier = Modifier
                    .size(dotSize)
                    .alpha(if (reducedMotion) 0.78f else 0.28f + wave * 0.58f),
                shape = CircleShape,
                color = color,
            ) {}
        }
    }
}

// endregion

// region â€” Connector line between steps

// endregion

// region â€” While you wait card

// endregion
