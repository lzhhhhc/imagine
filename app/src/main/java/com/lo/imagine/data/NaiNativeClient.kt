package com.lo.imagine.data

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** 内置默认任务 system；用户没改过它时，NAI 润色用专属工程覆盖它。 */
const val NAI_DEFAULT_LLM_INSTRUCTION = "将用户的画面描述转换为 NovelAI 英文绘图提示词。保留主体、动作、构图与画师权重，只输出提示词，不输出解释。"

data class NaiWorkspaceConfig(
    val endpoint: String = "https://image.novelai.net/ai/generate-image",
    val token: String = "",
    /** 连接默认跟随设置页的绘图 API（地址 + Key）；关闭后才用上面的独立 Endpoint/Token */
    val useBackendApi: Boolean = true,
    val model: String = "nai-diffusion-4-5-full",
    val prompt: String = "", val negative: String = "", val artists: String = "",
    val fixedPrefix: String = "", val fixedSuffix: String = "",
    /** 提示词替换规则：每行「原词=新词」；支持参考项目的「触发词=位置|替换词」写法（取竖线后的替换词） */
    val promptReplace: String = "",
    val profileName: String = "默认", val profiles: List<NaiConfigProfile> = emptyList(),
    val promptPresetName: String = "默认", val promptPresets: List<NaiPromptPreset> = emptyList(),
    val characterCards: List<NaiCharacterPrompt> = emptyList(),
    val llmTemplateName: String = "默认", val llmTemplates: List<NaiLlmTemplate> = emptyList(),
    val width: String = "832", val height: String = "1216",
    val steps: String = "28", val scale: String = "5", val seed: String = "-1",
    val sampler: String = "k_euler_ancestral", val schedule: String = "native",
    val rescale: String = "0",
    /** 正面质量预设（AQT）：off 不追加；standard 按模型版本取官方质量词 */
    val quality: String = "standard",
    /** 负面质量预设（UCP）：off / light / heavy */
    val uc: String = "light",
    val furryDataset: Boolean = false,
    val useCoords: Boolean = false,
    val smea: Boolean = false, val smeaDyn: Boolean = false,
    val variety: Boolean = true, val decrisp: Boolean = false,
    val straightAlpha: Boolean = false,
    val llmInstruction: String = NAI_DEFAULT_LLM_INSTRUCTION,
    val temperature: String = "0.7", val maxTokens: String = "1000"
)

