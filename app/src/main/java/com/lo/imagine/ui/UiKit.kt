package com.lo.imagine.ui
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

import com.lo.imagine.ui.theme.PopRadius

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.lo.imagine.R
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lo.imagine.data.AspectOption
import com.lo.imagine.data.ImageData
import com.lo.imagine.ui.theme.LocalPopAccents
import com.lo.imagine.ui.theme.Moss
import com.lo.imagine.ui.theme.SignalSoft
import com.lo.imagine.util.ImageUtils

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    /**
     * 左上角标题的自定义内容：传了就**替换**标题文字（例如「创作 / NAI」模式切换按钮）。
     * 三段主题（默认 / SKY / 罗德岛）都支持，避免某个主题下控件消失。
     */
    titleOverride: (@Composable () -> Unit)? = null
) {
    // 罗德岛终端：始终使用专属页头
    ArkScreenHeader(title, subtitle, action, titleOverride)
}

@Composable
private fun ArkScreenHeader(
    title: String,
    subtitle: String?,
    action: (@Composable () -> Unit)?,
    titleOverride: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val (pageEn, pageIndex) = when (title) {
        "标准", "NAI", "ComfyUI" -> "STUDIO" to 1
        "修图" -> "RETOUCH" to 2
        "导演台" -> "DIRECTOR" to 3
        "作品", "作品库", "历史" -> "GALLERY" to 4
        "设置" -> "SETTINGS" to 5
        else -> title.uppercase() to 1
    }
    val route = when (pageIndex) { 2 -> "edit"; 3 -> "director"; 4 -> "works"; 5 -> "settings"; else -> "studio" }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Reference coordinates scale with phone width; OS status-bar clearance is separate.
        val ratio = if (pageIndex == 3) .59f else .43f
        val textScale = LocalDensity.current.fontScale.coerceIn(1f, 1.7f)
        val stackedModeActions = titleOverride != null && (maxWidth < 430.dp || LocalDensity.current.fontScale > 1.1f)
        val heroHeight = (maxWidth * ratio).coerceIn(156.dp, 230.dp) + (30.dp * (textScale - 1f)) + if (stackedModeActions) 56.dp else 0.dp
        val safeTop = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(Modifier.fillMaxWidth().height(heroHeight)) {
            ArkHeroArtwork(route, Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().padding(top = safeTop).padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("RHODES ISLAND", fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = .6.sp, color = scheme.onSurface)
                        Text("· ONLINE", fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = .7.sp, color = scheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("%02d / 05".format(pageIndex), fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 1.sp, color = scheme.onSurfaceVariant)
                        Text(pageEn, fontSize = 7.sp, lineHeight = 10.sp, letterSpacing = .6.sp, color = scheme.onSurfaceVariant)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Text(when(pageIndex) {
                        2 -> "REFINE\nEVERY DETAIL."
                        3 -> "PEOPLE\nIDEAS\nYOUR STORY."
                        5 -> "CREATE\nA BRIGHTER\nTOMORROW."
                        else -> "IMAGINE\nANYTHING."
                    }, fontSize = 6.sp, lineHeight = 8.sp, letterSpacing = 1.sp,
                        color = scheme.onSurfaceVariant, modifier = Modifier.align(Alignment.TopStart).padding(top = 12.dp))
                    if (action != null && pageIndex == 3) {
                        Box(Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp)) { action() }
                    }
                }
                if (titleOverride != null) {
                    if (stackedModeActions) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            titleOverride()
                            Box(Modifier.align(Alignment.End)) { action?.invoke() }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            titleOverride()
                            Spacer(Modifier.weight(1f))
                            action?.invoke()
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(title, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold,
                                color = scheme.onBackground)
                            Text(pageEn, fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold,
                                letterSpacing = .7.sp, color = scheme.primary)
                            if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 11.sp, lineHeight = 15.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.onSurface)
                        }
                        if (pageIndex != 3) action?.invoke()
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(34.dp).height(1.5.dp).background(scheme.primary))
                    Box(Modifier.weight(1f).height(.6.dp).background(scheme.outlineVariant.copy(alpha=.6f)))
                    repeat(4) { n ->
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.width(if (n == 0) 19.dp else 6.dp).height(1.5.dp)
                            .background(if (n == 0) scheme.primary else scheme.outlineVariant))
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
fun SectionTitle(
    title: String,
    hint: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionMarker()
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (!hint.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
        action?.invoke()
    }
}

/**
 * 分组标题标记（每个主题一套「卷首符」）：
 * 波普三色网点、深夜竖光条、像素方块序列、包豪斯黑方块、动漫粉点、罗德岛切角块。
 * 之前只有包豪斯有分支，其余主题共用一颗红点——这是“主题之间不像一套系统”的主要来源之一。
 */
@Composable
private fun SectionMarker() {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(13.dp, 8.dp).background(scheme.primary, com.lo.imagine.ui.theme.ArkSlantShape(3.dp)))
    Spacer(Modifier.width(5.dp))
    Text("//", fontSize = 9.sp, lineHeight = 12.sp, color = scheme.primary)
    Spacer(Modifier.width(6.dp))
}

