package com.lo.imagine.ui.edit
import com.lo.imagine.ui.RefUiTokens
import androidx.compose.foundation.layout.heightIn


import androidx.compose.ui.unit.sp
import com.lo.imagine.ui.theme.PopRadius
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.lo.imagine.ui.PIcon
import com.lo.imagine.data.ApiResult
import com.lo.imagine.data.AppSettings
import com.lo.imagine.data.GenerationTasks
import com.lo.imagine.data.ASPECT_OPTIONS
import com.lo.imagine.data.EDIT_ACTIONS
import com.lo.imagine.data.EDIT_ACTIONS_CATEGORIES
import com.lo.imagine.data.ImageData
import com.lo.imagine.data.ImageRepository
import com.lo.imagine.data.QUALITY_TIERS
import com.lo.imagine.data.matchAspect
import com.lo.imagine.ui.theme.Moss
import com.lo.imagine.ui.theme.SignalSoft
import com.lo.imagine.ui.AspectGeometry
import com.lo.imagine.ui.CreativeBusyIcon
import com.lo.imagine.ui.PopNumericField
import com.lo.imagine.ui.ArkGenerateBar
import com.lo.imagine.ui.celInk
import com.lo.imagine.ui.celShadow
import com.lo.imagine.ui.DropdownField
import com.lo.imagine.ui.EditState
import com.lo.imagine.ui.EmojiChip
import com.lo.imagine.ui.ErrorPanel
import com.lo.imagine.ui.PopErrorDialog
import com.lo.imagine.ui.GenerationStatus
import com.lo.imagine.ui.HorizontalChips
import com.lo.imagine.ui.ImagePreview
import com.lo.imagine.ui.ImageResultCard
import com.lo.imagine.ui.LoadingLabel
import com.lo.imagine.ui.Panel
import com.lo.imagine.ui.PopBrush
import com.lo.imagine.ui.PopChevron
import com.lo.imagine.ui.PopGallery
import com.lo.imagine.ui.PopImageAdd
import com.lo.imagine.ui.PopIconButton
import com.lo.imagine.ui.PopInspect

import com.lo.imagine.ui.PopRefresh
import com.lo.imagine.ui.PopSlider
import com.lo.imagine.ui.PopSpark
import com.lo.imagine.ui.PopTextField
import com.lo.imagine.ui.PopTrash
// popBackdrop 已由壳层 ArkPageBackdrop 接管
import com.lo.imagine.ui.PreviewStore
import com.lo.imagine.ui.ScreenHeader
import com.lo.imagine.ui.SectionTitle
import com.lo.imagine.ui.TinyBadge
import com.lo.imagine.ui.theme.Ink
import com.lo.imagine.ui.theme.LocalPopAccents
import com.lo.imagine.ui.theme.themedCorner
import com.lo.imagine.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.Semaphore
import kotlinx.coroutines.launch

