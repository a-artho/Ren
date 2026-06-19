package com.hci.ren.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hci.ren.R

private val RenFontFamily = FontFamily(
    Font(R.font.manrope, weight = FontWeight.Normal),
    Font(R.font.manrope, weight = FontWeight.Medium),
    Font(R.font.manrope, weight = FontWeight.SemiBold),
    Font(R.font.manrope, weight = FontWeight.Bold)
)

private fun renTextStyle(
    fontWeight: FontWeight,
    fontSize: Int,
    lineHeight: Int
) = TextStyle(
    fontFamily = RenFontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp
)

val Typography = Typography(
    displaySmall = renTextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32,
        lineHeight = 38
    ),
    headlineSmall = renTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24,
        lineHeight = 30
    ),
    titleLarge = renTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20,
        lineHeight = 26
    ),
    titleMedium = renTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16,
        lineHeight = 22
    ),
    bodyLarge = renTextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16,
        lineHeight = 24
    ),
    bodyMedium = renTextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14,
        lineHeight = 20
    ),
    labelLarge = renTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14,
        lineHeight = 20
    ),
    labelMedium = renTextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12,
        lineHeight = 16
    )
)
