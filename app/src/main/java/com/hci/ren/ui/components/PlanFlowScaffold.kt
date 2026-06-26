package com.hci.ren.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hci.ren.R
import com.hci.ren.ui.motion.RenMotionDurationMillis
import com.hci.ren.ui.motion.RenMotionEasing
import com.hci.ren.ui.theme.RenGreenDark

@Composable
fun PlanFlowScaffold(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    stepLabel: String? = null,
    bottomContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    PlanShellScaffold(
        modifier = modifier,
        applyNavigationPadding = true,
        topContent = {
            PlanFlowTopRow(
                onBack = onBack,
                progress = progress,
                stepLabel = stepLabel,
            )
        },
        bottomContent = bottomContent,
        content = content,
    )
}

@Composable
fun PlanLandingScaffold(
    modifier: Modifier = Modifier,
    bottomContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    PlanShellScaffold(
        modifier = modifier,
        applyNavigationPadding = true,
        topContent = null,
        bottomContent = bottomContent,
        content = content,
    )
}

@Composable
private fun PlanShellScaffold(
    modifier: Modifier = Modifier,
    applyNavigationPadding: Boolean,
    topContent: (@Composable () -> Unit)?,
    bottomContent: @Composable ColumnScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .then(if (applyNavigationPadding) Modifier.navigationBarsPadding() else Modifier)
                .imePadding()
                .padding(horizontal = PlanFlowHorizontalPadding),
        ) {
            Spacer(Modifier.height(PlanFlowEdgePadding))

            if (topContent != null) {
                topContent()
                Spacer(Modifier.height(PlanFlowContentGap))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                content = content,
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = bottomContent,
            )

            Spacer(Modifier.height(PlanFlowEdgePadding))
        }
    }
}

@Composable
fun PlanFlowIntro(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    titleFontWeight: FontWeight = FontWeight.Bold,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(PlanFlowIntroTextGap),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = titleFontWeight,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = MaterialTheme.typography.headlineSmall.lineHeight,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun planFlowPrimaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        RenGreenDark
    } else {
        MaterialTheme.colorScheme.primary
    },
    contentColor = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
fun PlanFlowPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    testTag: String? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(PlanFlowControlHeight)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        colors = planFlowPrimaryButtonColors(),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun PlanFlowCircleAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector = Icons.AutoMirrored.Filled.ArrowForward,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(60.dp),
        shape = CircleShape,
        color = if (enabled) planFlowActionColor() else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
fun planFlowActionColor() =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        RenGreenDark
    } else {
        MaterialTheme.colorScheme.primary
    }

@Composable
private fun PlanFlowTopRow(
    onBack: () -> Unit,
    progress: Float?,
    stepLabel: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PlanFlowTopRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.offset(x = (-12).dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (progress != null) {
            Spacer(Modifier.width(8.dp))
            val animatedProgress by animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = tween(RenMotionDurationMillis, easing = RenMotionEasing),
                label = "plan-flow-progress",
            )
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            )

            if (stepLabel != null) {
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stepLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

val PlanFlowControlHeight = 58.dp
private val PlanFlowTopRowHeight = 48.dp
val PlanFlowHorizontalPadding = 20.dp
val PlanFlowEdgePadding = 12.dp
val PlanFlowContentGap = 18.dp
val PlanFlowIntroTextGap = 14.dp
val PlanFlowSectionGap = 30.dp
