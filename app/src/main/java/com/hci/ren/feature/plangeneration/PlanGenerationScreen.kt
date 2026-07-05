package com.hci.ren.feature.plangeneration

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hci.ren.R
import com.hci.ren.ui.components.PlanFlowScaffold
import com.hci.ren.ui.motion.RenEmphasizedDecelerateEasing
import com.hci.ren.ui.motion.RenEmphasizedEasing
import com.hci.ren.ui.motion.isReducedMotionEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun PlanGenerationScreen(
    state: PlanGenerationUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    var showCancelDialog by remember { mutableStateOf(false) }
    val reducedMotion = isReducedMotionEnabled()
    val isProcessing = state.status != PlanStatus.Failed
    val activeStepIndex = activeStepIndex(state.status)
    val topProgress by animateFloatAsState(
        targetValue = generationProgress(state.status),
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else 900,
            easing = RenEmphasizedDecelerateEasing,
        ),
        label = "plan-generation-top-progress",
    )

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
                    },
                ) {
                    Text(
                        stringResource(R.string.cancel_generation_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.cancel_generation_dismiss))
                }
            },
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
        progress = if (isProcessing) topProgress else null,
        stepLabel = if (isProcessing) "${activeStepIndex + 1} OF ${processingSteps.size}" else null,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.status == PlanStatus.Failed) {
                FailedContent(errorMessage = state.errorMessage, onRetry = onRetry)
            } else {
                ProcessingContent(
                    state = state,
                    activeStepIndex = activeStepIndex,
                    reducedMotion = reducedMotion,
                )
            }
        }
    }
}

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
    ) {
        Text(stringResource(R.string.retry))
    }
}

@Composable
private fun ProcessingContent(
    state: PlanGenerationUiState,
    activeStepIndex: Int,
    reducedMotion: Boolean,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxHeight < 660.dp
        val animationHeight = if (compact) 330.dp else 430.dp
        val animationTopGap = if (compact) 38.dp else 64.dp
        val statusTopGap = if (compact) 12.dp else 18.dp
        val bottomGap = if (compact) 4.dp else 12.dp

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Spacer(Modifier.height(animationTopGap))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                BreathingPlanAnimation(
                    stepIndex = activeStepIndex,
                    reducedMotion = reducedMotion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(animationHeight),
                )
            }

            Spacer(Modifier.height(statusTopGap))

            AnimatedContent(
                targetState = activeStepIndex,
                transitionSpec = {
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = if (reducedMotion) 0 else 260,
                            easing = RenEmphasizedDecelerateEasing,
                        ),
                    ) togetherWith fadeOut(
                        animationSpec = tween(
                            durationMillis = if (reducedMotion) 0 else 140,
                            easing = RenEmphasizedEasing,
                        ),
                    )
                },
                label = "plan-generation-current-step",
                modifier = Modifier.fillMaxWidth(),
            ) { targetIndex ->
                val step = processingSteps[targetIndex]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            stateDescription = "Processing step ${targetIndex + 1} of ${processingSteps.size}"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stepSubtitle(step),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.weight(0.45f))

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
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(bottomGap))
        }
    }
}

@Composable
private fun BreathingPlanAnimation(
    stepIndex: Int,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outlineVariant
    val profile = planAnimationProfile
    val transition = rememberInfiniteTransition(label = "plan-breathing-animation")
    val breatheAnimated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PLAN_BREATH_DURATION_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "plan-breath-motion",
    )
    val centerMotionAnimated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PLAN_INNER_MOTION_DURATION_MILLIS, easing = RenEmphasizedEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "plan-center-motion",
    )
    val centerPhaseAnimated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PLAN_CENTER_ROTATION_DURATION_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "plan-center-phase",
    )
    val breatheProgress = if (reducedMotion) 0.5f else breatheAnimated
    val breath = planGenerationBreathMotion(breatheProgress)
    val centerMotion = if (reducedMotion) 0.5f else centerMotionAnimated
    val centerPhase = if (reducedMotion) 0.18f else centerPhaseAnimated

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val minDimension = min(size.width, size.height)
        val coreRadius = minDimension * profile.coreScale
        val waveReach = minDimension * profile.waveReach
        val motionProfile = WaveMotionProfile(
            waveReach = profile.waveReach,
            scaleXBase = profile.scaleXBase,
            scaleYBase = profile.scaleYBase,
        )

        repeat(profile.waveCount) { index ->
            val progress = planGenerationRippleProgress(breatheProgress, index, profile.waveCount)
            val motion = planGenerationWaveMotion(motionProfile, index, progress)
            val radius = (coreRadius * profile.waveStart + waveReach * motion.progress) * breath.scale
            drawOval(
                color = primary.copy(alpha = motion.alphaMultiplier * profile.waveAlpha * breath.auraAlphaMultiplier),
                topLeft = Offset(center.x - radius * motion.scaleX, center.y - radius * motion.scaleY),
                size = Size(radius * 2f * motion.scaleX, radius * 2f * motion.scaleY),
            )
        }

        drawCenteredOval(
            color = primary.copy(alpha = profile.ambientAlpha * breath.auraAlphaMultiplier),
            center = center,
            width = minDimension * 0.86f * breath.scale,
            height = minDimension * 0.52f * breath.scale,
        )
        drawCenteredOval(
            color = primary.copy(alpha = profile.ambientAlpha * 0.72f * breath.auraAlphaMultiplier),
            center = center,
            width = minDimension * 0.58f * breath.scale,
            height = minDimension * 0.66f * breath.scale,
        )

        drawCircle(
            color = primary.copy(alpha = 0.06f * breath.coreAlphaMultiplier),
            radius = coreRadius * 2.7f * breath.scale,
            center = center,
        )
        drawCircle(
            color = primary.copy(alpha = 0.118f * breath.coreAlphaMultiplier),
            radius = coreRadius * 1.48f * breath.scale,
            center = center,
        )
        drawCircle(
            color = primary.copy(alpha = 0.18f * breath.coreAlphaMultiplier),
            radius = coreRadius * 1.02f * breath.scale,
            center = center,
        )
        drawCircle(
            color = primary.copy(alpha = 0.36f * breath.coreAlphaMultiplier),
            radius = coreRadius * 1.46f * breath.scale,
            center = center,
            style = Stroke(width = 1.35.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(
            color = primary.copy(alpha = 0.08f * breath.coreAlphaMultiplier),
            radius = coreRadius * 0.62f * breath.scale,
            center = center,
        )

        drawPlanGlyph(
            stepIndex = stepIndex,
            center = center,
            coreRadius = coreRadius * 1.08f * breath.scale,
            primary = primary,
            muted = muted,
            motion = centerMotion,
            phase = centerPhase,
        )
    }
}

