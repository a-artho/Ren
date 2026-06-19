package com.hci.ren.feature.plangeneration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hci.ren.R
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PlanGenerationScreen(state: PlanGenerationUiState, onBack: () -> Unit, onRetry: () -> Unit) {
    var showCancelDialog by remember { mutableStateOf(false) }

    if (state.status != PlanStatus.Failed) {
        BackHandler(enabled = true) {
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (state.status == PlanStatus.Failed) {
                            onBack()
                        } else {
                            showCancelDialog = true
                        }
                    }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    stringResource(R.string.ai_processing),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
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
}

// region — Failed state

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

// region — Processing state

@Composable
private fun ProcessingContent(state: PlanGenerationUiState) {
    val currentIndex = if (state.status == PlanStatus.Completed) {
        processingSteps.size
    } else {
        processingSteps.indexOfFirst { it.status == state.status }.coerceAtLeast(0)
    }
    
    val targetProgress = if (state.status == PlanStatus.Completed) 1f else (currentIndex + 1f) / processingSteps.size
    val animatedProgress = animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "plan generation progress",
    )

    // Title with sparkle
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.building_study_plan),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 36.sp,
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.processing_description),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(20.dp))

    // Animated progress bar with sparkle
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { animatedProgress.value },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .semantics {
                    stateDescription = if (state.status == PlanStatus.Completed) {
                        "Processing completed"
                    } else {
                        "Processing step ${currentIndex + 1} of ${processingSteps.size}"
                    }
                },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
        )
        Spacer(Modifier.width(8.dp))
        SparkleIcon()
    }

    Spacer(Modifier.height(24.dp))

    // Steps card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            processingSteps.forEachIndexed { index, step ->
                val visual = when {
                    state.status == PlanStatus.Completed || index < currentIndex -> StepVisual.Complete
                    index == currentIndex -> StepVisual.Current
                    else -> StepVisual.Upcoming
                }
                ProcessingStep(
                    icon = step.icon,
                    label = stringResource(step.labelRes),
                    subtitle = if (visual == StepVisual.Current) stringResource(step.subtitleRes) else null,
                    visual = visual,
                )
                // Connector line between steps (not after the last)
                if (index < processingSteps.lastIndex) {
                    val lineColor = if (state.status == PlanStatus.Completed || index < currentIndex) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                    ConnectorLine(color = lineColor)
                }
            }
        }
    }

    Spacer(Modifier.height(24.dp))

    // "While you wait" card
    AnimatedVisibility(
        visible = state.status == PlanStatus.Completed || currentIndex >= 1,
        enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 },
    ) {
        WhileYouWaitCard()
    }
}

// endregion

// region — Step data

private enum class StepVisual { Complete, Current, Upcoming }

private data class Step(
    val status: PlanStatus,
    val icon: ImageVector,
    val labelRes: Int,
    val subtitleRes: Int,
)

private val processingSteps = listOf(
    Step(PlanStatus.Uploading, Icons.Default.UploadFile, R.string.uploading_document, R.string.step_subtitle_uploading),
    Step(PlanStatus.Analyzing, Icons.AutoMirrored.Filled.MenuBook, R.string.reading_material, R.string.step_subtitle_analyzing),
    Step(PlanStatus.IdentifyingTopics, Icons.AutoMirrored.Filled.ManageSearch, R.string.identifying_topics, R.string.step_subtitle_identifying),
    Step(PlanStatus.CreatingBlocks, Icons.Default.CalendarViewDay, R.string.creating_blocks, R.string.step_subtitle_creating),
    Step(PlanStatus.Finalizing, Icons.Default.Flag, R.string.finalizing_plan, R.string.step_subtitle_finalizing),
)

// endregion

// region — ProcessingStep composable

@Composable
private fun ProcessingStep(icon: ImageVector, label: String, subtitle: String?, visual: StepVisual) {
    val contentAlpha = when (visual) {
        StepVisual.Upcoming -> .40f
        else -> 1f
    }
    val iconTint = when (visual) {
        StepVisual.Complete -> MaterialTheme.colorScheme.primary
        StepVisual.Current -> MaterialTheme.colorScheme.primary
        StepVisual.Upcoming -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val circleBg = when (visual) {
        StepVisual.Complete -> MaterialTheme.colorScheme.primaryContainer
        StepVisual.Current -> MaterialTheme.colorScheme.primaryContainer
        StepVisual.Upcoming -> MaterialTheme.colorScheme.surfaceVariant
    }

    // Pulse animation for the active step
    val infiniteTransition = rememberInfiniteTransition(label = "step-pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "step-circle-pulse",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .alpha(contentAlpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Step circle icon
        Surface(
            shape = CircleShape,
            color = circleBg,
            modifier = Modifier
                .size(48.dp)
                .scale(if (visual == StepVisual.Current) pulse else 1f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = iconTint)
            }
        }

        Spacer(Modifier.width(14.dp))

        // Label and subtitle
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (visual == StepVisual.Current) FontWeight.SemiBold else FontWeight.Normal,
                color = when (visual) {
                    StepVisual.Current -> MaterialTheme.colorScheme.onSurface
                    StepVisual.Complete -> MaterialTheme.colorScheme.onSurface
                    StepVisual.Upcoming -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Status indicator (right side)
        when (visual) {
            StepVisual.Complete -> CompletedCheckmark()
            StepVisual.Current -> BouncingDots()
            StepVisual.Upcoming -> UpcomingCircle()
        }
    }
}

// endregion

// region — Status indicator composables (Canvas-drawn, no emojis)

/** Green filled circle with a white checkmark drawn via Canvas. */
@Composable
private fun CompletedCheckmark() {
    val green = MaterialTheme.colorScheme.primary
    val checkColor = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = Modifier.size(28.dp)) {
        val r = size.minDimension / 2f
        drawCircle(color = green, radius = r)
        // White checkmark
        val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
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

/** Three dots that bounce with staggered timing. */
@Composable
private fun BouncingDots() {
    val dotColor = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "bouncing-dots")
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val offset by transition.animateFloat(
                initialValue = 0f,
                targetValue = -6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 500,
                        delayMillis = index * 150,
                        easing = FastOutSlowInEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot-$index",
            )
            Canvas(
                modifier = Modifier
                    .size(8.dp)
                    .offset(y = offset.dp),
            ) {
                drawCircle(color = dotColor)
            }
        }
    }
}

