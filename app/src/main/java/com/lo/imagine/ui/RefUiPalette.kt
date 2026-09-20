package com.lo.imagine.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

internal data class FrostedActionPalette(
    val top: Color, val body: Color, val bottom: Color,
    val edge: Color, val ink: Color, val shine: Color
)

internal fun frostedActionPalette(colors: ColorScheme): FrostedActionPalette {
    val dark = colors.background.luminance() < .5f
    return FrostedActionPalette(
        top = lerp(colors.surface, colors.primaryContainer, if (dark) .24f else .10f).copy(alpha = .96f),
        body = lerp(colors.surface, colors.primaryContainer, if (dark) .32f else .22f).copy(alpha = .92f),
        bottom = lerp(colors.surface, colors.primaryContainer, if (dark) .44f else .36f).copy(alpha = .96f),
        edge = colors.primary,
        ink = colors.onSurface,
        shine = if (dark) colors.primary.copy(alpha = .25f) else Color.White
    )
}
