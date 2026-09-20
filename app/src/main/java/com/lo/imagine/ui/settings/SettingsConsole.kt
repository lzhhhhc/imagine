package com.lo.imagine.ui.settings


import com.lo.imagine.ui.theme.PopRadius
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.lo.imagine.ui.PopBack
import com.lo.imagine.ui.PopIconButton
import com.lo.imagine.ui.celInk
import com.lo.imagine.ui.theme.themedCorner
import com.lo.imagine.ui.theme.LocalFlavor
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 全屏控制台壳：顶部是深色沉浸身份条，中间是可滚动配置区，底部提供唯一明确的完成动作。
 */
@Composable
internal fun SettingsConsoleDialog(
    title: String,
    subtitle: String,
    status: String?,
    statusDetail: String?,
    connected: Boolean,
    icon: @Composable () -> Unit,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // 罗德岛终端：统一使用硬朗直角风格
    val shellShape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = scheme.background,
            shape = shellShape,
            border = BorderStroke(1.5.dp, scheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // ===== 顶部：深色沉浸身份条（森空岛式信号层）=====
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = .052f),
                                    Color.Transparent
                                )
                            )
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                // 罗德岛终端：顶底双线装饰
                                drawLine(scheme.primary, Offset(0f, size.height), Offset(size.width * .28f, size.height), 2.dp.toPx())
                                drawLine(scheme.secondary, Offset(size.width * .78f, 0f), Offset(size.width, 0f), 2.dp.toPx())
                            }
                            .padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 10.dp)
                    ) {
                        PopIconButton(
                            icon = PopBack,
                            contentDescription = "返回",
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp),
                            iconTint = MaterialTheme.colorScheme.onSurface,
                            iconSize = 18.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // 状态圆点：主色点 = 已就绪 / 灰点 = 未连接
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(
                                    if (connected) MaterialTheme.colorScheme.primary else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .3f)
                                )
                        ) {}
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(Modifier.height(4.dp))

                    // ===== 状态概览卡（信号卡）：status 为 null 时整体隐藏 =====
                    if (status != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = com.lo.imagine.ui.theme.themedShape(PopRadius.card),
                        border = BorderStroke(1.5.dp, celInk()),
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            // 左侧粗色信号条
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (connected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .35f)
                                    )
                            )
                            Spacer(Modifier.size(13.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    status,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
if (statusDetail != null) {
                                     Spacer(Modifier.size(2.dp))
                                     Text(
                                         statusDetail,
                                         style = MaterialTheme.typography.bodySmall,
                                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                                         maxLines = 2,
                                         overflow = TextOverflow.Ellipsis
                                     )
                                 }
                            }
                            Spacer(Modifier.size(10.dp))
                            // 右侧功能圆钮：展示引擎身份图标
                            Surface(
                                color = if (connected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = CircleShape,
                                border = BorderStroke(1.5.dp, celInk()),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    icon()
                                }
                            }
                        }
                    }
                    }
                    Spacer(Modifier.height(16.dp))
                    content()
                    Spacer(Modifier.height(18.dp))
                }

                // ===== 底部：固定完成动作条 =====
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onComplete,
                        shape = com.lo.imagine.ui.theme.themedShape(PopRadius.card),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.5.dp, celInk()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("完成并返回", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsConsoleGroup(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val groupFlavor = LocalFlavor.current
    val groupAccents = com.lo.imagine.ui.theme.LocalPopAccents.current
    val groupShape = com.lo.imagine.ui.theme.themedShape(PopRadius.sheet)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = groupShape,
        // 描边粗细跟随主题：波普/像素 2dp、包豪斯 2.5dp、罗德岛 1.4dp、动漫 1dp
        border = BorderStroke(groupFlavor.panelBorder, celInk()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            // 左侧彩色竖条（官方菜单卡语言：每个模块一条专属色，按标题稳定取色）
            val stripePalette = listOf(
                androidx.compose.ui.graphics.Color(0xFFD2691E),  // 橙
                androidx.compose.ui.graphics.Color(0xFF40E0D0),  // 青
                androidx.compose.ui.graphics.Color(0xFFFFD800),  // 罗德岛黄
                androidx.compose.ui.graphics.Color(0xFFC0392B),  // 红
                androidx.compose.ui.graphics.Color(0xFF4A90D9)   // 蓝
            )
            val stripe = stripePalette[((title.hashCode() % stripePalette.size) + stripePalette.size) % stripePalette.size]
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(stripe)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 17.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)
            ) {
                // ===== 小节标题行：罗德岛终端模块徽章 =====
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 16.dp)
                            .clip(RoundedCornerShape(0.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                content()
            }
        }
    }
}
