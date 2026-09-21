package com.lo.imagine.ui.studio.comfy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.gson.JsonObject
import com.lo.imagine.R
import com.lo.imagine.data.AppSettings
import com.lo.imagine.data.comfy.*
import com.lo.imagine.ui.*
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

@Composable
fun ComfyWorkspaceScreen(settings: AppSettings, onSelectMode: (String) -> Unit, onPreview: () -> Unit) {
    val context = LocalContext.current
    val runtime = remember { ComfyRuntime.get(context) }
    val repository = runtime.repository
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var imageTarget by remember { mutableStateOf<ComfyImageUploadTarget?>(null) }
    var uploadingImageParameter by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = imageTarget
        imageTarget = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        scope.launch {
            var local: LocalComfyImage? = null
            uploadingImageParameter = target.parameterId
            repository.clearError()
            try {
                val copied = copyComfyImage(context, uri)
                local = copied
                val uploaded = withContext(Dispatchers.IO) { runtime.backend.upload(target.connection, copied.file, copied.filename) }
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
                uploadingImageParameter = null
            }
        }
    }
    var connectionOpen by rememberSaveable { mutableStateOf(false) }
    var workflowsOpen by rememberSaveable { mutableStateOf(false) }
    var paramsOpen by rememberSaveable { mutableStateOf(false) }
    var jobDetails by remember { mutableStateOf<ComfyJob?>(null) }
    var checking by remember { mutableStateOf(false) }
    var schema by remember { mutableStateOf<Map<String, JsonObject>>(emptyMap()) }
    var checkedFor by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var previewing by remember { mutableStateOf(false) }
    val workflow = state.selected
    val fingerprint = "${state.connection.fingerprint()}/${workflow?.id}/${workflow?.updatedAt}"
    LaunchedEffect(fingerprint) { schema = emptyMap(); checkedFor = null }
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
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader("ComfyUI", titleOverride = { StudioModeSwitch(StudioMode.COMFY, onSelectMode) }, action = {
                ArkBlockAction(R.drawable.ic_ark_transfer, "服务器", "SERVER", { connectionOpen = true })
            })
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(onClick = { workflowsOpen = true }, enabled = state.ready, color = MaterialTheme.colorScheme.surface.copy(alpha = .82f),
                    shape = themedShape(PopRadius.card), border = BorderStroke(.8.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(workflow?.name ?: "选择工作流", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(workflow?.let { "${it.parameters.size} 个可调参数 · ${it.outputNodes.size} 个图片输出" } ?: "导入 API 工作流，开始创作", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PIcon(RefIcons.Chevron, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
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
            items(workflow.parameters.filter { it.kind == ParameterKind.PROMPT || it.kind == ParameterKind.NEGATIVE }, key = { it.id }) { p ->
                PopTextField(p.value, { repository.editParameter(workflow.id, p.id, value = it) },
                    modifier = Modifier.padding(horizontal = 16.dp), label = p.label, minLines = if (p.kind == ParameterKind.PROMPT) 4 else 2, maxLines = 10)
            }
            items(workflow.parameters.filter { it.kind == ParameterKind.IMAGE }, key = { it.id }) { p ->
                ArkInkPanel(Modifier.padding(horizontal = 16.dp)) {
                    Text(p.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (p.value.isBlank()) "尚未上传参考图片"
                        else "已上传：${p.value}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            imageTarget = ComfyImageUploadTarget(workflow.id, p.id, state.connection.copy())
                            imagePicker.launch("image/*")
                        },
                        enabled = state.connection.baseUrl.isNotBlank() && !state.busy && uploadingImageParameter == null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uploadingImageParameter == p.id) "上传中…" else if (p.value.isBlank()) "选择并上传图片" else "更换参考图片")
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("生成参数", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { paramsOpen = !paramsOpen }) { Text(if (paramsOpen) "收起" else "展开") }
                    }
                    if (workflow.parameters.isEmpty()) Text("按工作流原值运行。可在工作流编辑页添加参数绑定。", style = MaterialTheme.typography.bodySmall)
                    if (paramsOpen) {
                        workflow.parameters.filter { it.kind !in setOf(ParameterKind.PROMPT, ParameterKind.NEGATIVE, ParameterKind.IMAGE) }.forEach { p ->
                            val target = p.targets.firstOrNull()
                            val type = target?.let { workflow.graph.getAsJsonObject(it.nodeId).get("class_type").asString }
                            val definition = schema[type]?.getAsJsonObject("input")?.let { fields ->
                                fields.getAsJsonObject("required")?.get(target?.input) ?: fields.getAsJsonObject("optional")?.get(target?.input)
                            }?.takeIf { it.isJsonArray }?.asJsonArray
                            val options = ComfyWorkflowEngine.comboOptions(definition?.firstOrNull())
                            if (options != null) ComfyChoice(p.label, p.value, options) { repository.editParameter(workflow.id, p.id, value = it) }
                            else PopTextField(p.value, { repository.editParameter(workflow.id, p.id, value = it) }, label = p.label, singleLine = true, enabled = !p.randomSeed)
                            if (type == "LoadImage" && target?.input == "image") Text(
                                "如需从手机选图上传，请在编辑页把此参数用途改为「参考图片」。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (p.kind == ParameterKind.SEED) Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(p.randomSeed, onCheckedChange = { repository.editParameter(workflow.id, p.id, random = it) })
                                Text("每次使用随机种子", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (workflow.parameters.none { it.kind == ParameterKind.BATCH }) Text("图片数量由工作流决定", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { scope.launch {
                        checking = true; repository.clearError()
                        try {
                            val connection = state.connection.copy()
                            val loaded = withContext(Dispatchers.IO) {
                                workflow.graph.entrySet().map { it.value.asJsonObject.get("class_type").asString }.distinct().associateWith { runtime.backend.nodeInfo(connection, it) }
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
        if (state.jobs.isNotEmpty()) item { RefSectionHeading("任务与结果", "WORKFLOW RESULTS", Modifier.padding(horizontal = 16.dp)) }
        items(state.jobs, key = { it.id }) { job ->
            ArkInkPanel(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(job.workflowName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(job.phase.label, style = MaterialTheme.typography.labelMedium, color = if (job.phase == ComfyPhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                if (state.busy && state.activeJobId == job.id) Text("已等待 ${((now - job.startedAt) / 1000).coerceAtLeast(0)} 秒", style = MaterialTheme.typography.bodySmall)
                if (job.message.isNotBlank()) Text(job.message, style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { jobDetails = job }) { Text("参数") }
                    if (job.phase !in setOf(ComfyPhase.SUCCEEDED, ComfyPhase.REMOVED) || job.images.any { it.key !in job.saved }) {
                        TextButton(onClick = { runtime.coordinator.resume(job.id) }, enabled = !state.busy) { Text(if (job.promptId == null) "确认任务" else "恢复查询") }
                    }
                    if (job.promptId != null && job.phase in setOf(ComfyPhase.QUEUED, ComfyPhase.PAUSED)) {
                        TextButton(onClick = { runtime.coordinator.removeQueued(job.id) }, enabled = !state.busy) { Text("移除排队") }
                    }
                }
                job.saved.values.toList().chunked(2).forEach { paths ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        paths.forEach { path ->
                            Surface(shape = themedShape(PopRadius.card), modifier = Modifier.weight(1f).aspectRatio(1f).clickable { preview(path) }) {
                                AsyncImage(File(path), contentDescription = "查看生成图片", contentScale = ContentScale.Crop)
                            }
                        }
                        if (paths.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
    if (connectionOpen) ComfyConnectionDialog { connectionOpen = false }
    if (workflowsOpen) ComfyWorkflows(runtime) { workflowsOpen = false }
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