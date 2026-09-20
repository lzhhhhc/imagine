package com.lo.imagine.ui.director

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.lo.imagine.data.*
import com.lo.imagine.ui.DropdownField
import com.lo.imagine.ui.PopAlertDialog
import com.lo.imagine.ui.PopTextField
import com.lo.imagine.ui.theme.PopRadius
import com.lo.imagine.ui.theme.themedShape
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File

@Composable
internal fun DirectorProductionDialog(
    engine: DirectorEngine, shots: List<DirectorShot>, aspect: String, selectedIds: List<String>,
    assets: List<DirectorAsset>, repository: SettingsRepository, onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { VideoClient() }
    val store = remember { DirectorVideoTaskStore(File(context.filesDir, "director_video_tasks.json")) }
    var config by remember { mutableStateOf<VideoApiSettings?>(null) }
    var tasks by remember { mutableStateOf<List<DirectorVideoTask>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var shotIndex by remember { mutableIntStateOf(0) }
    var resolution by remember { mutableStateOf("720p") }
    var releaseUnknown by remember { mutableStateOf<DirectorVideoTask?>(null) }
    var recoveryId by remember { mutableStateOf("") }
    val busy = job != null
    val selected = remember(selectedIds, assets) { runCatching { selectedDirectorAssets(selectedIds, assets) } }
    val shot = shots.getOrNull(shotIndex)
    var input by remember(shot, aspect, selected, resolution) { mutableStateOf<VideoInput?>(null) }
    var inputError by remember(shot, aspect, selected, resolution) { mutableStateOf<String?>(null) }
    val protocol = VideoProtocol.fromId(config?.protocolId)
    val supported = VideoProtocol.entries.filter { it.engineId == engine.id }

    LaunchedEffect(Unit) {
        try {
            config = repository.videoSettings.first()
            tasks = withContext(Dispatchers.IO) { store.load() }
            loaded = true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "视频配置或任务记录读取失败，请关闭后重试；未提交任何任务" }
    }
    LaunchedEffect(shot, aspect, selected, resolution) {
        input = null
        inputError = null
        if (shot == null) return@LaunchedEffect
        try {
            input = withContext(Dispatchers.IO) {
                val refs = readDirectorAssets(selected.getOrThrow())
                VideoInput(shot.prompt, shot.seconds.toIntOrNull() ?: error("请输入有效的分镜秒数"), aspect,
                    refs.map { VideoReference(it.label, it.dataUri) },
                    shot.imagePath?.let { readDirectorReference(it, "首帧").dataUri },
                    shot.endImagePath?.let { readDirectorReference(it, "尾帧").dataUri }, resolution)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { inputError = e.message ?: "素材读取失败" }
    }

    suspend fun record(task: DirectorVideoTask) {
        withContext(NonCancellable + Dispatchers.IO) { store.save(task) }
        tasks = tasks.filterNot { it.localId == task.localId } + task
    }
    fun taskConnection(task: DirectorVideoTask): Pair<VideoApiSettings, VideoProtocol> {
        val current = config ?: error("视频配置尚未读取")
        val target = VideoProtocol.fromId(task.protocolId) ?: error("任务协议无效")
        require(videoEndpoint(current.baseUrl, target) == videoEndpoint(task.baseUrl, target)) {
            "请先切回此任务的视频服务地址，再继续查询"
        }
        return current.copy(model = task.model) to target
    }
    suspend fun track(original: DirectorVideoTask) {
        var task = original
        val (connection, target) = taskConnection(task)
        // Refresh a temporary result URL on explicit resume; this is a GET, never a resubmission.
        if (task.status == "done" && (task.localPath == null || !File(task.localPath).isFile)) {
            val result = client.query(connection, target, task.requestId ?: error("缺少任务编号"))
            task = task.copy(status = result.status, videoUrl = result.url)
            record(task)
            if (result.finished && result.status != "done") {
                note = "${task.shotLabel} ${taskStatus(result.status)}，请到服务商后台查看"
                return
            }
        }
        repeat(180) {
            if (task.status == "done") {
                if (task.localPath == null || !File(task.localPath).isFile) {
                    note = "视频已生成，正在保存…"
                    val file = File(context.filesDir, "director_videos/${task.localId}.mp4")
                    client.download(task.videoUrl ?: error("视频地址为空"), file)
                    task = task.copy(localPath = file.absolutePath)
                    record(task)
                }
                note = "${task.shotLabel} 已保存，可打开或分享"
                return
            }
            val result = client.query(connection, target, task.requestId ?: error("缺少任务编号"))
            task = task.copy(status = result.status, videoUrl = result.url)
            record(task)
            if (result.finished && result.status != "done") {
                note = "${task.shotLabel} ${taskStatus(result.status)}，可到服务商后台查看详情"
                return
            }
            if (result.status != "done") { note = "${task.shotLabel} 正在制作…"; delay(5000) }
        }
        note = "本次查询已暂停，任务编号已保存，可稍后继续查询"
    }
    fun runWork(block: suspend () -> Unit) {
        if (job != null) return
        error = null
        note = null
        job = scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "制作未完成，已保留任务记录" }
            finally {
                withContext(NonCancellable) {
                    try { tasks = withContext(Dispatchers.IO) { store.load() } }
                    catch (_: Exception) { loaded = false; error = "任务记录读取失败，请关闭后重试" }
                }
                submitting = false
                job = null
            }
        }
    }
    val unresolved = tasks.any { it.engineId == engine.id && it.shotId == shot?.id && it.status in listOf("submitting", "pending") }
    val validation = when {
        !loaded -> "正在读取配置和任务…"
        supported.isEmpty() -> "${engine.label} 目前可编写与复制分镜；本版直接制作支持 Grok 和 Seedance"
        config == null -> "请先在设置 → 视频 API 填写连接"
        videoApiConfigurationError(config!!) != null -> videoApiConfigurationError(config!!)
        protocol == null -> "请选择视频服务协议"
        protocol.engineId != engine.id -> "视频协议与 ${engine.label} 工程不一致，请选择对应协议和模型"
        shot == null -> "请先添加分镜"
        inputError != null -> inputError
        input == null -> "正在读取并检查参考图…"
        unresolved -> "本镜已有待确认或制作中的任务，请先在下方继续查询"
        else -> videoInputError(protocol, input!!) ?: runCatching { videoEndpoint(config!!.baseUrl, protocol); null }
            .getOrElse { it.message ?: "视频基础地址无效" }
    }
    fun submit() {
        if (validation != null || busy) return
        val connection = config ?: return
        val target = protocol ?: return
        val prepared = input ?: return
        val currentShot = shot ?: return
        runWork {
            val initial = DirectorVideoTask(engineId = engine.id, shotId = currentShot.id,
                shotLabel = "分镜${shotIndex + 1}", protocolId = target.id,
                baseUrl = connection.baseUrl.trim(), model = connection.model.trim(), seconds = prepared.seconds,
                aspect = prepared.aspect, prompt = prepared.prompt,
                referenceLabels = prepared.references.map { it.label } + listOfNotNull(
                    prepared.firstFrame?.let { "首帧" }, prepared.lastFrame?.let { "尾帧" }))
            record(initial)
            note = "正在提交${initial.shotLabel}…"
            submitting = true
            // Persist response before any cancellable boundary; never automatically repeat this POST.
            val accepted = withContext(NonCancellable + Dispatchers.IO) {
                val id = client.submit(connection, target, prepared)
                initial.copy(requestId = id, status = "pending").also { store.save(it) }
            }
            record(accepted)
            submitting = false
            track(accepted)
        }
    }
    fun openVideo(task: DirectorVideoTask, share: Boolean) {
        try {
            val file = File(task.localPath ?: error("视频尚未保存"))
            require(file.isFile) { "本地视频已不存在，请重新下载" }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = if (share) Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
                else Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(if (share) Intent.createChooser(intent, "分享视频") else intent)
        } catch (e: Exception) { error = e.message ?: "没有可打开视频的应用" }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = themedShape(PopRadius.sheet),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).fillMaxHeight(.94f).imePadding()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("视频制作", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("返回") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        DirectorSummaryCard("${engine.label} · 逐镜制作", "每次提交当前分镜，按服务商价格计费。生成后保存为独立片段。返回时保留任务编号，可继续查询。")
                    }
                    item {
                        if (!busy && supported.isNotEmpty()) DropdownField(protocol?.label ?: "选择视频协议",
                            supported.map { it.label }, onSelect = { label ->
                                val next = config?.copy(protocolId = supported.first { it.label == label }.id) ?: return@DropdownField
                                runWork { repository.saveVideoSettings(next); config = next }
                            }, fieldLabel = "视频服务协议")
                        Text("模型：${config?.model?.ifBlank { "未配置" } ?: "读取中"}", style = MaterialTheme.typography.bodyMedium)
                        Text("服务：${config?.baseUrl?.ifBlank { "未配置" } ?: "读取中"}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (shots.isNotEmpty()) item {
                        if (!busy) DropdownField("分镜${shotIndex + 1} · ${shot?.seconds} 秒",
                            shots.mapIndexed { i, s -> "分镜${i + 1} · ${s.seconds} 秒" }, onSelect = { label ->
                                shotIndex = shots.indices.first { "分镜${it + 1} · ${shots[it].seconds} 秒" == label }
                            }, fieldLabel = "选择制作分镜")
                        Text("画幅 $aspect · 本镜 ${shot?.seconds} 秒 · 共 ${shots.sumOf { it.seconds.toIntOrNull() ?: 0 }} 秒",
                            style = MaterialTheme.typography.labelLarge)
                        if (!busy) DropdownField(resolution, listOf("480p", "720p"), onSelect = { resolution = it }, fieldLabel = "输出分辨率")
                        Text(shot?.prompt.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                    item {
                        Text("实际输入素材", fontWeight = FontWeight.SemiBold)
                        Text("人物 ${selected.getOrNull()?.count { it.kind != "scene" } ?: 0} 张 · 环境 ${selected.getOrNull()?.count { it.kind == "scene" } ?: 0} 张 · 首帧 ${if (shot?.imagePath != null) 1 else 0} 张 · 尾帧 ${if (shot?.endImagePath != null) 1 else 0} 张",
                            style = MaterialTheme.typography.labelMedium)
                        if (selectedIds.isEmpty()) Text("未选人物或环境参考图，将按提示词与已选首尾帧制作。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(selected.getOrNull().orEmpty(), key = { it.id }) { asset ->
                        ReferencePreview(directorAssetLabel(asset), asset.imagePath)
                    }
                    shot?.imagePath?.let { path -> item { ReferencePreview("本镜首帧", path) } }
                    shot?.endImagePath?.let { path -> item { ReferencePreview("本镜尾帧", path) } }
                    validation?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
                    error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
                    note?.let { item { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) } }
                    val ownTasks = tasks.filter { it.engineId == engine.id }.asReversed()
                    if (ownTasks.isNotEmpty()) item { Text("制作记录", style = MaterialTheme.typography.titleMedium) }
                    items(ownTasks, key = { it.localId }) { task ->
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = themedShape(PopRadius.field)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${task.shotLabel} · ${task.seconds}s · ${task.aspect} · ${taskStatus(task.status)}", fontWeight = FontWeight.SemiBold)
                                Text(task.model, style = MaterialTheme.typography.labelSmall)
                                task.requestId?.let { Text("任务 $it", style = MaterialTheme.typography.labelSmall) }
                                if (task.status == "submitting") {
                                    Text("提交结果待核对。请先在服务商后台确认；返回任务编号后可继续查询，避免重复扣费。", style = MaterialTheme.typography.bodySmall)
                                    PopTextField(recoveryId, { recoveryId = it }, placeholder = "后台任务编号", singleLine = true, enabled = !busy)
                                    TextButton(enabled = !busy && recoveryId.isNotBlank(), onClick = {
                                        runWork { val next = task.copy(requestId = recoveryId.trim(), status = "pending"); record(next); track(next) }
                                    }) { Text("保存编号并查询") }
                                    TextButton(enabled = !busy, onClick = { releaseUnknown = task }) { Text("已核对未创建任务") }
                                }
                                Row {
                                    if (task.requestId != null && task.status in listOf("pending", "done")) TextButton(enabled = !busy, onClick = { runWork { track(task) } }) {
                                        Text(if (task.status == "done") "保存视频" else "继续查询")
                                    }
                                    if (task.localPath != null) {
                                        TextButton(onClick = { openVideo(task, false) }) { Text("打开") }
                                        TextButton(onClick = { openVideo(task, true) }) { Text("分享") }
                                    }
                                }
                            }
                        }
                    }
                }
                if (busy) {
                    Text("关闭前请先停止查询；停止查询不会取消服务端制作。", style = MaterialTheme.typography.labelSmall)
                    OutlinedButton(onClick = { job?.cancel(); note = "查询已停止，任务记录已保留" }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text(if (submitting) "正在提交…" else "停止等待") }
                } else DirectorPrimaryAction("确认并制作当前分镜", enabled = validation == null, onClick = ::submit, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    releaseUnknown?.let { task ->
        PopAlertDialog(title = "确认后台未创建任务", onDismissRequest = { releaseUnknown = null },
            text = { Text("仅在服务商后台确认本次请求未创建任务后继续。若已有任务，请填写编号查询。") },
            confirmLabel = "已确认未创建", onConfirm = {
                releaseUnknown = null; runWork { record(task.copy(status = "not_created")) }
            }, dismissLabel = "返回核对", onDismiss = { releaseUnknown = null })
    }
}

private fun taskStatus(status: String): String = when (status) {
    "pending" -> "制作中"
    "done" -> "已完成"
    "failed" -> "制作失败"
    "expired" -> "已过期"
    "cancelled" -> "已取消"
    "not_created" -> "未创建"
    else -> "提交待核对"
}

@Composable
private fun ReferencePreview(label: String, path: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AsyncImage(File(path), label, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}