package com.lo.imagine.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import com.lo.imagine.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

sealed class ApiResult {
    data class Success(val images: List<ImageData>) : ApiResult()
    data class Error(val message: String) : ApiResult()
}

class ImageRepository(private val context: Context) {
    companion object {
        // 纯文本清洗不依赖 Android 上下文，放在 companion 里以便单元测试直接覆盖。

        /**
         * LLM 普通文本出口：去掉协议包装、实体编码、Markdown 围栏和多余空行。
         * 业务标题不在这里处理，避免破坏导演脚本或结构化文本。
         */
        internal fun sanitizeLlmText(text: String): String {
            val tagPattern = Regex(
                """(?i)</?\s*(?:正文|正向提示词|提示词|prompt|output|response|answer|result|content|text|scene|subject|details?|composition|constraints?)\b(?:\s+[^>]*)?\s*>"""
            )
            return text
                .replace("&amp;", "&", ignoreCase = true)
                .replace("&lt;", "<", ignoreCase = true)
                .replace("&gt;", ">", ignoreCase = true)
                .replace(tagPattern, "")
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("```") }
                .joinToString("\n")
                .trim()
        }

        /**
         * 提示词文本出口：在通用清洗之上移除模板分节标题、编号和 negative 段。
         * 反推、创作润色、修图润色和翻译结果统一经过这里后才交给 UI 状态。
         */
        internal fun sanitizePromptText(text: String): String {
            val normalized = sanitizeLlmText(text)
            val negativeHeader = Regex(
                """(?i)^[*#_\-\s]*(?:negative\s*(?:prompt|prompting)?|负面提示(?:词)?|negative)\s*[:：]"""
            )
            val heading = Regex(
                """(?i)^[*#_\s]*(?:scene|subject|details?|composition|constraints?|background|lighting|style|medium|pose|expression|mood|camera|outfit|appearance|emotion|atmosphere|environment|color\s*palette)(?:\s+(?:背景\s*[/／]\s*场景|主体\s*与\s*动作|关键\s*细节|构图\s*与\s*光线|约束))?\s*[*#_]*\s*(?:[:：\-–—]\s*|$)"""
            )
            val chineseHeading = Regex(
                """^[*#_\s]*(?:背景\s*[/／]\s*场景|构图\s*与\s*光线|关键\s*细节|主体\s*与\s*动作|主体|人物|表情|情绪|姿势|动作|服装|配饰|细节|光影|光线|光照|背景|场景|构图|镜头|媒介|风格|氛围|色调|环境|约束|限制)\s*[*#_]*\s*(?:[:：\-–—]\s*|$)"""
            )
            var inNegative = false
            return normalized.lines().mapNotNull { rawLine ->
                var line = rawLine.trim()
                if (line.isBlank()) return@mapNotNull null
                if (negativeHeader.containsMatchIn(line)) {
                    inNegative = true
                    return@mapNotNull null
                }
                if (inNegative) return@mapNotNull null
                line = line.replace(Regex("""^第[一二三四五六七八九十\d]+行\s*[:：]?\s*"""), "")
                val numericEmphasis = Regex("""^[-+]?\d+(?:\.\d+)?::""").containsMatchIn(line)
                if (!numericEmphasis) line = line.replace(Regex("""^\d+\s*[.、)）]\s*"""), "")
                line = line.replace(Regex("""^【[^】]*】\s*"""), "")
                if (!numericEmphasis) line = line.replace(Regex("""^(?:[-•·]\s*)+"""), "").trim()
                line = line.replace(Regex("""^[*#_]+"""), "").trim()
                // 加粗段名行首剥除后会残留 `画面风格**：`，只清理紧邻冒号的星号，不影响 NAI 的 `::` 权重语法。
                line = line.replace(Regex("""\*{1,2}(?=\s*[:：])"""), "").trim()
                line = line.replace(heading, "").trim()
                line = line.replace(chineseHeading, "").trim()
                line = line.replace(Regex("""[*#_]+$"""), "").trim()
                line.takeIf { it.isNotBlank() }
            }.joinToString("\n").trim()
        }

