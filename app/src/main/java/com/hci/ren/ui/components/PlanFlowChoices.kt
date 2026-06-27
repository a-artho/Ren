package com.hci.ren.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hci.ren.ui.motion.RenMotionDurationMillis
import com.hci.ren.ui.motion.renEnterSpec
import com.hci.ren.ui.motion.renExitSpec

@Composable
fun PlanFlowOptionRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    val targetBorderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    }
    val targetBackground = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(RenMotionDurationMillis),
        label = "plan-option-border",
    )
    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(RenMotionDurationMillis),
        label = "plan-option-background",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = background,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon == null) {
                RadioButton(
                    selected = isSelected,
                    onClick = null,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(18.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected && subtitle != null) {
                        FontWeight.Bold
                    } else {
                        FontWeight.SemiBold
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Surface(
                        modifier = Modifier.size(26.dp),
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(
                            1.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f),
                        ),
                    ) {}
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn(renEnterSpec()) + scaleIn(renEnterSpec(), initialScale = 0.9f),
                    exit = fadeOut(renExitSpec()) + scaleOut(renExitSpec(), targetScale = 0.9f),
                ) {
                    Surface(
                        modifier = Modifier.size(28.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlanFlowCircleChoice(
    label: String,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetBorderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    }
    val targetTextColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val targetBackgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor by animateColorAsState(targetBorderColor, tween(RenMotionDurationMillis), label = "plan-circle-choice-border")
    val textColor by animateColorAsState(targetTextColor, tween(RenMotionDurationMillis), label = "plan-circle-choice-text")
    val backgroundColor by animateColorAsState(targetBackgroundColor, tween(RenMotionDurationMillis), label = "plan-circle-choice-background")

    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .toggleable(value = isSelected, role = Role.Checkbox, onValueChange = { onClick() })
            .semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun PlanFlowPillChoice(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetBorderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    }
    val targetBackgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val targetTextColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor by animateColorAsState(targetBorderColor, tween(RenMotionDurationMillis), label = "plan-pill-choice-border")
    val backgroundColor by animateColorAsState(targetBackgroundColor, tween(RenMotionDurationMillis), label = "plan-pill-choice-background")
    val textColor by animateColorAsState(targetTextColor, tween(RenMotionDurationMillis), label = "plan-pill-choice-text")

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(42.dp)
            .semantics { selected = isSelected },
        shape = CircleShape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                color = textColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}