/** 角色卡：参考项目的正背面 + SFW/NSFW 矩阵结构。 */
data class NaiCharacterPrompt(
    val name: String = "角色",
    val nameEn: String = "",
    val traits: String = "",
    val face: String = "",
    val faceBack: String = "",
    val upperSfw: String = "",
    val upperSfwBack: String = "",
    val lowerSfw: String = "",
    val lowerSfwBack: String = "",
    val upperNsfw: String = "",
    val upperNsfwBack: String = "",
    val lowerNsfw: String = "",
    val lowerNsfwBack: String = "",
    val outfit: String = "",
    /** 额外补充（画风/姿态等自由文本），拼在末尾 */
    val prompt: String = "",
    val negative: String = "",
    val x: Double = .5,
    val y: Double = .5,
    val enabled: Boolean = true,
    /** 当前选中的视角：front / back */
    val viewAngle: String = "front",
    /** 当前身体状态模式：sfw / nsfw / custom */
    val bodyMode: String = "sfw",
    /** LLM 场景融合产物：非空时生成请求优先使用它（按场景取景推理，替代机械拼接的 caption） */
    val fusedCaption: String = ""
) {
    /** 拼装后的角色正向提示词（根据视角和身体模式智能合成，也兼容旧版直拼） */
    val caption: String get() {
        val isBack = viewAngle.equals("back", ignoreCase = true)
        val curFace = if (isBack && faceBack.isNotBlank()) faceBack else face
        val curUpper = when (bodyMode.lowercase()) {
            "nsfw" -> if (isBack && upperNsfwBack.isNotBlank()) upperNsfwBack else upperNsfw
            "sfw" -> if (isBack && upperSfwBack.isNotBlank()) upperSfwBack else upperSfw
            else -> ""
        }
        val curLower = when (bodyMode.lowercase()) {
            "nsfw" -> if (isBack && lowerNsfwBack.isNotBlank()) lowerNsfwBack else lowerNsfw
            "sfw" -> if (isBack && lowerSfwBack.isNotBlank()) lowerSfwBack else lowerSfw
            else -> ""
        }
        return listOf(traits, curFace, curUpper, curLower, outfit, prompt)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(", ")
    }
}
data class NaiPromptPreset(val name: String, val prefix: String = "", val suffix: String = "", val negative: String = "", val artists: String = "")
data class NaiLlmTemplate(val name: String, val instruction: String, val temperature: String = "0.7", val maxTokens: String = "1000")
data class NaiConfigProfile(
    val name: String,
    val endpoint: String,
    val token: String,
    val model: String,
    val width: String,
    val height: String,
    val steps: String,
    val scale: String,
    val seed: String,
    val sampler: String,
    val schedule: String,
    val rescale: String,
    val quality: String = "standard",
    val uc: String = "light",
    val useCoords: Boolean = false,
    val smea: Boolean = false,
    val smeaDyn: Boolean = false,
    val variety: Boolean = true,
    val decrisp: Boolean = false,
    val straightAlpha: Boolean = false
)
fun applyNaiConfigProfile(c: NaiWorkspaceConfig, p: NaiConfigProfile) = c.copy(profileName=p.name, endpoint=p.endpoint, token=p.token, model=p.model, width=p.width, height=p.height, steps=p.steps, scale=p.scale, seed=p.seed, sampler=p.sampler, schedule=p.schedule, rescale=p.rescale, quality=p.quality, uc=p.uc, useCoords=p.useCoords, smea=p.smea, smeaDyn=p.smeaDyn, variety=p.variety, decrisp=p.decrisp, straightAlpha=p.straightAlpha)
fun snapshotNaiConfig(c: NaiWorkspaceConfig, name: String) = NaiConfigProfile(name,c.endpoint,c.token,c.model,c.width,c.height,c.steps,c.scale,c.seed,c.sampler,c.schedule,c.rescale,c.quality,c.uc,c.useCoords,c.smea,c.smeaDyn,c.variety,c.decrisp,c.straightAlpha)
fun applyNaiPromptPreset(c: NaiWorkspaceConfig, p: NaiPromptPreset) = c.copy(promptPresetName=p.name, fixedPrefix=p.prefix, fixedSuffix=p.suffix, negative=p.negative, artists=p.artists)
fun applyNaiLlmTemplate(c: NaiWorkspaceConfig, p: NaiLlmTemplate) = c.copy(llmTemplateName=p.name,llmInstruction=p.instruction,temperature=p.temperature,maxTokens=p.maxTokens)

val NAI_NATIVE_MODELS = listOf("nai-diffusion-4-5-full", "nai-diffusion-4-5-curated", "nai-diffusion-5-full", "nai-diffusion-5-curated")

/** NAI 模型显示名：页头角标与选择器用，避免把 nai-diffusion-4-5-full 这种内部 ID 直接糊到界面上。 */
fun naiModelLabel(model: String): String = when {
    model.isBlank() -> "未选模型"
    model.contains("diffusion-5") && model.contains("curated") -> "NAI 5 Curated"
    model.contains("diffusion-5") -> "NAI 5 Full"
    model.contains("4-5") && model.contains("curated") -> "NAI 4.5 Curated"
    model.contains("4-5") -> "NAI 4.5 Full"
    else -> model
}
val NAI_SAMPLERS = listOf("k_euler", "k_euler_ancestral", "k_dpmpp_2m", "k_dpmpp_sde", "k_dpmpp_2m_sde", "k_dpmpp_2s_ancestral", "ddim_v3")
val NAI_SCHEDULES = listOf("native", "karras", "exponential", "polyexponential")
val NAI_SIZE_PRESETS = listOf("512x512", "640x640", "512x768", "768x512", "1024x1024", "1216x832", "832x1216")

