package com.devrayan.pengu.ui.theme

import android.os.Build
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDirection
import com.google.android.material.color.MaterialColors
import com.devrayan.pengu.ui.common.LocalDynamicColorSwitch
import com.devrayan.pengu.ui.common.LocalFixedColorRoles
import com.kyant.monet.LocalTonalPalettes
import com.kyant.monet.dynamicColorScheme

// Apple Human Interface Guidelines / iOS System Colors
val AppleBlueLight = Color(0xFF007AFF)
val AppleBlueDark = Color(0xFF0A84FF)
val AppleIndigoLight = Color(0xFF5856D6)
val AppleIndigoDark = Color(0xFF5E5CE6)
val AppleGreenLight = Color(0xFF34C759)
val AppleGreenDark = Color(0xFF30D158)
val AppleRedLight = Color(0xFFFF3B30)
val AppleRedDark = Color(0xFFFF453A)

val AppleLightColorScheme =
    lightColorScheme(
        primary = AppleBlueLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE5F0FF),
        onPrimaryContainer = Color(0xFF0040A8),
        inversePrimary = AppleBlueDark,
        secondary = AppleIndigoLight,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEEEEFF),
        onSecondaryContainer = Color(0xFF28248F),
        tertiary = AppleGreenLight,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE3F9E5),
        onTertiaryContainer = Color(0xFF0E561E),
        background = Color(0xFFF2F2F7), // iOS Grouped Background
        onBackground = Color(0xFF000000),
        surface = Color(0xFFFFFFFF), // iOS Card Surface
        onSurface = Color(0xFF000000),
        surfaceVariant = Color(0xFFE5E5EA), // iOS Tertiary Fill
        onSurfaceVariant = Color(0xFF3C3C43),
        surfaceTint = AppleBlueLight,
        inverseSurface = Color(0xFF1C1C1E),
        inverseOnSurface = Color(0xFFFFFFFF),
        error = AppleRedLight,
        onError = Color.White,
        errorContainer = Color(0xFFFFEBEA),
        onErrorContainer = Color(0xFF8A0A00),
        outline = Color(0xFFD1D1D6), // iOS Separator
        outlineVariant = Color(0xFFE5E5EA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF2F2F7),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFE5E5EA),
        surfaceContainerHighest = Color(0xFFD1D1D6),
    )

val AppleDarkColorScheme =
    darkColorScheme(
        primary = AppleBlueDark,
        onPrimary = Color.White,
        primaryContainer = Color(0xFF003770),
        onPrimaryContainer = Color(0xFFD0E4FF),
        inversePrimary = AppleBlueLight,
        secondary = AppleIndigoDark,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFF2E2C85),
        onSecondaryContainer = Color(0xFFE3E2FF),
        tertiary = AppleGreenDark,
        onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF0A4F1D),
        onTertiaryContainer = Color(0xFFC7FCD0),
        background = Color(0xFF000000), // iOS Pure OLED Black
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF1C1C1E), // iOS Card / Secondary Grouped Background
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF2C2C2E), // iOS Tertiary Fill
        onSurfaceVariant = Color(0xFFEBEBF5),
        surfaceTint = AppleBlueDark,
        inverseSurface = Color(0xFFF2F2F7),
        inverseOnSurface = Color(0xFF000000),
        error = AppleRedDark,
        onError = Color.White,
        errorContainer = Color(0xFF5A0000),
        onErrorContainer = Color(0xFFFFB4AB),
        outline = Color(0xFF38383A), // iOS Dark Separator
        outlineVariant = Color(0xFF48484A),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF121214),
        surfaceContainer = Color(0xFF1C1C1E),
        surfaceContainerHigh = Color(0xFF2C2C2E),
        surfaceContainerHighest = Color(0xFF3A3A3C),
    )

val AppleLightFixedColorRoles =
    FixedColorRoles(
        primaryFixed = AppleBlueLight,
        primaryFixedDim = AppleBlueDark,
        onPrimaryFixed = Color.White,
        onPrimaryFixedVariant = Color(0xFFE5F0FF),
        secondaryFixed = AppleIndigoLight,
        secondaryFixedDim = AppleIndigoDark,
        onSecondaryFixed = Color.White,
        onSecondaryFixedVariant = Color(0xFFEEEEFF),
        tertiaryFixed = AppleGreenLight,
        tertiaryFixedDim = AppleGreenDark,
        onTertiaryFixed = Color.White,
        onTertiaryFixedVariant = Color(0xFFE3F9E5),
    )

