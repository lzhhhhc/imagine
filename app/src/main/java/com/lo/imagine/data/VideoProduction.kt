package com.lo.imagine.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** Explicit wire protocol and capability version; never inferred from a model name. */
enum class VideoProtocol(val id: String, val label: String, val engineId: String) {
    GROK("grok", "Grok 标准", "grok"),
    GROK15("grok15", "Grok 1.5 · 首尾帧与参考图", "grok"),
    SEEDANCE("seedance", "Seedance 2.x · Ark", "seedance");
    companion object { fun fromId(id: String?): VideoProtocol? = entries.find { it.id == id } }
}

data class VideoReference(val label: String, val dataUri: String)
data class VideoInput(
    val prompt: String,
    val seconds: Int,
    val aspect: String,
    val references: List<VideoReference> = emptyList(),
    val firstFrame: String? = null,
    val lastFrame: String? = null,
    val resolution: String = "720p"
)

fun videoInputError(protocol: VideoProtocol, input: VideoInput): String? = when {
    input.prompt.isBlank() -> "请填写本镜画面提示词"
    input.seconds !in (if (protocol == VideoProtocol.SEEDANCE) 4..15 else 1..15) ->
        if (protocol == VideoProtocol.SEEDANCE) "Seedance 2.x 每镜需 4–15 秒，请调整本镜时长" else "Grok 每镜需 1–15 秒，请调整本镜时长"
    input.aspect !in (if (protocol == VideoProtocol.SEEDANCE) listOf("16:9", "9:16", "1:1", "4:3", "3:4", "21:9")
        else listOf("16:9", "9:16", "1:1", "4:3", "3:4", "3:2", "2:3")) -> "当前协议不支持 ${input.aspect} 画幅"
    input.resolution !in listOf("480p", "720p") -> "当前制作入口支持 480p 或 720p"
    input.references.size > (if (protocol == VideoProtocol.SEEDANCE) 9 else 7) -> "参考图过多：${protocol.label} 最多 ${if (protocol == VideoProtocol.SEEDANCE) 9 else 7} 张"
    protocol == VideoProtocol.GROK && input.lastFrame != null -> "尾帧需要选择 Grok 1.5 协议与对应模型"
    protocol == VideoProtocol.GROK && input.firstFrame != null && input.references.isNotEmpty() -> "Grok 标准不能同时传首帧和参考图；请选择 Grok 1.5 与对应模型，或明确移除其中一类"
    protocol == VideoProtocol.SEEDANCE && input.references.isNotEmpty() && (input.firstFrame != null || input.lastFrame != null) ->
        "Seedance 的参考图模式与首尾帧模式请分开使用；保留人物环境参考图时，先清除本镜首尾帧"
    protocol == VideoProtocol.SEEDANCE && input.lastFrame != null && input.firstFrame == null -> "Seedance 尾帧需要同时提供首帧"
    (input.references.map { it.dataUri } + listOfNotNull(input.firstFrame, input.lastFrame)).any {
        !it.startsWith("data:image/jpeg;base64,") || it.length <= 23
    } -> "参考图片未正确编码，请重新选择"
    else -> null
}

fun videoRequestBody(protocol: VideoProtocol, model: String, input: VideoInput): String {
    videoInputError(protocol, input)?.let { throw IllegalArgumentException(it) }
    require(model.isNotBlank()) { "请配置视频模型" }
    val prompt = buildString {
        append(input.prompt.trim())
        input.references.forEachIndexed { i, ref ->
            append("\n").append(if (protocol == VideoProtocol.SEEDANCE) "@Image${i + 1}" else "<IMAGE_${i + 1}>")
                .append("：").append(ref.label)
        }
    }
    val body = linkedMapOf<String, Any>("model" to model.trim(), "duration" to input.seconds,
        "resolution" to input.resolution)
    if (protocol == VideoProtocol.SEEDANCE) {
        body["ratio"] = input.aspect
        body["content"] = buildList<Map<String, Any>> {
            add(mapOf("type" to "text", "text" to prompt))
            fun image(uri: String, role: String) = mapOf("type" to "image_url", "image_url" to mapOf("url" to uri), "role" to role)
            input.references.forEach { add(image(it.dataUri, "reference_image")) }
            input.firstFrame?.let { add(image(it, "first_frame")) }
            input.lastFrame?.let { add(image(it, "last_frame")) }
        }
    } else {
        body["prompt"] = prompt
        body["aspect_ratio"] = input.aspect
        if (input.references.isNotEmpty()) body["reference_images"] = input.references.map { mapOf("url" to it.dataUri) }
        input.firstFrame?.let { body["image"] = mapOf("url" to it) }
        input.lastFrame?.let { body["last_frame"] = mapOf("url" to it) }
    }
    return Gson().toJson(body)
}