/** 赛璐璐硬投影：无模糊的偏移色块阴影，贴纸质感。亮色主题纯黑高对比，暗色主题降透明防糊。 */
fun Modifier.celShadow(
    shape: Shape,
    dx: Dp = 3.dp,
    dy: Dp = 3.dp
): Modifier = drawBehind {
    // 阴影色：亮主题用近黑纯墨，暗主题用半透明黑防"黑洞"感
    val ink = Color(0xFF17120A)
    translate(dx.toPx(), dy.toPx()) {
        drawOutline(
            outline = shape.createOutline(size, layoutDirection, this),
            color = ink,
            alpha = .85f
        )
    }
}

/**
 * 终端边线自适应：浅色用中性冷灰，深色用低亮度结构线，避免固定灰在两套主题里都显得生硬。
 */
@Composable
fun celInk(): Color {
    return MaterialTheme.colorScheme.outline.copy(alpha = .92f)
}

/**
 * 主行动按钮（CTA）的主题化描边：罗德岛终端深灰外框，边角由主题切角形状完成。
 */
@Composable
fun popCtaBorder(): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(1.5.dp, celInk())
}

/**
 * 主题化背景：罗德岛终端底纹装饰。
 */
@Composable
fun Modifier.popBackdrop(): Modifier {
    val flavor = com.lo.imagine.ui.theme.LocalFlavor.current
    val scheme = MaterialTheme.colorScheme
    // DrawScope 里不能访问 CompositionLocal：先在 Composable 主体取出主题强调色
    val popA = LocalPopAccents.current.a
    val popB = LocalPopAccents.current.b
    val popC = LocalPopAccents.current.c
    val popD = LocalPopAccents.current.d
    // 罗德岛底纹用的幽灵文字画笔（DrawScope 内不便构造，提前在 Composable 主体造好）
    val hudGhost = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(11, 200, 240, 255)
        textAlign = android.graphics.Paint.Align.LEFT
        typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
        letterSpacing = 0.24f
    }
    val backdropTransition = rememberInfiniteTransition(label = "backdropMotion")
    val arkSweep by backdropTransition.animateFloat(
        initialValue = -.16f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "arkBackdropSweep"
    )
    val arkPulse by backdropTransition.animateFloat(
        initialValue = .42f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(760, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "arkBackdropPulse"
    )
    // 上升粒子流：数据沿终端回路向上传输（低速、低透明，只添科技感不抢内容）
    val particleRise by backdropTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
        label = "arkParticleRise"
    )
    // 官网式错峰扫描：第二道光束延迟半周期，避免整屏只有一条机械匀速光带
    val arkSweepSecondary = (arkSweep + .5f).let { if (it > 1.16f) it - 1.32f else it }
    // 判断明暗模式：用 background 的亮度
    val isDark = scheme.background.luminance() < 0.5f
    return drawBehind {
        val w = size.width
        val h = size.height
        // 罗德岛终端底纹：根据明暗模式切换颜色
        // 暗色版：近黑底 + 细网格 + 标志青点缀（#2AC4E3）
        // 浅色版：冷白底 + 深青（#176F95），面板不透明、层级靠明度差
        val hudAccent = if (isDark) Color(0xFF2AC4E3) else Color(0xFF176F95)
        val lime = if (isDark) Color(0xFFE5C44B) else Color(0xFF8A6800)   // 罗德岛黄（官方加载页线框色），明暗各取一档
        val ink = if (isDark) Color(0xFF6A7075) else Color(0xFF1A1D1F)  // 暗版用亮灰、浅版用深灰
        // 0) 斜向菱形网格（官网 WORLD/ABOUT TERRA 灵魂：暗版用纯白细线，不发灰）
        val gridColor = if (isDark) Color.White.copy(alpha = .075f) else ink.copy(alpha = .065f)
        val gridSlope = 1.15f
        val gridGap = w * .16f
        var gridX = -h * gridSlope
        while (gridX < w) {
            drawLine(gridColor, Offset(gridX, 0f), Offset(gridX + h * gridSlope, h), 1f)
            drawLine(gridColor, Offset(gridX + h * gridSlope, 0f), Offset(gridX, h), 1f)
            gridX += gridGap
        }
        // 0.5) 右侧全高竖线（官网内容区/边栏分隔线）
        drawLine(if (isDark) Color.White.copy(alpha = .16f) else ink.copy(alpha = .14f), Offset(w * .885f, 0f), Offset(w * .885f, h), 1.2f)
        // ===== 官方 HUD 语法：元素全部退到边角，中间留白 =====

        // 1) 星点（仅暗版）：稀疏、低透明、固定伪随机——官方加载页的星空底
        if (isDark) {
            for (i in 0 until 26) {
                val sx = ((i * 37 + 13) % 97) / 97f * w
                val sy = ((i * 61 + 7) % 89) / 89f * h
                drawCircle(ink.copy(alpha = .30f), 1f, Offset(sx, sy))
            }
        }

        // 2) 横贯基准线（唯一一处黄：官方加载页灵魂线；浅底压淡，避免黄+白=米色糊成一片）
        val lineY = h * .5f
        drawLine(lime.copy(alpha = if (isDark) .22f else .14f), Offset(0f, lineY), Offset(w, lineY), 1f)
        drawLine(lime.copy(alpha = if (isDark) .80f else .34f), Offset(0f, lineY), Offset(w * .14f, lineY), 3f)

        // 3) 右上三段断线（信号强度语言：用标志青，全局只留一个强调色族）
        for (i in 0..2) {
            val xEnd = w - 14f - i * 20f
            drawLine(
                hudAccent.copy(alpha = (if (isDark) .60f else .42f) * (1f - i * .26f)),
                Offset(xEnd - 14f, h * .045f),
                Offset(xEnd, h * .045f),
                2.2f
            )
        }

        // 4) NO INFO 幽灵档案框（退到左下可见区，避开底部 Dock 遮挡）
        drawRect(
            color = ink.copy(alpha = if (isDark) .28f else .16f),
            topLeft = Offset(w * .06f, h * .70f),
            size = Size(w * .17f, h * .055f),
            style = Stroke(width = 1f)
        )
        hudGhost.let { p ->
            p.color = if (isDark) android.graphics.Color.argb(40, 106, 112, 117) else android.graphics.Color.argb(26, 26, 29, 31)
            p.textSize = (w * .019f).coerceAtMost(20f)
            drawContext.canvas.nativeCanvas.drawText("NO INFO/", w * .072f, h * .733f, p)
        }

        // 5) 版本锚点（官网角落语言：信息框正下方的版本号）
        hudGhost.let { p ->
            p.color = if (isDark) android.graphics.Color.argb(110, 255, 216, 0) else android.graphics.Color.argb(70, 160, 130, 0)
            p.textSize = (w * .019f).coerceAtMost(20f)
            drawContext.canvas.nativeCanvas.drawText("VER 1.0 //", w * .072f, h * .79f, p)
        }

        // 6) 错峰扫描带：两道窄光束缓慢穿过终端，第二道半周期错开
        fun drawScanBeam(progress: Float, alpha: Float, width: Float) {
            val center = w * progress
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, hudAccent.copy(alpha = alpha), Color.Transparent),
                    startX = center - width,
                    endX = center + width
                ),
                topLeft = Offset(center - width, 0f),
                size = Size(width * 2f, h)
            )
        }
        drawScanBeam(arkSweep, if (isDark) .045f else .028f, w * .12f)
        drawScanBeam(arkSweepSecondary, if (isDark) .026f else .016f, w * .07f)
        // 基准线只做很小的亮度呼吸，不改变整体配色重心
        drawLine(
            lime.copy(alpha = (if (isDark) .16f else .10f) + arkPulse * .07f),
            Offset(0f, lineY), Offset(w * .14f, lineY), 3f
        )
        // 7) 上升粒子流：数据包沿终端回路向上传输，尾迹渐变消隐
        val particleColor = hudAccent
        for (i in 0 until 8) {
            // 相位错开：8 颗粒子分布在 0..1 周期内，形成连续流
            val phase = (particleRise + i / 8f) % 1f
            val px = ((i * 61 + 23) % 89) / 89f * w  // 伪随机横向散布
            // 底部稍慢、顶部稍快，带轻微横向漂移
            val py = h - phase * h
            val drift = kotlin.math.sin(phase * 6.28f + i) * 6f
            val alpha = if (phase < .12f) phase / .12f else if (phase > .82f) (1f - phase) / .18f else 1f
            val trailH = 14f + (i % 3) * 6f  // 尾迹长短错落
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        particleColor.copy(alpha = 0f),
                        particleColor.copy(alpha = (if (isDark) .52f else .38f) * alpha),
                        particleColor.copy(alpha = 0f)
                    ),
                    startY = py + trailH,
                    endY = py
                ),
                topLeft = Offset(px + drift, py),
                size = Size(1.6f, trailH)
            )
            drawCircle(
                particleColor.copy(alpha = (if (isDark) .75f else .55f) * alpha),
                radius = 1.8f,
                center = Offset(px + drift, py)
            )
        }
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val flavor = com.lo.imagine.ui.theme.LocalFlavor.current
    val celShape = com.lo.imagine.ui.theme.themedShape(flavor.panelCorner)
    var base = modifier.fillMaxWidth()
    if (flavor.panelCelX > 0.dp) {
        base = base.celShadow(celShape, flavor.panelCelX, flavor.panelCelY)
    }
    // 罗德岛终端：始终使用专属面板
    ArkPanel(base, celShape, flavor.panelPadding, content)
}

