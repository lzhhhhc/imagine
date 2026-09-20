package com.lo.imagine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import com.lo.imagine.data.AspectOption

/** 在画幅选择项左侧展示与实际比例对应的几何框。 */
@Composable
fun AspectGeometry(
    aspect: AspectOption,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val strokeColor = color ?: MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.size(22.dp)) {
        val strokeWidth = 1.55.dp.toPx()
        val fitted = fitAspectFrame(aspect.ratioW / aspect.ratioH, size.width, size.height, strokeWidth)
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset((size.width - fitted.width) / 2f, (size.height - fitted.height) / 2f),
            size = fitted,
            cornerRadius = CornerRadius(1.5.dp.toPx()),
            style = Stroke(width = strokeWidth)
        )
    }
}

/** Exact aspect ratio with half-stroke clearance on each side, including square and ultra-wide. */
internal fun fitAspectFrame(ratio: Float, width: Float, height: Float, stroke: Float): Size {
    require(ratio.isFinite() && ratio > 0f)
    val availableWidth = (width - stroke).coerceAtLeast(0f)
    val availableHeight = (height - stroke).coerceAtLeast(0f)
    val frameWidth = minOf(availableWidth, availableHeight * ratio)
    return Size(frameWidth, frameWidth / ratio)
}