val AppleDarkFixedColorRoles =
    FixedColorRoles(
        primaryFixed = AppleBlueDark,
        primaryFixedDim = AppleBlueLight,
        onPrimaryFixed = Color.White,
        onPrimaryFixedVariant = Color(0xFF003770),
        secondaryFixed = AppleIndigoDark,
        secondaryFixedDim = AppleIndigoLight,
        onSecondaryFixed = Color.White,
        onSecondaryFixedVariant = Color(0xFF2E2C85),
        tertiaryFixed = AppleGreenDark,
        tertiaryFixedDim = AppleGreenLight,
        onTertiaryFixed = Color.Black,
        onTertiaryFixedVariant = Color(0xFF0A4F1D),
    )

fun Color.applyOpacity(enabled: Boolean): Color {
    return if (enabled) this else this.copy(alpha = 0.62f)
}

@Composable
@ReadOnlyComposable
fun Color.harmonizeWith(other: Color) =
    Color(MaterialColors.harmonize(this.toArgb(), other.toArgb()))

@Composable
@ReadOnlyComposable
fun Color.harmonizeWithPrimary(): Color =
    this.harmonizeWith(other = MaterialTheme.colorScheme.primary)

@Composable
fun PenguTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isHighContrastModeEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current

    LaunchedEffect(darkTheme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (darkTheme) {
                view.windowInsetsController?.setSystemBarsAppearance(
                    0,
                    APPEARANCE_LIGHT_STATUS_BARS,
                )
            } else {
                view.windowInsetsController?.setSystemBarsAppearance(
                    APPEARANCE_LIGHT_STATUS_BARS,
                    APPEARANCE_LIGHT_STATUS_BARS,
                )
            }
        }
    }

    val isDynamic = LocalDynamicColorSwitch.current
    val colorScheme =
        if (isDynamic) {
            dynamicColorScheme(!darkTheme).run {
                if (isHighContrastModeEnabled && darkTheme)
                    copy(
                        surface = Color.Black,
                        background = Color.Black,
                        surfaceContainerLowest = Color.Black,
                        surfaceContainerLow = surfaceContainerLowest,
                        surfaceContainer = surfaceContainerLow,
                        surfaceContainerHigh = surfaceContainerLow,
                        surfaceContainerHighest = surfaceContainer,
                    )
                else this
            }
        } else {
            if (darkTheme) {
                AppleDarkColorScheme.run {
                    if (isHighContrastModeEnabled)
                        copy(
                            surface = Color.Black,
                            background = Color.Black,
                            surfaceContainerLowest = Color.Black,
                            surfaceContainerLow = Color.Black,
                            surfaceContainer = Color(0xFF121214),
                            surfaceContainerHigh = Color(0xFF1C1C1E),
                            surfaceContainerHighest = Color(0xFF2C2C2E),
                        )
                    else this
                }
            } else {
                AppleLightColorScheme
            }
        }

    val textStyle =
        LocalTextStyle.current.copy(
            lineBreak = LineBreak.Paragraph,
            textDirection = TextDirection.Content,
        )

    val fixedColorRoles =
        if (isDynamic) {
            FixedColorRoles.fromTonalPalettes(LocalTonalPalettes.current)
        } else {
            if (darkTheme) AppleDarkFixedColorRoles else AppleLightFixedColorRoles
        }

    CompositionLocalProvider(
        LocalFixedColorRoles provides fixedColorRoles,
        LocalTextStyle provides textStyle,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}

@Composable
fun SealTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isHighContrastModeEnabled: Boolean = false,
    content: @Composable () -> Unit,
) = PenguTheme(darkTheme = darkTheme, isHighContrastModeEnabled = isHighContrastModeEnabled, content = content)

@Composable
@Deprecated("Use PenguTheme instead", replaceWith = ReplaceWith("PenguTheme(content)"))
fun PreviewThemeLight(content: @Composable () -> Unit) {
    PenguTheme(darkTheme = false, content = content)
}