@Composable
private fun ArkPanel(
    modifier: Modifier, shape: Shape, padding: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < .5f
    Surface(shape = shape, color = scheme.surface.copy(alpha = .96f),
        tonalElevation = 0.dp, shadowElevation = if (dark) 0.dp else 1.dp,
        border = BorderStroke(.7.dp, scheme.outlineVariant.copy(alpha = .65f)), modifier = modifier) {
        Column(Modifier.padding(padding), content = content)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PopChipRow(
    items: List<String>,
    onPick: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { ex ->
            Surface(
                onClick = { onPick(ex) },
                shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip, pill = true),
                color = LocalPopAccents.current.a,
                contentColor = com.lo.imagine.ui.theme.Ink,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, com.lo.imagine.ui.theme.Ink)
            ) {
                Text(
                    text = "试试：$ex",
                    style = MaterialTheme.typography.labelSmall,
                    color = com.lo.imagine.ui.theme.Ink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun TinyBadge(text: String, accent: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    val interaction = if (onClick == null) Modifier else Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick)
    Box(interaction, contentAlignment = Alignment.Center) {
        Surface(shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip, pill = true),
            color = if (accent) c.primaryContainer.copy(alpha = .55f) else c.surface.copy(alpha = .7f),
            border = BorderStroke(.7.dp, if (accent) c.primary.copy(alpha = .7f) else c.outlineVariant)) {
            Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(3.dp).background(if (accent) c.primary else c.outline))
                Spacer(Modifier.width(4.dp))
                Text(text, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = if (accent) FontWeight.Bold else FontWeight.Medium,
                    color = if (accent) c.onPrimaryContainer else c.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * 全局二级图标按钮：明暗共用尺寸、轮廓和按压反馈，只用语义色适配对比度。
 * 图标底座只随 UiMood 改变，切换日夜不会改变形状。
 */
@Composable
fun PopIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(36.dp),
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    iconSize: Dp = 18.dp,
    shape: Shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip)
) {
    val scheme = MaterialTheme.colorScheme
    val pressed = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // 图标按钮只保留一条结构边线，按压时用颜色反馈，避免每个小按钮都带同一组角标。
    val isPressed by pressed.collectIsPressedAsState()
    val activeTint by animateColorAsState(
        if (isPressed && enabled) scheme.primary else iconTint,
        animationSpec = tween(90),
        label = "arkIconTint"
    )
    val pressScale by animateFloatAsState(
        if (isPressed && enabled) .94f else 1f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "arkIconPressScale"
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = pressed,
        shape = shape,
        color = when {
            !enabled -> containerColor.copy(alpha = .48f)
            isPressed -> scheme.primaryContainer
            else -> containerColor
        },
        contentColor = activeTint,
        border = BorderStroke(
            1.dp,
            when {
                !enabled -> celInk().copy(alpha = .38f)
                isPressed -> scheme.primary.copy(alpha = .82f)
                else -> celInk().copy(alpha = .82f)
            }
        ),
        modifier = modifier.graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            PIcon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) activeTint else activeTint.copy(alpha = .38f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun EmojiChip(
    emoji: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    width: Int? = null
) {
    Surface(
        onClick = onClick,
        shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field),
        color = if (selected) LocalPopAccents.current.a else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) com.lo.imagine.ui.theme.Ink else MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) com.lo.imagine.ui.theme.Ink
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)
        ),
        tonalElevation = if (selected) 1.dp else 0.dp,
        modifier = if (width != null) Modifier.width(width.dp) else Modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            if (emoji.isNotBlank()) {
                Text(emoji, style = MaterialTheme.typography.labelLarge)
            }
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            if (selected) {
                PIcon(
                    PopCheck,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun HorizontalChips(content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        content = content
    )
}

@Composable
fun ErrorPanel(message: String, onDismiss: (() -> Unit)? = null) {
    Surface(
        shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
        color = MaterialTheme.colorScheme.errorContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.error,
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.size(width = 3.dp, height = 30.dp)
            ) {}
            Spacer(Modifier.width(10.dp))
            PIcon(PopAlert, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(text = message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (onDismiss != null) {
                PopIconButton(
                    icon = PopClose,
                    contentDescription = "关闭",
                    onClick = onDismiss,
                    modifier = Modifier.size(30.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    iconTint = MaterialTheme.colorScheme.error,
                    iconSize = 17.dp,
                )
            }
        }
    }
}

/** 波普风格错误弹窗：硬描边贴纸 + 硬投影，完整展示报错信息。失败只提供知晓，重试由用户自己决定。 */
@Composable
fun PopErrorDialog(
    message: String,
    title: String = "生成失败",
    onDismiss: () -> Unit
) {
    val shape = com.lo.imagine.ui.theme.themedShape(PopRadius.sheet)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(2.dp, celInk()),
            modifier = Modifier
                .fillMaxWidth()
                .celShadow(shape, 3.dp, 3.dp)
        ) {
            Box {
                Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                "!",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Spacer(Modifier.size(10.dp))
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                )
                Spacer(Modifier.height(18.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                ) { Text("知道了", fontWeight = FontWeight.SemiBold) }
            }
            }
        }
    }
}

/**
 * 波普风格确认/表单弹窗：硬描边贴纸 + 硬投影，替代 Material 默认 AlertDialog。
 * 支持：标题图标徽章、任意表单内容、危险色确认按钮、确认前的额外动作（如“删除”）。
 */
@Composable
fun PopAlertDialog(
    title: String,
    onDismissRequest: () -> Unit,
    text: (@Composable ColumnScope.() -> Unit)? = null,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    confirmContainer: Color = MaterialTheme.colorScheme.primary,
    confirmContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    extraActions: (@Composable RowScope.() -> Unit)? = null
) {
    val shape = com.lo.imagine.ui.theme.themedShape(PopRadius.card)
    val buttonShape = com.lo.imagine.ui.theme.themedShape(PopRadius.field)
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(2.dp, celInk()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box {
                Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Surface(
                            shape = CircleShape,
                            color = iconContainer,
                            border = BorderStroke(1.5.dp, celInk()),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                PIcon(
                                    icon,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
                if (text != null) {
                    Spacer(Modifier.height(12.dp))
                    text()
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (extraActions != null) {
                        extraActions()
                        Spacer(Modifier.size(6.dp))
                    }
                    if (dismissLabel != null && onDismiss != null) {
                        TextButton(onClick = onDismiss) {
                            Text(dismissLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.size(6.dp))
                    }
                    Surface(
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        shape = buttonShape,
                        color = if (confirmEnabled) confirmContainer else confirmContainer.copy(alpha = .48f),
                        contentColor = if (confirmEnabled) confirmContentColor else confirmContentColor.copy(alpha = .38f),
                        border = BorderStroke(
                            1.2.dp,
                            if (confirmEnabled) celInk() else celInk().copy(alpha = .38f)
                        ),
                        modifier = Modifier
                    ) {
                        Text(
                            confirmLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
fun CreativeBusyIcon(modifier: Modifier = Modifier) {
    // 罗德岛终端：始终使用专属忙碌图标
    ArkBusyIcon(modifier)
}

/** 罗德岛任务节点：方形终端框、状态脉冲与垂直扫描，不复用波普呼吸圆。 */
@Composable
private fun ArkBusyIcon(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "arkBusyNode")
    val scan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1180, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "arkBusyScan"
    )
    val signal by transition.animateFloat(
        initialValue = .32f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(540, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "arkBusySignal"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(com.lo.imagine.ui.theme.themedShape(PopRadius.chip))
            .background(scheme.surfaceVariant)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                val bracket = size.minDimension * .28f
                drawLine(scheme.primary, Offset.Zero, Offset(bracket, 0f), stroke)
                drawLine(scheme.primary, Offset.Zero, Offset(0f, bracket), stroke)
                drawLine(
                    scheme.primary,
                    Offset(size.width - bracket, size.height),
                    Offset(size.width, size.height),
                    stroke
                )
                drawLine(
                    scheme.primary,
                    Offset(size.width, size.height - bracket),
                    Offset(size.width, size.height),
                    stroke
                )
                val y = scan * size.height
                drawRect(
                    color = scheme.primary.copy(alpha = .12f),
                    topLeft = Offset(0f, (y - 4.dp.toPx()).coerceAtLeast(0f)),
                    size = Size(size.width, 8.dp.toPx().coerceAtMost(size.height))
                )
                drawLine(
                    color = scheme.primary.copy(alpha = .9f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        PIcon(
            PopSpark,
            contentDescription = "处理中",
            tint = scheme.onSurface,
            modifier = Modifier.fillMaxSize(.48f)
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(4.dp)
                .background(scheme.secondary.copy(alpha = signal))
        )
    }
}

/**
 * 加载循环动画：AI 渲染马赛克。
 * 一条高亮扫描线自左向右扫过，主题色马赛克块逐格「点亮」——隐喻 AI 正在逐块生成画面；
 * 扫完后整体渐隐，重新开始下一轮。优雅、安静、贴合绘画产品。
 */
@Composable
fun RunnerDinoLoader(
    modifier: Modifier = Modifier,
    trackHeight: Dp = 72.dp
) {
    // 罗德岛终端：始终使用专属渲染加载器
    ArkRenderLoader(modifier = modifier, trackHeight = trackHeight)
}

/**
 * SKY 渲染轨：一条柔光沿细轨缓缓往返（取代旧版九宫格糖果块——九种彩色方块并排，
 * 看起来像调色盘而不是加载动画）。轨道极细、光带两端渐隐、头部一枚微光点，
 * Reverse 往返意味着循环端点永远平滑、没有任何硬切。
 */
@Composable
fun LoadingLabel(text: String) {
    // 罗德岛终端：始终使用专属加载标签
    ArkLoadingLabel(text)
}

@Composable
private fun ArkLoadingLabel(text: String) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "arkLoadingLabel")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "arkLoadingSweep"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(com.lo.imagine.ui.theme.themedShape(PopRadius.chip))
            .background(scheme.surfaceVariant)
            .drawBehind {
                val y = size.height * sweep
                drawLine(
                    scheme.primary.copy(alpha = .72f),
                    Offset(0f, y),
                    Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        ArkBusyIcon(Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
            label = "arkLoadingText"
        ) { value ->
            Column {
                Text(
                    "RENDER // ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                    fontWeight = FontWeight.Black
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "●",
            color = scheme.secondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black
        )
    }
}

/** 长任务使用有阶段感的稳定状态，不让用户盯着一个小转圈。 */
@Composable
fun GenerationStatus(
    elapsedSeconds: Int,
    mode: String,
    completed: Int = 0,
    total: Int = 1,
    modifier: Modifier = Modifier
) {
    // 罗德岛终端：始终使用专属生成状态面板
    ArkGenerationStatus(
        elapsedSeconds = elapsedSeconds,
        mode = mode,
        completed = completed,
        total = total,
        modifier = modifier
    )
}

@Composable
private fun ArkGenerationStatus(
    elapsedSeconds: Int,
    mode: String,
    completed: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val progress = if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
    val title = when (mode.lowercase()) {
        "edit", "retouch" -> "RETOUCH // PROCESSING"
        else -> "CREATION // PROCESSING"
    }
    val statusMotion = rememberInfiniteTransition(label = "generationStatusMotion")
    val statusPulse by statusMotion.animateFloat(
        initialValue = .28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "generationStatusPulse"
    )
    val statusSweep by statusMotion.animateFloat(
        initialValue = -.12f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "generationStatusSweep"
    )
    val statusBorder = scheme.primary.copy(alpha = .34f + statusPulse * .28f)
    Surface(
        color = scheme.surface,
        contentColor = scheme.onSurface,
        shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
        border = BorderStroke(1.5.dp, statusBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .drawBehind {
                    val marker = 26.dp.toPx()
                    drawLine(scheme.primary, Offset(0f, 0f), Offset(marker, 0f), 2.dp.toPx())
                    drawLine(scheme.secondary, Offset(size.width - marker, size.height), Offset(size.width, size.height), 2.dp.toPx())
                }
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "T+${elapsedSeconds}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (total > 1) "OUTPUT  ${completed}/${total}" else "AWAITING MODEL RESPONSE",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (progress >= 1f) "COMPLETE" else "LINK // STABLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (progress >= 1f) scheme.secondary else scheme.tertiary,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.height(10.dp))
            ArkRenderLoader(trackHeight = 42.dp)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(com.lo.imagine.ui.theme.themedShape(PopRadius.chip))
                    .background(scheme.outline.copy(alpha = .22f))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(scheme.primary)
                        .drawBehind {
                            // 等待期间在已完成轨道内扫过一道窄光，给进度状态持续的终端反馈
                            if (progress > 0f && progress < 1f) {
                                val beamWidth = 22.dp.toPx()
                                val beamX = size.width * statusSweep
                                drawRect(
                                    color = scheme.onPrimary.copy(alpha = .36f),
                                    topLeft = Offset((beamX - beamWidth / 2f).coerceAtLeast(0f), 0f),
                                    size = Size(beamWidth.coerceAtMost(size.width), size.height)
                                )
                            }
                        }
                )
            }
        }
    }
}

/** 罗德岛渲染加载器：旋转的 3D 线框正二十面体（官方加载页灵魂动画）+ 星点背景。 */
@Composable
private fun ArkRenderLoader(
    modifier: Modifier = Modifier,
    trackHeight: Dp = 56.dp
) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "arkIcoSpin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "arkIcoAngle"
    )
    // 呼吸脉冲（官方 HUD 灵魂：线框整体透明度随脉冲起伏，模拟"系统在线"的心跳）
    val pulse by transition.animateFloat(
        initialValue = .45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "arkIcoPulse"
    )
    // 正二十面体：12 顶点 + 30 边（边长=2 的配对），预计算一次
    val (verts, edges) = remember {
        val p = 1.618034f
        val v = listOf(
            Triple(0f, 1f, p), Triple(0f, -1f, p), Triple(0f, 1f, -p), Triple(0f, -1f, -p),
            Triple(1f, p, 0f), Triple(-1f, p, 0f), Triple(1f, -p, 0f), Triple(-1f, -p, 0f),
            Triple(p, 0f, 1f), Triple(-p, 0f, 1f), Triple(p, 0f, -1f), Triple(-p, 0f, -1f)
        )
        val e = mutableListOf<Pair<Int, Int>>()
        for (i in v.indices) for (j in i + 1 until v.size) {
            val dx = v[i].first - v[j].first
            val dy = v[i].second - v[j].second
            val dz = v[i].third - v[j].third
            if (kotlin.math.sqrt(dx * dx + dy * dy + dz * dz) < 2.001f) e.add(i to j)
        }
        v to e
    }
    // 星点：固定伪随机分布，不随重组变化
    val stars = remember {
        List(18) { i ->
            val a = kotlin.math.sin(i * 12.9898f) * 43758.5453f
            val b = kotlin.math.sin(i * 78.233f) * 12543.123f
            (a - kotlin.math.floor(a)) to (b - kotlin.math.floor(b))
        }
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
    ) {
        stars.forEach { (sx, sy) ->
            drawCircle(scheme.onSurface.copy(alpha = .16f), 1f, Offset(size.width * sx, size.height * sy))
        }
        val cx = size.width / 2f
        val cy = size.height / 2f
        val scale = size.height * 0.44f / 1.9f
        val tilt = 0.42f
        val cosY = kotlin.math.cos(angle)
        val sinY = kotlin.math.sin(angle)
        val cosX = kotlin.math.cos(tilt)
        val sinX = kotlin.math.sin(tilt)
        val pts = verts.map { (x, y, z) ->
            val x1 = x * cosY + z * sinY
            val z1 = -x * sinY + z * cosY
            val y1 = y * cosX - z1 * sinX
            Offset(cx + x1 * scale, cy + y1 * scale)
        }
        edges.forEach { (a, b) ->
            drawLine(scheme.secondary.copy(alpha = .72f * pulse), pts[a], pts[b], 1.2f)
        }
        pts.forEach { pt -> drawCircle(scheme.secondary.copy(alpha = .9f * pulse), 1.8f, pt) }
    }
}

/**
 * 结果卡大图解码专用后台池：base64 → Bitmap 属 CPU 密集且耗时可达数百毫秒，
 * 与网络、存储 IO 隔离，避免占满公共 IO 池或冻结主线程。
 */
private val DecodeDispatcher = kotlinx.coroutines.newSingleThreadContext("imagine-b64-decode")

@Composable
fun ImageResultCard(
    data: ImageData,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cardShape = com.lo.imagine.ui.theme.themedShape(PopRadius.card)
    // 扫描线：数据写入语言——钢蓝细线沿卡片顶部缓慢下移，周期循环
    val scanTransition = rememberInfiniteTransition(label = "resultCardScan")
    val scanY by scanTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "resultCardScanY"
    )
    Surface(
        onClick = onClick,
        shape = cardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, celInk()),
        modifier = modifier.celShadow(cardShape, dx = 2.dp, dy = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().clip(cardShape)) {
            // 取图优先级与生成链路 resolveBitmap 对齐：b64 优先，url 只作兜底。
            // 旧版 url 优先——上游返回「临时 URL + 完整 b64」双字段时，Coil 拉取
            // 过期/受限 URL 会静默失败，卡片只剩 surface 底色（浅色主题下就是一块纯白）；
            // 而作品库与点开预览走 resolveBitmap（b64 优先）始终正常，
            // 这正是「只有结果格纯白」的原因。
            var decodedB64 by remember(data.b64Json) { mutableStateOf<Bitmap?>(null) }
            var decodeFailed by remember(data.b64Json) { mutableStateOf(false) }
            LaunchedEffect(data.b64Json) {
                val b64 = data.b64Json ?: return@LaunchedEffect
                // 大图解码（数十 MB base64）不能占主线程：放后台池
                val bmp = withContext(DecodeDispatcher) { ImageUtils.decodeBase64(b64) }
                if (bmp != null) decodedB64 = bmp else decodeFailed = true
            }
            val bmp = decodedB64
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "生成结果",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!data.url.isNullOrBlank()) {
                // b64 解码期间先用 url 顶上（能加载就立刻有图）；b64 就绪后自动替换
                AsyncImage(
                    model = data.url,
                    contentDescription = "生成结果",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (data.b64Json != null && !decodeFailed) {
                // 纯 b64 且解码中：卡片自己的忙碌指示，替代空转白底
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    PopBusySpinner(modifier = Modifier.size(18.dp))
                }
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    PIcon(PopAlert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 扫描线：叠加在图片上方，alpha 很低不抢内容
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val lineY = scanY * size.height
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    com.lo.imagine.ui.theme.ArkRef.steel.copy(alpha = .55f),
                                    Color.Transparent
                                )
                            ),
                            topLeft = Offset(0f, lineY),
                            size = Size(size.width, 1.5.dp.toPx())
                        )
                    }
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = .78f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(9.dp)
            ) {
                PIcon(
                    PopSpark,
                    contentDescription = null,
                    modifier = Modifier.padding(7.dp).size(14.dp),
                    tint = MaterialTheme.colorScheme.inversePrimary
                )
            }
        }
    }
}

@Composable
fun EmptyResults(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth().padding(vertical = 38.dp)
    ) {
        // 官方罗德岛 logo（官网加载页灵魂：黑底 + 白 logo 描边）
        Image(
            painter = painterResource(R.drawable.ark_rhodes_logo),
            contentDescription = "罗德岛",
            modifier = Modifier.size(56.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            alpha = .85f
        )
        Spacer(Modifier.height(6.dp))
        // 官方描边字（RHODES ISLAND）
        Image(
            painter = painterResource(R.drawable.ark_rhodes_stroke),
            contentDescription = null,
            modifier = Modifier.height(13.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(Modifier.height(13.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CopyableModel(model: String) {
    var copied by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TinyBadge(model, accent = true)
        PopIconButton(
            icon = if (copied) PopCheck else PopCopy,
            contentDescription = if (copied) "已复制" else "复制模型名",
            onClick = { copied = true },
            modifier = Modifier.size(30.dp),
            iconSize = 16.dp
        )
    }
}

@Composable
fun InfoHint(text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        PIcon(PopInfo, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DropdownField(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    menuHeight: Int = 300,
    leadingContent: (@Composable () -> Unit)? = null,
    optionLeadingContent: (@Composable (String) -> Unit)? = null,
    fieldLabel: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { anchorWidthPx = it.size.width }
    ) {
        val fieldShape = com.lo.imagine.ui.theme.themedShape(PopRadius.field)
        Surface(
            onClick = { expanded = !expanded },
            shape = fieldShape,
            color = if (expanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f) else MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            border = BorderStroke(
                if (expanded) 1.2.dp else .8.dp,
                if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = .78f)
                else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = RefUiTokens.controlHeight)
                .semantics {
                    fieldLabel?.let { contentDescription = it }
                    stateDescription = if (expanded) "选项已展开" else "选项已收起"
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (leadingContent != null) {
                    leadingContent()
                    Spacer(Modifier.width(9.dp))
                }
                Text(
                    selected,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                PIcon(
                    if (expanded) PopChevronUp else PopChevronDown,
                    contentDescription = if (expanded) "收起选项" else "展开选项",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field),
            containerColor = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp,
            border = BorderStroke(.8.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .then(
                    if (anchorWidthPx > 0) Modifier.width(with(density) { anchorWidthPx.toDp() })
                    else Modifier.fillMaxWidth()
                )
                .heightIn(max = menuHeight.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (optionLeadingContent != null) {
                                optionLeadingContent(option)
                                Spacer(Modifier.width(9.dp))
                            }
                            Text(
                                option,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    trailingIcon = {
                        if (isSelected) {
                            PIcon(
                                PopCheck,
                                contentDescription = "已选",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    modifier = Modifier.background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f)
                        else Color.Transparent
                    )
                )
            }
        }
    }
}

/** 数字输入与下拉选择共用参考稿的表面、细描边和触控规格。 */
@Composable
fun PopNumericField(
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "1–4",
    enabled: Boolean = true,
    label: String? = null
) {
    val shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field)
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val containerColor = if (focused) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = if (focused) {
        MaterialTheme.colorScheme.primary.copy(alpha = .78f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        shape = shape,
        color = containerColor,
        tonalElevation = 0.dp,
        border = BorderStroke(if (focused) 1.2.dp else .8.dp, borderColor),
        modifier = modifier.fillMaxWidth().heightIn(min = RefUiTokens.controlHeight)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                onCommit()
                keyboard?.hide()
                focusManager.clearFocus()
            }),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { label?.let { contentDescription = it } }
                .onFocusChanged { state ->
                    if (focused && !state.isFocused) onCommit()
                    focused = state.isFocused
                }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isBlank()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/** 统一的圆角文本输入；聚焦状态使用主题强调色细描边。 */
@Composable
fun PopTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field)
    var focused by remember { mutableStateOf(false) }
    val containerColor = if (focused && enabled) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = .42f)
        focused -> MaterialTheme.colorScheme.primary.copy(alpha = .78f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
        }
        Surface(
            shape = shape,
            color = containerColor,
            tonalElevation = 0.dp,
            border = BorderStroke(
                if (focused && enabled) 1.2.dp else .8.dp,
                borderColor
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = RefUiTokens.controlHeight)
        ) {
            Row(
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                if (leadingContent != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .then(if (singleLine) Modifier else Modifier.padding(top = 1.dp))
                            .size(20.dp)
                    ) {
                        leadingContent()
                    }
                    Spacer(Modifier.width(9.dp))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focused = it.isFocused }
                        .semantics { label?.let { contentDescription = it } },
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
                        ) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(
                                    placeholder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (trailingContent != null) {
                    Spacer(Modifier.width(6.dp))
                    trailingContent()
                }
            }
        }
    }
}

/** 波普风格滑杆：硬描边轨道 + 贴纸圆钮，替代 Material Slider。 */
@Composable
fun PopSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true
) {
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span <= 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val trackShape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip, pill = true)
    val thumbShape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip, pill = true)
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .height(28.dp)
            .graphicsLayer { alpha = if (enabled) 1f else .45f },
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidthPx = constraints.maxWidth.toFloat()
        val thumbSizePx = with(density) { 22.dp.toPx() }
        val thumbTravel = with(density) { (trackWidthPx - thumbSizePx).coerceAtLeast(0f).toDp() }
        fun positionToValue(x: Float): Float {
            val usable = (trackWidthPx - thumbSizePx).coerceAtLeast(1f)
            val f = ((x - thumbSizePx / 2f) / usable).coerceIn(0f, 1f)
            return valueRange.start + f * span
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(trackShape)
                .background(MaterialTheme.colorScheme.surface)
        )
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(10.dp)
                    .clip(trackShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .border(1.5.dp, celInk(), trackShape)
        )
        Box(
            Modifier
                .offset(x = thumbTravel * fraction)
                .size(22.dp)
                .clip(thumbShape)
                .background(LocalPopAccents.current.a)
                .border(1.5.dp, celInk(), thumbShape)
        )
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(enabled, valueRange, trackWidthPx) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset -> onValueChange(positionToValue(offset.x)) }
                }
                .pointerInput(enabled, valueRange, trackWidthPx) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { change, _ ->
                        change.consume()
                        onValueChange(positionToValue(change.position.x))
                    }
                }
        )
    }
}