/** 解析提示词替换规则：每行「原词=新词」；兼容「触发词=位置|替换词」，取竖线后的替换词。 */
fun naiReplaceRules(rules: String): List<Pair<String, String>> = rules.lines().mapNotNull { line ->
    val t = line.trim()
    if (t.isBlank() || t.startsWith("#") || '=' !in t) return@mapNotNull null
    val from = t.substringBefore('=').trim()
    val rawTo = t.substringAfter('=').trim()
    val to = if ('|' in rawTo) rawTo.substringAfterLast('|').trim() else rawTo
    if (from.isBlank() || to.isBlank()) null else from to to
}

fun applyNaiReplace(text: String, rules: List<Pair<String, String>>): String =
    rules.fold(text) { acc, (from, to) -> acc.replace(from, to) }

/**
 * 多角色字段用的角色 caption：NAI 要求人数词只属于 base_caption（全局），
 * char_caption / characterPrompts.prompt 里必须去掉 1girl/1boy——
 * 否则与全局人数冲突、角色特征被稀释（st-chatu8 生产环境同样处理）。
 */
internal fun naiCharacterFieldCaption(card: NaiCharacterPrompt): String {
    // 有 LLM 场景融合版时优先使用（按场景取景推理的产物），否则退回机械拼接版
    val base = card.fusedCaption.ifBlank { card.caption }
    return base.replace("1girl", "girl", ignoreCase = true)
        .replace("1boy", "boy", ignoreCase = true)
}

