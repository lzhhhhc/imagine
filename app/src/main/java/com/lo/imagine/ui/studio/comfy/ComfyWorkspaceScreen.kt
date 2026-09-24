package com.lo.imagine.ui.studio.comfy

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.gson.JsonObject
import com.lo.imagine.R
import com.lo.imagine.data.AppSettings
import com.lo.imagine.data.ImageRepository
import com.lo.imagine.data.comfy.*
import com.lo.imagine.ui.*
import com.lo.imagine.ui.edit.MaskDrawView
import com.lo.imagine.ui.PopChevronDown
import com.lo.imagine.ui.PopChevronUp
import com.lo.imagine.ui.studio.ReversePromptDialog
import com.lo.imagine.ui.theme.PopRadius
import com.lo.imagine.ui.theme.themedShape
import com.lo.imagine.util.ImageUtils
import kotlinx.coroutines.*
import java.io.File

private data class ComfyImageUploadTarget(
    val workflowId: String,
    val parameterId: String,
    val connection: ComfyConnection
)

private data class PendingComfyMask(
    val uri: android.net.Uri,
    val target: ComfyImageUploadTarget,
    val bitmap: Bitmap
)

/** 提示词全屏编辑：默认占满大半屏，写长提示词不再挤在两三行的小框里。 */
@Composable
private fun ComfyPromptFullscreenDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(initial) { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = MaterialTheme.colorScheme.background, shape = themedShape(PopRadius.sheet),
            border = BorderStroke(2.dp, celInk()), modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    PopIconButton(PopClose, "关闭", onClick = onDismiss, modifier = Modifier.size(32.dp), iconSize = 16.dp)
                }
                PopTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 440.dp),
                    placeholder = "输入提示词，支持多行", minLines = 16, maxLines = 32)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(onClick = { onConfirm(text) }, modifier = Modifier.weight(1f)) { Text("保存") }
                }
            }
        }
    }
}

