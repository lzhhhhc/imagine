package com.lo.imagine.data.comfy

import kotlinx.coroutines.*
import java.io.File
import java.io.IOException

interface ComfyArchive {
    fun temporary(job: ComfyJob, image: RemoteImage): File
    suspend fun save(job: ComfyJob, image: RemoteImage, temporary: File): String
    suspend fun gallery(path: String): Boolean
}

/** UI-independent task engine, using one explicit provider and recoverable local records. */
class ComfyTaskCoordinator(
    private val repository: ComfyRepository,
    private val backend: WorkflowBackend,
    private val archive: ComfyArchive,
    private val launch: (suspend () -> Unit) -> Job,
    private val pollMillis: Long = 2000
) {
    @Volatile private var active: Job? = null
    @Synchronized private fun begin(task: suspend () -> Unit) {
        if (repository.state.value.busy) { repository.report(IllegalStateException("已有任务，请等待或先停止等待")); return }
        if (!repository.state.value.ready) { repository.report(IllegalStateException("配置尚未读取")); return }
        repository.change { it.copy(busy = true, error = null, status = "检查工作流") }
        try {
            active = launch {
                try { task() } catch (e: CancellationException) { throw e }
                catch (e: Exception) { repository.report(e) }
            }.also { job -> job.invokeOnCompletion { repository.change { it.copy(busy = false, activeJobId = null) } } }
        } catch (e: Exception) { repository.change { it.copy(busy = false) }; repository.report(e) }
    }
    fun pause() { active?.cancel(CancellationException("用户停止等待")) }

    fun generate(autoGallery: Boolean) {
        val state = repository.state.value
        val workflow = state.selected ?: run { repository.report(IllegalArgumentException("请先导入并选择工作流")); return }
        val connection = state.connection.copy()
        begin {
            require(connection.providerId == backend.providerId) { "尚未支持此后端" }
            val origin = comfyBaseUrl(connection.baseUrl).toString()
            repository.flushDraft()
            require(state.jobs.none { it.origin == origin && it.phase == ComfyPhase.UNKNOWN }) {
                "这台服务器有提交结果待确认的任务，请先确认或标记已核对，再提交新任务"
            }
            val prepared = ComfyWorkflowEngine.prepare(workflow)
            val types = prepared.graph.entrySet().map { it.value.asJsonObject.get("class_type").asString }.distinct()
            val schemas = types.associateWith { type -> backend.nodeInfo(connection, type) }
            ComfyWorkflowEngine.validateWithInfo(prepared.graph, schemas)
            var job = ComfyJob(providerId = connection.providerId, origin = origin, workflowName = workflow.name,
                graph = prepared.graph, values = prepared.values, outputNodes = workflow.outputNodes,
                prompt = workflow.parameters.firstOrNull { it.kind == ParameterKind.PROMPT }?.value.orEmpty(), autoGallery = autoGallery)
            repository.saveJob(job) // Write-ahead: a crash after sending is an unknown submission, never a new POST.
            repository.change { it.copy(activeJobId = job.id, status = "正在提交") }
            try {
                val submitted = backend.submit(connection, prepared.graph, job.clientId)
                job = job.copy(promptId = submitted.id, phase = ComfyPhase.TRACKING, message = submitted.warning)
                withContext(NonCancellable) { repository.saveJob(job) }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { repository.saveJob(job.copy(phase = if (job.promptId == null) ComfyPhase.UNKNOWN else ComfyPhase.PAUSED,
                    message = if (job.promptId == null) "停止了提交等待，请先查询是否已入队" else "等待已暂停；服务器可能继续运行")) }
                throw e
            } catch (e: Exception) {
                repository.saveJob(job.copy(phase = if (e is SubmissionUncertain || job.promptId != null) ComfyPhase.UNKNOWN else ComfyPhase.FAILED, message = e.message.orEmpty()))
                throw e
            }
            follow(connection, job)
        }
    }

    fun resume(id: String) {
        val stored = repository.state.value.jobs.find { it.id == id } ?: return
        val connection = repository.state.value.connection.copy()
        begin {
            require(stored.providerId == connection.providerId && stored.origin == comfyBaseUrl(connection.baseUrl).toString()) { "请先切回这项任务原来的服务器，再恢复查询" }
            repository.change { it.copy(activeJobId = id) }
            var job = stored
            if (job.promptId == null) {
                val ids = backend.findSubmitted(connection, job.clientId)
                require(ids.size == 1) { if (ids.isEmpty()) "未找到可确认的任务，不代表服务器未接收；可在电脑端核对" else "找到多个匹配任务，请在电脑端核对" }
                job = job.copy(promptId = ids.single(), phase = ComfyPhase.TRACKING)
                repository.saveJob(job)
            }
            follow(connection, job)
        }
    }

    fun acknowledgeUnknown(id: String) {
        val stored = repository.state.value.jobs.find { it.id == id } ?: return
        begin {
            require(stored.phase == ComfyPhase.UNKNOWN && stored.promptId == null)
            repository.saveJob(stored.copy(phase = ComfyPhase.PAUSED,
                message = "已在服务器端人工核对；这条记录不再阻止新任务，仍可尝试确认编号"))
        }
    }

    fun removeQueued(id: String) {
        val stored = repository.state.value.jobs.find { it.id == id } ?: return
        val connection = repository.state.value.connection.copy()
        begin {
            require(stored.origin == comfyBaseUrl(connection.baseUrl).toString() && stored.providerId == connection.providerId) { "请切回原服务器" }
            val promptId = requireNotNull(stored.promptId)
            val before = backend.status(connection, promptId, stored.outputNodes)
            require(before.phase == ComfyPhase.QUEUED) { "任务已不在等待队列；可以停止本地等待" }
            backend.removeQueued(connection, promptId)
            val after = backend.status(connection, promptId, stored.outputNodes)
            val removed = after.phase == ComfyPhase.TRACKING && !after.complete
            repository.saveJob(stored.copy(phase = if (removed) ComfyPhase.REMOVED else ComfyPhase.PAUSED,
                message = if (removed) "已请求移除，当前队列中已不见此任务" else "任务状态已变化：${after.phase.label}，可继续查询"))
        }
    }

    private suspend fun follow(connection: ComfyConnection, initial: ComfyJob) {
        var job = initial
        val deadline = System.nanoTime() + connection.waitMinutes * 60_000_000_000L
        var missing = 0
        try {
            var images = job.images
            if (images.isEmpty()) {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (System.nanoTime() > deadline) throw IOException("等待时间已到；可稍后恢复查询")
                    val status = readStatus(connection, job)
                    if (status.complete) {
                        images = status.images
                        job = job.copy(images = images, executionError = status.error)
                        if (images.isEmpty()) {
                            repository.saveJob(job.copy(phase = if (status.error == null) ComfyPhase.SUCCEEDED else ComfyPhase.FAILED,
                                message = status.error ?: "执行完成，但所选输出没有可下载图片")); return
                        }
                        break
                    }
                    missing = if (status.phase == ComfyPhase.TRACKING) missing + 1 else 0
                    if (missing >= 5) throw IOException("队列与历史暂时查不到任务；记录已保留，可稍后恢复")
                    if (job.phase != status.phase) { job = job.copy(phase = status.phase); repository.saveJob(job) }
                    repository.change { it.copy(status = status.phase.label) }
                    delay(pollMillis)
                }
            }
            job = job.copy(phase = ComfyPhase.DOWNLOADING, images = images)
            repository.saveJob(job)
            val failures = mutableListOf<String>()
            for ((index, image) in images.withIndex()) {
                currentCoroutineContext().ensureActive()
                repository.change { it.copy(status = "下载 ${index + 1}/${images.size}") }
                try {
                    var path = job.saved[image.key]?.takeIf { File(it).isFile }
                    if (path == null) {
                        val temp = archive.temporary(job, image)
                        try {
                            backend.download(connection, image, temp)
                            path = archive.save(job, image, temp)
                        } finally { temp.delete() }
                        job = job.copy(saved = job.saved + (image.key to requireNotNull(path)))
                        repository.saveJob(job)
                    }
                    if (job.autoGallery && image.key !in job.gallerySaved) {
                        if (archive.gallery(requireNotNull(path))) {
                            job = job.copy(gallerySaved = job.gallerySaved + image.key); repository.saveJob(job)
                        } else failures += "第 ${index + 1} 张已存作品，相册未保存"
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { failures += "第 ${index + 1} 张：${e.message}" }
            }
            val message = listOfNotNull(job.executionError, "已归档 ${job.saved.size}/${images.size} 张", failures.takeIf { it.isNotEmpty() }?.joinToString("\n")).joinToString("\n")
            repository.saveJob(job.copy(phase = when { failures.isNotEmpty() -> ComfyPhase.PARTIAL; job.executionError != null -> ComfyPhase.FAILED; else -> ComfyPhase.SUCCEEDED }, message = message))
            repository.change { it.copy(status = message) }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { repository.saveJob(job.copy(phase = ComfyPhase.PAUSED, message = "等待已暂停；服务器可能继续运行")) }
            throw e
        } catch (e: Exception) {
            repository.saveJob(job.copy(phase = ComfyPhase.PAUSED, message = e.message.orEmpty()))
            repository.report(e)
        }
    }
    private suspend fun readStatus(c: ComfyConnection, job: ComfyJob): RemoteStatus {
        repeat(3) { attempt ->
            try { return backend.status(c, requireNotNull(job.promptId), job.outputNodes) }
            catch (e: IOException) { if (attempt == 2) throw e; delay(1000L * (attempt + 1)) }
        }
        error("无法读取任务状态")
    }
}