fun naiNativePayload(c: NaiWorkspaceConfig, actualSeed: Long): Map<String, Any?> {
    require(c.model in NAI_NATIVE_MODELS) { "请选择支持的 NAI 模型" }
    val w = c.width.toIntOrNull(); val h = c.height.toIntOrNull()
    require(w != null && h != null && w in 64..2048 && h in 64..2048 && w % 64 == 0 && h % 64 == 0) { "宽高应为 64–2048 内的 64 倍数" }
    val steps = c.steps.toIntOrNull(); val scale = c.scale.toDoubleOrNull(); val rescale = c.rescale.toDoubleOrNull()
    require(steps != null && steps in 1..50) { "步数应为 1–50" }
    require(scale != null && scale.isFinite() && scale in 0.0..10.0) { "CFG 应为 0–10" }
    require(rescale != null && rescale.isFinite() && rescale in 0.0..1.0) { "CFG Rescale 应为 0–1" }
    require(actualSeed in 0..4294967295L) { "种子应为 -1（随机）或 0–4294967295" }
    require(c.sampler in NAI_SAMPLERS && c.schedule in NAI_SCHEDULES) { "采样器或调度器无效" }
    val rules = naiReplaceRules(c.promptReplace)
    val mainPrompt = applyNaiReplace(c.prompt.trim(), rules)
    val userNegative = applyNaiReplace(c.negative, rules)
    val artists = applyNaiReplace(c.artists, rules)
    val prefix = applyNaiReplace(c.fixedPrefix, rules)
    val suffix = applyNaiReplace(c.fixedSuffix, rules)
    require(mainPrompt.isNotBlank()) { "请填写画面提示词" }
    require('|' !in mainPrompt) { "多角色请使用角色卡，不要写 |" }
    val options = NaiOptions(modeEnabled = true, quality = c.quality, negative = c.uc, styleEnabled = false)
    val profile = requireNotNull(resolveNaiProfile(c.model, options))
    // 真实注入顺序：数据集前缀 → 画师串 → 固定前置 → 主体 → 固定后置 →（质量词由引擎追加到末尾）
    // 画师串走 assembleNaiPrompt 的 artists 形参，不再拼进 raw，否则会被引擎当正文重排到后面
    val body = (if (c.furryDataset) "fur dataset, " else "") +
        listOf(prefix, mainPrompt, suffix).map(String::trim).filter(String::isNotEmpty).joinToString(", ")
    val assembled = assembleNaiPrompt(body, artists, userNegative, STYLE_PRESETS.first(), profile, options)
    val chars = c.characterCards.filter { it.enabled && it.caption.isNotBlank() }
    require(chars.size <= 6) { "角色最多 6 个" }
    // 多角色字段 caption：去掉 1girl/1boy（人数词只属于 base_caption，见 naiCharacterFieldCaption）
    val errors = assembled.errors + chars.flatMap { naiSyntaxErrors(naiCharacterFieldCaption(it)) + naiSyntaxErrors(it.negative) }
    require(errors.isEmpty()) { errors.joinToString("\n") }
    // 坐标：use_coords 关闭时（默认）传空对象 [{}]（与 st-chatu8 对齐）——固定 0.5/0.5 会把所有角色钉在画面中心互相污染。
    fun centersFor(card: NaiCharacterPrompt): Any =
        if (c.useCoords) listOf(mapOf("x" to card.x, "y" to card.y)) else listOf(emptyMap<String, Double>())
    fun centerFor(card: NaiCharacterPrompt): Map<String, Double> =
        if (c.useCoords) mapOf("x" to card.x, "y" to card.y) else emptyMap()
    fun caption(text: String, values: List<NaiCharacterPrompt>, negative: Boolean = false) = mapOf("caption" to mapOf("base_caption" to text, "char_captions" to values.map { mapOf("char_caption" to if (negative) it.negative else naiCharacterFieldCaption(it), "centers" to centersFor(it)) }), "use_coords" to c.useCoords, "use_order" to true)
    val parameters = mutableMapOf<String, Any?>(
        "params_version" to if (profile.isV5) 4 else 3,
        "width" to w, "height" to h, "steps" to steps, "scale" to scale,
        "seed" to actualSeed, "sampler" to c.sampler, "noise_schedule" to c.schedule,
        "n_samples" to 1, "cfg_rescale" to rescale,
        // 质量词由本应用按版本注入，关闭官方 qualityToggle 防止重复叠加
        "qualityToggle" to false, "ucPreset" to 3,
        "negative_prompt" to assembled.negative.orEmpty(),
        "sm" to c.smea, "sm_dyn" to (c.smea && c.smeaDyn),
        "dynamic_thresholding" to c.decrisp, "autoSmea" to false,
        "legacy" to false, "legacy_uc" to false, "use_coords" to c.useCoords,
        "v4_prompt" to caption(assembled.positive, chars),
        "v4_negative_prompt" to caption(assembled.negative.orEmpty(), chars, true),
        "characterPrompts" to chars.map { mapOf("enabled" to true, "prompt" to naiCharacterFieldCaption(it), "uc" to it.negative, "center" to centerFor(it)) }
    )
    if (c.variety) parameters["skip_cfg_above_sigma"] = if (profile.isV5) 58.0 else 19.0
    if (profile.isV5) {
        parameters["straight_alpha"] = c.straightAlpha
        parameters["tag_hint_uc_preset"] = when (c.uc) { "heavy" -> 1; "light" -> 3; else -> 0 }
        parameters["tag_hint_qt"] = if (c.quality == "off") 0 else 1
    }
    return mapOf("input" to assembled.positive, "model" to c.model, "action" to "generate", "parameters" to parameters)
}

/**
 * 展示/存档用完整提示词：主体 + 启用角色 caption（NAI 多角色 `|` 语法）。
 * 角色卡走独立字段（v4_prompt.char_captions / characterPrompts），不并入 input，
 * 预览页与作品库若只存 input 会看不到角色部分——这里补齐，与真实请求内容对齐。
 */
fun naiDisplayPrompt(c: NaiWorkspaceConfig, input: String): String {
    val chars = c.characterCards.filter { it.enabled && it.caption.isNotBlank() }
    if (chars.isEmpty()) return input
    // 与请求组装同源：去掉人数词，保证「作品里看到的」=「实际发出的」
    return input + chars.joinToString("") { " | " + naiCharacterFieldCaption(it) }
}