fun videoEndpoint(base: String, protocol: VideoProtocol, taskId: String? = null): HttpUrl {
    val url = base.trim().trimEnd('/').toHttpUrl()
    require(url.query == null && url.fragment == null && url.username.isEmpty() && url.password.isEmpty()) { "视频基础地址无效" }
    val path = url.encodedPath.trimEnd('/')
    val version = if (protocol == VideoProtocol.SEEDANCE) "/api/v3" else "/v1"
    require(path.isEmpty() || path.endsWith(version)) { "基础地址应为服务根地址或以 $version 结尾" }
    val builder = url.newBuilder().encodedPath(if (path.isEmpty()) version else path)
    if (protocol == VideoProtocol.SEEDANCE) builder.addPathSegments("contents/generations/tasks")
    else builder.addPathSegment("videos").apply { if (taskId == null) addPathSegment("generations") }
    if (taskId != null) {
        require(taskId.isNotBlank()) { "任务编号为空" }
        builder.addPathSegment(taskId)
    }
    return builder.build()
}

data class VideoTaskResult(val status: String, val url: String? = null) {
    val finished: Boolean get() = status in listOf("done", "failed", "expired", "cancelled")
}

fun parseVideoTask(protocol: VideoProtocol, json: JsonObject): VideoTaskResult {
    val status = json.get("status")?.asString ?: error("视频任务缺少状态")
    val normalized = when (status) {
        "pending", "queued", "running" -> "pending"
        "done", "succeeded" -> "done"
        "failed", "expired", "cancelled" -> status
        else -> error("视频服务返回未知状态：${status.take(40)}")
    }
    val url = if (normalized == "done") {
        val value = if (protocol == VideoProtocol.SEEDANCE) json.getAsJsonObject("content")?.get("video_url")?.asString
            else json.getAsJsonObject("video")?.get("url")?.asString
        require(!value.isNullOrBlank()) { "任务已完成但没有视频地址" }
        require(value.toHttpUrl().scheme == "https" || value.toHttpUrl().scheme == "http")
        value
    } else null
    return VideoTaskResult(normalized, url)
}

class VideoClient(private val client: OkHttpClient = OkHttpClient.Builder()
    .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
    .callTimeout(90, TimeUnit.SECONDS).build()) {
    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(IOException("网络请求未完成，请检查连接"))
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }
    private suspend fun json(request: Request): JsonObject = withContext(Dispatchers.IO) { execute(request).use { response ->
        check(response.isSuccessful) { "视频服务 HTTP ${response.code}，请检查协议、模型权限与参数" }
        val text = response.body?.string() ?: error("视频服务返回为空")
        JsonParser.parseString(text).asJsonObject
    } }
    suspend fun submit(settings: VideoApiSettings, protocol: VideoProtocol, input: VideoInput): String {
        videoApiConfigurationError(settings)?.let { throw IllegalArgumentException(it) }
        val body = videoRequestBody(protocol, settings.model, input)
        val result = json(Request.Builder().url(videoEndpoint(settings.baseUrl, protocol))
            .header("Authorization", "Bearer ${settings.apiKey.trim()}")
            .post(body.toRequestBody("application/json".toMediaType())).build())
        return result.get(if (protocol == VideoProtocol.SEEDANCE) "id" else "request_id")?.asString
            ?.takeIf { it.isNotBlank() } ?: error("提交后未返回任务编号；请在服务商后台核对，勿直接重复提交")
    }
    suspend fun query(settings: VideoApiSettings, protocol: VideoProtocol, id: String): VideoTaskResult {
        videoApiConfigurationError(settings)?.let { throw IllegalArgumentException(it) }
        return parseVideoTask(protocol, json(Request.Builder().url(videoEndpoint(settings.baseUrl, protocol, id))
            .header("Authorization", "Bearer ${settings.apiKey.trim()}").get().build()))
    }
    /** Result hosts receive no API credentials. Atomic local copy survives temporary URL expiry. */
    suspend fun download(url: String, target: File): Unit = withContext(Dispatchers.IO) {
        val partial = File(target.parentFile, "${target.name}.part")
        try {
            execute(Request.Builder().url(url).get().build()).use { response ->
                check(response.isSuccessful) { "视频下载 HTTP ${response.code}" }
                val body = response.body ?: error("视频下载为空")
                check(body.contentType()?.subtype?.contains("json") != true && body.contentType()?.type != "text") { "下载返回的不是视频" }
                target.parentFile?.mkdirs()
                body.byteStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
                check(partial.length() > 0 && partial.renameTo(target)) { "视频保存失败" }
            }
        } finally { partial.delete() }
    }
}

/** No keys or image payloads on disk. A submitting record blocks accidental duplicate POSTs. */
data class DirectorVideoTask(
    val localId: String = UUID.randomUUID().toString(),
    val engineId: String,
    val shotId: Long,
    val shotLabel: String,
    val protocolId: String,
    val baseUrl: String,
    val model: String,
    val seconds: Int,
    val aspect: String,
    val prompt: String,
    val referenceLabels: List<String>,
    val requestId: String? = null,
    val status: String = "submitting",
    val videoUrl: String? = null,
    val localPath: String? = null
)

class DirectorVideoTaskStore(private val file: File) {
    private val gson = Gson()
    companion object { private val lock = Any() }
    fun load(): List<DirectorVideoTask> = synchronized(lock) {
        if (!file.exists()) emptyList() else
            gson.fromJson(file.readText(), Array<DirectorVideoTask>::class.java).toList()
    }
    fun save(task: DirectorVideoTask) = synchronized(lock) {
        val next = load().filterNot { it.localId == task.localId } + task
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(gson.toJson(next))
        check(temp.renameTo(file)) { "视频任务记录保存失败" }
    }
}
