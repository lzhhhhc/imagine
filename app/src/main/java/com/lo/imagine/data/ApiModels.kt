package com.lo.imagine.data

import com.google.gson.annotations.SerializedName

/**
 * OpenAI 兼容的文生图 / 图生图请求。
 * image 字段（data uri 或裸 base64）是硅基流动等平台在
 * /images/generations 上扩展的图生图写法，与 /images/edits 二选一。
 */
data class GenerateRequest(
    val model: String,
    val prompt: String,
    val n: Int = 1,
    val size: String = "1024x1024",
    @SerializedName("negative_prompt") val negativePrompt: String? = null,
    val mask: String? = null, // base64 data uri；少数国产服务支持"图生图"分支下的 mask
    val image: String? = null // data uri；某些中转端在 /images/generations 上扩展的图生图写法
)

data class ImageResponse(
    val data: List<ImageData>? = null,
    val error: ApiError? = null
)

data class ImageData(
    val url: String? = null,
    @SerializedName("b64_json") val b64Json: String? = null,
    @SerializedName("revised_prompt") val revisedPrompt: String? = null,
    /** 本地元数据：上游模型实际输出的像素尺寸（如1536x1024），仅用于展示，不参与请求 */
    val upstreamSize: String? = null
)

data class ApiError(
    val message: String? = null,
    val type: String? = null
)

/** GET /models 响应 */
data class ModelsResponse(
    val data: List<ModelItem>? = null,
    val error: ApiError? = null
)

data class ModelItem(
    val id: String? = null,
    @SerializedName("owned_by") val ownedBy: String? = null
)

/**
 * LLM 提示词润色（OpenAI Chat Completions 兼容）。
 * 与绘图接口分离，使用独立的 Base URL / Key / 模型。
 */
data class ChatMessage(
    val role: String,
    val content: Any
)

/** 多模态消息里的图片块（OpenAI Chat Completions vision 格式） */
data class ImageUrlPart(
    val url: String  // data:image/...;base64,xxx  或  https://...
)

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    @SerializedName("max_tokens") val maxTokens: Int = 800,
    val stream: Boolean = false
)

data class ChatCompletionResponse(
    val choices: List<ChatChoice>? = null,
    val error: ApiError? = null
)

data class ChatChoice(
    val message: ChatMessage? = null,
    val text: String? = null
)