/** NAI 原生生图路径。跟随设置页绘图 API 时，站点根地址要自动补全，否则请求会打到首页。 */
const val NAI_GENERATE_PATH = "/ai/generate-image"

/** 只填到站点根（或空路径）时补上 NAI 原生路径；已带路径的原样保留。 */
fun completeNaiEndpoint(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return trimmed
    val path = runCatching { java.net.URI(trimmed).path }.getOrNull().orEmpty()
    return if (path.isBlank() || path == "/") trimmed.trimEnd('/') + NAI_GENERATE_PATH else trimmed
}

/** 连接解析：默认跟随设置页的绘图 API，关闭跟随才用 NAI 页自己的 Endpoint/Token。 */
fun naiEffectiveEndpoint(c: NaiWorkspaceConfig, s: AppSettings): String =
    completeNaiEndpoint(if (c.useBackendApi) s.baseUrl else c.endpoint)

fun naiEffectiveToken(c: NaiWorkspaceConfig, s: AppSettings): String =
    if (c.useBackendApi) s.apiKey.trim() else c.token.trim()

fun naiApiConfigurationError(c: NaiWorkspaceConfig, s: AppSettings): String? =
    apiConfigurationError(
        if (c.useBackendApi) s.baseUrl else c.endpoint,
        if (c.useBackendApi) s.apiKey else c.token,
        c.model,
        if (c.useBackendApi) "设置 → 绘图 API" else "NAI → 连接设置"
    )

/** 上游/中转不可用的可读提示：这几类状态码不是本地参数错误，重试或换通道才有用。 */
fun naiHttpHint(code: Int): String = when (code) {
    401, 403 -> "（鉴权失败：请确认这把 Key 有 NovelAI 通道权限）"
    429 -> "（触发限流：稍后再试或降低并发）"
    502, 503, 504 -> "（中转或上游 NovelAI 暂时不可用，不是本地参数问题；稍后重试或在设置页换通道）"
    else -> ""
}

/**
 * 从 Chat Completions 响应里稳健地取出正文。
 * 兼容这些真实形态：
 *  - message.content 是字符串（标准）
 *  - message.content 是分块数组（[{type:text,text:...}]）
 *  - 老式 choices[0].text
 *  - HTTP200 但 body 里是 error 对象
 * 只回思考内容（reasoning_content）时不算正文，由调用方给出更准确的提示。
 */
internal fun chatContentOf(raw: String): String {
    val direct = runCatching { com.google.gson.JsonParser.parseString(raw.trim()) }.getOrNull()
    if (direct != null && direct.isJsonObject) {
        val text = contentOfObject(direct.asJsonObject)
        if (text.isNotBlank()) return text
    }
    // 网关无视 stream:false、直接回 SSE 时，正文在 data: 分片里
    val chunks = raw.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("data:") }
        .map { it.removePrefix("data:").trim() }
        .filter { it.isNotEmpty() && it != "[DONE]" }
        .toList()
    if (chunks.isEmpty()) return ""
    return chunks.mapNotNull { line ->
        runCatching { com.google.gson.JsonParser.parseString(line) }.getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.let { contentOfObject(it.asJsonObject).takeIf { t -> t.isNotBlank() } }
    }.joinToString("").trim()
}

/** 解析单个 Chat Completions 对象；body 里是 error 对象时直接抛出，别吞成“空内容”。 */
private fun contentOfObject(obj: com.google.gson.JsonObject): String {
    obj.getAsJsonObject("error")?.let { err ->
        val msg = err.get("message")?.let { if (it.isJsonPrimitive) it.asString else it.toString() } ?: err.toString()
        throw IllegalStateException("LLM 返回错误：$msg")
    }
    val choice = obj.getAsJsonArray("choices")
        ?.firstOrNull { it.isJsonObject }
        ?.asJsonObject ?: return ""
    val message = choice.getAsJsonObject("message")
    return listOfNotNull(
        message?.get("content")?.let { jsonToPlainText(it) },
        choice.get("text")?.let { jsonToPlainText(it) }
    ).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
}

