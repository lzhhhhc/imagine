package com.lo.imagine.data.comfy

import com.google.gson.*
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import java.io.File
import java.io.IOException
import java.net.URLConnection
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun comfyBaseUrl(raw: String): HttpUrl {
    val url = raw.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("请输入完整 http:// 或 https:// 服务器地址")
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "地址不能包含账号、查询参数或片段；Key 请填到独立输入框" }
    return if (url.encodedPath.endsWith('/')) url else url.newBuilder().addPathSegment("").build()
}

/** OkHttp reports a stalled socket as the bare word "timeout". Keep that out of the workbench. */
internal fun comfyIoMessage(error: IOException): IOException {
    val raw = error.message.orEmpty()
    val timedOut = error is java.net.SocketTimeoutException ||
        raw.equals("timeout", ignoreCase = true) || raw.contains("timed out", ignoreCase = true)
    if (!timedOut) return error
    return IOException("连接 ComfyUI 超时。图片较大或服务器暂时没响应，请确认地址还能打开后再试", error)
}

class ComfyClient : WorkflowBackend {
    override val providerId = COMFY_PROVIDER
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).callTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    /** Uploads and downloads move whole files; a 10-second write stall must not abort them. */
    private val transfer = http.newBuilder()
        .readTimeout(180, TimeUnit.SECONDS).writeTimeout(0, TimeUnit.SECONDS).callTimeout(10, TimeUnit.MINUTES)
        .build()

    private fun endpoint(c: ComfyConnection, vararg segments: String): HttpUrl {
        require(c.providerId == providerId) { "未实现的后端：${c.providerId}" }
        return comfyBaseUrl(c.baseUrl).newBuilder().apply { segments.forEach { addPathSegment(it) } }.build()
    }
    private fun requestBuilder(c: ComfyConnection, url: HttpUrl): Request.Builder {
        val builder = Request.Builder().url(url).header("Accept", "application/json").header("User-Agent", "Imagine-ComfyUI")
        if (c.bearerToken.isNotBlank()) builder.header("Authorization", "Bearer ${c.bearerToken.trim()}")
        return builder
    }
    private fun request(c: ComfyConnection, url: HttpUrl, body: JsonObject? = null): Request {
        val builder = requestBuilder(c, url)
        if (body != null) {
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            builder.post(object : RequestBody() {
                override fun contentType() = "application/json; charset=utf-8".toMediaType()
                override fun contentLength() = bytes.size.toLong()
                override fun isOneShot() = true // Also prevents 503/408 follow-up resubmission.
                override fun writeTo(sink: BufferedSink) { sink.write(bytes) }
            })
        }
        return builder.build()
    }
    private fun fileBody(source: File, mediaType: MediaType, length: Long): RequestBody = object : RequestBody() {
        override fun contentType() = mediaType
        override fun contentLength() = length
        override fun isOneShot() = true
        override fun writeTo(sink: BufferedSink) {
            var copied = 0L
            source.inputStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    require(copied <= length) { "上传文件在读取时变大" }
                    sink.write(buffer, 0, count)
                }
            }
            require(copied == length) { "上传文件读取不完整" }
        }
    }
    private suspend fun <T> execute(request: Request, consume: (Response) -> T, client: OkHttpClient = http): T = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(comfyIoMessage(e))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use(consume)
                    if (continuation.isActive) continuation.resume(result)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
    private fun text(response: Response, maxBytes: Int = 16 * 1024 * 1024): String {
        val body = response.body ?: throw IOException("服务器响应为空")
        require(body.contentLength() <= maxBytes) { "服务器响应过大" }
        val out = java.io.ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer); if (n < 0) break
                require(out.size() + n <= maxBytes) { "服务器响应过大" }
                out.write(buffer, 0, n)
            }
        }
        return out.toString("UTF-8")
    }
    private fun jsonResponse(response: Response): JsonObject {
        val raw = text(response)
        val json = runCatching { JsonParser.parseString(raw).takeIf { it.isJsonObject }?.asJsonObject }.getOrNull()
        if (!response.isSuccessful) {
            val detail = json?.get("error")?.let { if (it.isJsonObject) it.asJsonObject.get("message")?.asString else it.toString() }
            val nodes = json?.getAsJsonObject("node_errors")?.keySet()?.joinToString().orEmpty()
            throw ComfyHttpException(response.code, "HTTP ${response.code}：${detail ?: when (response.code) { 401, 403 -> "鉴权被拒绝，请检查 Key"; in 300..399 -> "地址发生跳转，请填写最终 API 地址"; else -> "ComfyUI 请求失败" }}${if (nodes.isNotBlank()) "；节点 $nodes" else ""}")
        }
        return json ?: throw IOException("服务器返回的不是 JSON 对象，请检查地址是否指向 ComfyUI API")
    }
    private suspend fun get(c: ComfyConnection, vararg path: String): JsonObject = execute(request(c, endpoint(c, *path)), ::jsonResponse)

    override suspend fun inspect(connection: ComfyConnection): String {
        val result = get(connection, "system_stats")
        require(result.get("system")?.isJsonObject == true && result.get("devices")?.isJsonArray == true) { "响应不是 ComfyUI 系统信息" }
        val version = result.getAsJsonObject("system").get("comfyui_version")?.asString.orEmpty()
        return "已连接 ComfyUI${if (version.isNotBlank()) " $version" else ""}"
    }
    override suspend fun nodeInfo(connection: ComfyConnection, classType: String): JsonObject =
        get(connection, "object_info", classType).get(classType)?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("服务器未安装节点：$classType")

    override suspend fun upload(connection: ComfyConnection, source: File, filename: String): UploadedImage {
        require(source.isFile) { "待上传图片不存在" }
        val length = source.length()
        require(length in 1..COMFY_MAX_UPLOAD_BYTES) { "图片大小必须在 1 B–128 MiB 之间" }
        require(filename.isNotBlank() && filename.length <= 255 && filename == filename.substringAfterLast('/') && filename == filename.substringAfterLast('\\') && filename.none { it.isISOControl() }) {
            "上传文件名无效"
        }
        val mediaType = URLConnection.guessContentTypeFromName(filename)?.toMediaType() ?: "application/octet-stream".toMediaType()
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("image", filename, fileBody(source, mediaType, length))
            .addFormDataPart("type", "input")
            .build()
        val request = requestBuilder(connection, endpoint(connection, "upload", "image")).post(multipart).build()
        val json = execute(request, ::jsonResponse, transfer)
        val name = json.get("name")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?: throw IOException("上传响应缺少文件名")
        val subfolder = json.get("subfolder")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString.orEmpty()
        val type = json.get("type")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: "input"
        return UploadedImage(name, subfolder, type)
    }

    override suspend fun submit(connection: ComfyConnection, graph: JsonObject, clientId: String): Submission {
        val body = JsonObject().apply { add("prompt", graph); addProperty("client_id", clientId) }
        val req = request(connection, endpoint(connection, "prompt"), body)
        try {
            val json = execute(req, ::jsonResponse)
            val id = json.get("prompt_id")?.takeIf { it.isJsonPrimitive }?.asString
            if (id.isNullOrBlank()) throw IOException("提交响应缺少 prompt_id")
            val warnings = json.getAsJsonObject("node_errors")?.keySet().orEmpty()
            return Submission(id, if (warnings.isEmpty()) "" else "服务器已接收，部分输出有校验提示：${warnings.joinToString()}")
        } catch (e: ComfyHttpException) {
            if (e.code in 400..499 && e.code !in setOf(408, 429)) throw e
            throw SubmissionUncertain(e)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { throw SubmissionUncertain(e) }
    }

    override suspend fun status(connection: ComfyConnection, id: String, outputs: List<String>): RemoteStatus {
        val entry = get(connection, "history", id).get(id)?.takeIf { it.isJsonObject }?.asJsonObject
        if (entry != null) {
            val state = entry.getAsJsonObject("status") ?: throw IOException("历史记录缺少执行状态")
            val stateName = state.get("status_str")?.asString
            val failed = stateName == "error"
            val complete = state.get("completed")?.asBoolean == true || failed
            if (complete) {
                val error = if (failed) state.getAsJsonArray("messages")?.mapNotNull { row ->
                    if (!row.isJsonArray || row.asJsonArray.size() < 2) null else {
                        val data = row.asJsonArray[1].takeIf { it.isJsonObject }?.asJsonObject
                        data?.get("exception_message")?.asString?.take(500)
                    }
                }?.lastOrNull() ?: "服务器执行失败或任务被中断" else null
                return RemoteStatus(if (failed) ComfyPhase.FAILED else ComfyPhase.SUCCEEDED, images(entry, outputs), error, true)
            }
        }
        val queue = get(connection, "queue")
        fun contains(key: String) = queue.getAsJsonArray(key)?.any { it.isJsonArray && it.asJsonArray.size() > 1 && it.asJsonArray[1].asString == id } == true
        return RemoteStatus(when { contains("queue_running") -> ComfyPhase.RUNNING; contains("queue_pending") -> ComfyPhase.QUEUED; else -> ComfyPhase.TRACKING })
    }

    private fun images(entry: JsonObject, selected: List<String>): List<RemoteImage> {
        val outputs = entry.getAsJsonObject("outputs") ?: return emptyList()
        return selected.flatMap { id ->
            outputs.get(id)?.takeIf { it.isJsonObject }?.asJsonObject?.getAsJsonArray("images")?.mapNotNull { value ->
                if (!value.isJsonObject) return@mapNotNull null
                val image = value.asJsonObject
                val filename = image.get("filename")?.asString ?: return@mapNotNull null
                val type = image.get("type")?.asString ?: "output"
                require(type in setOf("input", "output", "temp")) { "未知图片目录类型" }
                RemoteImage(id, filename, image.get("subfolder")?.asString.orEmpty(), type)
            }.orEmpty()
        }.distinctBy { it.key }
    }

    override suspend fun findSubmitted(connection: ComfyConnection, clientId: String): List<String> {
        fun match(value: JsonElement): String? {
            if (!value.isJsonArray) return null
            val row = value.asJsonArray
            return if (row.size() > 3 && row[3].isJsonObject && row[3].asJsonObject.get("client_id")?.asString == clientId) row[1].asString else null
        }
        val queue = get(connection, "queue")
        val found = listOf("queue_running", "queue_pending").flatMap { queue.getAsJsonArray(it)?.mapNotNull(::match).orEmpty() }.toMutableList()
        val url = endpoint(connection, "history").newBuilder().addQueryParameter("max_items", "100").build()
        val history = execute(request(connection, url), ::jsonResponse)
        history.entrySet().forEach { (_, value) ->
            value.takeIf { it.isJsonObject }?.asJsonObject?.get("prompt")?.let { match(it)?.let(found::add) }
        }
        return found.distinct()
    }

    override suspend fun removeQueued(connection: ComfyConnection, id: String) {
        require(id.isNotBlank())
        val body = JsonObject().apply { add("delete", JsonArray().apply { add(id) }) }
        execute(request(connection, endpoint(connection, "queue"), body), { response ->
            if (!response.isSuccessful) jsonResponse(response)
        })
    }

    override suspend fun download(connection: ComfyConnection, image: RemoteImage, destination: File) {
        val url = endpoint(connection, "view").newBuilder().addQueryParameter("filename", image.filename)
            .addQueryParameter("subfolder", image.subfolder).addQueryParameter("type", image.type).build()
        execute(request(connection, url), { response ->
            if (!response.isSuccessful) jsonResponse(response)
            val body = response.body ?: throw IOException("图片内容为空")
            val limit = 128L * 1024 * 1024
            require(body.contentLength() <= limit) { "单张图片超过 128 MiB" }
            try {
                destination.outputStream().use { out -> body.byteStream().use { input ->
                    val buffer = ByteArray(32768); var size = 0L
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        size += n; require(size <= limit) { "图片超过大小限制" }; out.write(buffer, 0, n)
                    }
                    require(size > 0 && (body.contentLength() < 0 || size == body.contentLength())) { "图片下载不完整" }
                } }
            } catch (e: Exception) { destination.delete(); throw e }
        }, transfer)
    }
}