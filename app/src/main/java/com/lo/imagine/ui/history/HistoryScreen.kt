package com.lo.imagine.ui.history


import com.lo.imagine.ui.theme.PopRadius
import androidx.compose.foundation.BorderStroke

import com.lo.imagine.ui.PIcon
import com.lo.imagine.ui.PopMediaCard
import com.lo.imagine.ui.PopChevron
import com.lo.imagine.ui.PopCheck
import com.lo.imagine.ui.PopClose
import com.lo.imagine.ui.PopGallery
import com.lo.imagine.ui.PopGrid
import com.lo.imagine.ui.PopTrash
import com.lo.imagine.ui.PopAlertDialog
import com.lo.imagine.ui.PopIconButton
import com.lo.imagine.ui.celInk
import com.lo.imagine.ui.celShadow
// popBackdrop 已由壳层 ArkPageBackdrop 接管
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lo.imagine.ui.AppBus
import com.lo.imagine.ui.PreviewStore
import com.lo.imagine.ui.ScreenHeader
import com.lo.imagine.ui.TinyBadge
import com.lo.imagine.util.HistoryEntry
import com.lo.imagine.util.ImageUtils
import androidx.compose.foundation.combinedClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HistoryScreen(onPreview: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    // 防连点：解码大图期间忽略再次点击，避免重复 decode 与预览数据错乱
    var opening by remember { mutableStateOf(false) }
    /** 每行排列数量：右上角按钮切换（原先的双指捏合已移除） */
    var columnCount by rememberSaveable { mutableIntStateOf(2) }
    var showColumns by remember { mutableStateOf(false) }
    /** 选择模式：长按进入，右上角出现勾选 / 全选 / 批量删除 */
    var selecting by remember { mutableStateOf(false) }
    var selectedNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDelete by remember { mutableStateOf(false) }
    // 未进入选择模式时，长按单个卡片仍然走「删除这一张」的确认
    var entryToDelete by remember { mutableStateOf<HistoryEntry?>(null) }

    fun refresh() {
        entries = ImageUtils.listHistory(context)
    }

    LaunchedEffect(PreviewStore.historyRevision) { refresh() }

    fun exitSelection() {
        selecting = false
        selectedNames = emptySet()
    }

    fun open(entry: HistoryEntry, index: Int) {
        if (opening) return
        opening = true
        // 大图解码（4096 长边 JPEG 可达数百 ms）放 IO 线程：主线程 decode 会冻结点击后的整个界面
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { ImageUtils.decodeFile(entry.file) }
            opening = false
            if (bitmap == null) return@launch
            PreviewStore.bitmap = bitmap
            PreviewStore.prompt = entry.meta.prompt
            PreviewStore.model = entry.meta.model
            PreviewStore.workflowDetails = entry.meta.workflowDetails
            PreviewStore.elapsedText = ImageUtils.formatElapsed(entry.meta.elapsedSec)
            PreviewStore.sizeNote = null // 历史条目未记录上游原始像素
            PreviewStore.historyList = entries
            PreviewStore.historyIndex = index
            onPreview()
        }
    }

    entryToDelete?.let { target ->
        PopAlertDialog(
            title = "删除这个作品？",
            onDismissRequest = { entryToDelete = null },
            text = { Text("本机保存的图片与提示词将被删除，无法恢复。") },
            confirmLabel = "删除",
            confirmContainer = MaterialTheme.colorScheme.error,
            confirmContentColor = MaterialTheme.colorScheme.onError,
            onConfirm = {
                ImageUtils.deleteHistory(target)
                entryToDelete = null
                refresh()
            }
        )
    }

    if (showBatchDelete) {
        val count = selectedNames.size
        PopAlertDialog(
            title = "删除选中的 $count 个作品？",
            onDismissRequest = { showBatchDelete = false },
            text = { Text("选中的图片与提示词将被删除，无法恢复。") },
            confirmLabel = "删除",
            confirmContainer = MaterialTheme.colorScheme.error,
            confirmContentColor = MaterialTheme.colorScheme.onError,
            onConfirm = {
                entries.filter { it.file.name in selectedNames }.forEach { ImageUtils.deleteHistory(it) }
                showBatchDelete = false
                exitSelection()
                refresh()
            }
        )
    }

    if (showColumns) {
        PopAlertDialog(
            title = "每行数量",
            onDismissRequest = { showColumns = false },
            confirmLabel = "完成",
            onConfirm = { showColumns = false },
            dismissLabel = "取消",
            onDismiss = { showColumns = false },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "选择每行显示几张；改完立即生效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..5).forEach { n ->
                            val selected = columnCount == n
                            Surface(
                                onClick = { columnCount = n },
                                shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)
                                ),
                                modifier = Modifier.size(width = 40.dp, height = 32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text("$n", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        ScreenHeader(
            title = if (selecting) "已选 ${selectedNames.size} / ${entries.size}" else "作品",
            action = {
                if (entries.isEmpty()) return@ScreenHeader
                if (selecting) {
                    val allSelected = selectedNames.size == entries.size
                    TinyBadge(if (allSelected) "取消全选" else "全选") {
                        selectedNames = if (allSelected) emptySet() else entries.map { it.file.name }.toSet()
                    }
                    PopIconButton(
                        icon = PopTrash,
                        contentDescription = "删除选中",
                        onClick = { if (selectedNames.isNotEmpty()) showBatchDelete = true },
                        modifier = Modifier.size(32.dp),
                        iconTint = if (selectedNames.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        iconSize = 17.dp
                    )
                    PopIconButton(
                        icon = PopClose,
                        contentDescription = "退出选择",
                        onClick = { exitSelection() },
                        modifier = Modifier.size(32.dp),
                        iconSize = 17.dp
                    )
                } else {
                    TinyBadge("${entries.size} 张")
                    PopIconButton(
                        icon = PopGrid,
                        contentDescription = "每行数量",
                        onClick = { showColumns = true },
                        modifier = Modifier.size(32.dp),
                        iconSize = 17.dp
                    )
                }
            }
        )

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = com.lo.imagine.ui.theme.themedShape(PopRadius.card),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(76.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PIcon(PopGallery, contentDescription = null, modifier = Modifier.size(34.dp))
                        }
                    }
                    Spacer(Modifier.size(18.dp))
                    Text("还没有作品", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.size(14.dp))
                    Surface(
                        onClick = { AppBus.goTo("studio") },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                        ) {
                            Text("开始创作", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.size(6.dp))
                            PIcon(PopChevron, contentDescription = null, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(entries, key = { _, entry -> entry.file.name }) { index, entry ->
                    val name = entry.file.name
                    HistoryCard(
                        entry = entry,
                        selecting = selecting,
                        selected = name in selectedNames,
                        onClick = {
                            if (selecting) {
                                selectedNames = if (name in selectedNames) selectedNames - name else selectedNames + name
                            } else {
                                open(entry, index)
                            }
                        },
                        onLongClick = {
                            if (selecting) {
                                selectedNames = selectedNames - name
                            } else {
                                selecting = true
                                selectedNames = setOf(name)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun HistoryCard(
    entry: HistoryEntry,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // 罗德岛终端：档案时间戳统一使用 ARCHIVE // 时间 格式
    PopMediaCard(
        selecting = selecting,
        selected = selected,
        footerText = "ARCHIVE  //  ${ImageUtils.formatTime(entry.meta.createdAt)}",
        onClick = onClick,
        onLongClick = onLongClick,
        contentDescription = "作品"
    ) {
        AsyncImage(
            model = entry.file,
            contentDescription = "作品",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}