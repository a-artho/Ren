package com.hci.ren.feature.home.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import com.hci.ren.ui.motion.RenMotionEasing
import com.hci.ren.ui.motion.isReducedMotionEnabled

@Composable
fun EmptyHomeContent(
    onUploadPdf: () -> Unit,
    onUseSampleMaterial: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        UploadCard(
            onUploadPdf = onUploadPdf,
            onUseSampleMaterial = onUseSampleMaterial,
        )
        BenefitsRow()
    }
}

@Composable
private fun UploadCard(
    onUploadPdf: () -> Unit,
    onUseSampleMaterial: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = isReducedMotionEnabled()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 0.98f else 1f,
        animationSpec = tween(120, easing = RenMotionEasing),
        label = "upload-card-press",
    )
    Card(
        modifier = Modifier.fillMaxWidth().scale(scale),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UploadVisual()
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Upload your first study material",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Turn a PDF chapter, lecture note, or exam material into a focused study map.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onUploadPdf,
                modifier = Modifier.fillMaxWidth(),
                interactionSource = interactionSource,
            ) {
                Text(
                    text = "Upload PDF",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(onClick = onUseSampleMaterial) {
                Text(
                    text = "Try sample material",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun UploadVisual() {
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer
    Box(
        modifier = Modifier
            .size(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(color = container)
            val centerX = size.width / 2f
            val top = size.height * 0.27f
            val bottom = size.height * 0.68f
            drawLine(
                color = primary,
                start = Offset(centerX, bottom),
                end = Offset(centerX, top),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = primary,
                start = Offset(centerX, top),
                end = Offset(size.width * 0.36f, size.height * 0.43f),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = primary,
                start = Offset(centerX, top),
                end = Offset(size.width * 0.64f, size.height * 0.43f),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawArc(
                color = primary,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(size.width * 0.27f, size.height * 0.55f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.46f, size.height * 0.22f),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun BenefitsRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BenefitCard(
            title = "Extract topics",
            description = "AI finds key topics and sections.",
            modifier = Modifier.weight(1f),
        )
        BenefitCard(
            title = "Create sessions",
            description = "Turns topics into focused sessions.",
            modifier = Modifier.weight(1f),
        )
        BenefitCard(
            title = "Track progress",
            description = "Monitor learning and stay on track.",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BenefitCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
