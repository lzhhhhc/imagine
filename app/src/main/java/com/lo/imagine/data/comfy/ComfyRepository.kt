package com.lo.imagine.data.comfy

import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

/** Private local records. Credentials live in Android noBackupFilesDir, separately from workflows. */
class ComfyStore(private val root: File, private val credentials: File) {
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
    init { require(root.exists() || root.mkdirs()); require(credentials.parentFile!!.exists() || credentials.parentFile!!.mkdirs()) }
    private fun <T> read(file: File, type: Class<T>): T = file.reader().use { gson.fromJson(it, type) }
        ?: throw IllegalArgumentException("${file.name} 内容为空")
    @Synchronized fun write(file: File, value: Any) {
        require(file.parentFile!!.exists() || file.parentFile!!.mkdirs())
        val temp = File(file.parentFile, file.name + ".pending")
        try {
            FileOutputStream(temp).use { stream ->
                stream.write(gson.toJson(value).toByteArray(Charsets.UTF_8)); stream.fd.sync()
            }
            check(temp.renameTo(file)) { "无法保存 ${file.name}" }
        } finally { temp.delete() }
    }
    fun loadConnection(): ComfyConnection {
        val value = if (credentials.exists()) read(credentials, ComfyConnection::class.java) else ComfyConnection()
        require(value.providerId == COMFY_PROVIDER && value.waitMinutes in 1..180) { "连接配置不受支持" }
        requireNotNull(value.bearerToken)
        if (value.baseUrl.isNotEmpty()) comfyBaseUrl(value.baseUrl)
        return value
    }
    fun saveConnection(value: ComfyConnection) = write(credentials, value)
    fun loadLibrary(): WorkflowLibrary {
        val file = File(root, "library.json")
        val library = if (file.exists()) read(file, WorkflowLibrary::class.java) else WorkflowLibrary()
        require(library.schemaVersion == 1 && library.workflows.size <= 50) { "工作流库版本或数量不受支持，原文件已保留" }
        require(library.workflows.map { it.id }.distinct().size == library.workflows.size) { "工作流 ID 重复" }
        require(library.selectedId == null || library.workflows.any { it.id == library.selectedId }) { "选定工作流不存在" }
        library.workflows.forEach { workflow ->
            require(workflow.schemaVersion == 1 && workflow.id.isNotBlank() && workflow.name.isNotBlank())
            val graph = ComfyWorkflowEngine.parse(workflow.graph.toString())
            require(workflow.outputNodes.isNotEmpty() && workflow.outputNodes.all { graph.has(it) }) { "图片输出记录已失效" }
            val used = mutableSetOf<InputTarget>()
            require(workflow.parameters.map { it.id }.distinct().size == workflow.parameters.size)
            workflow.parameters.forEach { parameter ->
                requireNotNull(parameter.kind); requireNotNull(parameter.value)
                require(parameter.id.isNotBlank() && parameter.label.isNotBlank() && parameter.targets.isNotEmpty())
                parameter.targets.forEach { target ->
                    require(used.add(target) && graph.getAsJsonObject(target.nodeId)?.getAsJsonObject("inputs")?.get(target.input)?.isJsonPrimitive == true) { "参数绑定记录已失效" }
                }
            }
        }
        return library
    }
    fun saveLibrary(value: WorkflowLibrary) = write(File(root, "library.json"), value)
    fun loadJobs(): Pair<List<ComfyJob>, List<String>> {
        val errors = mutableListOf<String>()
        val jobs = File(root, "jobs").listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { file ->
            try {
                read(file, ComfyJob::class.java).also {
                    require(it.schemaVersion == 1 && it.id.matches(Regex("[A-Za-z0-9-]+")) && file.nameWithoutExtension == it.id)
                    requireNotNull(it.phase); requireNotNull(it.graph); requireNotNull(it.workflowName); requireNotNull(it.message)
                    require(it.providerId == COMFY_PROVIDER && it.clientId.isNotBlank())
                    comfyBaseUrl(it.origin)
                    it.values.forEach { (key, value) -> requireNotNull(key); requireNotNull(value) }
                    it.saved.forEach { (key, value) -> requireNotNull(key); requireNotNull(value) }
                    it.outputNodes.forEach { value -> requireNotNull(value) }
                    it.gallerySaved.forEach { value -> requireNotNull(value) }
                    it.images.forEach { image -> require(image.filename.isNotBlank()); requireNotNull(image.subfolder); requireNotNull(image.type) }
                }
            } catch (e: Exception) { errors += "任务 ${file.name} 无法读取，原文件已保留"; null }
        }.sortedByDescending { it.startedAt }
        return jobs to errors
    }
    fun saveJob(value: ComfyJob) {
        require(value.id.matches(Regex("[A-Za-z0-9-]+")))
        write(File(root, "jobs/${value.id}.json"), value)
    }
}

