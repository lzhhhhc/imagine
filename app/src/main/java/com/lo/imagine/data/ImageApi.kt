package com.lo.imagine.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap

interface ImageApi {

    /** 拉取平台可用模型列表 */
    @GET("models")
    suspend fun models(): ModelsResponse

    /** 文生图 / JSON 图生图（generations_image 模式） */
    @POST("images/generations")
    suspend fun generate(@Body body: GenerateRequest): ImageResponse

    /** OpenAI 标准图生图 / 修图（multipart），mask 为可选遮罩 */
    @Multipart
    @POST("images/edits")
    suspend fun edit(
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part image: MultipartBody.Part,
        @Part mask: MultipartBody.Part?
    ): ImageResponse

    /** LLM 提示词润色（Chat Completions） */
    @POST("chat/completions")
    suspend fun chat(@Body body: ChatCompletionRequest): ChatCompletionResponse
}