/** 选图后的涂抹层。遮罩印进这一张图，再作为 LoadImage 上传，不进入修图页。 */
@Composable
private fun ComfyLoadImageMaskDialog(image: Bitmap, onDismiss: () -> Unit, onUpload: (Bitmap?) -> Unit) {
    val canvasHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.62f).dp
    var strokeWidth by remember { mutableFloatStateOf(40f) }
    var mask by remember { mutableStateOf<Bitmap?>(null) }
    var maskView by remember(image) { mutableStateOf<MaskDrawView?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加载图片 · 画遮罩") },
        text = {
            Column {
                Text("白色笔迹会印在这张图上再上传。不涂就按原图上传。", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                AndroidView(
                    factory = { viewContext ->
                        MaskDrawView(viewContext, image, maskColor = android.graphics.Color.WHITE).also { view ->
                            maskView = view
                            view.setStrokeWidth(strokeWidth)
                            view.onMaskChanged = { mask = it }
                        }
                    },
                    update = { view -> view.setStrokeWidth(strokeWidth) },
                    modifier = Modifier.fillMaxWidth().height(canvasHeight)
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("笔刷", style = MaterialTheme.typography.labelLarge)
                    PopSlider(strokeWidth, { strokeWidth = it }, valueRange = 12f..120f,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    TinyBadge("${strokeWidth.toInt()}px")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onUpload(mask) }) { Text(if (mask == null) "原图上传" else "上传涂抹图") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { maskView?.clearMask() ?: run { mask = null } }) { Text("清除") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
fun ComfyWorkspaceScreen(settings: AppSettings, imageRepository: ImageRepository, onSelectMode: (String) -> Unit, onPreview: () -> Unit) {
    val context = LocalContext.current
    val runtime = remember { ComfyRuntime.get(context) }
    val repository = runtime.repository
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var imageTarget by remember { mutableStateOf<ComfyImageUploadTarget?>(null) }
    var uploadingImageParameter by remember { mutableStateOf<String?>(null) }
    var pendingMask by remember { mutableStateOf<PendingComfyMask?>(null) }
    fun uploadPickedImage(uri: android.net.Uri, target: ComfyImageUploadTarget, mask: Bitmap?) {
        scope.launch {
            var local: LocalComfyImage? = null
            var painted: File? = null
            uploadingImageParameter = target.parameterId
            repository.clearError()
            try {
                val copied = copyComfyImage(context, uri)
                local = copied
                val uploadFile = if (mask == null) copied.file else {
                    val baked = withContext(Dispatchers.Default) {
                        val source = BitmapFactory.decodeFile(copied.file.path)
                            ?: error("图片无法解码，不能画遮罩")
                        try { ImageUtils.bakeMaskOntoImage(source, mask) } finally { source.recycle() }
                    }
                    File.createTempFile("comfy-mask-", ".png", context.cacheDir).also { file ->
                        painted = file
                        file.outputStream().use { baked.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        baked.recycle()
                    }
                }
                val filename = if (mask == null) copied.filename else copied.filename.substringBeforeLast('.') + "-mask.png"
                val uploaded = withContext(Dispatchers.IO) { runtime.backend.upload(target.connection, uploadFile, filename) }
                val current = repository.state.value
                require(current.selectedId == target.workflowId && current.connection.fingerprint() == target.connection.fingerprint()) {
                    "工作流或服务器已切换，请重新选择图片"
                }
                val selected = current.selected
                val parameterStillTargetsLoadImage = selected?.parameters?.any { parameter ->
                    parameter.id == target.parameterId && parameter.kind == ParameterKind.IMAGE && parameter.targets.any { input ->
                        input.input == "image" && selected.graph.getAsJsonObject(input.nodeId)?.get("class_type")?.asString == "LoadImage"
                    }
                } == true
                require(parameterStillTargetsLoadImage) { "参考图片绑定已变化，请重新打开工作流后选择" }
                repository.editParameter(target.workflowId, target.parameterId, value = uploaded.inputValue)
                repository.flushDraft()
                repository.change { it.copy(status = "参考图片已上传：${uploaded.inputValue}") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { repository.report(e) }
            finally {
                local?.file?.delete()
                painted?.delete()
                uploadingImageParameter = null
            }
        }
    }
    val imagePicker = com.lo.imagine.ui.rememberImageImport(
        com.lo.imagine.ui.ImageImportSource.fromId(settings.imageImportSource)
    ) { uris ->
        val uri = uris.firstOrNull()
        val target = imageTarget
        imageTarget = null
        if (uri == null || target == null) return@rememberImageImport
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }
            if (decoded == null) {
                repository.report(IllegalArgumentException("图片无法打开，请重新选择"))
            } else {
                pendingMask = PendingComfyMask(uri, target, decoded)
            }
        }
    }
    var connectionOpen by rememberSaveable { mutableStateOf(false) }
    var workflowsOpen by rememberSaveable { mutableStateOf(false) }
    var jobDetails by remember { mutableStateOf<ComfyJob?>(null) }
    /** 节点面板默认收起，用户只展开当前需要调整的节点。 */
    var expandedPanelIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    /** 长按节点卡进入的拖动排序；值为正在拖动的节点 ID。 */
    var draggingPanelId by remember { mutableStateOf<String?>(null) }
    /** 提示词全屏编辑的参数 ID。 */
    var promptEditor by remember { mutableStateOf<String?>(null) }
    /** 长按拖动后短暂屏蔽点击，避免松手误触发展开。 */
    var lastDragEndAt by remember { mutableLongStateOf(0L) }
    /** 生成页节点卡的拖动排序状态。 */
    val listState = rememberLazyListState()
    var panelsOrderOverride by remember { mutableStateOf<List<String>?>(null) }
    /** 正在从服务器拉取参数列表的节点类型。 */
    var fetchingTypes by remember { mutableStateOf<Set<String>>(emptySet()) }
    /** 任务记录仍保留用于恢复，但不在工作台参数区渲染历史列表。 */
    var expandedJobId by rememberSaveable { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var schema by remember { mutableStateOf<Map<String, JsonObject>>(emptyMap()) }
    var checkedFor by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var previewing by remember { mutableStateOf(false) }
    var textTask by remember { mutableStateOf<Pair<String, String>?>(null) }
    var reverseTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val translationPairs = remember { mutableStateMapOf<String, Pair<String, String>>() }
    val textHistory = remember { mutableStateMapOf<String, List<String>>() }
    val workflow = state.selected
    fun currentText(id: String): WorkflowParameter? = repository.state.value.selected?.parameters?.firstOrNull { it.id == id }
    fun setText(id: String, value: String, rememberPrevious: Boolean = true) {
        val current = currentText(id) ?: return
        if (current.value == value) return
        if (rememberPrevious) textHistory[id] = (textHistory[id].orEmpty() + current.value).takeLast(20)
        translationPairs.remove(id)
        val workflowId = repository.state.value.selected?.id ?: return
        repository.editParameter(workflowId, id, value = value)
    }
    fun undoText(id: String) {
        val history = textHistory[id].orEmpty()
        val previous = history.lastOrNull() ?: run { repository.report(IllegalStateException("这个输入还没有可撤回的修改")); return }
        textHistory[id] = history.dropLast(1)
        setText(id, previous, rememberPrevious = false)
    }
    fun translateText(id: String) {
        val value = currentText(id)?.value?.trim().orEmpty()
        if (value.isBlank() || textTask != null) return
        translationPairs[id]?.let { pair ->
            setText(id, if (value == pair.second) pair.first else pair.second)
            translationPairs[id] = pair
            return
        }
        textTask = id to "翻译中"
        scope.launch {
            imageRepository.translateText(settings, value)
                .onSuccess { translated -> setText(id, translated); translationPairs[id] = value to translated }
                .onFailure { repository.report(it) }
            textTask = null
        }
    }
    fun polishText(id: String) {
        val value = currentText(id)?.value?.trim().orEmpty()
        if (value.isBlank() || textTask != null) return
        textTask = id to "润色中"
        scope.launch {
            imageRepository.polishPrompt(settings, value, "gen")
                .onSuccess { setText(id, it) }
                .onFailure { repository.report(it) }
            textTask = null
        }
    }
    val fingerprint = "${state.connection.fingerprint()}/${workflow?.id}/${workflow?.updatedAt}"
    val latestJobId = state.jobs.firstOrNull()?.id
    LaunchedEffect(fingerprint) { schema = emptyMap(); checkedFor = null }
    // 新任务只更新内部恢复记录；工作台不自动把历史任务重新铺到页面上。
    LaunchedEffect(latestJobId) {
        expandedJobId = latestJobId
    }
    LaunchedEffect(workflow?.id, workflow?.updatedAt) {
        expandedPanelIds = emptySet()
    }
    LaunchedEffect(state.busy) { if (state.busy) while (true) { now = System.currentTimeMillis(); delay(1000) } }
    fun preview(path: String) {
        if (previewing) return
        previewing = true
        scope.launch {
            try {
                val entries = withContext(Dispatchers.IO) { ImageUtils.listHistory(context) }
                val index = entries.indexOfFirst { it.file.absolutePath == path }
                require(index >= 0) { "作品文件不存在" }
                val entry = entries[index]
                val bitmap = withContext(Dispatchers.IO) { ImageUtils.decodeFile(entry.file) } ?: error("无法打开图片")
                PreviewStore.bitmap = bitmap; PreviewStore.prompt = entry.meta.prompt; PreviewStore.model = entry.meta.model
                PreviewStore.workflowDetails = entry.meta.workflowDetails
                PreviewStore.elapsedText = ImageUtils.formatElapsed(entry.meta.elapsedSec); PreviewStore.sizeNote = null
                PreviewStore.historyList = entries; PreviewStore.historyIndex = index; onPreview()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { repository.report(e) }
            finally { previewing = false }
        }
    }
    /** 展开节点卡时顺带向服务器拉取该节点的 object_info，模型/LoRA 这类 combo 字段才有真实下拉项。 */
    fun togglePanel(nodeId: String, classType: String, opening: Boolean) {
        expandedPanelIds = if (opening) expandedPanelIds + nodeId else expandedPanelIds - nodeId
        if (!opening || classType.isBlank() || schema.containsKey(classType) || classType in fetchingTypes) return
        if (state.connection.baseUrl.isBlank() || workflow == null || !ComfyWorkflowEngine.nodeEnabled(workflow.graph, nodeId)) return
        scope.launch {
            fetchingTypes = fetchingTypes + classType
            try {
                val info = withContext(Dispatchers.IO) { runtime.backend.nodeInfo(state.connection.copy(), classType) }
                schema = schema + (classType to info)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { /* 拉取失败保持手填，不打断编辑 */ }
            finally { fetchingTypes = fetchingTypes - classType }
        }
    }
    /** 节点卡顺序：优先用已保存的 panelOrder，拖动时用临时覆盖，松手才落盘。 */
    val groupedPanels = workflow?.parameters?.groupBy { it.targets.firstOrNull()?.nodeId ?: it.id } ?: emptyMap()
    val basePanelIds = workflow?.let { w ->
        (w.panelOrder.filter { it in groupedPanels } + groupedPanels.keys.filter { id -> w.panelOrder.none { it == id } }).distinct()
    } ?: emptyList()
    val orderedPanels = (panelsOrderOverride ?: basePanelIds).mapNotNull { id -> groupedPanels[id]?.let { id to it } }
    val orderedIds by rememberUpdatedState(orderedPanels.map { it.first })
    val itemLifts = remember { mutableStateMapOf<String, Float>() }
    fun movePanel(from: Int, to: Int) {
        val current = panelsOrderOverride ?: orderedIds
        if (from !in current.indices || to !in current.indices || from == to) return
        panelsOrderOverride = current.toMutableList().apply { add(to, removeAt(from)) }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader("ComfyUI", titleOverride = { StudioModeSwitch(StudioMode.COMFY, onSelectMode) }, action = {
                ArkBlockAction(R.drawable.ic_ark_hex, "工作流", "WORKFLOW", { workflowsOpen = true })
            })
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.ready) {
                    Text(state.error ?: "正在读取工作台…", color = if (state.error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    if (state.error != null) TextButton(onClick = repository::reload) { Text("重新读取") }
                } else if (state.connection.baseUrl.isBlank()) {
                    ArkInkPanel {
                        Text("连接你的 ComfyUI", style = MaterialTheme.typography.titleSmall)
                        Text("配置电脑的局域网地址；图像由服务器生成。", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { connectionOpen = true }) { Text("配置服务器") }
                    }
                }
                state.error?.takeIf { state.ready }?.let { message ->
                    ArkInkPanel { Text(message, color = MaterialTheme.colorScheme.error); TextButton(onClick = repository::clearError) { Text("关闭提示") } }
                }
            }
        }
        if (workflow != null) {
            val panelCount = orderedPanels.size
            items(orderedPanels, key = { it.first }) { (nodeId, fields) ->
                val node = workflow.graph.get(nodeId)?.takeIf { it.isJsonObject }?.asJsonObject
                val classType = node?.get("class_type")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                val expanded = nodeId in expandedPanelIds
                val nodeActive = ComfyWorkflowEngine.nodeEnabled(workflow.graph, nodeId)
                val panelIndex by rememberUpdatedState(orderedPanels.indexOfFirst { it.first == nodeId })
                val lifted = itemLifts[nodeId] ?: 0f
                ArkInkPanel(Modifier
                    .padding(horizontal = 16.dp)
                    .zIndex(if (draggingPanelId == nodeId) 1f else 0f)
                    .graphicsLayer { translationY = lifted }) {
                    Surface(onClick = {
                        if (System.currentTimeMillis() - lastDragEndAt < 350) return@Surface
                        togglePanel(nodeId, classType, !expanded)
                    },
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .001f), shape = themedShape(PopRadius.field),
                        modifier = Modifier.pointerInput(workflow.id, nodeId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingPanelId = nodeId
                                    itemLifts[nodeId] = 0f
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    itemLifts[nodeId] = (itemLifts[nodeId] ?: 0f) + change.positionChange().y
                                    val step = (itemLifts[nodeId]!! / 64.dp.toPx()).toInt()
                                    if (step != 0) {
                                        movePanel(panelIndex, panelIndex + step)
                                        itemLifts[nodeId] = (itemLifts[nodeId]!! % 64.dp.toPx())
                                    }
                                },
                                onDragEnd = {
                                    draggingPanelId = null
                                    itemLifts[nodeId] = 0f
                                    lastDragEndAt = System.currentTimeMillis()
                                    repository.reorderPanels(workflow.id, orderedIds)
                                },
                                onDragCancel = { draggingPanelId = null; itemLifts[nodeId] = 0f }
                            )
                        }) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            PIcon(RefIcons.Sliders, "长按上下拖动调整顺序", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (node == null) fields.first().label else "${ComfyWorkflowEngine.title(workflow.graph, nodeId)} · $nodeId", style = MaterialTheme.typography.titleSmall)
                                if (classType.isNotBlank()) Text("$classType · ${fields.size} 个参数" + if (fetchingTypes.contains(classType)) " · 正在读取服务器列表…" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(if (nodeActive) "启用" else "停用", style = MaterialTheme.typography.labelSmall,
                                color = if (nodeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            PIcon(if (expanded) PopChevronUp else PopChevronDown, if (expanded) "收起节点面板" else "展开节点面板", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (expanded) {
                        fields.forEach { p ->
                            if (p.kind == ParameterKind.IMAGE) {
                                Text(if (p.value.isBlank()) "尚未上传参考图片" else "已上传：${p.value}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        imageTarget = ComfyImageUploadTarget(workflow.id, p.id, state.connection.copy())
                                        imagePicker.launch()
                                    }, enabled = nodeActive && state.connection.baseUrl.isNotBlank() && !state.busy && uploadingImageParameter == null, modifier = Modifier.weight(1f)) {
                                        Text(if (uploadingImageParameter == p.id) "上传中…" else if (p.value.isBlank()) "选择并上传图片" else "更换参考图片")
                                    }
                                    if (p.value.isNotBlank()) OutlinedButton(onClick = {
                                        repository.editParameter(workflow.id, p.id, value = "")
                                        scope.launch { repository.flushDraft() }
                                    }, enabled = !state.busy && uploadingImageParameter == null) { Text("清除") }
                                }
                                if (!nodeActive) Text("该节点已停用，启用后才能上传参考图片。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            } else if (p.kind in setOf(ParameterKind.PROMPT, ParameterKind.NEGATIVE)) {
                                PopTextField(p.value, {
                                    textHistory[p.id] = (textHistory[p.id].orEmpty() + p.value).takeLast(20)
                                    translationPairs.remove(p.id)
                                    repository.editParameter(workflow.id, p.id, value = it)
                                }, label = p.label,
                                    modifier = Modifier.heightIn(min = if (p.kind == ParameterKind.NEGATIVE) 180.dp else 240.dp),
                                    minLines = if (p.kind == ParameterKind.NEGATIVE) 7 else 10,
                                    maxLines = if (p.kind == ParameterKind.NEGATIVE) 16 else 28, enabled = textTask == null,
                                    trailingContent = {
                                        PopIconButton(RefIcons.Expand, "全屏编辑", onClick = { promptEditor = p.id },
                                            modifier = Modifier.size(30.dp), iconSize = 15.dp)
                                    })
                            } else {
                                val definition = schema[classType]?.getAsJsonObject("input")?.let { input ->
                                    input.getAsJsonObject("required")?.get(p.targets.firstOrNull()?.input) ?: input.getAsJsonObject("optional")?.get(p.targets.firstOrNull()?.input)
                                }?.takeIf { it.isJsonArray }?.asJsonArray
                                val options = ComfyWorkflowEngine.comboOptions(definition?.firstOrNull())
                                if (options != null) ComfyChoice(p.label, p.value, options) { repository.editParameter(workflow.id, p.id, value = it) }
                                else PopTextField(p.value, { repository.editParameter(workflow.id, p.id, value = it) }, label = p.label, singleLine = true, enabled = nodeActive && !p.randomSeed)
                                if (p.kind == ParameterKind.SEED) Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(p.randomSeed, onCheckedChange = { repository.editParameter(workflow.id, p.id, random = it) })
                                    Text("每次使用随机种子", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        fields.filter { it.kind in setOf(ParameterKind.PROMPT, ParameterKind.NEGATIVE) }.forEach { p ->
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                ArkTileButton(cn = "撤回", en = "UNDO", icon = R.drawable.ic_ark_undo,
                                    onClick = { undoText(p.id) }, enabled = textHistory[p.id].orEmpty().isNotEmpty() && textTask == null, modifier = Modifier.weight(1f))
                                ArkTileButton(cn = if (textTask?.first == p.id && textTask?.second == "翻译中") "翻译中" else "翻译",
                                    en = "TRANSLATE", glyph = { ArkTranslateGlyph() }, onClick = { translateText(p.id) }, enabled = p.value.isNotBlank() && textTask == null, modifier = Modifier.weight(1f))
                                ArkTileButton(cn = "图像", en = "IMAGE", icon = R.drawable.ic_ark_image,
                                    onClick = { reverseTargetId = p.id }, enabled = textTask == null, modifier = Modifier.weight(1f))
                                ArkTileButton(cn = if (textTask?.first == p.id && textTask?.second == "润色中") "润色中" else "润色",
                                    en = "ENHANCE", glyph = { PIcon(RefIcons.Wand, null, Modifier.size(RefUiTokens.tileIcon).padding(1.dp)) },
                                    onClick = { polishText(p.id) }, enabled = p.value.isNotBlank() && textTask == null, modifier = Modifier.weight(1f))
                                ArkTileButton(cn = "清空", en = "CLEAR", icon = R.drawable.ic_ark_trash,
                                    onClick = { setText(p.id, "") }, enabled = p.value.isNotBlank() && textTask == null, modifier = Modifier.weight(1f))
                            }
                            textTask?.takeIf { it.first == p.id }?.let { task ->
                                Text(task.second, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val disabledTargets = workflow.parameters.filter { it.targets.any { t -> !ComfyWorkflowEngine.nodeEnabled(workflow.graph, t.nodeId) } }
                    if (workflow.parameters.isEmpty()) Text("还没有展开节点。到工作流里勾选一个节点，它的全部参数会出现在这里。", style = MaterialTheme.typography.bodySmall)
                    if (disabledTargets.isNotEmpty()) Text("有 ${disabledTargets.size} 个参数位于停用节点上，启用后才会真正生效。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { scope.launch {
                        checking = true; repository.clearError()
                        try {
                            val connection = state.connection.copy()
                            val loaded = withContext(Dispatchers.IO) {
                                ComfyWorkflowEngine.enabledClassTypes(workflow.graph).associateWith { runtime.backend.nodeInfo(connection, it) }
                            }
                            ComfyWorkflowEngine.validateWithInfo(ComfyWorkflowEngine.prepare(workflow).graph, loaded)
                            schema = loaded; checkedFor = fingerprint
                            repository.change { it.copy(status = "工作流节点与参数检查通过") }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { repository.report(e) }
                        finally { checking = false }
                    } }, enabled = !checking && !state.busy && uploadingImageParameter == null && state.connection.baseUrl.isNotBlank()) {
                        Text(if (checking) "检查中…" else if (checkedFor == fingerprint) "重新检查工作流" else "检查服务器节点与参数")
                    }
                    ArkGenerateBar(loading = state.busy, enabled = state.ready && !state.busy && uploadingImageParameter == null && state.connection.baseUrl.isNotBlank(),
                        label = if (state.busy) state.status else "开始生成", sub = "COMFYUI WORKFLOW", onClick = { runtime.coordinator.generate(settings.autoSaveGallery) })
                    if (state.busy) {
                        Text(state.status, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = runtime.coordinator::pause) { Text("停止等待") }
                        Text("停止的是手机端等待，服务器可能继续运行。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // 任务记录仍写入本地用于恢复，但工作台只保留最新任务的图片预览，不再渲染历史任务列表。
        val previewPaths = state.jobs.firstOrNull()?.saved?.values?.toList().orEmpty()
        if (previewPaths.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RefSectionHeading("预览", "WORKFLOW PREVIEW")
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().height(168.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(previewPaths, key = { it }) { path ->
                            Surface(
                                shape = themedShape(PopRadius.card),
                                modifier = Modifier.size(156.dp).clickable { preview(path) }
                            ) {
                                AsyncImage(File(path), contentDescription = "查看生成图片", contentScale = ContentScale.Crop)
                            }
                        }
                    }
                }
            }
        }
    }
    pendingMask?.let { pending ->
        ComfyLoadImageMaskDialog(pending.bitmap, onDismiss = {
            pending.bitmap.recycle()
            pendingMask = null
        }, onUpload = { mask ->
            val current = pendingMask
            pendingMask = null
            if (current != null) uploadPickedImage(current.uri, current.target, mask)
        })
    }
    reverseTargetId?.let { targetId ->
        ReversePromptDialog(settings = settings, repository = imageRepository,
            onApply = { setText(targetId, it); reverseTargetId = null },
            onDismiss = { reverseTargetId = null }, targetModel = settings.genModel)
    }
    promptEditor?.let { pid ->
        val param = workflow?.parameters?.find { it.id == pid }
        if (param == null) promptEditor = null else ComfyPromptFullscreenDialog(
            title = param.label, initial = param.value,
            onConfirm = { value -> setText(pid, value); promptEditor = null },
            onDismiss = { promptEditor = null }
        )
    }
    if (connectionOpen) ComfyConnectionDialog { connectionOpen = false }
    if (workflowsOpen) ComfyWorkflows(runtime, settings, imageRepository) { workflowsOpen = false }
    jobDetails?.let { job -> ComfyDialog("任务参数", { jobDetails = null }) {
        if (job.phase == ComfyPhase.UNKNOWN) {
            Text("提交响应中断。请先确认服务器是否已有任务，避免重复出图。")
            OutlinedButton(onClick = { runtime.coordinator.acknowledgeUnknown(job.id); jobDetails = null }, enabled = !state.busy) {
                Text("已在电脑核对，允许再次生成")
            }
        }
        SelectionContainer { Text("${job.workflowName}\n${job.promptId ?: "尚未取得任务编号"}\n\n" + job.values.entries.joinToString("\n") { "${it.key}：${it.value}" }) }
    } }
}