/** Compact reference switch with a 48dp semantic touch target. */
@Composable
fun PopSwitch(
    checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val x by animateDpAsState(if (checked) 20.dp else 3.dp, tween(160), label = "switchThumb")
    Box(modifier.size(48.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center) {
        Box(Modifier.width(40.dp).height(22.dp).clip(CircleShape)
            .background(if (checked) scheme.primary else scheme.surfaceContainerHighest)
            .border(.7.dp, scheme.outline.copy(alpha = .28f), CircleShape)) {
            Box(Modifier.align(Alignment.CenterStart).offset(x = x).size(17.dp)
                .background(if (enabled) Color.White else scheme.onSurfaceVariant, CircleShape))
        }
    }
}

/** 波普风格选择片：硬描边 + 选中硬投影贴纸，替代 Material FilterChip。 */
@Composable
fun PopChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip)
    // 选择片：未选中是安静的描边块，选中才是实心主色，减少整屏高饱和块的数量。
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (selected) {
                PIcon(PopCheck, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
            )
        }
    }
}

/** 波普忙碌指示：星芒旋转，替代 Material CircularProgressIndicator。 */
@Composable
fun PopBusySpinner(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "popBusySpin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "popBusyAngle"
    )
    PIcon(
        PopSpark,
        contentDescription = "处理中",
        tint = tint,
        modifier = modifier.graphicsLayer { rotationZ = angle }
    )
}

@Composable
fun ImagePreview(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    contentDescription: String = "图片"
) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(com.lo.imagine.ui.theme.themedShape(PopRadius.chip))
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .clip(com.lo.imagine.ui.theme.themedShape(PopRadius.field))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            PIcon(PopAlert, contentDescription = null)
        }
    }
}