class ComfyRepository(private val store: ComfyStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Mutex()
    private val mutable = MutableStateFlow(ComfyState())
    val state = mutable.asStateFlow()
    private var draftSave: Job? = null
    @Synchronized fun change(transform: (ComfyState) -> ComfyState) { mutable.value = transform(mutable.value) }
    fun report(e: Throwable) { change { it.copy(error = e.message ?: "操作失败") } }
    fun clearError() { change { it.copy(error = null) } }
    init { reload() }
    fun reload() {
        if (mutable.value.busy) { report(IllegalStateException("请先停止等待再重新读取配置")); return }
        draftSave?.cancel()
        change { it.copy(ready = false, error = null) }
        scope.launch {
        writes.withLock {
            try {
                val library = store.loadLibrary(); val connection = store.loadConnection(); val (jobs, errors) = store.loadJobs()
                check(errors.isEmpty()) { errors.joinToString("\n") + "；为避免重复提交，已暂停工作台" }
                change { it.copy(ready = true, connection = connection, workflows = library.workflows, selectedId = library.selectedId,
                    jobs = jobs.map { job ->
                        when (job.phase) {
                            ComfyPhase.SUBMITTING -> job.copy(phase = ComfyPhase.UNKNOWN, message = "上次提交未取得确认，请先查询")
                            ComfyPhase.TRACKING, ComfyPhase.QUEUED, ComfyPhase.RUNNING, ComfyPhase.DOWNLOADING -> job.copy(phase = ComfyPhase.PAUSED, message = "可恢复查询上次任务")
                            else -> job
                        }
                    }, error = null) }
            } catch (e: Exception) { change { it.copy(ready = false, error = "配置读取失败：${e.message}；原文件未修改") } }
        }
    } }
    private fun library(): WorkflowLibrary = mutable.value.let { WorkflowLibrary(selectedId = it.selectedId, workflows = it.workflows) }
    private fun ensureReady() = check(mutable.value.ready) { "配置尚未读取，不能覆盖存储" }
    suspend fun saveConnection(value: ComfyConnection) = withContext(Dispatchers.IO) {
        val normal = value.copy(baseUrl = comfyBaseUrl(value.baseUrl).toString(), bearerToken = value.bearerToken.trim())
        require(normal.waitMinutes in 1..180) { "等待时间应为 1–180 分钟" }
        require(normal.providerId == COMFY_PROVIDER) { "此后端尚未实现" }
        writes.withLock {
            ensureReady(); store.saveConnection(normal)
            change { it.copy(connection = normal, connectedFingerprint = null, status = "连接已保存") }
        }
    }
    suspend fun saveWorkflow(value: ComfyWorkflow) = withContext(Dispatchers.IO) {
        ComfyWorkflowEngine.prepare(value, resolveRandom = false)
        writes.withLock {
            ensureReady(); val current = library()
            require(current.workflows.size < 50 || current.workflows.any { it.id == value.id }) { "最多保存 50 份工作流" }
            val updated = current.copy(selectedId = value.id, workflows = current.workflows.filterNot { it.id == value.id } + value.copy(updatedAt = System.currentTimeMillis()))
            store.saveLibrary(updated); change { it.copy(workflows = updated.workflows, selectedId = value.id) }
        }
    }
    fun select(id: String) { scope.launch { try { writes.withLock {
        ensureReady(); require(mutable.value.workflows.any { it.id == id }); val next = library().copy(selectedId = id)
        store.saveLibrary(next); change { it.copy(selectedId = id) }
    } } catch (e: Exception) { report(e) } } }
    suspend fun delete(id: String) = withContext(Dispatchers.IO) { writes.withLock {
        ensureReady(); val next = library().let { it.copy(selectedId = if (it.selectedId == id) null else it.selectedId, workflows = it.workflows.filterNot { w -> w.id == id }) }
        store.saveLibrary(next); change { it.copy(workflows = next.workflows, selectedId = next.selectedId) }
    } }
    fun editParameter(workflowId: String, parameterId: String, value: String? = null, random: Boolean? = null) {
        if (!mutable.value.ready) return
        change { s -> s.copy(workflows = s.workflows.map { w -> if (w.id != workflowId) w else w.copy(parameters = w.parameters.map { p ->
            if (p.id != parameterId) p else p.copy(value = value ?: p.value, randomSeed = random ?: p.randomSeed)
        }) }) }
        draftSave?.cancel()
        draftSave = scope.launch {
            delay(400)
            try { writes.withLock { ensureReady(); store.saveLibrary(library()) } } catch (e: Exception) { report(e) }
        }
    }
    suspend fun flushDraft() = withContext(Dispatchers.IO) { writes.withLock { ensureReady(); store.saveLibrary(library()) } }
    suspend fun saveJob(value: ComfyJob) = withContext(Dispatchers.IO) { writes.withLock {
        ensureReady(); store.saveJob(value)
        change { it.copy(jobs = (it.jobs.filterNot { old -> old.id == value.id } + value).sortedByDescending { job -> job.startedAt }) }
    } }
}