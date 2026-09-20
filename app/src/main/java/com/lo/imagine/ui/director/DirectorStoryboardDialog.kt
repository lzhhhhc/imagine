package com.lo.imagine.ui.director

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.lo.imagine.data.DirectorAsset
import com.lo.imagine.data.DirectorShot
import com.lo.imagine.ui.*
import com.lo.imagine.ui.theme.PopRadius
import com.lo.imagine.ui.theme.themedShape
import java.io.File

@Composable
internal fun DirectorStoryboardDialog(
    shots: List<DirectorShot>, aspect: String, assets: List<DirectorAsset>, busy: Boolean,
    generatingId: Long?, error: String?, onDismiss: () -> Unit, onAdd: () -> Unit,
    onUpdate: (DirectorShot) -> Unit, onDelete: (Long) -> Unit,
    onPickFrame: (Long, Boolean) -> Unit, onGenerateFrame: (DirectorShot) -> Unit,
    onCompose: () -> Unit, onAspect: () -> Unit, onCharacters: () -> Unit, onScene: () -> Unit,
    onCopy: () -> Unit, onProduce: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = themedShape(PopRadius.sheet), color = c.surface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).fillMaxHeight(.92f).imePadding()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("分镜头", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${shots.size} 镜 · 共 ${shots.sumOf { it.seconds.toIntOrNull() ?: 0 }} 秒",
                            style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                    }
                    TextButton(onClick = onAdd, enabled = !busy) { Text("＋分镜") }
                    TextButton(onClick = onDismiss) { Text("完成") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onCharacters, enabled = !busy, contentPadding = PaddingValues(4.dp)) {
                        Text("人物 ${assets.count { it.kind != "scene" }}")
                    }
                    TextButton(onClick = onScene, enabled = !busy, contentPadding = PaddingValues(4.dp)) {
                        Text("环境 ${assets.count { it.kind == "scene" }}")
                    }
                    TextButton(onClick = onAspect, enabled = !busy, contentPadding = PaddingValues(4.dp)) { Text(aspect) }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (shots.isEmpty()) item {
                        DirectorSummaryCard("从一个镜头开始", "完成导演访谈后，分镜会自动写入这里。也可以点「＋分镜」自行编写，再核对图片与参数开始制作。")
                    }
                    itemsIndexed(shots, key = { _, shot -> shot.id }) { index, shot ->
                        Surface(shape = themedShape(PopRadius.field), color = c.surfaceContainerLow,
                            border = BorderStroke(.7.dp, c.outlineVariant)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${(index + 1).toString().padStart(2, '0')}  分镜", fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f))
                                    PopTextField(value = shot.seconds, onValueChange = { value ->
                                        onUpdate(shot.copy(seconds = value.filter { it.isDigit() }.take(3)))
                                    }, singleLine = true, enabled = !busy, label = "秒",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(76.dp))
                                    IconButton(onClick = { onDelete(shot.id) }, enabled = !busy) {
                                        PIcon(PopTrash, "删除分镜${index + 1}", Modifier.size(20.dp))
                                    }
                                }
                                PopTextField(value = shot.prompt, onValueChange = { onUpdate(shot.copy(prompt = it)) },
                                    placeholder = "主体、动作、场景、运镜与声音…", minLines = 3, maxLines = 7,
                                    enabled = !busy, modifier = Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FrameSlot("首帧", shot.imagePath, !busy, Modifier.weight(1f),
                                        onPick = { onPickFrame(shot.id, false) }, onClear = { onUpdate(shot.copy(imagePath = null)) })
                                    FrameSlot("尾帧 · 可选", shot.endImagePath, !busy, Modifier.weight(1f),
                                        onPick = { onPickFrame(shot.id, true) }, onClear = { onUpdate(shot.copy(endImagePath = null)) })
                                }
                                TextButton(onClick = { onGenerateFrame(shot) }, enabled = !busy && shot.prompt.isNotBlank()) {
                                    if (generatingId == shot.id) PopBusySpinner(Modifier.size(16.dp))
                                    Text(if (generatingId == shot.id) "正在生成首帧…" else "生成首帧")
                                }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onCompose, enabled = !busy && shots.isNotEmpty()) { Text("整理分镜脚本") }
                            TextButton(onClick = onCopy, enabled = shots.isNotEmpty()) { Text("复制脚本") }
                        }
                    }
                    error?.let { message -> item { Text(message, color = c.error, style = MaterialTheme.typography.bodySmall) } }
                }
                DirectorPrimaryAction("开始视频制作", enabled = !busy, onClick = onProduce, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun FrameSlot(label: String, path: String?, enabled: Boolean, modifier: Modifier,
    onPick: () -> Unit, onClear: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = themedShape(PopRadius.chip),
            modifier = Modifier.fillMaxWidth().height(72.dp).clip(themedShape(PopRadius.chip))
                .clickable(enabled = enabled, onClick = onPick)) {
            if (path != null) AsyncImage(File(path), "选择或更换$label", contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
            else Box(contentAlignment = Alignment.Center) { Text("＋ $label", style = MaterialTheme.typography.labelMedium) }
        }
        if (path != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear, enabled = enabled, contentPadding = PaddingValues(2.dp)) { Text("移除") }
        }
    }
}