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
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.max
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
        val topGap = if (compact) 4.dp else 10.dp
        val animationTopGap = if (compact) 34.dp else 54.dp
        val statusTopGap = if (compact) 12.dp else 18.dp
        val bottomGap = if (compact) 4.dp else 12.dp

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Spacer(Modifier.height(topGap))

            Text(
                text = stringResource(R.string.ai_processing),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(animationTopGap))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                RadiatingPlanAnimation(
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
private fun RadiatingPlanAnimation(
    stepIndex: Int,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outlineVariant
    val profile = animationProfile(stepIndex)
    val transition = rememberInfiniteTransition(label = "plan-radiating-animation")
    val phaseAnimated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = profile.waveDurationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radiating-wave-phase",
    )
    val breatheAnimated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = profile.breatheDurationMillis, easing = RenEmphasizedEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "radiating-core-breathe",
    )
    val scanAnimated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = profile.scanDurationMillis, easing = RenEmphasizedEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "radiating-inner-motion",
    )
    val phase = if (reducedMotion) 0.18f else phaseAnimated
    val breathe = if (reducedMotion) 0.55f else breatheAnimated
    val scan = if (reducedMotion) 0.5f else scanAnimated

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val minDimension = min(size.width, size.height)
        val maxDimension = max(size.width, size.height)
        val coreRadius = minDimension * (profile.coreScale + breathe * 0.01f)
        val waveReach = maxDimension * profile.waveReach

        repeat(profile.waveCount) { index ->
            val progress = (phase + index * profile.waveDelay) % 1f
            val fade = (1f - progress) * (1f - progress)
            val angle = profile.angleOffset + index * profile.angleStep
            val drift = maxDimension * (profile.drift + index * 0.004f) * progress
            val radius = coreRadius * profile.waveStart + waveReach * progress
            val scaleX = profile.scaleXBase + (index % 4) * 0.11f
            val scaleY = profile.scaleYBase + ((index + 2) % 4) * 0.09f
            val waveCenter = Offset(
                x = center.x + cos(angle).toFloat() * drift,
                y = center.y + sin(angle).toFloat() * drift * 0.78f,
            )
            drawOval(
                color = primary.copy(alpha = fade * profile.waveAlpha),
                topLeft = Offset(waveCenter.x - radius * scaleX, waveCenter.y - radius * scaleY),
                size = Size(radius * 2f * scaleX, radius * 2f * scaleY),
            )
        }

        val ambientLean = if (reducedMotion) 0.35f else breathe
        drawOval(
            color = primary.copy(alpha = profile.ambientAlpha * 0.42f + ambientLean * 0.014f),
            topLeft = Offset(center.x - maxDimension * 0.62f, center.y - maxDimension * 0.38f),
            size = Size(maxDimension * 1.24f, maxDimension * 0.76f),
        )
        drawOval(
            color = primary.copy(alpha = profile.ambientAlpha * 0.36f + ambientLean * 0.012f),
            topLeft = Offset(center.x - maxDimension * 0.34f, center.y - maxDimension * 0.58f),
            size = Size(maxDimension * 0.86f, maxDimension * 1.08f),
        )
        drawOval(
            color = primary.copy(alpha = profile.ambientAlpha + ambientLean * 0.028f),
            topLeft = Offset(center.x - maxDimension * 0.42f, center.y - maxDimension * 0.27f),
            size = Size(maxDimension * 0.84f, maxDimension * 0.54f),
        )
        drawOval(
            color = primary.copy(alpha = profile.ambientAlpha * 0.72f + ambientLean * 0.02f),
            topLeft = Offset(center.x - maxDimension * 0.33f, center.y - maxDimension * 0.36f),
            size = Size(maxDimension * 0.66f, maxDimension * 0.72f),
        )

        drawCircle(
            color = primary.copy(alpha = 0.038f + breathe * 0.038f),
            radius = coreRadius * 2.7f,
            center = center,
        )
        drawCircle(
            color = primary.copy(alpha = 0.105f + breathe * 0.035f),
            radius = coreRadius * 1.48f,
            center = center,
        )
        drawCircle(
            color = primary.copy(alpha = 0.18f),
            radius = coreRadius * 1.02f,
            center = center,
        )
        drawCircle(
            color = primary.copy(alpha = 0.36f),
            radius = coreRadius * 1.46f,
            center = center,
            style = Stroke(width = 1.35.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(
            color = primary.copy(alpha = 0.08f),
            radius = coreRadius * 0.62f,
            center = center,
        )

        drawPlanGlyph(
            stepIndex = stepIndex,
            center = center,
            coreRadius = coreRadius * 1.08f,
            primary = primary,
            muted = muted,
            motion = scan,
        )
    }
}

private fun DrawScope.drawPlanGlyph(
    stepIndex: Int,
    center: Offset,
    coreRadius: Float,
    primary: Color,
    muted: Color,
    motion: Float,
) {
    when (stepIndex) {
        0 -> drawDocumentStack(center, coreRadius, primary)
        1 -> drawReadingLines(center, coreRadius, primary, muted, motion)
        2 -> drawTopicNodes(center, coreRadius, primary, muted, motion)
        3 -> drawStudyBlocks(center, coreRadius, primary)
        else -> drawCalendarMark(center, coreRadius, primary, muted)
    }
}