/** Empty outlined circle for upcoming steps. */
@Composable
private fun UpcomingCircle() {
    val outlineColor = MaterialTheme.colorScheme.primaryContainer
    Canvas(modifier = Modifier.size(28.dp)) {
        drawCircle(
            color = outlineColor,
            radius = size.minDimension / 2f - 1.5.dp.toPx(),
            style = Stroke(width = 1.8.dp.toPx()),
        )
    }
}

// endregion

// region — Connector line between steps

@Composable
private fun ConnectorLine(color: Color) {
    Box(
        modifier = Modifier
            .padding(start = 23.dp) // align with the center of the 48dp circle
            .width(2.dp)
            .height(20.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawLine(
                color = color,
                start = Offset(size.width / 2, 0f),
                end = Offset(size.width / 2, size.height),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.cornerPathEffect(4.dp.toPx()),
                cap = StrokeCap.Round,
            )
        }
    }
}

// endregion

// region — Sparkle icon (animated)

@Composable
private fun SparkleIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "sparkle")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sparkle-rotation",
    )
    val sparkleColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(20.dp)) {
        val cx = center.x
        val cy = center.y
        val arm = size.minDimension * 0.38f
        val small = size.minDimension * 0.18f
        val strokeW = 1.8.dp.toPx()
        val radians = Math.toRadians(rotation.toDouble()).toFloat()

        // Main cross star
        drawLine(sparkleColor, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeW, StrokeCap.Round)
        drawLine(sparkleColor, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeW, StrokeCap.Round)

        // Diagonal small arms (rotate slowly)
        val cos45 = kotlin.math.cos(radians + 0.785f)
        val sin45 = kotlin.math.sin(radians + 0.785f)
        drawLine(sparkleColor, Offset(cx + small * cos45, cy + small * sin45), Offset(cx - small * cos45, cy - small * sin45), strokeW * 0.7f, StrokeCap.Round)
        drawLine(sparkleColor, Offset(cx - small * sin45, cy + small * cos45), Offset(cx + small * sin45, cy - small * cos45), strokeW * 0.7f, StrokeCap.Round)
    }
}

// endregion

// region — While you wait card

@Composable
private fun WhileYouWaitCard() {
    val tips = listOf(R.string.wait_tip_1, R.string.wait_tip_2, R.string.wait_tip_3)
    var tipIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(6000.milliseconds)
            tipIndex = (tipIndex + 1) % tips.size
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.while_you_wait),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(tips[tipIndex]),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                    lineHeight = 20.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            // Decorative plant illustration drawn via Canvas
            PlantIllustration()
        }
    }
}

/** Simple decorative plant illustration drawn with Canvas. */
@Composable
private fun PlantIllustration() {
    val leafGreen = MaterialTheme.colorScheme.primary
    val potColor = MaterialTheme.colorScheme.tertiaryContainer
    val potBorder = MaterialTheme.colorScheme.outline
    Canvas(modifier = Modifier.size(56.dp)) {
        val w = size.width
        val h = size.height
        // Pot
        val potTop = h * 0.55f
        val potW = w * 0.45f
        val potH = h * 0.35f
        drawRoundRect(
            color = potBorder,
            topLeft = Offset(center.x - potW / 2, potTop),
            size = androidx.compose.ui.geometry.Size(potW, potH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
        )
        drawRoundRect(
            color = potColor,
            topLeft = Offset(center.x - potW / 2 + 2.dp.toPx(), potTop + 2.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(potW - 4.dp.toPx(), potH - 4.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
        )
        // Stem
        val stemBottom = potTop + 2.dp.toPx()
        val stemTop = h * 0.15f
        drawLine(leafGreen, Offset(center.x, stemBottom), Offset(center.x, stemTop), 2.dp.toPx(), StrokeCap.Round)
        // Leaves (simple ovals)
        val leafSize = androidx.compose.ui.geometry.Size(w * 0.25f, h * 0.12f)
        drawOval(color = leafGreen, topLeft = Offset(center.x, h * 0.22f), size = leafSize)
        drawOval(color = leafGreen, topLeft = Offset(center.x - leafSize.width, h * 0.32f), size = leafSize)
        drawOval(color = leafGreen, topLeft = Offset(center.x + 2.dp.toPx(), h * 0.40f), size = leafSize)
    }
}

// endregion

// region — Plan details screen (unchanged)

@Composable
fun PlanDetailsScreen(plan: GeneratedStudyPlan, onBack: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.your_study_plan),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.total_minutes, plan.totalEstimatedMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            
            Text(
                text = stringResource(R.string.detected_topics),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = CardDefaults.outlinedCardBorder(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    plan.topics.forEach { topic ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${topic.order}. ${topic.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.study_blocks),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(10.dp))
            plan.blocks.forEach { block ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    border = CardDefaults.outlinedCardBorder(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = block.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Block ${block.order}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.block_minutes, block.durationMinutes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        
                        Text(
                            text = block.instructions,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        val associatedTopics = plan.topics.filter { it.id in block.topicIds }
                        if (associatedTopics.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                associatedTopics.forEach { topic ->
                                    Text(
                                        text = topic.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// endregion