/** 只回思考内容时的提示语；没有思考内容返回 null。 */
internal fun reasoningOnlyHintOf(raw: String): String? {
    val obj = runCatching { com.google.gson.JsonParser.parseString(raw.trim()) }
        .getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    val message = obj.getAsJsonArray("choices")?.firstOrNull { it.isJsonObject }
        ?.asJsonObject?.getAsJsonObject("message") ?: return null
    val reasoning = message.get("reasoning_content")?.let { jsonToPlainText(it) }.orEmpty()
    if (reasoning.isBlank()) return null
    val content = message.get("content")?.let { jsonToPlainText(it) }.orEmpty()
    return if (content.isBlank()) "模型只返回了思考内容（content 为空，思考 ${reasoning.length} 字）：换用非思考模型，或把 Max tokens 调大" else null
}

private fun jsonToPlainText(el: com.google.gson.JsonElement?): String {
    if (el == null || el.isJsonNull) return ""
    if (el.isJsonPrimitive) return el.asString
    if (el.isJsonArray) return el.asJsonArray.map { jsonToPlainText(it) }.filter { it.isNotBlank() }.joinToString("\n")
    if (el.isJsonObject) {
        val o = el.asJsonObject
        o.get("text")?.let { return jsonToPlainText(it) }
        o.get("content")?.let { return jsonToPlainText(it) }
    }
    return ""
}