private fun DrawScope.drawCenteredOval(
    color: Color,
    center: Offset,
    width: Float,
    height: Float,
) {
    drawOval(
        color = color,
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = Size(width, height),
    )
}

private fun DrawScope.drawPlanGlyph(
    stepIndex: Int,
    center: Offset,
    coreRadius: Float,
    primary: Color,
    muted: Color,
    motion: Float,
    phase: Float,
) {
    when (stepIndex) {
        0 -> drawDocumentStack(center, coreRadius, primary, motion)
        1 -> drawReadingLines(center, coreRadius, primary, muted, motion)
        2 -> drawTopicNodes(center, coreRadius, primary, muted, phase)
        3 -> drawStudyBlocks(center, coreRadius, primary, phase)
        else -> drawCalendarMark(center, coreRadius, primary, muted, motion)
    }
}

private fun DrawScope.drawDocumentStack(
    center: Offset,
    radius: Float,
    color: Color,
    motion: Float,
) {
    val pageWidth = radius * 0.94f
    val pageHeight = radius * 1.1f
    val corner = CornerRadius(7.dp.toPx(), 7.dp.toPx())
    val lift = (motion - 0.5f) * 4.dp.toPx()
    repeat(2) { index ->
        val layer = index - 0.5f
        val offset = layer * 6.dp.toPx()
        val yOffset = -offset + lift * layer
        drawRoundRect(
            color = color.copy(alpha = 0.14f + index * 0.18f),
            topLeft = Offset(center.x - pageWidth / 2f + offset, center.y - pageHeight / 2f + yOffset),
            size = Size(pageWidth, pageHeight),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = color.copy(alpha = 0.42f + index * 0.16f),
            topLeft = Offset(center.x - pageWidth / 2f + offset, center.y - pageHeight / 2f + yOffset),
            size = Size(pageWidth, pageHeight),
            cornerRadius = corner,
            style = Stroke(width = 1.45.dp.toPx(), cap = StrokeCap.Round),
        )
    }
    val scanY = center.y + pageHeight * (-0.2f + motion * 0.4f)
    drawLine(
        color = color.copy(alpha = 0.72f),
        start = Offset(center.x - pageWidth * 0.28f, scanY),
        end = Offset(center.x + pageWidth * 0.28f, scanY),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawReadingLines(
    center: Offset,
    radius: Float,
    color: Color,
    muted: Color,
    motion: Float,
) {
    val width = radius * 1.24f
    val startX = center.x - width / 2f
    val startY = center.y - radius * 0.46f
    repeat(4) { index ->
        val y = startY + index * radius * 0.3f
        val lineWidth = width * if (index == 3) 0.62f else 1f
        drawLine(
            color = muted.copy(alpha = 0.42f),
            start = Offset(startX, y),
            end = Offset(startX + lineWidth, y),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    val scanY = startY + motion * radius * 0.9f
    drawLine(
        color = color.copy(alpha = 0.86f),
        start = Offset(startX + width * 0.08f, scanY),
        end = Offset(startX + width * 0.92f, scanY),
        strokeWidth = 2.6.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawTopicNodes(
    center: Offset,
    radius: Float,
    color: Color,
    muted: Color,
    phase: Float,
) {
    val rotation = phase.toDouble() * TOPIC_NODE_TURN
    val points = List(5) { index ->
        val angle = -PI / 2.0 + index * (TOPIC_NODE_TURN / 5.0) + rotation
        val distance = radius * (0.62f + if (index % 2 == 0) 0.1f else 0f)
        Offset(
            x = center.x + cos(angle).toFloat() * distance,
            y = center.y + sin(angle).toFloat() * distance,
        )
    }
    points.forEachIndexed { index, point ->
        val emphasis = 0.58f + 0.26f * ((1f + cos(rotation - index * TOPIC_NODE_TURN / points.size).toFloat()) / 2f)
        drawLine(
            color = muted.copy(alpha = 0.34f),
            start = center,
            end = point,
            strokeWidth = 1.35.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = color.copy(alpha = emphasis),
            radius = (3.4f + emphasis * 0.8f).dp.toPx(),
            center = point,
        )
    }
    drawCircle(color = color, radius = 4.6.dp.toPx(), center = center)
}

private fun DrawScope.drawStudyBlocks(
    center: Offset,
    radius: Float,
    color: Color,
    phase: Float,
) {
    val width = radius * 1.32f
    val height = radius * 0.3f
    val corner = CornerRadius(7.dp.toPx(), 7.dp.toPx())
    repeat(3) { index ->
        val y = center.y - height * 1.55f + index * height * 1.2f
        val emphasis = ((1f + sin(phase.toDouble() * TOPIC_NODE_TURN + index * TOPIC_NODE_TURN / 3.0).toFloat()) / 2f)
        val rowWidth = width * (0.82f + emphasis * 0.16f - index * 0.06f)
        drawRoundRect(
            color = color.copy(alpha = 0.28f + index * 0.14f + emphasis * 0.12f),
            topLeft = Offset(center.x - rowWidth / 2f, y),
            size = Size(rowWidth, height),
            cornerRadius = corner,
        )
    }
}

private fun DrawScope.drawCalendarMark(
    center: Offset,
    radius: Float,
    color: Color,
    muted: Color,
    motion: Float,
) {
    val width = radius * 1.24f
    val height = radius * 1.0f
    val topLeft = Offset(center.x - width / 2f, center.y - height / 2f)
    val corner = CornerRadius(8.dp.toPx(), 8.dp.toPx())
    drawRoundRect(
        color = color.copy(alpha = 0.64f),
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = corner,
        style = Stroke(width = 1.8.dp.toPx()),
    )
    drawLine(
        color = color.copy(alpha = 0.74f),
        start = Offset(topLeft.x, topLeft.y + height * 0.32f),
        end = Offset(topLeft.x + width, topLeft.y + height * 0.32f),
        strokeWidth = 1.4.dp.toPx(),
        cap = StrokeCap.Round,
    )
    repeat(2) { row ->
        repeat(3) { column ->
            drawCircle(
                color = if (row == 1 && column == 2) color.copy(alpha = 0.66f + motion * 0.28f) else muted.copy(alpha = 0.48f),
                radius = (2.4f + if (row == 1 && column == 2) motion * 0.7f else 0f).dp.toPx(),
                center = Offset(
                    x = topLeft.x + width * (0.25f + column * 0.25f),
                    y = topLeft.y + height * (0.54f + row * 0.22f),
                ),
            )
        }
    }
}

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

private fun activeStepIndex(status: PlanStatus): Int = when (status) {
    PlanStatus.Uploading -> 0
    PlanStatus.Analyzing -> 1
    PlanStatus.IdentifyingTopics -> 2
    PlanStatus.CreatingBlocks -> 3
    PlanStatus.Finalizing, PlanStatus.Completed -> 4
    PlanStatus.Failed -> 0
}

private fun generationProgress(status: PlanStatus): Float {
    if (status == PlanStatus.Completed) return 1f
    val index = activeStepIndex(status)
    return (index + 1f) / processingSteps.size
}

@Composable
private fun stepSubtitle(step: Step): String = stringResource(step.subtitleRes)

private data class AnimationProfile(
    val waveCount: Int,
    val waveReach: Float,
    val waveStart: Float,
    val waveAlpha: Float,
    val ambientAlpha: Float,
    val coreScale: Float,
    val scaleXBase: Float,
    val scaleYBase: Float,
)

private val planAnimationProfile = AnimationProfile(
    waveCount = 4,
    waveReach = 0.48f,
    waveStart = 1.38f,
    waveAlpha = 0.024f,
    ambientAlpha = 0.017f,
    coreScale = 0.126f,
    scaleXBase = 0.64f,
    scaleYBase = 0.36f,
)

private const val PLAN_BREATH_DURATION_MILLIS = 2600
private const val PLAN_INNER_MOTION_DURATION_MILLIS = 900
private const val PLAN_CENTER_ROTATION_DURATION_MILLIS = 2400
private const val TOPIC_NODE_TURN = 6.283185307179586
