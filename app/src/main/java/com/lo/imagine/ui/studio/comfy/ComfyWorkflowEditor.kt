package com.lo.imagine.ui.studio.comfy

import android.provider.OpenableColumns
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
import com.lo.imagine.data.comfy.*
import com.lo.imagine.ui.ArkInkPanel
import com.lo.imagine.ui.PopTextField
import com.lo.imagine.ui.theme.PopRadius
import com.lo.imagine.ui.theme.themedShape
import kotlinx.coroutines.*

@Composable
internal fun ComfyWorkflows(runtime: ComfyRuntime, onDismiss: () -> Unit) {
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
    fun importGraph(text: String, name: String) {
        scope.launch {
            working = true; error = null
            try {
                edit = withContext(Dispatchers.Default) {
                    val graph = ComfyWorkflowEngine.parse(text)
                    ComfyWorkflow(name = name.removeSuffix(".json").ifBlank { "新工作流" }, graph = graph,
                        parameters = ComfyWorkflowEngine.suggest(graph), outputNodes = ComfyWorkflowEngine.suggestedOutputs(graph))
                }
                paste = false; raw = ""
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
            finally { working = false }
        }
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
        ComfyWorkflowEditor(edit!!, runtime.repository, onDismiss = { edit = null }) { edit = null }
        return
    }
    ComfyDialog("工作流", onDismiss) {
        Text("从 ComfyUI 的 File → Export Workflow (API) 导出 JSON。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !working && state.ready, modifier = Modifier.weight(1f)) { Text("导入文件") }
            OutlinedButton(onClick = { paste = !paste }, enabled = !working && state.ready, modifier = Modifier.weight(1f)) { Text("粘贴 JSON") }
        }
        if (paste) {
            PopTextField(raw, { raw = it }, label = "API 工作流 JSON", minLines = 4, maxLines = 8)
            Button(onClick = { importGraph(raw, "新工作流") }, enabled = raw.isNotBlank() && !working) { Text("解析并核对") }
        }
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.workflows.isEmpty()) Text("导入后可核对提示词、种子等参数，保留未绑定的工作流原值。", style = MaterialTheme.typography.bodyMedium)
        state.workflows.forEach { workflow ->
            val selected = workflow.id == state.selectedId
            Surface(color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shape = themedShape(PopRadius.card), border = BorderStroke(.8.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(workflow.name, style = MaterialTheme.typography.titleSmall)
                    Text("${workflow.graph.size()} 节点 · ${workflow.parameters.size} 个参数 · ${workflow.outputNodes.size} 个输出", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { runtime.repository.select(workflow.id) }) { Text(if (selected) "已选择" else "选择") }
                        TextButton(onClick = { edit = workflow }) { Text("编辑") }
                        TextButton(onClick = {
                            try { exportText = ComfyWorkflowEngine.prepare(workflow).graph.toString(); exporter.launch("${workflow.name}.json") }
                            catch (e: Exception) { error = e.message }
                        }) { Text("导出") }
                        TextButton(onClick = { deleting = workflow }) { Text("删除", color = MaterialTheme.colorScheme.error) }
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
internal fun ComfyWorkflowEditor(initial: ComfyWorkflow, repository: ComfyRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var workflow by remember(initial.id) { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var binding by remember { mutableStateOf(false) }
    var parameterName by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ParameterKind.CUSTOM) }
    var targets by remember { mutableStateOf<List<InputTarget>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var showAllOutputs by remember { mutableStateOf(false) }
    val scalars = remember(initial.graph) { ComfyWorkflowEngine.scalars(initial.graph) }
    val scope = rememberCoroutineScope()
    ComfyDialog("核对工作流", onDismiss) {
        PopTextField(workflow.name, { workflow = workflow.copy(name = it) }, label = "工作流名称", singleLine = true)
        Text("${workflow.graph.size()} 个节点。下面的绑定决定哪些输入会出现在生成页面。", style = MaterialTheme.typography.bodySmall)
        workflow.parameters.forEach { p ->
            ArkInkPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { workflow = workflow.copy(parameters = workflow.parameters.filterNot { it.id == p.id }) }) { Text("移除") }
                }
                Text(p.targets.joinToString { it.key }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.kind == ParameterKind.IMAGE) {
                    Text("生成页会通过 ComfyUI /upload/image 上传并写入 LoadImage.image。当前值：${p.value.ifBlank { "未设置" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    PopTextField(p.value, { text -> workflow = workflow.copy(parameters = workflow.parameters.map { if (it.id == p.id) it.copy(value = text) else it }) }, label = "初始值", maxLines = 4)
                }
            }
        }
        OutlinedButton(onClick = { binding = !binding }) { Text(if (binding) "收起绑定" else "添加参数绑定") }
        if (binding) {
            PopTextField(parameterName, { parameterName = it }, label = "参数名称", placeholder = "例如：画面提示词", singleLine = true)
            ComfyChoice("参数用途", kind.label, ParameterKind.entries.map { it.label }) { label ->
                kind = ParameterKind.entries.first { it.label == label }
                targets = emptyList()
                if (parameterName.isBlank()) parameterName = label
            }
            PopTextField(search, { search = it }, label = "搜索节点或输入字段", singleLine = true)
            val candidates = scalars.filter { field ->
                val imageTarget = kind == ParameterKind.IMAGE && workflow.graph.getAsJsonObject(field.target.nodeId)?.get("class_type")?.asString == "LoadImage" && field.target.input == "image"
                val allowed = if (kind == ParameterKind.IMAGE) imageTarget else true
                allowed && (field.nodeTitle + field.target.input).contains(search, ignoreCase = true)
            }
            Column(Modifier.heightIn(max = 240.dp).then(Modifier)) {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(candidates.size) { index ->
                        val field = candidates[index]
                        val occupied = workflow.parameters.any { field.target in it.targets }
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(field.target in targets, enabled = !occupied, role = Role.Checkbox) {
                            targets = if (it) targets + field.target else targets - field.target
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(field.target in targets, onCheckedChange = null, enabled = !occupied)
                            Text("${field.nodeTitle}\n${field.target.input} · ${field.type}${if (occupied) " · 已绑定" else ""}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Button(onClick = {
                val value = scalars.first { it.target == targets.first() }.value
                workflow = workflow.copy(parameters = workflow.parameters + WorkflowParameter(label = parameterName.trim(), kind = kind, targets = targets, value = value))
                targets = emptyList(); parameterName = ""; binding = false
            }, enabled = targets.isNotEmpty() && parameterName.isNotBlank()) { Text("添加绑定") }
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
internal fun ComfyChoice(label: String, value: String, values: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("$label：$value") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            values.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}