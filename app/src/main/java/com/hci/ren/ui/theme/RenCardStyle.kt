package com.hci.ren.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun renCardContainerColor(): Color = RenCardSurface

@Composable
fun renCardBorderColor(): Color = RenCardOutline

@Composable
fun renCardBorderStroke(): BorderStroke = BorderStroke(1.dp, renCardBorderColor())

@Composable
fun renMutedIconColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)

@Composable
fun renSelectedBorderColor(): Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.58f)