        internal fun sanitizeReversePrompt(text: String): String = sanitizePromptText(text)
        internal fun sanitizeDeepPrompt(text: String): String = sanitizePromptText(text)
    }

    private val gson = Gson()

    /** 文生图 */
    suspend fun generate(
        settings: AppSettings,
        prompt: String,
        negativePrompt: String?,
        size: String,
        count: Int
    ): ApiResult = withContext(Dispatchers.IO) {
        imageApiConfigurationError(settings)?.let { return@withContext ApiResult.Error(it) }
        val api = buildApi(settings.baseUrl, settings.apiKey)
            ?: return@withContext ApiResult.Error("绘图 API 配置无效，请在设置中检查。")
        // 中转端对部分模型（如 gpt-image-2）有排队锁：并发提交会返回
        // "你已有生成任务在排队"。遇到该错误自动等待重试，实现优雅串行。
        var lastResult: ApiResult = ApiResult.Error("未发起请求")
        for (attempt in 1..4) {
            lastResult = try {
                val model = settings.genModel.trim()
                // FLUX/gpt-image 等架构不支持 negative_prompt（发了也被忽略）：
                // 改为把负面词转成正向对冲词追加到 prompt，让「负面提示」真正起作用
                val useNegField = supportsNegativePrompt(model)
                val effectivePrompt = if (!useNegField && !negativePrompt.isNullOrBlank()) {
                    val counter = negativeToPositive(negativePrompt)
                    if (counter.isNotBlank()) "$prompt, $counter" else prompt
                } else prompt
                val resp = api.generate(
                    GenerateRequest(
                        model = model,
                        prompt = effectivePrompt,
                        n = count,
                        size = size,
                        negativePrompt = if (useNegField) negativePrompt else null
                    )
                )
                handle(resp, count)
            } catch (e: Exception) {
                ApiResult.Error(describeError(e))
            }
            val queued = lastResult is ApiResult.Error &&
                lastResult.message.contains("排队")
            if (!queued) return@withContext lastResult
            if (attempt < 4) delay((attempt * 10_000L))
        }
        lastResult
    }

    /**
     * gpt-image 系模型在严格 OpenAI 兼容端只接受官方枚举尺寸（1024x1024 / 1536x1024 / 1024x1536 / auto）。
     * 宽松中转（大部分第三方）接受任意 WxH 且能给出更高分辨率，所以只把它当作「被拒后的兜底尺寸」，
     * 优先按真实尺寸请求。按目标画幅吸附到最接近的枚举。
     */
    private fun snapGptImageSize(model: String, size: String): String {
        if (!model.lowercase().contains("gpt-image")) return size
        val parts = size.split("x")
        val w = parts.getOrNull(0)?.toIntOrNull() ?: return "auto"
        val h = parts.getOrNull(1)?.toIntOrNull() ?: return "auto"
        if (w <= 0 || h <= 0) return "auto"
        return when {
            h > w * 1.15f -> "1024x1536"   // 竖版画幅
            w > h * 1.15f -> "1536x1024"   // 横版画幅
            else -> "1024x1024"            // 方形/接近方形
        }
    }

        /** 尺寸/端点兼容性兜底的状态码：尺寸不被接受或端点缺失时换尺寸/换路径重试 */
    private val SIZE_FALLBACK_CODES = setOf(400, 404, 405, 415, 422, 501)

    /**
     * JSON 图生图：先按真实尺寸请求（宽松中转能给出更高分辨率）；
     * 被拒后自动改用 gpt-image 官方枚举尺寸重试一次（严格端点只认枚举）。
     */
    private suspend fun generateWithSizeFallback(
        api: ImageApi,
        model: String,
        prompt: String,
        count: Int,
        size: String,
        negativePrompt: String?,
        image: String,
        mask: String? = null
    ): ImageResponse {
        val safeSize = snapGptImageSize(model, size)
        val candidates = if (safeSize == size) listOf(size) else listOf(size, safeSize)
        // 与 generate() 同策略：模型不支持 negative_prompt 时转正向对冲词
        val useNegField = supportsNegativePrompt(model)
        val effectivePrompt = if (!useNegField && !negativePrompt.isNullOrBlank()) {
            val counter = negativeToPositive(negativePrompt)
            if (counter.isNotBlank()) "$prompt, $counter" else prompt
        } else prompt
        var lastErr: retrofit2.HttpException? = null
        for (s in candidates) {
            try {
                return api.generate(
                    GenerateRequest(
                        model = model,
                        prompt = effectivePrompt,
                        n = count,
                        size = s,
                        negativePrompt = if (useNegField) negativePrompt else null,
                        image = image,
                        mask = mask
                    )
                )
            } catch (e: retrofit2.HttpException) {
                lastErr = e
                if (e.code() !in SIZE_FALLBACK_CODES) throw e
            }
        }
        throw lastErr ?: IllegalStateException("generate failed")
    }

    /** 修图：按设置里的 editMode 走 multipart 或 JSON 带图；maskBytes 可选遮罩（透明区域将被重绘） */
    suspend fun editImage(
        settings: AppSettings,
        imageBytes: ByteArray,
        mimeType: String,
        prompt: String,
        negativePrompt: String?,
        size: String,
        count: Int = 1,
        maskBytes: ByteArray? = null
    ): ApiResult = withContext(Dispatchers.IO) {
        imageApiConfigurationError(settings)?.let { return@withContext ApiResult.Error(it) }
        val api = buildApi(settings.baseUrl, settings.apiKey)
            ?: return@withContext ApiResult.Error("绘图 API 配置无效，请在设置中检查。")
        try {
            val resp = when (settings.editMode) {
                "edits_multipart" -> {
                    val model = settings.editModel.trim()
                    // multipart 端点（gpt-image 系）不支持 negative_prompt：转正向对冲词并入 prompt
                    val mpPrompt = if (!negativePrompt.isNullOrBlank()) {
                        val counter = negativeToPositive(negativePrompt)
                        if (counter.isNotBlank()) "$prompt, $counter" else prompt
                    } else prompt
                    // 尺寸候选：宽松中转直接按真实尺寸给更高分辨率；严格端点只认枚举 → 自动换枚举重试
                    val safeSize = snapGptImageSize(model, size)
                    val sizeCandidates = if (safeSize == size) listOf(size) else listOf(size, safeSize)
                    val part = MultipartBody.Part.createFormData(
                        "image",
                        "input.png",
                        imageBytes.toRequestBody(mimeType.toMediaType())
                    )
                    val maskPart = maskBytes?.let {
                        MultipartBody.Part.createFormData(
                            "mask",
                            "mask.png",
                            it.toRequestBody("image/png".toMediaType())
                        )
                    }
                    var lastErr: retrofit2.HttpException? = null
                    var done: ImageResponse? = null
                    for (s in sizeCandidates) {
                        val fields = mapOf(
                            "model" to model.toRequestBody("text/plain".toMediaType()),
                            "prompt" to mpPrompt.toRequestBody("text/plain".toMediaType()),
                            "size" to s.toRequestBody("text/plain".toMediaType()),
                            "n" to count.coerceAtLeast(1).toString().toRequestBody("text/plain".toMediaType()),
                            "response_format" to "b64_json".toRequestBody("text/plain".toMediaType())
                        )
                        try {
                            done = api.edit(fields, part, maskPart)
                            break
                        } catch (e: retrofit2.HttpException) {
                            lastErr = e
                            if (e.code() !in SIZE_FALLBACK_CODES) throw e
                        }
                    }
                    if (done != null) {
                        done
                    } else {
                        // 端点缺失或所有尺寸都被拒 → 降级 JSON 图生图：把原图作为 image 字段一并提交。
                        Log.i(
                            "ImagineHttp",
                            "/images/edits rejected (HTTP ${lastErr?.code()}), falling back to generations+image"
                        )
                        val b64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
                        val maskB64 = maskBytes?.let {
                            android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)
                        }
                        generateWithSizeFallback(
                            api = api,
                            model = settings.editModel.trim(),
                            prompt = prompt,
                            count = count.coerceAtLeast(1),
                            size = size,
                            negativePrompt = negativePrompt,
                            image = "data:$mimeType;base64,$b64",
                            mask = maskB64?.let { "data:image/png;base64,$it" }
                        )
                    }
                }
                else -> {
                    val b64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
                    val maskB64 = maskBytes?.let {
                        android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)
                    }
                    // JSON 图生图：image 必传（否则参考图完全无效，等于纯文生图！）；
                    // mask 仅部分国产服务识别，没有就纯参考图生图。
                    generateWithSizeFallback(
                        api = api,
                        model = settings.editModel.trim(),
                        prompt = prompt,
                        count = count.coerceAtLeast(1),
                        size = size,
                        negativePrompt = negativePrompt,
                        image = "data:$mimeType;base64,$b64",
                        mask = maskB64?.let { "data:image/png;base64,$it" }
                    )
                }
            }
            handle(resp, count.coerceAtLeast(1))
        } catch (e: Exception) {
            ApiResult.Error(describeError(e))
        }
    }

    /** 拉取平台模型列表，用于设置页一键选择 */
    suspend fun fetchModels(settings: AppSettings): Result<List<ModelItem>> =
        withContext(Dispatchers.IO) {
            val api = buildApi(settings.baseUrl, settings.apiKey)
                ?: return@withContext Result.failure(
                    IllegalStateException("请先填写 API 地址和 Key")
                )
            try {
                val resp = api.models()
                val err = resp.error?.message
                if (!err.isNullOrBlank()) return@withContext Result.failure(Exception(err))
                val list = resp.data?.filter { !it.id.isNullOrBlank() }.orEmpty()
                if (list.isEmpty()) {
                    Result.failure(Exception("该平台没有返回模型列表，请手动填写模型名"))
                } else {
                    Result.success(list)
                }
            } catch (e: Exception) {
                Result.failure(Exception(describeError(e)))
            }
        }

    /** 拉取 LLM 模型列表（独立配置），用于设置页选择润色模型 */
    suspend fun fetchLlmModels(settings: AppSettings): Result<List<ModelItem>> =
        withContext(Dispatchers.IO) {
            val api = buildApi(settings.llmBaseUrl, settings.llmApiKey)
                ?: return@withContext Result.failure(
                    IllegalStateException("请先填写 LLM Base URL 和 Key")
                )
            try {
                val resp = api.models()
                val err = resp.error?.message
                if (!err.isNullOrBlank()) return@withContext Result.failure(Exception(err))
                val list = resp.data?.filter { !it.id.isNullOrBlank() }.orEmpty()
                if (list.isEmpty()) {
                    Result.failure(Exception("该 LLM 网关没有返回模型列表，请手动填写"))
                } else {
                    Result.success(list)
                }
            } catch (e: Exception) {
                Result.failure(Exception(describeError(e)))
            }
        }

    /** Single request path for the director interview. Failure preserves the current stage. */
    suspend fun directorStepTurn(
        settings: AppSettings,
        engine: DirectorEngine,
        state: DirectorInterviewState,
        transcript: String,
        characterDesc: String,
        envDesc: String,
        durationSec: String,
        videoAspect: String,
        images: List<String> = emptyList(),
        referenceLabels: String = "",
        onStream: suspend (String) -> Unit = {}
    ): Result<DirectorStepTurn> = withContext(Dispatchers.IO) {
        if (state.isReview) return@withContext Result.failure(IllegalStateException("框架已进入确认阶段"))
        llmApiConfigurationError(settings)?.let { return@withContext Result.failure(IllegalArgumentException(it)) }
        val base = settings.llmBaseUrl.trim().trimEnd('/')
        val apiKey = settings.llmApiKey.trim()
        val model = settings.llmModel.trim()
        if (base.isEmpty() || apiKey.isEmpty() || model.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("请先在设置中配置提示词润色 LLM"))
        }
        buildApi(base, apiKey)
            ?: return@withContext Result.failure(IllegalStateException("LLM 配置无效"))
        val materials = buildString {
            appendLine("【框架】")
            appendLine(state.brief())
            appendLine("【本地素材文字描述】人物：${characterDesc.ifBlank { "未选" }}")
            appendLine("环境：${envDesc.ifBlank { "未选" }}")
            appendLine("【当前界面参数，仍需用户确认】${durationSec}s，$videoAspect")
            appendLine(referenceLabels)
            appendLine("【对话记录】")
            append(transcript)
        }
        try {
            val requestBody = ChatCompletionRequest(model = model, messages = listOf(
                ChatMessage("system", directorInterviewSystem(engine, state.stageIndex)),
                ChatMessage("user", directorMultimodalContent(materials, images))
            ), stream = true)
            val raw = streamDirectorChat(base, apiKey, requestBody, onStream)
            require(raw.isNotBlank()) { "导演返回为空，请重试" }
            Result.success(parseDirectorStepTurn(raw, DIRECTOR_STAGES[state.stageIndex].key))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(describeError(e)))
        }
    }

    private suspend fun streamDirectorChat(
        baseUrl: String,
        apiKey: String,
        body: ChatCompletionRequest,
        onStream: suspend (String) -> Unit
    ): String {
        val url = "${baseUrl.trim().trimEnd('/')}/chat/completions"
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Accept", "text/event-stream")
            .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
            .build()
        val client = httpClientBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS)
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body ?: throw IOException("导演流式响应为空")
            check(response.isSuccessful) { "导演流式请求 HTTP ${response.code}" }
            val type = responseBody.contentType()
            require(type?.type == "text" && type.subtype.contains("event-stream", ignoreCase = true)) {
                "服务商未返回流式事件，请确认当前 LLM 支持 stream=true"
            }
            val full = StringBuilder()
            var done = false
            responseBody.source().use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") { done = true; break }
                    if (data.isBlank()) continue
                    val delta = directorSseDelta(data)
                    if (delta.isNotEmpty()) {
                        full.append(delta)
                        onStream(full.toString())
                    }
                }
            }
            require(done) { "导演流式响应提前中断，请重试本轮" }
            return full.toString()
        }
    }

    suspend fun createDirectorStoryboard(
        settings: AppSettings, engine: DirectorEngine, brief: String,
        totalSeconds: Int, aspect: String, images: List<String>
    ): Result<DirectorStoryboard> = withContext(Dispatchers.IO) {
        try {
            llmApiConfigurationError(settings)?.let { throw IllegalArgumentException(it) }
            val api = buildApi(settings.llmBaseUrl, settings.llmApiKey) ?: error("请配置润色 LLM")
            val response = api.chat(ChatCompletionRequest(model = settings.llmModel.trim(), messages = listOf(
                ChatMessage("system", directorProductionSystem(engine, totalSeconds, aspect)),
                ChatMessage("user", directorMultimodalContent(brief, images)))))
            response.error?.message?.takeIf { it.isNotBlank() }?.let { error(it) }
            val raw = directorReplyText(response.choices?.firstOrNull()?.message?.content
                ?: response.choices?.firstOrNull()?.text)
            require(raw.isNotBlank()) { "导演返回为空" }
            Result.success(parseDirectorStoryboard(raw, totalSeconds, aspect))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    /** Exactly one request using the selected protocol, preserving every reference image. */
    suspend fun generateDirectorFrame(
        settings: AppSettings, prompt: String, size: String, images: List<ByteArray>
    ): ApiResult = withContext(Dispatchers.IO) {
        try {
            imageApiConfigurationError(settings)?.let { throw IllegalArgumentException(it) }
            val api = buildApi(settings.baseUrl, settings.apiKey) ?: error("绘图配置无效")
            val response = if (images.isEmpty()) api.generate(GenerateRequest(
                model = settings.model.trim(), prompt = prompt, n = 1, size = size)) else {
                require(settings.editMode == "edits_multipart") {
                    "人物/环境参考图生成首帧需要在绘图设置中选择 multipart 修图协议及支持多图的模型"
                }
                val fields = mapOf("model" to settings.model.trim(), "prompt" to prompt, "n" to "1", "size" to size)
                    .mapValues { it.value.toRequestBody("text/plain".toMediaType()) }
                api.directorEdit(fields, images.mapIndexed { i, bytes ->
                    MultipartBody.Part.createFormData("image[]", "reference_${i + 1}.jpg",
                        bytes.toRequestBody("image/jpeg".toMediaType()))
                })
            }
            handle(response, 1)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { ApiResult.Error(e.message ?: "首帧生成失败") }
    }

    suspend fun polishDirectorPrompt(
        settings: AppSettings, engine: DirectorEngine, structured: String
    ): Result<String> = composeDirectorStoryboard(settings, engine, structured, emptyList())

    /** Selected video prompt engineering is used by interview and manual storyboard alike. */
    suspend fun composeDirectorStoryboard(
        settings: AppSettings,
        engine: DirectorEngine,
        brief: String,
        shots: List<String>,
        images: List<String> = emptyList()
    ): Result<String> =
        withContext(Dispatchers.IO) {
            llmApiConfigurationError(settings)?.let { return@withContext Result.failure(IllegalArgumentException(it)) }
            val base = settings.llmBaseUrl.trim().trimEnd('/')
            val key = settings.llmApiKey.trim()
            val model = settings.llmModel.trim()
            if (base.isEmpty() || key.isEmpty() || model.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("请先在「设置」里配置提示词润色 LLM")
                )
            }
            val api = buildApi(base, key)
                ?: return@withContext Result.failure(IllegalStateException("LLM 配置无效"))
            val system = directorStoryboardSystem(engine)
            val textPart = "总体设定：\n$brief\n\n分镜列表：\n${shots.joinToString("\n")}" +
                if (images.isNotEmpty()) {
                    "\n\n附实际参考图，按分镜列表中标注的图序使用。仅有标注为尾帧的图片才是结尾参考；不要把最后一张自动当作尾帧。"
                } else ""
            val userContent: Any = if (images.isEmpty()) {
                textPart
            } else {
                buildList {
                    add(mapOf("type" to "text", "text" to textPart))
                    images.forEach { b64 ->
                        add(mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:image/jpeg;base64,$b64")))
                    }
                }
            }
            val messages = listOf(
                ChatMessage("system", system),
                ChatMessage("user", userContent)
            )
            try {
                val resp = api.chat(ChatCompletionRequest(model = model, messages = messages))
                val err = resp.error?.message
                if (!err.isNullOrBlank()) return@withContext Result.failure(Exception(err))
                val rawText = resp.choices?.firstOrNull()?.message?.content
                    ?: resp.choices?.firstOrNull()?.text
                val text = when (rawText) {
                    is String -> rawText
                    else -> rawText?.toString().orEmpty()
                }
                if (text.isBlank()) {
                    Result.failure(Exception("LLM 返回为空"))
                } else {
                    Result.success(sanitizeLlmText(text))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception(describeError(e)))
            }
        }

    /** 反推提示词：直接把图丢给 vision LLM，让它写出一段可用的英文 prompt。 */
    suspend fun reversePrompt(
        settings: AppSettings,
        imageBase64: String,
        targetModel: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val base = settings.llmBaseUrl.trim().trimEnd('/')
        val key = settings.llmApiKey.trim()
        val model = settings.llmModel.trim()
        if (base.isEmpty() || key.isEmpty() || model.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("请先在「设置」里配置提示词润色 LLM")
            )
        }
        val api = buildApi(base, key)
            ?: return@withContext Result.failure(IllegalStateException("LLM 配置无效"))
        
        // 根据目标模型推断模板：NAI 走标签流清洗，其余走通用清洗
        val tpl = detectPolishTemplate(targetModel.ifBlank { settings.genModel })
        // 必须显式开 modeEnabled：resolveNaiProfile 默认关着，会让专属反推工程永远走不到
        val naiProfile = if (tpl == PolishTemplate.NOVELAI)
            resolveNaiProfile(targetModel.ifBlank { settings.genModel }, NaiOptions(modeEnabled = true))
        else null
        // system 由设置页「提示词模板」决定：填了就用用户那段，留空用内置（NAI 目标模型给专属标签流规则）
        val system = PromptTemplates.reverseSystem(settings, targetModel.ifBlank { settings.genModel })
        // 语言自适应按用户句判定：标准模式必须用中文发问，否则新模板会输出英文四段式
        val userText = if (naiProfile != null) "反推这张图为 NovelAI 提示词标签流。" else "反推这张图的提示词。"
        val messages = listOf(
            ChatMessage("system", system),
            ChatMessage(
                "user",
                listOf(
                    mapOf("type" to "text", "text" to userText),
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf("url" to "data:image/jpeg;base64,$imageBase64")
                    )
                )
            )
        )
        try {
            // system 由设置页「提示词模板」决定，不再拼接任何隐式注入
            val resp = api.chat(ChatCompletionRequest(model = model, messages = messages))
            val err = resp.error?.message
            if (!err.isNullOrBlank()) return@withContext Result.failure(Exception(err))
            val rawText = resp.choices?.firstOrNull()?.message?.content
                ?: resp.choices?.firstOrNull()?.text
            val text = when (rawText) {
                is String -> rawText
                else -> rawText?.toString().orEmpty()
            }
            if (text.isBlank()) {
                Result.failure(Exception("LLM 返回为空，请确认该模型支持图文"))
            } else {
                Result.success(if (tpl == PolishTemplate.NOVELAI) cleanNaiOutput(sanitizeLlmText(text)) else sanitizeReversePrompt(text))
            }
        } catch (e: Exception) {
            Result.failure(Exception(describeError(e)))
        }
    }


    /** 用独立 LLM 把简短想法润色成高质量提示词。
     *  kind=edit 时会带当前正在编辑的图片（base64 data uri），让 LLM 真正“看到”后再扩写；
     *  若 LLM 选了 vision 模型失败/不支持图片，自动降级为纯文本润色，保证不会失败。 */
    suspend fun polishPrompt(
        settings: AppSettings,
        raw: String,
        kind: String = "gen",
        imageBase64: String? = null,
        depth: PolishDepth = PolishDepth.MEDIUM,
        template: PolishTemplate = PolishTemplate.AUTO,
        aspectLabel: String = "",
        aspectOrientation: String = "",
        naiProfile: NaiProfile? = null,
        artistContext: String = "",
        styleContext: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val base = settings.llmBaseUrl.trim().trimEnd('/')
        val key = settings.llmApiKey.trim()
        val model = settings.llmModel.trim()
        if (base.isEmpty() || key.isEmpty() || model.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("请先在「设置」里配置提示词润色 LLM")
            )
        }
        val api = buildApi(base, key)
            ?: return@withContext Result.failure(IllegalStateException("LLM 配置无效"))
        // 模板解析：AUTO=按生成模型名推断，但 NAI 模板只跟显式 naiProfile（NAI 工作台）绑定；
// 普通链路即使 genModel 是 NAI 中继模型名、或残留 NovelAI 模板选择，也一律走通用模板，
// 否则 NAI 的深度/形态说明文字会拼进通用 system，输出还会被按 NAI 语法校验。
        val tpl = when {
            naiProfile != null -> PolishTemplate.NOVELAI
            template == PolishTemplate.AUTO ->
                detectPolishTemplate(settings.genModel)?.takeIf { it != PolishTemplate.NOVELAI }
            template == PolishTemplate.NOVELAI -> null
            else -> template
        }
        val depthNote = when (depth) {
            PolishDepth.LIGHT ->
                "本次为「轻度润色」：只修正措辞、去除重复与歧义，保持原有语言、结构与内容范围不变，" +
                    "不新增任何画面元素。输出仍为一段可直接使用的提示词。"
            PolishDepth.MEDIUM ->
                "本次为「中度润色」：在保持用户意图不变的前提下适度扩写，补充主体特征、服饰、环境、" +
                    "光线、构图与色调等细节。保持标签式表达（用逗号分隔的短语），不要写成散文或完整句子。"
            PolishDepth.DEEP -> when (tpl) {
                PolishTemplate.BANANA ->
                    "本次为「深度重构」：把用户输入扩写成一份给 Google Nano Banana 的自然语言创作简报，" +
                        "像给人类插画师下达创作任务。3～6 个完整句子，依次交代：镜头与构图（景别、视角）、主体与动作、" +
                        "环境与场景细节、光线与氛围、材质与色调。描述要具体可感（材质用 matte finish / soft velvet 这类质感词），" +
                        "禁止 tag 堆砌、禁止逗号罗列关键词、禁止 8k/masterpiece 这类质量词；与用户原文语言保持一致；只输出简报本身。"
                PolishTemplate.IMAGE2 ->
                    "本次为「深度重构」：把用户输入扩写成一份给 OpenAI GPT Image 2 的结构化提示词，固定顺序分节（每节一到两行短句）：\n" +
                        "Scene 背景/场景 → Subject 主体与动作 → Details 关键细节（材质、纹理、视觉媒介如 photo/水彩/3D 渲染）→ " +
                        "Composition 构图与光线（景别、视角、光源类型）→ Constraints 约束（明确写出不要出现的元素与需保留的部分）。\n" +
                        "写实照片必须直接写 photorealistic；人物写明身体取景、视线与互动；句子具体不空泛；与用户原文语言保持一致；只输出提示词本身。"
                PolishTemplate.GROK ->
                    "本次为「深度重构」：把用户输入扩写成一份给 xAI Grok Imagine（Aurora 引擎）的创作简报。" +
                        "按「主体与动作 → 风格 → 环境场景 → 光线 → 镜头（景别、视角、景深）→ 细节点缀」的顺序，" +
                        "写成 2～5 个完整句子，像给设计师下达排版任务；\n" +
                        "需要渲染进画面的文字必须原样写进提示词——英文用大写（如 a sign reading CYBER BODY MOD），" +
                        "中文/日文等直接写原文（如 a sign reading 拉麺），不要用「一块写着字的招牌」这种模糊描述；\n" +
                        "海报/产品图等干净画面要显式加 no additional text or decorative elements，抑制模型自动添加的装饰性文案；\n" +
                        "禁止 tag 堆砌与 masterpiece 类质量词；与用户原文语言保持一致（需渲染的文字除外）；只输出简报本身。"
                else ->
                    "本次为「深度重构」：把用户输入重组成分组式的标签提示词。\n" +
                        "分组顺序：主体与人物特征 → 服装与配饰 → 随身点缀 → 场景与建筑 → 光影 → 色调与氛围 → 画质词（2-4 个，永远最后一行）。\n" +
                        "排版硬规则：每组独占一行，行内只用「英文逗号+空格」连接裸标签；\n" +
                        "行首绝对不能出现组名或任何标记——禁止「主体：」「【主体】」「1.」「**」这类写法，行内也不要括号说明，不要空行；\n" +
                        "不写完整句子，不使用 markdown，不做任何解释。\n" +
                        "语言与用户原文保持一致；下面的示例仅示意排版，标签内容必须来自用户输入：\n" +
                        "1girl, solo, long silver hair, blue eyes\n" +
                        "white dress, lace trim, silver earrings\n" +
                        "held bouquet, drifting petals\n" +
                        "moonlit courtyard, stone arches\n" +
                        "rim light, soft glow\n" +
                        "cool blue tones, quiet mood\n" +
                        "masterpiece, best quality, intricate details"
            }
        }
        // 模型输出形态规范（轻/中档也生效：形态跟着模板走，深度只管扩写力度）
        val modelNote = when (tpl) {
            PolishTemplate.BANANA ->
                "输出形态（目标模型 Google Nano Banana）：用自然语言完整句子写成叙述式提示，" +
                    "像给人类艺术家做创意简报——交代镜头景别/视角、场景环境、光线氛围、材质质感；" +
                    "不要逗号罗列的关键词堆，不要 8k/masterpiece 这类质量词。"
            PolishTemplate.IMAGE2 ->
                "输出形态（目标模型 OpenAI GPT Image 2）：按「背景场景 → 主体 → 关键细节（材质/媒介）→ 约束」的顺序组织，" +
                    "短句可分节或换行；写实照片直接写 photorealistic；构图写明景别视角、光线写明类型（golden hour、soft diffuse 等）。"
            PolishTemplate.NOVELAI -> "输出形态（NovelAI 标签流）：英文 Danbooru 标签，逗号分隔；不要写成完整句子；不要输出质量词与画师标签（质量预设与画师串由应用单独注入，不要回显）。"
            PolishTemplate.GROK ->
                "输出形态（目标模型 xAI Grok Imagine / Aurora）：自然语言简报式短文，" +
                    "按主体→风格→环境→光线→镜头→细节的顺序组织；" +
                    "要出现在画面里的文字原样嵌入提示词（大写英文或目标语言原文），干净画面加 no additional text or decorative elements。"
            null -> ""
            PolishTemplate.AUTO -> ""
        }
        // 画幅感知：构图/镜头描述必须与用户所选画幅一致，否则润色结果与实际出图比例冲突
        val aspectNote = if (kind != "gen" || aspectLabel.isBlank()) "" else when (aspectOrientation) {
            "portrait" ->
                " 目标画幅为 $aspectLabel 竖幅：所有构图与镜头描述必须适配竖幅——适合 full-length、portrait、上下层次的纵深场景，主体居中或上置；" +
                    "不要描述横向铺陈的全景、超宽视野或左右并排的多主体构图。"
            "landscape" ->
                " 目标画幅为 $aspectLabel 横幅：所有构图与镜头描述必须适配横幅——适合 wide shot、全景、左右延伸的场景，可用横向空间安排环境细节；" +
                    "不要描述竖长满幅的人物构图或纵向堆叠的画面结构。"
            else ->
                " 目标画幅为 $aspectLabel 方幅：构图宜居中紧凑、主体突出、四周留有呼吸空间；避免明显偏向横向铺陈或纵向堆叠的构图描述。"
        }
        // NAI 专属润色工程只跟「显式开启的 NaiOptions」走（naiProfile != null）。
// 不能只凭 genModel 名字带 nai、或用户选过 NovelAI 模板就启用——那会把画师串和
// NAI 语法规则注入首页润色，普通模式输出被污染（LLM 还会回显画师串）。
val naiMode = kind == "gen" && naiProfile != null
        // 设置页「提示词模板」填了润色文本就整段用它（画幅提示仍然追加，因为它跟这次请求的画幅绑死）
        val customPolish = settings.polishPromptTemplate.trim()
        val system = if (customPolish.isNotBlank()) {
            customPolish + aspectNote
        } else if (naiMode) {
            naiPolishInstructions(
                naiProfile,
                depth, artistContext, styleContext
            ) + aspectNote
        } else if (kind == "edit") {
            "你是专业的图片编辑指令撰写助手。你会看到用户当前正在修改的图片，请仔细观察原图的：" +
                "画风与绘画技法（anime/写实/3D渲染/水彩等）、光影质感（bloom/rim light/soft lighting等）、" +
                "主体外观、构图、色调与氛围。把用户简短的想法扩写成一条清晰的修图指令。" +
                "硬规则：\n" +
                "1) 优先明确要保留的核心特征：画风、光影、主体身份、整体构图——这些在指令开头写清楚，确保修改后画面不会整体重绘；\n" +
                "2) 具体说明要修改什么：常见操作如换服装/表情/姿势/视角、调整背景/道具、改变光线/色调——用具体的视觉描述替换原有元素；\n" +
                "3) 如果用户只说'改XX'没说保留什么，你必须根据原图补充'保持原有画风/光影/构图'这类保留项，避免整张图被重绘；\n" +
                "4) 一句自然语言指令即可，不要分点罗列、不要标签堆砌；只输出最终指令，不要解释、不要引号。"
        } else {
            "你是专业的 AI 绘画提示词润色师。把用户输入处理成一份高质量的绘画提示词，" +
                "自动补充光线、构图、细节、色调与风格，保持用户意图不变。$depthNote " +
                (if (modelNote.isNotBlank()) "$modelNote " else "") +
                (if (aspectNote.isNotBlank()) "$aspectNote " else "") +
                "直接输出最终提示词，不要解释、不要引号、不要 markdown。"
        }
        val textMsg = ChatMessage("user", raw.trim())
        val messages = if (kind == "edit" && !imageBase64.isNullOrBlank()) {
            val visionText = if (raw.isBlank()) "请描述这张图，并根据它给出一条合适的修图建议。" else raw.trim()
            listOf(
                ChatMessage("system", system),
                ChatMessage(
                    "user",
                    listOf(
                        mapOf("type" to "text", "text" to visionText),
                        mapOf(
                            "type" to "image_url",
                            "image_url" to mapOf("url" to "data:image/jpeg;base64,$imageBase64")
                        )
                    )
                )
            )
        } else {
            listOf(ChatMessage("system", system), textMsg)
        }
        try {
            val resp = api.chat(ChatCompletionRequest(model = model, messages = messages))
            val err = resp.error?.message
            if (!err.isNullOrBlank()) return@withContext Result.failure(Exception(err))
            val rawText = resp.choices?.firstOrNull()?.message?.content
                ?: resp.choices?.firstOrNull()?.text
            val text = when (rawText) {
                is String -> rawText
                else -> rawText?.toString().orEmpty()
            }
            if (text.isBlank()) {
                Result.failure(Exception("LLM 返回为空，请确认该模型支持文本对话"))
            } else {
                val cleaned = if (naiMode) cleanNaiOutput(sanitizeLlmText(text)) else sanitizePromptText(text)
                val issues = if (naiMode) naiSyntaxErrors(cleaned) else emptyList()
                if (issues.isNotEmpty()) Result.failure(IllegalArgumentException(issues.joinToString("\n")))
                else Result.success(cleaned)
            }
        } catch (e: Exception) {
            // vision 模型在某些 LLM 端点上可能不被识别；降级为纯文本润色
            if (kind == "edit" && !imageBase64.isNullOrBlank()) {
                return@withContext runCatching { polishPrompt(settings, raw, "edit", null).getOrThrow() }
                    .fold(
                        onSuccess = { Result.success(it) },
                        onFailure = { Result.failure(Exception(describeError(e))) }
                    )
            }
            Result.failure(Exception(describeError(e)))
        }
    }

    /** 中英互译：检测原文语种后翻译成另一语言。
     *  由调用方持有原文与译文做「再点击翻转」，这里只负责单向翻译。 */
    suspend fun translateText(
        settings: AppSettings,
        text: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val base = settings.llmBaseUrl.trim().trimEnd('/')
        val key = settings.llmApiKey.trim()
        val model = settings.llmModel.trim()
        if (base.isEmpty() || key.isEmpty() || model.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("请先在「设置」里配置提示词润色 LLM")
            )
        }
        val api = buildApi(base, key)
            ?: return@withContext Result.failure(IllegalStateException("LLM 配置无效"))
        val hasCjk = text.any { it in '\u4e00'..'\u9fff' }
        val target = if (hasCjk) "英文" else "中文"
        val system = "你是专业的翻译引擎。把用户输入翻译成$target，" +
            "完整保留语义与画面描述细节，不要解释、不要引号、不要额外说明，直接输出译文。"
        try {
            val resp = api.chat(ChatCompletionRequest(
                    model = model,
                    messages = listOf(
                        ChatMessage("system", system),
                        ChatMessage("user", text.trim())
                    )
                ))
            val err = resp.error?.message
            if (!err.isNullOrBlank()) return@withContext Result.failure(Exception(err))
            val rawText = resp.choices?.firstOrNull()?.message?.content
                ?: resp.choices?.firstOrNull()?.text
            val out = when (rawText) {
                is String -> rawText
                else -> rawText?.toString().orEmpty()
            }
            if (out.isBlank()) {
                Result.failure(Exception("LLM 返回为空，请确认该模型支持文本对话"))
            } else {
                Result.success(sanitizePromptText(out))
            }
        } catch (e: Exception) {
            Result.failure(Exception(describeError(e)))
        }
    }

    /** 用设置页的润色模型分析工作流摘要。返回原文，失败时不改走别的分析。 */
    suspend fun analyzeComfyWorkflow(settings: AppSettings, digest: String): Result<String> = withContext(Dispatchers.IO) {
        llmApiConfigurationError(settings)?.let { return@withContext Result.failure(IllegalArgumentException(it)) }
        val api = buildApi(settings.llmBaseUrl, settings.llmApiKey)
            ?: return@withContext Result.failure(IllegalStateException("LLM 配置无效"))
        val system = """
            你是 ComfyUI API 工作流分析器。用户消息是节点摘要，不是待润色的提示词。
            只输出一个 JSON 对象，不要 Markdown，不要解释。
            {"nodes":["12","5"]}
            nodes 是要在前端整块展开的节点编号。选中一个节点，就等于展示它上面所有已经写成字面值的输入，不要只挑其中一个字段。
            - 选择用户真正要改的节点：画面提示词所在的 CLIPTextEncode、负面词所在的 CLIPTextEncode、决定最终画面尺寸的节点、主采样器、需要换参考图的 LoadImage。
            - 尺寸选离采样器最近、width 或 height 已经是数字的那个节点。后面的节点如果改写了尺寸，不要选更早的空 latent。
            - 不要选择只负责连线的节点，也不要选择模型、CLIP、VAE 加载节点。
            - 编号必须出现在摘要里。没有把握就不要选。最多 8 个。
            - 如果都不确定，输出 {"nodes":[]}。
        """.trimIndent()
        try {
            val resp = api.chat(ChatCompletionRequest(
                model = settings.llmModel.trim(),
                messages = listOf(
                    ChatMessage("system", system),
                    ChatMessage("user", "下面是 ComfyUI API 工作流摘要。每一行是一个节点，等号是字面值，箭头是连线。\n$digest")
                ),
                temperature = 0.1,
                maxTokens = 2000
            ))
            val err = resp.error?.message
            if (!err.isNullOrBlank()) return@withContext Result.failure(Exception(err))
            val rawText = resp.choices?.firstOrNull()?.message?.content ?: resp.choices?.firstOrNull()?.text
            val out = when (rawText) {
                is String -> rawText
                else -> rawText?.toString().orEmpty()
            }
            if (out.isBlank()) Result.failure(Exception("模型没有返回分析结果"))
            else Result.success(out.trim())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(describeError(e)))
        }
    }

    /** 把结果（b64 或 url）解析成 Bitmap，供预览 / 保存 */
    suspend fun resolveBitmap(data: ImageData): Bitmap? = withContext(Dispatchers.IO) {
        data.b64Json?.let { ImageUtils.decodeBase64(it) }
            ?: data.url?.let { url ->
                try {
                    val req = okhttp3.Request.Builder().url(url).build()
                    httpClientBuilder().build().newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            resp.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                        } else null
                    }
                } catch (e: Exception) {
                    null
                }
            }
    }

    private fun handle(resp: ImageResponse, expectedCount: Int): ApiResult {
        val err = resp.error
        if (err != null && !err.message.isNullOrBlank()) return ApiResult.Error(err.message)
        // 部分中转对 n=1 也会返回多条 data（gemini 系常见），按请求数截断，避免「要 1 张出 2 张」
        val images = resp.data
            ?.filter { !it.url.isNullOrBlank() || !it.b64Json.isNullOrBlank() }
            ?.take(expectedCount.coerceAtLeast(1))
        return if (images.isNullOrEmpty()) {
            ApiResult.Error("接口返回了空结果，请检查模型名 / 请求参数")
        } else {
            ApiResult.Success(images)
        }
    }

    private fun describeError(e: Exception): String {
        val raw = when (e) {
            is HttpException -> {
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                Log.e("ImagineApi", "HTTP ${e.code()} Error body: $body", e)
                val parsedMsg = body?.let {
                    runCatching { gson.fromJson(it, ImageResponse::class.java).error?.message }.getOrNull()
                }
                parsedMsg ?: (body?.takeIf { it.isNotBlank() && it.length < 200 } ?: "HTTP ${e.code()}")
            }
            is IOException -> {
                Log.e("ImagineApi", "Network IOException: ${e.message}", e)
                // 把底层原因带出来（DNS / 连接被拒 / 超时），否则用户只能看到笼统的一句
                val why = e.message?.take(120)?.takeIf { it.isNotBlank() }
                if (why != null) "网络连接失败：$why（检查网络、系统代理或 LLM 地址）"
                else "网络连接失败，请检查网络或系统代理"
            }
            else -> {
                Log.e("ImagineApi", "Unknown exception: ${e.message}", e)
                e.message ?: "未知错误"
            }
        }
        return friendlyError(raw)
    }

    /** 把中转端常见报错翻译成用户能看懂的提示 */
    private fun friendlyError(raw: String): String = when {
        raw.contains("502") || raw.contains("Bad Gateway", true) ->
            "服务商网关异常（502 Bad Gateway）：该服务商服务器或其上游接口未响应，请检查模型名称是否正确、或在「设置」中切换服务商"
        raw.contains("Remote end closed", true) || raw.contains("unexpected end", true) ||
            raw.contains("Connection reset", true) || raw.contains("EOF", true) ->
            "上游连接被中断（网关过载或限流）：请求已提交但未收到完整响应，请稍等几秒重试"
        raw.contains("504") || raw.contains("Gateway Timeout", true) ->
            "服务商网关超时（504 Gateway Timeout）：上游处理超时，请稍后重试或更换模型"
        raw.contains("500") || raw.contains("Internal Server Error", true) ->
            "服务商内部错误（HTTP 500）：服务商接口处理失败，请检查模型名称或参数"
        raw.contains("排队") -> "服务商对该模型限流：同一时间只能跑 1 个任务，正在自动排队重试…"
        raw.contains("not supported", true) && raw.contains("account", true) ->
            "该服务商的当前分组没有支持这个模型的账号：请到「设置」核对模型名（不同平台叫法不同，如 gpt-image-1 / chatgpt-image-latest），或换服务商、在设置里拉取模型列表确认可用名称"
        raw.contains("401") || raw.contains("Unauthorized", true) -> "API Key 无效或已过期，请到「设置」检查"
        raw.contains("403") || raw.contains("Forbidden", true) -> "没有该模型的访问权限，请换模型或联系服务商"
        raw.contains("429") || raw.contains("rate limit", true) -> "请求太频繁被限流，稍等几秒再试"
        raw.contains("余额") || raw.contains("balance", true) || raw.contains("quota", true) ->
            "账户额度不足，请充值或更换 Key"
        raw.contains("timeout", true) || raw.contains("timed out", true) -> "请求超时：模型响应慢，建议减小张数或画质档位"
        raw.contains("Failed to connect", true) || raw.contains("ECONNREFUSED", true) ->
            "无法连接服务器，请检查 Base URL 或代理设置"
        else -> raw
    }

    /** 读取 Android 系统全局 HTTP 代理（Wi-Fi 手动代理 / 代理类 App 的系统代理模式） */
    private fun systemProxy(): Proxy? {
        return try {
            val resolver = context.contentResolver
            val direct = Settings.Global.getString(resolver, Settings.Global.HTTP_PROXY)
            val hostPort = if (!direct.isNullOrBlank()) {
                direct.substringAfterLast("://").trim()
            } else {
                val host = Settings.Global.getString(resolver, "global_http_proxy_host")
                val port = Settings.Global.getString(resolver, "global_http_proxy_port")
                if (host.isNullOrBlank()) "" else "$host:${port?.takeIf { it.isNotBlank() } ?: "80"}"
            }
            if (hostPort.isBlank()) {
                null
            } else {
                val host = hostPort.substringBefore(":").trim()
                val port = hostPort.substringAfter(":", "").trim().toIntOrNull() ?: 80
                if (host.isEmpty()) null else Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 统一构建带系统代理的 OkHttp client builder */
    private fun httpClientBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder().apply {
            systemProxy()?.let { proxy(it) }
            // BASIC 级别只记录请求行/状态码/耗时，不打请求体（避免 base64 刷屏）。
            // logcat 过滤 ImagineHttp 即可确认修图实际打到 /images/edits 还是 /images/generations。
            addInterceptor(
                okhttp3.logging.HttpLoggingInterceptor { message ->
                    Log.i("ImagineHttp", message)
                }.apply { level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC }
            )
        }

    private fun buildApi(baseUrl: String, apiKey: String): ImageApi? {
        if (apiConfigurationError(baseUrl, apiKey, null, "设置") != null) return null
        val base = baseUrl.trim().trimEnd('/')
        val key = apiKey.trim()
        val client = httpClientBuilder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS) // 生图耗时长
            .writeTimeout(120, TimeUnit.SECONDS) // 大 body（base64 图）上传不至于无限挂
            .callTimeout(240, TimeUnit.SECONDS)  // 总闸：避免上游慢速滴流时永远转圈
            .build()
        return Retrofit.Builder()
            .baseUrl("$base/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ImageApi::class.java)
    }
}

