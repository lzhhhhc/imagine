package com.lo.imagine.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lo.imagine.R
import com.lo.imagine.ui.theme.RefHud

/** The supplied long-frame design, redrawn as responsive geometry rather than a stretched bitmap. */
@Composable
internal fun FrostedActionButton(
    loading: Boolean,
    enabled: Boolean,
    label: String,
    sub: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = frostedActionPalette(MaterialTheme.colorScheme)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(110), label = "glassPress")
    val shape = CutCornerShape(11.dp)
    val scaledType = LocalDensity.current.fontScale > 1.3f
    Surface(
        onClick = onClick, enabled = enabled, interactionSource = interaction,
        color = Color.Transparent, contentColor = palette.ink, shape = shape,
        shadowElevation = if (pressed) 1.dp else 3.dp,
        border = BorderStroke(1.dp, palette.edge.copy(alpha = if (enabled) .85f else .34f)),
        modifier = modifier.heightIn(min = 64.dp).semantics {
            if (loading) stateDescription = "正在生成，点按取消"
            else if (!enabled) stateDescription = "填写提示词后可生成"
        }
    ) {
        Box(Modifier.fillMaxWidth().drawWithCache {
            val inset = 3.dp.toPx()
            val corner = 8.dp.toPx()
            fun frame(d: Float): Path = Path().apply {
                moveTo(d + corner, d); lineTo(size.width - d - corner, d)
                lineTo(size.width - d, d + corner); lineTo(size.width - d, size.height - d - corner)
                lineTo(size.width - d - corner, size.height - d); lineTo(d + corner, size.height - d)
                lineTo(d, size.height - d - corner); lineTo(d, d + corner); close()
            }
            val outline = frame(inset)
            val frost = Brush.linearGradient(
                listOf(palette.top, palette.body, palette.bottom), Offset.Zero, Offset(size.width * .72f, size.height)
            )
            val glint = Brush.verticalGradient(listOf(palette.shine.copy(alpha = .36f * palette.shine.alpha), Color.Transparent),
                0f, size.height * .55f)
            // A deterministic microscopic grain keeps the finish frosted without a ticking animation.
            val grain = List(280) { i ->
                Offset(((i * 73 + 17) % 997) / 997f * size.width, ((i * 139 + 37) % 991) / 991f * size.height)
            }
            onDrawBehind {
                drawRect(frost)
                drawRect(glint)
                clipPath(outline) {
                    grain.forEachIndexed { index, point ->
                        drawCircle(if (index % 3 == 0) palette.edge.copy(alpha = .045f) else palette.shine.copy(alpha = .17f * palette.shine.alpha),
                            .42.dp.toPx(), point)
                    }
                }
                drawPath(outline, palette.shine.copy(alpha = .66f * palette.shine.alpha), style = Stroke(.75.dp.toPx()))
                val y1 = 8.dp.toPx()
                val y2 = size.height - y1
                val x1 = 18.dp.toPx()
                val x2 = size.width - x1
                drawLine(palette.edge.copy(alpha = .25f), Offset(x1, y1), Offset(size.width * .45f, y1), .6.dp.toPx())
                drawLine(palette.edge.copy(alpha = .2f), Offset(size.width * .55f, y2), Offset(x2, y2), .6.dp.toPx())
                drawLine(palette.edge, Offset(5.dp.toPx(), size.height - 14.dp.toPx()), Offset(12.dp.toPx(), size.height - 7.dp.toPx()), 2.dp.toPx())
                drawLine(palette.edge, Offset(size.width - 12.dp.toPx(), 7.dp.toPx()), Offset(size.width - 5.dp.toPx(), 14.dp.toPx()), 2.dp.toPx())
                if (press > 0f) drawRect(palette.edge.copy(alpha = .09f * press))
                if (!enabled) drawRect(palette.body.copy(alpha = .42f))
            }
        }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(if (scaledType) 30.dp else 36.dp), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(Modifier.size(26.dp), color = palette.ink, strokeWidth = 1.8.dp)
                    else androidx.compose.material3.Icon(painterResource(R.drawable.ic_creation_emblem), null,
                        Modifier.size(if (scaledType) 28.dp else 34.dp), tint = palette.edge)
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(.7.dp).height(32.dp).background(palette.edge.copy(alpha = .26f)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = palette.ink.copy(alpha = if (enabled) 1f else .55f),
                        fontSize = if (loading) 13.sp else 16.sp,
                        lineHeight = if (loading) 18.sp else 22.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    if (!scaledType) {
                        Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GlassRule(palette.edge, Modifier.weight(1f).height(8.dp))
                            Text(sub, color = palette.ink.copy(alpha = if (enabled) .82f else .45f),
                                fontFamily = RefHud, fontSize = 9.sp, lineHeight = 12.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 132.dp))
                            GlassRule(palette.edge, Modifier.weight(1f).height(8.dp))
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                GlassArrow(loading, palette.edge.copy(alpha = if (enabled) 1f else .45f), palette.shine)
            }
        }
    }
}

@Composable
private fun GlassRule(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val mid = size.height / 2
        drawLine(color.copy(alpha = .28f), Offset(0f, mid), Offset(size.width, mid), .6.dp.toPx())
        val r = 1.6.dp.toPx()
        val x = size.width / 2
        drawPath(Path().apply { moveTo(x, mid - r); lineTo(x + r, mid); lineTo(x, mid + r); lineTo(x - r, mid); close() }, color.copy(alpha = .7f))
    }
}

@Composable
private fun GlassArrow(loading: Boolean, color: Color, highlight: Color) {
    Canvas(Modifier.size(42.dp)) {
        val d = 4.dp.toPx()
        val radius = CornerRadius(5.dp.toPx())
        drawRoundRect(highlight.copy(alpha = .14f * highlight.alpha), Offset(d, d), Size(size.width - d * 2, size.height - d * 2), radius)
        drawRoundRect(color.copy(alpha = .76f), Offset(d, d), Size(size.width - d * 2, size.height - d * 2), radius,
            style = Stroke(.9.dp.toPx()))
        val bracket = 5.dp.toPx()
        for ((x, y, dx, dy) in listOf(
            listOf(0f, 0f, 1f, 1f), listOf(size.width, 0f, -1f, 1f),
            listOf(0f, size.height, 1f, -1f), listOf(size.width, size.height, -1f, -1f)
        )) {
            drawLine(color.copy(alpha = .65f), Offset(x, y + dy * bracket), Offset(x, y), 1.dp.toPx())
            drawLine(color.copy(alpha = .65f), Offset(x, y), Offset(x + dx * bracket, y), 1.dp.toPx())
        }
        val cx = size.width / 2
        val cy = size.height / 2
        val r = 6.dp.toPx()
        val stroke = 1.8.dp.toPx()
        if (loading) {
            drawLine(color, Offset(cx - r, cy - r), Offset(cx + r, cy + r), stroke, StrokeCap.Square)
            drawLine(color, Offset(cx + r, cy - r), Offset(cx - r, cy + r), stroke, StrokeCap.Square)
        } else {
            drawLine(color, Offset(cx - r * 1.3f, cy), Offset(cx + r * 1.2f, cy), stroke, StrokeCap.Square)
            drawPath(Path().apply { moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r) }, color,
                style = Stroke(stroke, cap = StrokeCap.Square))
        }
    }
}
