package com.lo.imagine.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.lo.imagine.data.SettingsRepository
import com.lo.imagine.ui.settings.SettingsActionRow
import com.lo.imagine.ui.theme.PopRadius
import com.lo.imagine.ui.theme.themedShape
import com.lo.imagine.util.ImageUtils
import java.io.File
import kotlinx.coroutines.launch

/** 导入图片的来源。本地是应用作品库，相册是系统图片选择器。 */
enum class ImageImportSource(val id: String, val label: String) {
    LOCAL("local", "本地"),
    ASK("ask", "询问"),
    GALLERY("gallery", "相册");

    companion object {
        fun fromId(raw: String?): ImageImportSource = entries.firstOrNull { it.id == raw } ?: ASK
    }
}

/** 一次导入请求落到哪条路径。询问只是先弹选择，不会自己变成第三条存储。 */
enum class ImageImportRoute { LOCAL, GALLERY, ASK }

fun resolveImageImport(source: ImageImportSource): ImageImportRoute = when (source) {
    ImageImportSource.LOCAL -> ImageImportRoute.LOCAL
    ImageImportSource.GALLERY -> ImageImportRoute.GALLERY
    ImageImportSource.ASK -> ImageImportRoute.ASK
}

/**
 * 一个导入入口同时持有系统相册和本地作品库。
 * 调用方只发请求，来源由设置决定，不在每个按钮上再分叉。
 */
class ImageImportLauncher internal constructor(
    private val source: () -> ImageImportSource,
    private val gallery: () -> Unit,
    private val local: () -> Unit,
    private val ask: () -> Unit
) {
    fun launch() {
        when (resolveImageImport(source())) {
            ImageImportRoute.GALLERY -> gallery()
            ImageImportRoute.LOCAL -> local()
            ImageImportRoute.ASK -> ask()
        }
    }
}

@Composable
fun rememberImageImport(
    source: ImageImportSource,
    multiple: Boolean = false,
    onPicked: (List<Uri>) -> Unit
): ImageImportLauncher {
    val context = LocalContext.current
    var asking by remember { mutableStateOf(false) }
    var browsing by remember { mutableStateOf(false) }
    val singleGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(listOf(uri))
    }
    val multiGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    fun openGallery() {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (multiple) multiGallery.launch(request) else singleGallery.launch(request)
    }
    if (asking) {
        PopAlertDialog(
            title = "从哪里导入",
            onDismissRequest = { asking = false },
            icon = PopImageAdd,
            confirmLabel = "相册",
            onConfirm = {
                asking = false
                openGallery()
            },
            dismissLabel = "取消",
            onDismiss = { asking = false },
            extraActions = {
                androidx.compose.material3.TextButton(onClick = {
                    asking = false
                    browsing = true
                }) { Text("本地作品") }
            },
            text = {
                Text(
                    "这次要导入的图片，从本地作品库拿，还是去系统相册选。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
    if (browsing) {
        LocalLibraryPicker(
            multiple = multiple,
            onDismiss = { browsing = false },
            onPicked = { files ->
                browsing = false
                val uris = files.map { Uri.fromFile(it) }
                if (uris.isNotEmpty()) onPicked(uris)
            }
        )
    }
    return remember(source, multiple) {
        ImageImportLauncher(
            source = { source },
            gallery = { openGallery() },
            local = {
                if (ImageUtils.listHistory(context).isEmpty()) {
                    android.widget.Toast.makeText(context, "本地作品库还是空的", android.widget.Toast.LENGTH_SHORT).show()
                } else browsing = true
            },
            ask = { asking = true }
        )
    }
}

@Composable
private fun LocalLibraryPicker(multiple: Boolean, onDismiss: () -> Unit, onPicked: (List<File>) -> Unit) {
    val context = LocalContext.current
    val entries = remember { ImageUtils.listHistory(context) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = themedShape(PopRadius.sheet),
            color = MaterialTheme.colorScheme.background,
            border = androidx.compose.foundation.BorderStroke(2.dp, celInk()),
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("本地作品", fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (multiple) "点选要导入的图片" else "点一张即可导入",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                if (entries.isEmpty()) {
                    Text("作品库还是空的", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f, fill = false).heightIn(max = 360.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(entries, key = { it.file.name }) { entry ->
                            val on = entry.file.name in selected
                            Box(
                                Modifier.aspectRatio(1f).clip(themedShape(PopRadius.chip))
                                    .border(
                                        if (on) 1.5.dp else .6.dp,
                                        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        themedShape(PopRadius.chip)
                                    )
                                    .clickable {
                                        if (!multiple) {
                                            onPicked(listOf(entry.file))
                                        } else {
                                            selected = if (on) selected - entry.file.name else selected + entry.file.name
                                        }
                                    }
                            ) {
                                AsyncImage(entry.file, "本地作品", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                if (on) {
                                    Box(
                                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp)
                                            .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) { PIcon(PopCheck, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(10.dp)) }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                    if (multiple) {
                        androidx.compose.material3.Button(
                            onClick = { onPicked(entries.map { it.file }.filter { it.name in selected }) },
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            shape = themedShape(PopRadius.field)
                        ) { Text("导入 ${selected.size}") }
                    }
                }
            }
        }
    }
}

/** 设置页的三段来源开关。点一下立即写回，不另做保存按钮。 */
@Composable
fun ImageImportSourceSwitch(
    selected: ImageImportSource,
    onSelect: (ImageImportSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Text(
            "导入图片来源",
            fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, color = c.onSurface,
            modifier = Modifier.semantics { heading() }
        )
        Text("之后每次导入图片都按这里走", fontSize = 10.sp, lineHeight = 15.sp, color = c.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(themedShape(PopRadius.field))
                .background(c.surface.copy(alpha = .68f))
                .border(.7.dp, c.outlineVariant, themedShape(PopRadius.field))
                .selectableGroup().padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ImageImportSource.entries.forEach { item ->
                val active = item == selected
                Box(
                    Modifier.weight(1f).heightIn(min = 36.dp).clip(themedShape(PopRadius.chip))
                        .background(if (active) c.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .selectable(active, role = Role.RadioButton) { onSelect(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        item.label,
                        fontSize = 12.sp, lineHeight = 16.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) c.onPrimaryContainer else c.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun LibrarySettingsSection(repository: SettingsRepository, source: ImageImportSource) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SettingsActionRow(
        icon = RefIcons.Folder,
        title = "打开本地文件夹",
        subtitle = "手机文件夹 Pictures/Imagine",
        status = "打开",
        accent = true,
        onClick = {
            if (!ImageUtils.openPublicImageFolder(context)) {
                android.widget.Toast.makeText(context, "没有能打开这个文件夹的文件管理器", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    )
    Spacer(Modifier.height(8.dp))
    Surface(
        shape = themedShape(PopRadius.card),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .68f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).background(MaterialTheme.colorScheme.surface, themedShape(PopRadius.chip)),
                    contentAlignment = Alignment.Center
                ) {
                    PIcon(PopGallery, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("图片来源", fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when (source) {
                            ImageImportSource.LOCAL -> "直接打开本地作品"
                            ImageImportSource.GALLERY -> "直接打开系统相册"
                            ImageImportSource.ASK -> "每次先问本地还是相册"
                        },
                        fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            ImageImportSourceSwitch(selected = source, onSelect = { next ->
                scope.launch { repository.saveInterface(imageImportSource = next.id) }
            })
        }
    }
}