class NaiNativeClient {
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).callTimeout(210, TimeUnit.SECONDS).build()
    /** 文本类调用用更短的超时：润色/翻译挂太久没有意义，早点报错早点换模型。 */
    private val llmClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).callTimeout(75, TimeUnit.SECONDS).build()

    /** 进行中的请求；协程取消不会中断 execute()，必须由这里真正掐掉连接。 */
    @Volatile private var activeCall: okhttp3.Call? = null
    val busy: Boolean get() = activeCall != null
    fun cancelActive() { activeCall?.cancel(); activeCall = null }

    private inline fun <T> withCall(call: okhttp3.Call, block: () -> T): T {
        activeCall = call
        try { return block() } finally { if (activeCall === call) activeCall = null }
    }

    suspend fun generate(c: NaiWorkspaceConfig, seed: Long, settings: AppSettings): Result<List<ByteArray>> = withContext(Dispatchers.IO) {
        naiApiConfigurationError(c, settings)?.let {
            return@withContext Result.failure(IllegalArgumentException(it))
        }
        try {
            val endpoint = naiEffectiveEndpoint(c, settings)
            val token = naiEffectiveToken(c, settings)
            require(token.isNotBlank()) { "请填写 NAI Token，或在设置页配置绘图 API" }
            require(endpoint.isNotBlank()) { "请填写 NAI Endpoint，或在设置页配置绘图 API" }
            // 站点根地址已由 completeNaiEndpoint 补成 /ai/generate-image；走到这里还是根说明地址本身有问题
            val path = runCatching { java.net.URI(endpoint).path }.getOrNull()
            require(!path.isNullOrBlank() && path != "/") { "Endpoint 无法解析出生图路径：$endpoint" }
            val payload = naiNativePayload(c, seed)
            val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $token")
                .header("Accept", "application/zip").post(Gson().toJson(payload).toRequestBody("application/json".toMediaType())).build()
            val call = client.newCall(request)
            withCall(call) { call.execute() }.use { response ->
                check(response.isSuccessful) {
                    val detail = response.body?.string()?.take(400).orEmpty()
                        .replace(token, "[TOKEN]").replace('\n', ' ').trim()
                    // 中转经常回纯文本（如 Cloudflare 的 "error code: 502"），一并带上，别只给个数字
                    "NAI HTTP ${response.code}${naiHttpHint(response.code)}" +
                        (if (detail.isBlank()) "" else "：$detail")
                }
                val body = requireNotNull(response.body) { "NAI 响应为空" }
                val images = mutableListOf<ByteArray>()
                if (body.contentType()?.type == "image") {
                    val bytes = body.bytes(); check(bytes.size <= 32 * 1024 * 1024) { "图片超出大小限制" }; images += bytes
                } else ZipInputStream(body.byteStream()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.lowercase().endsWith(".png")) {
                            check(images.size < 4) { "响应图片数量超出限制" }
                            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                            var n = zip.read(buffer)
                            while (n != -1) { check(out.size() + n <= 32 * 1024 * 1024) { "图片超出大小限制" }; out.write(buffer, 0, n); n = zip.read(buffer) }
                            images += out.toByteArray()
                        }
                        zip.closeEntry(); entry = zip.nextEntry
                    }
                }
                check(images.isNotEmpty()) { "响应中没有 PNG 图片，请确认接口使用 NAI 原生 ZIP 协议" }
                Result.success(images)
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }
    /** 通用 LLM 文本调用：润色与翻译共用同一条链路（设置页的润色 LLM + 可选前置提示词）。 */
    private suspend fun chatText(
        s: AppSettings,
        system: String,
        user: String,
        temperature: Double,
        maxTokens: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            require(s.llmReady) { "请先在设置页填写 LLM 地址、Key 和模型" }
            val req = ChatCompletionRequest(s.llmModel, listOf(ChatMessage("system", system), ChatMessage("user", user)), temperature, maxTokens)
            // 文本调用必须走 llmClient（15/60/75s），用生图客户端会挂到 210 秒才超时
            val call = llmClient.newCall(Request.Builder().url(s.llmBaseUrl.trim().trimEnd('/') + "/chat/completions")
                .header("Authorization", "Bearer ${s.llmApiKey}").post(Gson().toJson(req).toRequestBody("application/json".toMediaType())).build())
            withCall(call) { call.execute() }.use { response ->
                val raw = response.body?.string().orEmpty()
                check(response.isSuccessful) { "LLM HTTP ${response.code}：${raw.take(200)}" }
                val content = chatContentOf(raw)
                if (content.isBlank()) {
                    // 把真实原因说清楚：思考模型只回 reasoning、或响应体另有结构
                    throw IllegalStateException(reasoningOnlyHintOf(raw) ?: "LLM 返回空内容，原始响应：${raw.take(300)}")
                }
                Result.success(content.trim())
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun polish(c: NaiWorkspaceConfig, s: AppSettings): Result<String> {
        val temperature = c.temperature.toDoubleOrNull(); val tokens = c.maxTokens.toIntOrNull()
        if (temperature == null || !temperature.isFinite() || temperature !in 0.0..2.0 || tokens == null || tokens !in 64..32000) {
            return Result.failure(IllegalArgumentException("LLM temperature 应为 0–2，max_tokens 应为 64–32000"))
        }
        // system 优先级：设置页「提示词模板」里填的润色文本 > 工作台自己的任务 system
        val custom = s.polishPromptTemplate.trim()
        val profile = resolveNaiProfile(c.model, NaiOptions(modeEnabled = true, quality = c.quality, negative = c.uc, styleEnabled = false))
        val system = when {
            custom.isNotBlank() -> custom
            profile != null -> {
                val extra = c.llmInstruction.trim().takeIf { it.isNotBlank() && it != NAI_DEFAULT_LLM_INSTRUCTION }
                // 画师串不再塞进 system：user 消息里已经有「画师参考」，双份会让模型重复推理
                naiTaskInstructions(NaiTask.POLISH, profile, PolishDepth.MEDIUM, "", "") +
                    (extra?.let { "\n用户附加要求（不得违反上面的 NAI 语法与注入规则）：$it" } ?: "")
            }
            else -> c.llmInstruction
        }
        return chatText(
            s, system,
            "目标模型：${c.model}\n画师参考：${c.artists}\n画面：${c.prompt}",
            temperature, tokens
        )
    }

    /** 中英互译：按输入语种自动落到另一侧，只回译文，方便直接替换输入框内容。 */
    suspend fun translate(c: NaiWorkspaceConfig, s: AppSettings, text: String, toEnglish: Boolean? = null): Result<String> {
        val tokens = c.maxTokens.toIntOrNull()?.coerceIn(64, 32000) ?: 1000
        val direction = when (toEnglish) {
            true -> "译成英文"
            false -> "译成中文"
            null -> "输入以中文为主就译成英文，以英文为主就译成中文"
        }
        val profile = resolveNaiProfile(c.model, NaiOptions(modeEnabled = true, quality = c.quality, negative = c.uc, styleEnabled = false))
        val system = naiTaskInstructions(NaiTask.TRANSLATE, profile, direction = direction)
        return chatText(s, system, text, 0.3, tokens)
    }

    /**
     * 角色场景融合：把启用角色的完整设定资料 + 画面提示词交给 LLM 推理，
     * 输出「只属于当前场景」的角色标签段（按取景/视角/状态取舍，而非全量机械拼接）。
     * 这是 st-chatu8 链路里「角色数据 → LLM 推理 → 场景适配提示词」的关键一层。
     */
    suspend fun fuseCharacters(c: NaiWorkspaceConfig, s: AppSettings, scene: String): Result<String> {
        val cards = c.characterCards.filter { it.enabled && it.caption.isNotBlank() }
        if (cards.isEmpty()) return Result.failure(IllegalArgumentException("先启用至少一个角色"))
        if (scene.isBlank()) return Result.failure(IllegalArgumentException("先写画面提示词再融合"))
        val tokens = c.maxTokens.toIntOrNull()?.coerceIn(256, 32000) ?: 1200
        val profile = resolveNaiProfile(c.model, NaiOptions(modeEnabled = true, quality = c.quality, negative = c.uc, styleEnabled = false))
        val system = naiTaskInstructions(NaiTask.FUSE, profile)
        // 角色资料按「给 LLM 读」的格式组织：完整矩阵（正/背面、上下身、多套服装），
        // 由 LLM 按场景推理取舍——这正是把角色卡当上下文而不是当标签的关键区别。
        val characterDossier = cards.joinToString("\n\n") { card ->
            buildString {
                append("角色：").append(card.name.ifBlank { "未命名" })
                if (card.nameEn.isNotBlank()) append("（").append(card.nameEn).append("）")
                append("\n特征：").append(card.traits.ifBlank { "无" })
                append("\n五官（正面）：").append(card.face.ifBlank { "无" })
                if (card.faceBack.isNotBlank()) append("\n五官（背面）：").append(card.faceBack)
                append("\n上半身SFW（正面）：").append(card.upperSfw.ifBlank { "无" })
                if (card.upperSfwBack.isNotBlank()) append("\n上半身SFW（背面）：").append(card.upperSfwBack)
                append("\n下半身SFW（正面）：").append(card.lowerSfw.ifBlank { "无" })
                if (card.lowerSfwBack.isNotBlank()) append("\n下半身SFW（背面）：").append(card.lowerSfwBack)
                if (card.upperNsfw.isNotBlank()) append("\n上半身NSFW（正面）：").append(card.upperNsfw)
                if (card.upperNsfwBack.isNotBlank()) append("\n上半身NSFW（背面）：").append(card.upperNsfwBack)
                if (card.lowerNsfw.isNotBlank()) append("\n下半身NSFW（正面）：").append(card.lowerNsfw)
                if (card.lowerNsfwBack.isNotBlank()) append("\n下半身NSFW（背面）：").append(card.lowerNsfwBack)
                if (card.outfit.isNotBlank()) append("\n服装：").append(card.outfit)
                if (card.prompt.isNotBlank()) append("\n补充：").append(card.prompt)
            }
        }
        val user = "画面场景：$scene\n\n角色设定资料：\n$characterDossier\n\n请按场景取景推理，输出这组角色的融合标签段（每个角色一段，用 | 分隔）。"
        return chatText(s, system, user, 0.4, tokens).map { cleanNaiOutput(it) }
    }
}
