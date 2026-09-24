package com.lo.imagine.ui.studio.comfy

import android.provider.OpenableColumns
import com.google.gson.JsonObject
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lo.imagine.data.AppSettings
import com.lo.imagine.data.ImageRepository
import com.lo.imagine.data.comfy.*
import com.lo.imagine.ui.ArkInkPanel
import com.lo.imagine.ui.PopDownload
import com.lo.imagine.ui.PopEdit
import com.lo.imagine.ui.PopIconButton
import com.lo.imagine.ui.PopTextField
import com.lo.imagine.ui.PopTrash
import com.lo.imagine.ui.PIcon
import com.lo.imagine.ui.RefIcons

import com.lo.imagine.ui.theme.PopRadius
import com.lo.imagine.ui.theme.themedShape
import kotlinx.coroutines.*

@Composable
internal fun ComfyWorkflows(runtime: ComfyRuntime, settings: AppSettings, imageRepository: ImageRepository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by runtime.repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf<ComfyWorkflow?>(null) }
    var paste by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ComfyWorkflow?>(null) }
    var exportText by remember { mutableStateOf("") }
    var workflowSearch by remember { mutableStateOf("") }
    var analysisNote by remember { mutableStateOf<String?>(null) }
    suspend fun importGraph(text: String, name: String) {
        val graph = withContext(Dispatchers.Default) { ComfyWorkflowEngine.parse(text) }
        val analyzed = imageRepository.analyzeComfyWorkflow(settings, ComfyWorkflowEngine.digest(graph))
        val parsed = analyzed.mapCatching { reply ->
            withContext(Dispatchers.Default) { ComfyWorkflowEngine.nodesFromAnalysis(graph, reply) }
        }
        val found = parsed.getOrElse { emptyList() }
        analysisNote = parsed.exceptionOrNull()?.message
        edit = ComfyWorkflow(
            name = name.removeSuffix(".json").ifBlank { "新工作流" }, graph = graph,
            parameters = found.flatMap { ComfyWorkflowEngine.panelParameters(graph, it) },
            outputNodes = ComfyWorkflowEngine.suggestedOutputs(graph)
        )
        paste = false; raw = ""
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            working = true; error = null
            try {
                val (text, name) = withContext(Dispatchers.IO) {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else "工作流.json"
                    } ?: "工作流.json"
                    val input = context.contentResolver.openInputStream(uri) ?: error("文件无法读取")
                    val bytes = input.use { stream ->
                        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) { val n = stream.read(buffer); if (n < 0) break
                            require(out.size() + n <= ComfyWorkflowEngine.MAX_BYTES) { "工作流不能超过 5 MiB" }; out.write(buffer, 0, n) }
                        out.toByteArray()
                    }
                    String(bytes, Charsets.UTF_8) to name
                }
                importGraph(text, name)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
            finally { working = false }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try { withContext(Dispatchers.IO) {
                val output = context.contentResolver.openOutputStream(uri) ?: error("无法创建文件")
                output.bufferedWriter().use { it.write(exportText) }
            } } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
        }
    }
    if (edit != null) {
        ComfyWorkflowEditor(edit!!, runtime.repository, analysisNote, onDismiss = {
            edit = null; analysisNote = null
        }) { edit = null; analysisNote = null }
        return
    }
    ComfyDialog("工作流", onDismiss) {
        Text("从 ComfyUI 的 File → Export Workflow (API) 导出 JSON。导入后会挑出要改的节点，并把每个节点上的全部参数展开到生成页。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !working && state.ready, modifier = Modifier.weight(1f)) { Text("导入文件") }
            OutlinedButton(onClick = { paste = !paste }, enabled = !working && state.ready, modifier = Modifier.weight(1f)) { Text("粘贴 JSON") }
        }
        if (paste) {
            PopTextField(raw, { raw = it }, label = "API 工作流 JSON", minLines = 4, maxLines = 8)
            Button(onClick = { scope.launch {
                working = true; error = null
                try { importGraph(raw, "新工作流") }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message }
                finally { working = false }
            } }, enabled = raw.isNotBlank() && !working) { Text(if (working) "分析中…" else "解析并分析") }
        }
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.workflows.isEmpty()) Text("导入后可核对提示词、种子等参数，保留未绑定的工作流原值。", style = MaterialTheme.typography.bodyMedium)
        if (state.workflows.isNotEmpty()) {
            PopTextField(workflowSearch, { workflowSearch = it }, label = "搜索工作流",
                placeholder = "按名称、节点数量或参数数量筛选", singleLine = true)
        }
        val visibleWorkflows = state.workflows.filter { workflow ->
            val query = workflowSearch.trim()
            query.isBlank() || workflow.name.contains(query, ignoreCase = true) ||
                workflow.graph.size().toString().contains(query) || workflow.parameters.size.toString().contains(query)
        }
        if (visibleWorkflows.isEmpty() && workflowSearch.isNotBlank()) {
            Text("没有匹配的工作流", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        visibleWorkflows.forEach { workflow ->
            val selected = workflow.id == state.selectedId
            Surface(
                onClick = {
                    runtime.repository.select(workflow.id)
                    onDismiss()
                },
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shape = themedShape(PopRadius.card),
                border = BorderStroke(.8.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(workflow.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text("${workflow.graph.size()} 节点 · ${workflow.parameters.size} 个参数 · ${workflow.outputNodes.size} 个输出",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PIcon(RefIcons.Chevron, "点击使用此工作流", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (selected) "当前工作流" else "点击卡片即可使用", style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f))
                        PopIconButton(PopEdit, "编辑工作流", onClick = { analysisNote = null; edit = workflow }, modifier = Modifier.size(32.dp), iconSize = 16.dp)
                        PopIconButton(PopDownload, "导出工作流", onClick = {
                            try { exportText = ComfyWorkflowEngine.prepare(workflow).graph.toString(); exporter.launch("${workflow.name}.json") }
                            catch (e: Exception) { error = e.message }
                        }, modifier = Modifier.size(32.dp), iconSize = 16.dp)
                        PopIconButton(PopTrash, "删除工作流", onClick = { deleting = workflow }, modifier = Modifier.size(32.dp), iconSize = 16.dp,
                            iconTint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        deleting?.let { workflow ->
            ArkInkPanel {
                Text("删除「${workflow.name}」？已生成的作品和任务记录会保留。")
                Row {
                    TextButton(onClick = { deleting = null }) { Text("取消") }
                    TextButton(onClick = { scope.launch {
                        try { runtime.repository.delete(workflow.id); deleting = null }
                        catch (e: Exception) { error = e.message }
                    } }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
internal fun ComfyWorkflowEditor(
    initial: ComfyWorkflow,
    repository: ComfyRepository,
    analysisNote: String? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var workflow by remember(initial.id) { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var binding by remember { mutableStateOf(false) }
    var parameterName by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ParameterKind.CUSTOM) }
    var targets by remember { mutableStateOf<List<InputTarget>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var showAllOutputs by remember { mutableStateOf(false) }
    var showSampling by remember { mutableStateOf(false) }
    val scalars = remember(initial.graph) { ComfyWorkflowEngine.scalars(initial.graph) }
    val scope = rememberCoroutineScope()
    ComfyDialog("核对工作流", onDismiss) {
        PopTextField(workflow.name, { workflow = workflow.copy(name = it) }, label = "工作流名称", singleLine = true)
        val disabledCount = remember(workflow.graph) { ComfyWorkflowEngine.enabledNodeCount(workflow.graph).let { enabled -> workflow.graph.size() - enabled } }
        Text("${workflow.graph.size()} 个节点（${if (disabledCount > 0) "$disabledCount 个停用，" else ""}${workflow.graph.size() - disabledCount} 个启用）。勾选一个节点，它上面的全部参数都会出现在生成页。", style = MaterialTheme.typography.bodySmall)
        if (workflow.outputNodes.any { !ComfyWorkflowEngine.nodeEnabled(workflow.graph, it) }) {
            Text("图片输出节点已停用，保存前请先打开它的开关。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        analysisNote?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        NodePicker(workflow, { workflow = it })
        var appendTo by remember { mutableStateOf<String?>(null) }
        val sampling = setOf(ParameterKind.SEED, ParameterKind.STEPS, ParameterKind.CFG, ParameterKind.WIDTH, ParameterKind.HEIGHT, ParameterKind.BATCH, ParameterKind.SAMPLER)
        val injection = workflow.parameters.filter { it.kind !in sampling }
        val advanced = workflow.parameters.filter { it.kind in sampling }
        fun removeTarget(parameterId: String, target: InputTarget) {
            workflow = workflow.copy(parameters = workflow.parameters.mapNotNull { parameter ->
                if (parameter.id != parameterId) parameter
                else parameter.copy(targets = parameter.targets - target).takeIf { it.targets.isNotEmpty() }
            })
            if (targets.contains(target)) targets = targets - target
        }
        injection.forEach { p ->
            ParameterCard(p, scalars, appendTo == p.id, onRemove = {
                workflow = workflow.copy(parameters = workflow.parameters.filterNot { it.id == p.id })
            }, onRemoveTarget = { removeTarget(p.id, it) }, onAppend = {
                appendTo = p.id; binding = true; targets = emptyList(); kind = p.kind; parameterName = p.label
            }, onValue = { text ->
                workflow = workflow.copy(parameters = workflow.parameters.map { if (it.id == p.id) it.copy(value = text) else it })
            }, onConvert = { next ->
                workflow = workflow.copy(parameters = workflow.parameters.map { if (it.id == p.id) it.copy(kind = next, label = if (it.label == p.kind.label) next.label else it.label) else it })
            })
        }
        if (advanced.isNotEmpty()) {
            TextButton(onClick = { showSampling = !showSampling }) { Text(if (showSampling) "收起采样参数" else "采样参数（步数、CFG 等 ${advanced.size} 项，平时不用动）") }
            if (showSampling) advanced.forEach { p ->
                ParameterCard(p, scalars, appendTo == p.id, compact = true, onRemove = {
                    workflow = workflow.copy(parameters = workflow.parameters.filterNot { it.id == p.id })
                }, onRemoveTarget = { removeTarget(p.id, it) }, onAppend = {
                    appendTo = p.id; binding = true; targets = emptyList(); kind = p.kind; parameterName = p.label
                }, onValue = { text ->
                    workflow = workflow.copy(parameters = workflow.parameters.map { if (it.id == p.id) it.copy(value = text) else it })
                }, onConvert = {})
            }
        }
        OutlinedButton(onClick = {
            binding = !binding
            if (binding) { appendTo = null; targets = emptyList() }
        }) { Text(if (binding && appendTo == null) "收起绑定" else "新建一张参数卡") }
        if (binding) {
            val host = workflow.parameters.find { it.id == appendTo }
            Text(
                if (host == null) "先搜节点，再一次勾选多个字段。同一张卡里的字段会写入同一个值。"
                else "正在往「${host.label}」追加。只能加同一种值，点字段即可多选。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (host == null) {
                PopTextField(parameterName, { parameterName = it }, label = "参数卡名称", placeholder = "例如：画面提示词", singleLine = true)
                ComfyChoice("参数用途", kind.label, ParameterKind.entries.map { it.label }) { label ->
                    kind = ParameterKind.entries.first { it.label == label }
                    targets = emptyList()
                    if (parameterName.isBlank()) parameterName = label
                }
            }
            PopTextField(search, { search = it }, label = "搜索节点、编号或字段", placeholder = "例如 673、提示词、seed", singleLine = true)
            BindingGroups(
                graph = workflow.graph, scalars = scalars, parameters = workflow.parameters,
                pending = targets, query = search, host = host, newKind = kind,
                onToggle = { field, on -> targets = if (on) targets + field.target else targets - field.target }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val picked = scalars.filter { it.target in targets }
                    val types = picked.map { it.type }.distinct()
                    val reason = bindingRejection(host, kind, picked, workflow.graph)
                    if (reason != null || types.size != 1) {
                        error = reason ?: "一张卡只能绑定同一种值"
                    } else if (host == null) {
                        workflow = workflow.copy(parameters = workflow.parameters + WorkflowParameter(
                            label = parameterName.trim(), kind = kind, targets = picked.map { it.target }, value = picked.first().value
                        ))
                        targets = emptyList(); parameterName = ""; binding = false; error = null
                    } else {
                        workflow = workflow.copy(parameters = workflow.parameters.map { parameter ->
                            if (parameter.id != host.id) parameter else parameter.copy(targets = parameter.targets + picked.map { it.target })
                        })
                        targets = emptyList(); appendTo = null; binding = false; error = null
                    }
                }, enabled = targets.isNotEmpty() && (host != null || parameterName.isNotBlank()), modifier = Modifier.weight(1f)) {
                    Text(if (host == null) "做成一张卡（${targets.size}）" else "追加到这张卡（${targets.size}）")
                }
                if (host != null) TextButton(onClick = { appendTo = null; targets = emptyList(); binding = false }) { Text("取消") }
            }
        }
        Text("图片输出", style = MaterialTheme.typography.titleSmall)
        Text("选择用于收取结果的节点；中间预览可不选。", style = MaterialTheme.typography.bodySmall)
        val outputCandidates = workflow.graph.entrySet().filter { showAllOutputs || it.key in workflow.outputNodes || it.value.asJsonObject.get("class_type").asString in setOf("SaveImage", "PreviewImage") }
        outputCandidates.forEach { (id, _) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(id in workflow.outputNodes, role = Role.Checkbox) { checked ->
                workflow = workflow.copy(outputNodes = if (checked) workflow.outputNodes + id else workflow.outputNodes - id)
            }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(id in workflow.outputNodes, onCheckedChange = null)
                Text("$id · ${ComfyWorkflowEngine.title(workflow.graph, id)}")
            }
        }
        TextButton(onClick = { showAllOutputs = !showAllOutputs }) { Text(if (showAllOutputs) "只显示常见图片输出" else "选择自定义输出节点") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { scope.launch {
            saving = true; error = null
            try { repository.saveWorkflow(workflow); onSaved() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
            finally { saving = false }
        } }, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (saving) "保存中…" else if (workflow.parameters.isEmpty()) "保留工作流原值并保存" else "确认绑定并保存") }
    }
}

@Composable
    private fun NodePicker(workflow: ComfyWorkflow, onChange: (ComfyWorkflow) -> Unit) {
        var search by remember { mutableStateOf("") }
        val panels = remember(workflow.graph) { ComfyWorkflowEngine.nodePanels(workflow.graph) }
        val shown = workflow.parameters.map { it.targets.firstOrNull()?.nodeId }.filterNotNull().toSet()
        val words = search.trim()
        val visible = panels.filter { panel ->
            words.isBlank() || listOf(panel.nodeId, panel.title, panel.classType).any { it.contains(words, ignoreCase = true) }
        }.sortedWith(
            compareByDescending<NodePanel> { !ComfyWorkflowEngine.nodeEnabled(workflow.graph, it.nodeId) }
                .thenBy { it.nodeId.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.nodeId }
        )
        Text("节点", style = MaterialTheme.typography.titleSmall)
        if (visible.any { !ComfyWorkflowEngine.nodeEnabled(workflow.graph, it.nodeId) }) {
            Text("有节点处于停用状态（导入时的 Bypass/Mute 会原样保留）。打开开关即可在生成时启用它。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PopTextField(search, { search = it }, label = "搜索节点编号或名称", placeholder = "例如 673、KSampler", singleLine = true)
        if (words.isBlank() && visible.size > 16) {
            Text("有 ${visible.size} 个带参数的节点。搜索编号后再勾选。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        if (visible.isEmpty()) {
            Text("没有匹配的节点", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        visible.forEach { panel ->
            val checked = panel.nodeId in shown
            val enabled = ComfyWorkflowEngine.nodeEnabled(workflow.graph, panel.nodeId)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked, onCheckedChange = { on ->
                    val rest = workflow.parameters.filterNot { it.targets.any { target -> target.nodeId == panel.nodeId } }
                    onChange(if (on) workflow.copy(parameters = rest + ComfyWorkflowEngine.panelParameters(workflow.graph, panel.nodeId)) else workflow.copy(parameters = rest))
                })
                Column(Modifier.weight(1f)) {
                    Text("${panel.title} · ${panel.nodeId}", style = MaterialTheme.typography.bodyMedium)
                    Text("${panel.classType} · ${panel.fields.size} 个参数：${panel.fields.joinToString("、") { it.target.input }}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (enabled) "启用" else "停用", style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Switch(checked = enabled, onCheckedChange = { on ->
                    val mode = if (on) ComfyWorkflowEngine.MODE_ENABLED else ComfyWorkflowEngine.MODE_BYPASS
                    onChange(workflow.copy(graph = ComfyWorkflowEngine.setNodeMode(workflow.graph, panel.nodeId, mode)))
                })
            }
        }
    }

@Composable
private fun ParameterCard(
    parameter: WorkflowParameter,
    scalars: List<ScalarInput>,
    appending: Boolean,
    compact: Boolean = false,
    onRemove: () -> Unit,
    onRemoveTarget: (InputTarget) -> Unit,
    onAppend: () -> Unit,
    onValue: (String) -> Unit,
    onConvert: (ParameterKind) -> Unit
) {
    ArkInkPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(parameter.label, style = MaterialTheme.typography.titleSmall)
                Text("${parameter.kind.label} · ${parameter.targets.size} 个绑定", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onAppend) { Text(if (appending) "追加中" else "追加") }
            TextButton(onClick = onRemove) { Text("移除") }
        }
        parameter.targets.forEach { target ->
            val field = scalars.find { it.target == target }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${field?.nodeTitle ?: target.nodeId} · ${target.input}",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { onRemoveTarget(target) }) { Text("去掉") }
            }
        }
        if (parameter.kind == ParameterKind.IMAGE) {
            Text("生成页会上传图片，并写入这张卡里的每个 LoadImage.image。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            PopTextField(
                parameter.value, onValue,
                modifier = when {
                    !compact && parameter.kind == ParameterKind.PROMPT -> Modifier.heightIn(min = 240.dp)
                    !compact && parameter.kind == ParameterKind.NEGATIVE -> Modifier.heightIn(min = 180.dp)
                    else -> Modifier
                },
                label = "初始值",
                singleLine = compact,
                minLines = when {
                    compact -> 1
                    parameter.kind == ParameterKind.PROMPT -> 10
                    parameter.kind == ParameterKind.NEGATIVE -> 7
                    else -> 1
                },
                maxLines = when {
                    compact -> 1
                    parameter.kind == ParameterKind.PROMPT -> 28
                    parameter.kind == ParameterKind.NEGATIVE -> 16
                    else -> 4
                }
            )
        }
        val imageTargets = parameter.targets.isNotEmpty() && parameter.targets.all { target ->
            scalars.none { it.target == target } || target.input == "image"
        }
        if (!compact && parameter.kind != ParameterKind.IMAGE && imageTargets && parameter.targets.all { it.input == "image" }) {
            TextButton(onClick = { onConvert(ParameterKind.IMAGE) }) { Text("改为参考图片") }
        }
        val textTargets = parameter.kind == ParameterKind.CUSTOM && parameter.targets.isNotEmpty() && parameter.targets.all { it.input == "text" }
        if (!compact && textTargets) TextButton(onClick = { onConvert(ParameterKind.PROMPT) }) { Text("改为主提示词") }
    }
}

@Composable
private fun BindingGroups(
    graph: JsonObject,
    scalars: List<ScalarInput>,
    parameters: List<WorkflowParameter>,
    pending: List<InputTarget>,
    query: String,
    host: WorkflowParameter?,
    newKind: ParameterKind,
    onToggle: (ScalarInput, Boolean) -> Unit
) {
    val kind = host?.kind ?: newKind
    val words = query.trim()
    val visible = scalars.filter { field ->
        val nodeId = field.target.nodeId
        val classType = graph.getAsJsonObject(nodeId)?.get("class_type")?.asString.orEmpty()
        val imageAllowed = kind != ParameterKind.IMAGE || (classType == "LoadImage" && field.target.input == "image")
        imageAllowed && (words.isBlank() || listOf(field.nodeTitle, field.target.input, nodeId, classType, field.type, field.value)
            .any { it.contains(words, ignoreCase = true) })
    }.groupBy { it.target.nodeId }
    if (words.isBlank() && visible.size > 12) {
        Text("有 ${visible.size} 个节点。先搜索编号或字段，再一次勾选多个。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (visible.isEmpty()) {
        Text("没有匹配的字段", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    visible.forEach { (nodeId, fields) ->
        Text("${ComfyWorkflowEngine.title(graph, nodeId)} · $nodeId", style = MaterialTheme.typography.titleSmall)
        fields.forEach { field ->
            val owner = parameters.find { field.target in it.targets }
            val selected = field.target in pending
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(selected, enabled = owner == null, role = Role.Checkbox) {
                onToggle(field, it)
            }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(selected, onCheckedChange = null, enabled = owner == null)
                Column {
                    Text("${field.target.input} · ${field.type}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        field.value.take(48) + if (owner == null) "" else " · 已在「${owner.label}」",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun bindingRejection(host: WorkflowParameter?, newKind: ParameterKind, picked: List<ScalarInput>, graph: JsonObject): String? {
    if (picked.isEmpty()) return "先勾选字段"
    val kind = host?.kind ?: newKind
    val numeric = setOf(ParameterKind.SEED, ParameterKind.STEPS, ParameterKind.CFG, ParameterKind.WIDTH, ParameterKind.HEIGHT, ParameterKind.BATCH)
    if (kind in numeric && picked.any { it.type != "数值" }) return "${kind.label}只能绑定数字"
    if (kind == ParameterKind.SAMPLER && picked.any { it.type != "文本" }) return "采样器只能绑定文本"
    if (kind == ParameterKind.IMAGE && picked.any { field ->
            graph.getAsJsonObject(field.target.nodeId)?.get("class_type")?.asString != "LoadImage" || field.target.input != "image"
        }) return "参考图片只能绑定 LoadImage.image"
    val existing = host?.targets?.firstOrNull()?.let { target ->
        graph.getAsJsonObject(target.nodeId)?.getAsJsonObject("inputs")?.get(target.input)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
    }
    val existingType = when {
        existing == null -> null
        existing.isNumber -> "数值"
        existing.isBoolean -> "布尔"
        else -> "文本"
    }
    if (existingType != null && picked.any { it.type != existingType }) return "这张卡是$existingType，不能混入其他类型"
    return if (picked.map { it.type }.distinct().size == 1) null else "一次只能选择同一种值"
}

@Composable
internal fun ComfyChoice(label: String, value: String, values: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("$label：$value") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            values.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}