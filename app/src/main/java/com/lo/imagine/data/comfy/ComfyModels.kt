package com.lo.imagine.data.comfy

import com.google.gson.JsonObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

const val COMFY_PROVIDER = "comfy-local"
fun comfyId(): String = UUID.randomUUID().toString()
fun comfyHash(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

data class ComfyConnection(
    val providerId: String = COMFY_PROVIDER,
    val baseUrl: String = "",
    val bearerToken: String = "",
    val waitMinutes: Int = 30
) {
    fun fingerprint(): String = comfyHash("$providerId\n$baseUrl\n$bearerToken")
}
enum class ParameterKind(val label: String) {
    PROMPT("画面提示词"), NEGATIVE("负面提示词"), IMAGE("参考图片"), SEED("种子"), STEPS("步数"),
    CFG("CFG"), WIDTH("宽度"), HEIGHT("高度"), BATCH("批量"), SAMPLER("采样器"), CUSTOM("其他参数")
}

data class InputTarget(val nodeId: String = "", val input: String = "") {
    val key: String get() = "$nodeId/$input"
}
data class WorkflowParameter(
    val id: String = comfyId(),
    val label: String = "",
    val kind: ParameterKind = ParameterKind.CUSTOM,
    val targets: List<InputTarget> = emptyList(),
    val value: String = "",
    val randomSeed: Boolean = false
)

/** 导入分析给出的勾选项。只有勾上的才会变成 WorkflowParameter 存下来。 */
data class BindingProposal(
    val id: String = comfyId(),
    val label: String = "",
    val kind: ParameterKind = ParameterKind.CUSTOM,
    val targets: List<InputTarget> = emptyList(),
    val value: String = "",
    val reason: String = ""
) {
    fun toParameter() = WorkflowParameter(id = id, label = label, kind = kind, targets = targets, value = value)
}
data class ComfyWorkflow(
    val schemaVersion: Int = 1,
    val id: String = comfyId(),
    val name: String = "工作流",
    val graph: JsonObject = JsonObject(),
    val parameters: List<WorkflowParameter> = emptyList(),
    val outputNodes: List<String> = emptyList(),
    /** 展开节点卡在生成页的显示顺序（节点 ID 列表）；缺省按绑定顺序。 */
    val panelOrder: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)
data class ScalarInput(val target: InputTarget, val nodeTitle: String, val value: String, val type: String)
/** One ComfyUI node and every literal input that can be shown together as its panel. */
data class NodePanel(val nodeId: String, val title: String, val classType: String, val fields: List<ScalarInput>)
data class PreparedWorkflow(val graph: JsonObject, val values: Map<String, String>)
const val COMFY_MAX_UPLOAD_BYTES = 128L * 1024 * 1024
data class UploadedImage(
    val filename: String = "", val subfolder: String = "", val type: String = "input"
) {
    init {
        require(filename.isNotBlank() && filename != "." && filename != "..") { "上传响应缺少有效文件名" }
        require(filename.substringAfterLast('/') == filename && filename.substringAfterLast('\\') == filename) { "上传响应文件名不能包含路径" }
        require(type == "input") { "图片未上传到 ComfyUI 输入目录" }
        require(subfolder.split('/').none { it == ".." }) { "上传响应包含非法目录" }
    }
    val inputValue: String
        get() = listOf(subfolder.trim('/'), filename).filter { it.isNotBlank() }.joinToString("/")
}
data class RemoteImage(

    val nodeId: String = "", val filename: String = "", val subfolder: String = "", val type: String = "output"
) {
    val key: String get() = comfyHash("$filename\n$subfolder\n$type").take(24)
}
enum class ComfyPhase(val label: String) {
    SUBMITTING("正在提交"), UNKNOWN("提交结果待确认"), QUEUED("排队中"), RUNNING("执行中"),
    TRACKING("查询任务"), PAUSED("等待已暂停"), DOWNLOADING("下载图片"), PARTIAL("部分完成"),
    SUCCEEDED("已完成"), FAILED("执行失败"), REMOVED("已移出队列")
}
data class ComfyJob(
    val schemaVersion: Int = 1,
    val id: String = comfyId(),
    val providerId: String = COMFY_PROVIDER,
    val origin: String = "",
    val clientId: String = comfyId(),
    val promptId: String? = null,
    val workflowName: String = "",
    val graph: JsonObject = JsonObject(),
    val values: Map<String, String> = emptyMap(),
    val prompt: String = "",
    val outputNodes: List<String> = emptyList(),
    val phase: ComfyPhase = ComfyPhase.SUBMITTING,
    val message: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val images: List<RemoteImage> = emptyList(),
    val saved: Map<String, String> = emptyMap(),
    val gallerySaved: List<String> = emptyList(),
    val autoGallery: Boolean = false,
    val executionError: String? = null
)
data class WorkflowLibrary(val schemaVersion: Int = 1, val selectedId: String? = null, val workflows: List<ComfyWorkflow> = emptyList())
data class ComfyState(
    val ready: Boolean = false,
    val connection: ComfyConnection = ComfyConnection(),
    val workflows: List<ComfyWorkflow> = emptyList(),
    val selectedId: String? = null,
    val jobs: List<ComfyJob> = emptyList(),
    val busy: Boolean = false,
    val activeJobId: String? = null,
    val status: String = "",
    val error: String? = null,
    val connectedFingerprint: String? = null
) {
    val selected: ComfyWorkflow? get() = workflows.find { it.id == selectedId }
}
data class Submission(val id: String, val warning: String = "")
data class RemoteStatus(val phase: ComfyPhase, val images: List<RemoteImage> = emptyList(), val error: String? = null, val complete: Boolean = false)
class SubmissionUncertain(cause: Throwable) : Exception("提交响应中断，服务器可能已经接收；请先查询任务，不要重复生成", cause)
class ComfyHttpException(val code: Int, message: String) : Exception(message)

/** Provider boundary: future hosted services implement their own protocol; no automatic provider switching. */
interface WorkflowBackend {
    val providerId: String
    suspend fun inspect(connection: ComfyConnection): String
    suspend fun nodeInfo(connection: ComfyConnection, classType: String): JsonObject
    suspend fun upload(connection: ComfyConnection, source: File, filename: String): UploadedImage
    suspend fun submit(connection: ComfyConnection, graph: JsonObject, clientId: String): Submission

    suspend fun status(connection: ComfyConnection, id: String, outputs: List<String>): RemoteStatus
    suspend fun findSubmitted(connection: ComfyConnection, clientId: String): List<String>
    suspend fun removeQueued(connection: ComfyConnection, id: String)
    suspend fun download(connection: ComfyConnection, image: RemoteImage, destination: File)
}