private fun DrawScope.drawDocumentStack(center: Offset, radius: Float, color: Color) {
    val pageWidth = radius * 0.94f
    val pageHeight = radius * 1.1f
    val corner = CornerRadius(7.dp.toPx(), 7.dp.toPx())
    repeat(2) { index ->
        val offset = (index - 0.5f) * 6.dp.toPx()
        drawRoundRect(
            color = color.copy(alpha = 0.14f + index * 0.18f),
            topLeft = Offset(center.x - pageWidth / 2f + offset, center.y - pageHeight / 2f - offset),
            size = Size(pageWidth, pageHeight),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = color.copy(alpha = 0.42f + index * 0.16f),
            topLeft = Offset(center.x - pageWidth / 2f + offset, center.y - pageHeight / 2f - offset),
            size = Size(pageWidth, pageHeight),
            cornerRadius = corner,
            style = Stroke(width = 1.45.dp.toPx(), cap = StrokeCap.Round),
        )
    }
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
    motion: Float,
) {
    val points = List(5) { index ->
        val angle = -PI / 2.0 + index * (2.0 * PI / 5.0)
        val distance = radius * (0.62f + if (index % 2 == 0) 0.1f else 0f)
        Offset(
            x = center.x + cos(angle).toFloat() * distance,
            y = center.y + sin(angle).toFloat() * distance,
        )
    }
    points.forEachIndexed { index, point ->
        drawLine(
            color = muted.copy(alpha = 0.34f),
            start = center,
            end = point,
            strokeWidth = 1.35.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = color.copy(alpha = if (index == ((motion * points.size).toInt() % points.size)) 0.9f else 0.48f),
            radius = if (index == ((motion * points.size).toInt() % points.size)) 4.6.dp.toPx() else 3.4.dp.toPx(),
            center = point,
        )
    }
    drawCircle(color = color, radius = 4.6.dp.toPx(), center = center)
}

private fun DrawScope.drawStudyBlocks(center: Offset, radius: Float, color: Color) {
    val width = radius * 1.32f
    val height = radius * 0.3f
    val corner = CornerRadius(7.dp.toPx(), 7.dp.toPx())
    repeat(3) { index ->
        val y = center.y - height * 1.55f + index * height * 1.2f
        drawRoundRect(
            color = color.copy(alpha = 0.28f + index * 0.18f),
            topLeft = Offset(center.x - width / 2f, y),
            size = Size(width * (1f - index * 0.1f), height),
            cornerRadius = corner,
        )
    }
}

private fun DrawScope.drawCalendarMark(center: Offset, radius: Float, color: Color, muted: Color) {
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
                color = if (row == 1 && column == 2) color else muted.copy(alpha = 0.48f),
                radius = 2.4.dp.toPx(),
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
    val waveDelay: Float,
    val waveReach: Float,
    val waveStart: Float,
    val waveAlpha: Float,
    val ambientAlpha: Float,
    val coreScale: Float,
    val drift: Float,
    val angleOffset: Float,
    val angleStep: Float,
    val scaleXBase: Float,
    val scaleYBase: Float,
    val waveDurationMillis: Int,
    val breatheDurationMillis: Int,
    val scanDurationMillis: Int,
)

private fun animationProfile(stepIndex: Int): AnimationProfile = when (stepIndex) {
    0 -> AnimationProfile(
        waveCount = 6,
        waveDelay = 0.165f,
        waveReach = 0.7f,
        waveStart = 1.34f,
        waveAlpha = 0.034f,
        ambientAlpha = 0.024f,
        coreScale = 0.124f,
        drift = 0.04f,
        angleOffset = -0.72f,
        angleStep = 1.18f,
        scaleXBase = 0.68f,
        scaleYBase = 0.44f,
        waveDurationMillis = 4400,
        breatheDurationMillis = 3300,
        scanDurationMillis = 2500,
    )
    1 -> AnimationProfile(
        waveCount = 7,
        waveDelay = 0.143f,
        waveReach = 0.78f,
        waveStart = 1.42f,
        waveAlpha = 0.038f,
        ambientAlpha = 0.028f,
        coreScale = 0.126f,
        drift = 0.045f,
        angleOffset = -0.95f,
        angleStep = 1.08f,
        scaleXBase = 0.74f,
        scaleYBase = 0.46f,
        waveDurationMillis = 4600,
        breatheDurationMillis = 3400,
        scanDurationMillis = 2800,
    )
    2 -> AnimationProfile(
        waveCount = 8,
        waveDelay = 0.125f,
        waveReach = 0.84f,
        waveStart = 1.32f,
        waveAlpha = 0.04f,
        ambientAlpha = 0.03f,
        coreScale = 0.122f,
        drift = 0.052f,
        angleOffset = -1.2f,
        angleStep = 0.92f,
        scaleXBase = 0.66f,
        scaleYBase = 0.5f,
        waveDurationMillis = 4100,
        breatheDurationMillis = 3200,
        scanDurationMillis = 2300,
    )
    3 -> AnimationProfile(
        waveCount = 6,
        waveDelay = 0.155f,
        waveReach = 0.74f,
        waveStart = 1.5f,
        waveAlpha = 0.036f,
        ambientAlpha = 0.026f,
        coreScale = 0.132f,
        drift = 0.034f,
        angleOffset = -0.55f,
        angleStep = 1.05f,
        scaleXBase = 0.8f,
        scaleYBase = 0.42f,
        waveDurationMillis = 5000,
        breatheDurationMillis = 3600,
        scanDurationMillis = 3000,
    )
    else -> AnimationProfile(
        waveCount = 5,
        waveDelay = 0.18f,
        waveReach = 0.66f,
        waveStart = 1.58f,
        waveAlpha = 0.032f,
        ambientAlpha = 0.022f,
        coreScale = 0.13f,
        drift = 0.028f,
        angleOffset = -0.42f,
        angleStep = 1.24f,
        scaleXBase = 0.72f,
        scaleYBase = 0.48f,
        waveDurationMillis = 5200,
        breatheDurationMillis = 3700,
        scanDurationMillis = 3200,
    )
}
