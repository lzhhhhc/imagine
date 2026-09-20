package com.lo.imagine.ui

import com.lo.imagine.ui.theme.PopRadius
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.lo.imagine.ui.theme.LocalFlavor
import com.lo.imagine.ui.theme.LocalPopAccents
import com.lo.imagine.ui.theme.themedShape

/**
 * 媒体卡片：作品库与 NAI 会话结果共用同一套外观，避免两处各写一份导致主题下风格分裂。
 *
 * 造型规则完全交给主题：圆角走 [themedShape]，描边走 [celInk]，投影走 [celShadow]；
 * 像素/罗德岛主题走直角硬块 + 顶部彩条，其余主题走圆角 + 底部渐隐文字。
 * 选中态（右上角圆形勾选框 + 主色蒙层）也只有这一份实现。
 */
@Composable
fun PopMediaCard(
    modifier: Modifier = Modifier,
    aspectRatio: Float = .78f,
    selecting: Boolean = false,
    selected: Boolean = false,
    /** 左下/底部的次要文字；null 表示不显示 */
    footerText: String? = null,
    /** 右上角角标（未进入选择模式时显示）；null 表示不显示 */
    cornerBadge: String? = null,
    /** 卡片首次进入的错峰延迟；默认 0 保持旧调用的静态行为 */
    animationDelayMs: Int = 0,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    contentDescription: String,
    media: @Composable () -> Unit
) {
    // 罗德岛终端：统一硬朗直角卡片风格
    val shape = themedShape(8.dp)
    var revealed by remember { mutableStateOf(animationDelayMs <= 0) }
    LaunchedEffect(animationDelayMs) {
        if (animationDelayMs > 0) {
            delay(animationDelayMs.toLong())
            revealed = true
        }
    }
    val revealProgress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "mediaCardReveal"
    )
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.2.dp, celInk()),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .graphicsLayer {
                alpha = revealProgress
                translationY = (1f - revealProgress) * 10.dp.toPx()
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize().clip(shape),
                contentAlignment = Alignment.Center
            ) {
                media()
            }

            // 选择态：主色蒙层 + 右上角勾选框（唯一实现）
            if (selecting && selected) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = .18f)))
            }
            if (selecting) {
                SelectionBadge(selected = selected, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(24.dp))
            } else if (cornerBadge != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = themedShape(PopRadius.pill, pill = true),
                    border = BorderStroke(1.5.dp, celInk()),
                    modifier = Modifier.align(Alignment.TopEnd).padding(7.dp)
                ) {
                    Text(
                        cornerBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
                    )
                }
            }

            // 罗德岛终端：底部状态轨 + 顶部彩条
            Box(Modifier.fillMaxWidth().height(34.dp).align(Alignment.BottomCenter).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .78f)))
            footerText?.let {
                    Text(
                    it,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 9.dp)
                )
            }
            Box(Modifier.width(28.dp).height(2.dp).align(Alignment.TopStart).background(MaterialTheme.colorScheme.secondary))
        }
    }
}

/** 选中勾选框：两种屏幕共用，尺寸与描边固定，避免各写一套视觉。 */
@Composable
fun SelectionBadge(selected: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = .82f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = themedShape(PopRadius.pill, pill = true),
        border = BorderStroke(1.5.dp, celInk()),
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) PIcon(PopCheck, contentDescription = "已选", modifier = Modifier.size(14.dp))
        }
    }
}

/** 选择模式的标题行：统一「已选 N / M + 全选 + 操作按钮」的排版，两处屏幕保持一致。 */
@Composable
fun SelectionHeaderRow(
    selecting: Boolean,
    selectedCount: Int,
    totalCount: Int,
    idleText: String,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (selecting) "已选 $selectedCount / $totalCount" else idleText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (selecting) {
            TinyBadge(if (allSelected) "取消全选" else "全选") { onToggleAll() }
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}
