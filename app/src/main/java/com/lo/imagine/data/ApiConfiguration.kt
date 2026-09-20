package com.lo.imagine.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Local preflight: reject incomplete connections before starting a task or touching its results. */
fun imageApiConfigurationError(settings: AppSettings): String? = apiConfigurationError(
    settings.baseUrl, settings.apiKey, settings.model, "设置 → 绘图 API"
)

fun llmApiConfigurationError(settings: AppSettings): String? = apiConfigurationError(
    settings.llmBaseUrl, settings.llmApiKey, settings.llmModel, "设置 → 提示词润色 LLM"
)

/** Configuration validation only; this does not make a video task or a connectivity request. */
fun videoApiConfigurationError(settings: AppSettings): String? = apiConfigurationError(
    settings.videoBaseUrl, settings.videoApiKey, settings.videoModel, "设置 → 视频 API"
)

fun videoApiConfigurationError(settings: VideoApiSettings): String? = apiConfigurationError(
    settings.baseUrl, settings.apiKey, settings.model, "设置 → 视频 API"
)

internal fun apiEndpointError(raw: String): String? {
    val value = raw.trim()
    if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(value) ||
        value.any { it.isWhitespace() }
    ) return "API 地址格式不正确，请填写以 https:// 或 http:// 开头的完整地址。"
    val url = value.toHttpUrlOrNull() ?: return "API 地址格式不正确，请填写有效的服务器地址。"
    if (url.query != null || url.fragment != null) {
        return "请填写 API 基础地址，去掉地址中的查询参数和 # 后的内容。"
    }
    return null
}

/** Safe inline validation: never include the credential in an error message. */
internal fun apiKeyFormatError(raw: String): String? =
    if (raw.trim().any { it.code !in 33..126 })
        "API Key 格式不正确，请重新粘贴 Key，去掉中间的空格或换行。"
    else null

internal fun apiConfigurationError(
    baseUrl: String,
    apiKey: String,
    model: String?,
    location: String
): String? {
    val missing = buildList {
        if (baseUrl.isBlank()) add("API 地址")
        if (apiKey.isBlank()) add("API Key")
        if (model != null && model.isBlank()) add("模型")
    }
    if (missing.isNotEmpty()) {
        return "尚未配置${missing.joinToString("、")}。请到「$location」填写并保存后再试。"
    }
    apiEndpointError(baseUrl)?.let { return "$it 请到「$location」修改。" }
    // OkHttp rejects control characters and non-ASCII header values. Do not echo the key.
    if (apiKeyFormatError(apiKey) != null) {
        return "API Key 格式不正确，请到「$location」重新粘贴 Key，去掉中间的空格或换行。"
    }
    return null
}
