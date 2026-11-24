@file:OptIn(ExperimentalTextApi::class)

package com.devrayan.pengu.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp

/**
 * Apple SF Pro-inspired typography hierarchy with crisp weights and tracking
 */
val Typography =
    Typography().run {
        copy(
            displayLarge = displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp).applyTextDirection(),
            displayMedium = displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp).applyTextDirection(),
            displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp).applyTextDirection(),
            headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp).applyTextDirection(),
            headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp).applyTextDirection(),
            headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp).applyTextDirection(),
            titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp).applyTextDirection(),
            titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp).applyTextDirection(),
            titleSmall = titleSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.1).sp).applyTextDirection(),
            bodyLarge = bodyLarge.copy(fontWeight = FontWeight.Normal, letterSpacing = (-0.1).sp).applyLinebreak().applyTextDirection(),
            bodyMedium = bodyMedium.copy(fontWeight = FontWeight.Normal).applyLinebreak().applyTextDirection(),
            bodySmall = bodySmall.copy(fontWeight = FontWeight.Normal).applyLinebreak().applyTextDirection(),
            labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold).applyTextDirection(),
            labelMedium = labelMedium.copy(fontWeight = FontWeight.SemiBold).applyTextDirection(),
            labelSmall = labelSmall.copy(fontWeight = FontWeight.Medium).applyTextDirection(),
        )
    }

private fun TextStyle.applyLinebreak(): TextStyle = this.copy(lineBreak = LineBreak.Paragraph)

private fun TextStyle.applyTextDirection(): TextStyle =
    this.copy(textDirection = TextDirection.Content)