@Composable
fun EditScreen(
    settings: AppSettings,
    repository: ImageRepository,
    onPreview: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<android.net.Uri?>(null) }
    /** 当前正在涂遮罩的图索引；null=关闭 */
    var editingMaskIndex by remember { mutableStateOf<Int?>(null) }
    var polishing by remember { mutableStateOf(false) }
    var reverseRunning by remember { mutableStateOf(false) }
    var editCountText by remember { mutableStateOf(EditState.count.coerceIn(1, 4).toString()) }
    var editCountFocused by remember { mutableStateOf(false) }

    fun commitEditCount() {
        val next = editCountText.toIntOrNull()?.coerceIn(1, 4) ?: 1
        editCountText = next.toString()
        EditState.count = next
    }

    LaunchedEffect(EditState.count, editCountFocused) {
        if (!editCountFocused) editCountText = EditState.count.coerceIn(1, 4).toString()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val fresh = mutableListOf<android.graphics.Bitmap>()
            for (uri in uris) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                val bitmap = bytes?.let { raw ->
                    android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size)
                }
                if (bitmap != null) fresh += bitmap
            }
            if (fresh.isNotEmpty()) {
                // 追加合并：保留上一张，最多 6 张；已有遮罩按索引保留
                val merged = (EditState.refBitmaps + fresh).take(6)
                EditState.refBitmaps = merged
                sourceUri = uris.first()
                // 多图模式下源图 = 全部图拼合（仅用于整图重绘的回退路径）
                val combined = ImageUtils.combineReferenceGrid(merged)
                val (editBitmap, editBytes) = ImageUtils.prepareForEdit(combined)
                EditState.setSource(
                    bitmap = editBitmap,
                    bytes = editBytes,
                    mime = "image/jpeg"
                )
            }
        }
    }

    LaunchedEffect(EditState.loading) {
        while (EditState.loading) {
            EditState.elapsed = ((System.currentTimeMillis() - EditState.startedAt) / 1000).toInt().coerceAtLeast(0)
            delay(500)
        }
    }

    fun runEdit() {
        if (EditState.refBitmaps.isEmpty()) return
        val prompt = EditState.prompt.trim()
        if (prompt.isBlank() || EditState.loading) return
        com.lo.imagine.data.imageApiConfigurationError(settings)?.let {
            EditState.error = it
            return
        }
        EditState.loading = true
        EditState.startedAt = System.currentTimeMillis()
        EditState.elapsed = 0
        EditState.error = null
        EditState.results = emptyList()

        if (EditState.refBitmaps.isEmpty()) return
        com.lo.imagine.data.GenerationTasks.launch(
            context = context,
            doneTitle = "修图完成",
            failTitle = "修图失败",
            work = {
                var summary = ""
                var taskOk = false
                try {
                    // 每张参考图独立编码；遮罩按图各自生效
                    val perImage = withContext(Dispatchers.Default) {
                        EditState.refBitmaps.mapIndexed { imageIndex, bmp ->
                            val prepared = ImageUtils.prepareForEdit(bmp)
                            // 图片可能因最长边限制被降采样；遮罩必须按实际发送图片的尺寸编码
                            val maskPng = EditState.refMasks[imageIndex]?.let { previewMask ->
                                ImageUtils.encodeMaskPng(previewMask, prepared.first.width, prepared.first.height)
                            }
                            prepared.second to maskPng
                        }
                    }
                    val n = perImage.size.coerceAtLeast(1)
                    // 张数始终由用户设定决定；多参考图时任务轮流分配到各张参考图（图少张多则复用，图多张少则只处理前几张）
                    val taskCount = EditState.count.coerceIn(1, 4)
                    val tasks = List(taskCount) { idx ->
                        val (imgBytes, maskBytes) = perImage[idx % n]
                        suspend {
                            repository.editImage(
                                settings = settings,
                                imageBytes = imgBytes,
                                mimeType = "image/jpeg",
                                prompt = prompt,
                                negativePrompt = "lowres, blurry, watermark, distorted, unwanted changes",
                                size = EditState.size,
                                count = 1,
                                maskBytes = maskBytes
                            )
                        }
                    }
                    val errs = mutableListOf<String>()
                    val newResults = mutableListOf<com.lo.imagine.data.ImageData>()
                    // 2 路并发：多参考图时总时长约减半；限流风险由信号量控制
                    val ordered = coroutineScope {
                        val sem = Semaphore(2)
                        tasks.map { task ->
                            async {
                                sem.acquire()
                                try {
                                    runCatching { task() }
                                } finally {
                                    sem.release()
                                }
                            }
                        }.awaitAll()
                    }
                    for (r in ordered) {
                        val res = r.getOrNull()
                        if (res is ApiResult.Success) {
                            // 画质兜底：上游若忽略 size，放大到所选画质档长边
                            val sizeParts = EditState.size.split("x")
                            val targetW = sizeParts.getOrNull(0)?.toIntOrNull() ?: 0
                            val targetH = sizeParts.getOrNull(1)?.toIntOrNull() ?: 0
                            val taskOut = mutableListOf<com.lo.imagine.data.ImageData>()
                            res.images.forEach { img ->
                                val rawBitmap = repository.resolveBitmap(img)
                                val finalBitmap = if (rawBitmap != null && !settings.upscaleEnabled) {
                                    rawBitmap
                                } else {
                                    ImageUtils.ensureResolution(rawBitmap, targetW, targetH)
                                }
                                val upstreamNote = rawBitmap?.let { "${it.width}x${it.height}" }
                                if (rawBitmap != null) {
                                    android.util.Log.i(
                                        "ImagineHttp",
                                        "upstream $upstreamNote → target ${targetW}x${targetH}" +
                                            if (finalBitmap !== rawBitmap) " (已补齐)" else ""
                                    )
                                }
                                val out: com.lo.imagine.data.ImageData? = when {
                                    rawBitmap != null && finalBitmap !== rawBitmap -> {
                                        val upscaledB64 = android.util.Base64.encodeToString(
                                            ImageUtils.bitmapToJpegBytes(finalBitmap),
                                            android.util.Base64.NO_WRAP
                                        )
                                        // b64 换成补齐后的版本；url 置空防结果卡渲染上游原图；
                                        // upstreamSize 记录模型真实产出，预览页可见
                                        img.copy(b64Json = upscaledB64, url = null, upstreamSize = upstreamNote)
                                    }
                                    rawBitmap != null -> img.copy(upstreamSize = upstreamNote)
                                    else -> {
                                        // 成功状态但数据不可解码（b64 截断/损坏）：按失败计，
                                        // 不能让空白结果卡混进 results 伪装成成功
                                        errs.add("图片数据损坏或被截断，无法解码为图像")
                                        null
                                    }
                                }
                                if (out != null) {
                                    taskOut += out
                                    newResults += out
                                }
                                if (rawBitmap != null) {
                                    ImageUtils.archiveResult(
                                        context = context,
                                        bitmap = finalBitmap,
                                        prompt = prompt,
                                        model = settings.editModel,
                                        kind = "edit",
                                        elapsedSec = (System.currentTimeMillis() - EditState.startedAt) / 1000,
                                        toGallery = settings.autoSaveGallery
                                    )
                                }
                            }
                            if (res.images.isEmpty()) {
                                // 成功状态但 images 列表为空：同样按失败计，防止通知误报成功
                                errs.add("上游未返回任何图片（响应可能被截断或额度不足）")
                            }
                            // 结果区用补齐/打标后的数据，而不是上游原始返回
                            EditState.results = EditState.results + taskOut
                        } else {
                            // 关键修复：ApiResult.Error（HTTP/上游错误）此前被 res == null 分支漏掉，
                            // 错误被吞 → 汇总误报「全部成功」、状态栏也报完成
                            val msg = (res as? ApiResult.Error)?.message
                                ?: r.exceptionOrNull()?.message
                                ?: "修改失败"
                            errs.add(msg)
                        }
                    }
                    EditState.round += 1
                    summary = when {
                        newResults.isNotEmpty() && errs.isNotEmpty() ->
                            "${errs.size} 张失败 · 其余 ${newResults.size} 张已存入作品库"
                        errs.isNotEmpty() -> "全部失败：${errs.firstOrNull() ?: "未知错误"}"
                        else -> "全部成功 · 已存入作品库"
                    }
                    // 显式成败：零失败且真的出了图才算成功
                    taskOk = errs.isEmpty() && newResults.isNotEmpty()
                    // 全部失败时弹窗完整展示报错；部分失败也弹，给出失败原因
                    if (errs.isNotEmpty()) {
                        EditState.error = if (newResults.isEmpty()) {
                            errs.firstOrNull() ?: "修改失败"
                        } else {
                            "部分失败（${errs.size}/${taskCount}）：${errs.firstOrNull() ?: "未知错误"}"
                        }
                    }
                } catch (e: Exception) {
                    EditState.error = e.message ?: "修改失败"
                    summary = "失败：${e.message ?: "未知错误"}"
                } finally {
                    EditState.loading = false
                    PreviewStore.elapsedText = ImageUtils.formatElapsed((System.currentTimeMillis() - EditState.startedAt) / 1000)
                }
                com.lo.imagine.data.TaskOutcome(summary, taskOk)
            }
        )
    }

    fun polish() {
        if (polishing) return
        // 修图润色：把当前源图作为 vision 输入发给 LLM，让它真正看到原图再扩写
        val srcBitmap = EditState.sourceBitmap
        val rawPrompt = EditState.prompt.ifBlank { "请基于这张图给出一条合适的修图建议" }
        polishing = true
        scope.launch {
            val imageB64 = srcBitmap?.let {
                withContext(Dispatchers.Default) {
                    android.util.Base64.encodeToString(
                        ImageUtils.bitmapToJpegBytes(it, quality = 85),
                        android.util.Base64.NO_WRAP
                    )
                }
            }
            repository.polishPrompt(settings, rawPrompt, "edit", imageB64)
                .onSuccess { polished -> EditState.prompt = polished }
                .onFailure { e -> EditState.error = e.message ?: "润色失败" }
            polishing = false
        }
    }

    /**
     * 反推提示词：把图片丢给 vision LLM，让它直接写出一段可用的英文 prompt，
     * 用途是「看到喜欢的图，想照着改但不知道写什么」的场景。
     */
    fun reversePrompt() {
        if (reverseRunning) return
        val srcBitmap = EditState.sourceBitmap ?: return
        reverseRunning = true
        scope.launch {
            val imageB64 = withContext(Dispatchers.Default) {
                android.util.Base64.encodeToString(
                    ImageUtils.bitmapToJpegBytes(srcBitmap, quality = 85),
                    android.util.Base64.NO_WRAP
                )
            }
            repository.reversePrompt(settings, imageB64, settings.editModel)
                .onSuccess { prompt -> EditState.prompt = prompt }
                .onFailure { e -> EditState.error = e.message ?: "反推失败" }
            reverseRunning = false
        }
    }

    fun openResult(data: ImageData) {
        scope.launch {
            val bitmap = repository.resolveBitmap(data)
            if (bitmap != null) {
                PreviewStore.bitmap = bitmap
                PreviewStore.prompt = EditState.prompt
                PreviewStore.model = settings.editModel
                PreviewStore.historyList = null // 创作/修图入口不支持左右切换
                onPreview()
            } else {
                EditState.error = "图片加载失败，请稍后重试"
            }
        }
    }

    fun continueWith(data: ImageData) {
        scope.launch {
            val bitmap = repository.resolveBitmap(data) ?: return@launch
            val (editBitmap, editBytes) = ImageUtils.prepareForEdit(bitmap)
            EditState.setSource(editBitmap, editBytes, "image/jpeg")
            EditState.refBitmaps = listOf(editBitmap)
            sourceUri = null
            EditState.prompt = ""
            EditState.results = emptyList()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Surface(
                color = Color.Transparent,
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                ArkGenerateBar(
                    loading = EditState.loading,
                    enabled = EditState.loading || (EditState.sourceBytes != null && EditState.refBitmaps.isNotEmpty() &&
                        EditState.prompt.isNotBlank()),
                    label = if (EditState.loading) {
                        "${EditState.elapsed}s · ${EditState.results.size}/${EditState.count.coerceIn(1, 4)} · 点此取消"
                    } else {
                        "生成 ${EditState.count} 张修改"
                    },
                    sub = if (EditState.loading) "WAITING / TAP TO CANCEL" else "EDIT GENERATE",
                    onClick = { if (EditState.loading) EditState.loading = false else runEdit() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = RefUiTokens.pageInset, vertical = 14.dp).heightIn(min = RefUiTokens.actionHeight)
                )
            }
        }
    ) { scaffoldPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(bottom = 24.dp)
    ) {
        ScreenHeader(
            title = "修图",
            action = {
                if (EditState.round > 0) TinyBadge("第 ${EditState.round} 轮", accent = true)
            }
        )

        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
            // ===== 遮罩绘制状态（原图卡铅笔入口使用） =====
            var showMaskDialog by remember { mutableStateOf(false) }
            // 选完图是否直接进入涂抹（局部重绘触发的选图走这条路径）
            var openMaskAfterPick by remember { mutableStateOf(false) }
            val maskPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    val raw = bytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
                    if (raw != null) {
                        sourceUri = uri
                        val (editBitmap, editBytes) = ImageUtils.prepareForEdit(raw)
                        EditState.setSource(editBitmap, editBytes, "image/jpeg")
                        // 局部重绘换图为单图语义：参考图列表重置为这一张
                        EditState.refBitmaps = listOf(editBitmap)
                        EditState.refMasks.clear()
                        editingMaskIndex = 0
                        // 换图后旧遮罩与新图尺寸不符，必须清掉
                        EditState.maskBitmap = null
                        EditState.maskVersion += 1
                        if (openMaskAfterPick) {
                            openMaskAfterPick = false
                            showMaskDialog = true
                        }
                    }
                }
            }

            if (EditState.sourceBitmap == null) {
                Surface(
                    onClick = {
                        picker.launch("image/*")
                    },
                    shape = com.lo.imagine.ui.theme.themedShape(PopRadius.card),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .62f)),
                    modifier = Modifier.fillMaxWidth().height(188.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = com.lo.imagine.ui.theme.themedShape(PopRadius.card),
                        tonalElevation = 0.dp,
                        border = androidx.compose.foundation.BorderStroke(.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = .6f))
                    ) {
                                PIcon(
                                    PopImageAdd,
                                    contentDescription = null,
                                    modifier = Modifier.padding(14.dp).size(34.dp)
                                )
                            }
                            Spacer(Modifier.height(13.dp))
                            Text("选择图片", fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(7.dp))
                            Text("点击上传需要修改的图片", fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("JPG / PNG / WEBP", fontSize = 8.sp, lineHeight = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = com.lo.imagine.ui.theme.themedShape(PopRadius.card),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .62f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        // ===== 一张图一行：每张可独立涂遮罩 =====
                        EditState.refBitmaps.forEachIndexed { imgIdx, bmp ->
                            Surface(
                                shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(com.lo.imagine.ui.theme.themedShape(PopRadius.chip))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        ImagePreview(
                                            bitmap = bmp,
                                            modifier = Modifier.fillMaxSize(),
                                            contentDescription = "参考图 ${imgIdx + 1}"
                                        )
                                    }
                                    Spacer(Modifier.width(9.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "图片 ${imgIdx + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            if (EditState.refMasks.containsKey(imgIdx)) "已涂遮罩 · 局部重绘" else "整图重绘 · 可点铅笔涂遮罩",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (EditState.refMasks.containsKey(imgIdx))
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    // 小铅笔：涂这张图的遮罩
                                    Box {
                                        com.lo.imagine.ui.PopIconButton(
                                            icon = PopBrush,
                                            contentDescription = "涂遮罩",
                                            onClick = {
                                                editingMaskIndex = imgIdx
                                                showMaskDialog = true
                                            },
                                            modifier = Modifier.size(32.dp),
                                            iconSize = 16.dp
                                        )
                                        if (EditState.refMasks.containsKey(imgIdx)) {
                                            Box(
                                                modifier = Modifier
                                                    .align(androidx.compose.ui.Alignment.TopEnd)
                                                    .size(9.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(com.lo.imagine.ui.theme.LocalPopAccents.current.a)
                                                    .border(
                                                        1.dp,
                                                        com.lo.imagine.ui.theme.Ink,
                                                        androidx.compose.foundation.shape.CircleShape
                                                    )
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    com.lo.imagine.ui.PopIconButton(
                                        icon = PopTrash,
                                        contentDescription = "删除这张图",
                                        onClick = {
                                            val newMasks = mutableMapOf<Int, android.graphics.Bitmap>()
                                            EditState.refMasks.forEach { (k, v) ->
                                                when {
                                                    k < imgIdx -> newMasks[k] = v
                                                    k > imgIdx -> newMasks[k - 1] = v
                                                }
                                            }
                                            EditState.refMasks.clear()
                                            EditState.refMasks.putAll(newMasks)
                                            val next = EditState.refBitmaps.filterIndexed { j, _ -> j != imgIdx }
                                            EditState.refBitmaps = next
                                            if (next.isEmpty()) {
                                                EditState.reset()
                                                sourceUri = null
                                            }
                                        },
                                        modifier = Modifier.size(32.dp),
                                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        iconSize = 16.dp
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        // 底部：新增图片（追加，不顶替）
                        Surface(
                            onClick = { picker.launch("image/*") },
                            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                PIcon(
                                    PopImageAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "新增图片",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (showMaskDialog && EditState.refBitmaps.isNotEmpty()) {
                val mIdx = (editingMaskIndex ?: 0).coerceIn(0, EditState.refBitmaps.size - 1)
                MaskDialog(
                    image = EditState.refBitmaps[mIdx],
                    initialMask = EditState.refMasks[mIdx],
                    onMaskChanged = { mask -> if (mask != null) EditState.refMasks[mIdx] = mask else EditState.refMasks.remove(mIdx) },
                    onDismiss = { showMaskDialog = false },
                    onChangeImage = {
                        showMaskDialog = false
                        openMaskAfterPick = true
                        maskPicker.launch("image/*")
                    }
                )
            }

            SectionTitle("快捷修改")
            var showPresetDialog by remember { mutableStateOf(false) }
            var presetCategory by remember { mutableStateOf("全部") }
            Surface(
                onClick = { showPresetDialog = true },
                shape = com.lo.imagine.ui.theme.themedShape(PopRadius.card),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
                shadowElevation = 1.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .58f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    PIcon(
                        PopSpark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    val currentPreset = EDIT_ACTIONS.firstOrNull { it.prompt == EditState.prompt }
                    Text(
                        currentPreset?.label ?: "选择修改预设",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (currentPreset != null) {
                        TinyBadge(currentPreset.category, accent = true)
                        Spacer(Modifier.width(6.dp))
                    }
                    PIcon(
                        PopChevron,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (showPresetDialog) {
                PresetDialog(
                    currentCategory = presetCategory,
                    onCategoryChange = { presetCategory = it },
                    onPick = { action ->
                        EditState.prompt = action.prompt
                        showPresetDialog = false
                    },
                    onClear = {
                        EditState.prompt = ""
                        showPresetDialog = false
                    },
                    onDismiss = { showPresetDialog = false }
                )
            }

            SectionTitle("修改描述")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                EditPromptAction(
                    icon = PopInspect,
                    label = if (reverseRunning) "反推中" else "反推提示词",
                    onClick = { reversePrompt() },
                    enabled = EditState.sourceBitmap != null && !reverseRunning && !polishing,
                    modifier = Modifier.weight(1f)
                )
                EditPromptAction(
                    icon = PopSpark,
                    label = if (polishing) "润色中" else "润色",
                    onClick = { polish() },
                    enabled = EditState.sourceBitmap != null && !polishing && !reverseRunning,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(9.dp))
            Panel {
                PopTextField(
                    value = EditState.prompt,
                    onValueChange = { EditState.prompt = it },
                    placeholder = "例如：保留人物，把背景换成樱花树林…",
                    minLines = 2,
                    maxLines = 7,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${EditState.prompt.length} / 500", fontSize = 9.sp, lineHeight = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End).padding(top = 6.dp))
            }
            Spacer(Modifier.height(2.dp))

            val editAspect = ASPECT_OPTIONS.firstOrNull { it.label == EditState.aspectLabel }
                ?: ASPECT_OPTIONS.first()
            val editQuality = QUALITY_TIERS.firstOrNull { it.id == EditState.qualityId }
                ?: QUALITY_TIERS[1]
            var outputExpanded by remember { mutableStateOf(false) }
            Spacer(Modifier.height(12.dp))
            Surface(onClick = { outputExpanded = !outputExpanded }, shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field),
                color = MaterialTheme.colorScheme.surface, border = BorderStroke(.7.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("输出设置", fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${editAspect.label} · ${editQuality.label} · ${EditState.count} 张", fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(7.dp))
                    PIcon(if (outputExpanded) com.lo.imagine.ui.PopChevronUp else com.lo.imagine.ui.PopChevronDown,
                        null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (outputExpanded) Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("输出设置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${editAspect.label} · ${editQuality.label} · ${EditState.count} 张",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TinyBadge(EditState.size, accent = true)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("画幅", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        DropdownField(
                            selected = editAspect.label,
                            options = ASPECT_OPTIONS.map { it.label },
                            menuHeight = 220,
                            leadingContent = { AspectGeometry(editAspect) },
                            optionLeadingContent = { label ->
                                ASPECT_OPTIONS.firstOrNull { it.label == label }?.let { AspectGeometry(it) }
                            },
                            onSelect = { label ->
                                ASPECT_OPTIONS.firstOrNull { it.label == label }?.let { picked ->
                                    EditState.aspectLabel = picked.label
                                    val quality = QUALITY_TIERS.firstOrNull { it.id == EditState.qualityId }
                                        ?: QUALITY_TIERS[1]
                                    EditState.size = picked.sizeFor(quality.longEdge)
                                }
                            }
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("画质", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        DropdownField(
                            selected = editQuality.label,
                            options = QUALITY_TIERS.map { it.label },
                            menuHeight = 200,
                            onSelect = { label ->
                                QUALITY_TIERS.firstOrNull { it.label == label }?.let { tier ->
                                    EditState.qualityId = tier.id
                                    val aspect = ASPECT_OPTIONS.firstOrNull { it.label == EditState.aspectLabel }
                                        ?: ASPECT_OPTIONS.first()
                                    EditState.size = aspect.sizeFor(tier.longEdge)
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("张数", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        PopNumericField(
                            value = editCountText,
                            onValueChange = { value -> editCountText = value.filter { it.isDigit() } },
                            onCommit = { commitEditCount() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            if (EditState.error != null) {
                PopErrorDialog(
                    message = EditState.error ?: "",
                    title = "修图失败",
                    onDismiss = { EditState.error = null }
                )
            }

            // 主操作固定在底部，参数滚动时无需反复寻找按钮。

            if (EditState.results.isNotEmpty()) {
                SectionTitle("修改结果")
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().height(if (EditState.results.size > 2) 380.dp else 190.dp)
                ) {
                    gridItems(EditState.results) { data ->
                        Box {
                            ImageResultCard(
                                data = data,
                                modifier = Modifier.fillMaxWidth().height(180.dp),
                                onClick = { openResult(data) }
                            )
                            PopIconButton(
                                icon = PopRefresh,
                                contentDescription = "继续编辑",
                                onClick = { continueWith(data) },
                                modifier = Modifier.align(Alignment.BottomStart).padding(7.dp).size(34.dp),
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
                                iconTint = MaterialTheme.colorScheme.onSurface,
                                iconSize = 18.dp,
                                shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }

    }
}

@Composable
private fun EditPromptAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field),
        color = if (accent) LocalPopAccents.current.a else MaterialTheme.colorScheme.surface,
        contentColor = if (accent) com.lo.imagine.ui.theme.Ink else MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = if (accent) 1.dp else 0.dp,
        border = BorderStroke(
            1.2.dp,
            if (accent) com.lo.imagine.ui.theme.Ink
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)
        ),
        modifier = modifier.height(42.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
        ) {
            if (label.endsWith("中")) {
                CreativeBusyIcon(Modifier.size(18.dp))
            } else {
                PIcon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.size(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun PresetDialog(
    currentCategory: String,
    onCategoryChange: (String) -> Unit,
    onPick: (com.lo.imagine.data.EditAction) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var category by remember(currentCategory) { mutableStateOf(currentCategory) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.sheet),
            border = BorderStroke(2.dp, celInk()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 26.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "预设库",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClear) { Text("清空") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Spacer(Modifier.height(10.dp))
                // 分类筛选条（横滚）
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    items(EDIT_ACTIONS_CATEGORIES.size) { i ->
                        val cat = EDIT_ACTIONS_CATEGORIES[i]
                        val selected = cat == category
                        Surface(
                            onClick = { category = cat },
                            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                            color = if (selected) SignalSoft else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (selected) Moss else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    cat,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) Ink else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                val visible = remember(category) {
                    if (category == "全部") EDIT_ACTIONS
                    else EDIT_ACTIONS.filter { it.category == category }
                }
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                ) {
                    items(visible.size) { i ->
                        val action = visible[i]
                        Surface(
                            onClick = { onPick(action) },
                            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                                    border = androidx.compose.foundation.BorderStroke(.8.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        PIcon(PopSpark, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(Modifier.size(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            action.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.size(6.dp))
                                        Surface(
                                            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                action.category,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        action.short,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                PIcon(
                                    PopChevron,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 全屏遮罩绘制弹窗 */
@Composable
private fun MaskDialog(
    image: android.graphics.Bitmap,
    initialMask: android.graphics.Bitmap?,
    onMaskChanged: (android.graphics.Bitmap?) -> Unit,
    onDismiss: () -> Unit,
    onChangeImage: (() -> Unit)? = null
) {
    val src = image
    var strokeWidth by remember { mutableStateOf(40f) }
    var maskView by remember(src) { mutableStateOf<MaskDrawView?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.sheet),
            border = BorderStroke(2.dp, celInk()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "涂抹要重绘的区域",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (onChangeImage != null) {
                        TextButton(onClick = onChangeImage) { Text("换图") }
                    }
                    TextButton(onClick = {
                        maskView?.clearMask() ?: onMaskChanged(null)
                    }) { Text("清除") }
                    TextButton(onClick = onDismiss) {
                        Text("完成", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                AndroidView(
                    factory = { viewContext ->
                        MaskDrawView(
                            context = viewContext,
                            source = src,
                            initialMask = initialMask
                        ).also { view ->
                            maskView = view
                            view.setStrokeWidth(strokeWidth)
                            view.onMaskChanged = onMaskChanged
                        }
                    },
                    update = { view ->
                        maskView = view
                        view.setStrokeWidth(strokeWidth)
                        if (view.currentMask() !== initialMask) {
                            view.setMaskBitmap(initialMask)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(340.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("笔刷", style = MaterialTheme.typography.labelLarge)
                    PopSlider(
                        value = strokeWidth,
                        onValueChange = { strokeWidth = it },
                        valueRange = 12f..120f,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    )
                    TinyBadge("${strokeWidth.toInt()}px")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "涂过的区域会被 AI 重新绘制，其余部分保持原样。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
