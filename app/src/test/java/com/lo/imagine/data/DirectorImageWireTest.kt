package com.lo.imagine.data

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class DirectorImageWireTest {
    @Test fun `director first frame multipart sends both images without changing aspect size`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[{"b64_json":"YQ=="}]}"""))
            val api = Retrofit.Builder().baseUrl(server.url("/v1/"))
                .addConverterFactory(GsonConverterFactory.create()).build().create(ImageApi::class.java)
            api.directorEdit(mapOf("model" to "image-model", "prompt" to "reference 1 character; reference 2 scene",
                "n" to "1", "size" to directorFrameSize("3:4")).mapValues { it.value.toRequestBody("text/plain".toMediaType()) },
                listOf("character-image", "scene-image").mapIndexed { i, content ->
                    MultipartBody.Part.createFormData("image[]", "reference_${i + 1}.jpg", content.toRequestBody("image/jpeg".toMediaType()))
                })
            val req = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/v1/images/edits", req.path)
            val body = req.body.readUtf8()
            assertTrue(body.contains("1008x1344"))
            assertTrue(body.contains("character-image"))
            assertTrue(body.contains("scene-image"))
            assertEquals(2, Regex("name=\"image\\[\\]\"").findAll(body).count())
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun `interview wire payload carries reference labels followed by the actual images`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""))
            val api = Retrofit.Builder().baseUrl(server.url("/v1/"))
                .addConverterFactory(GsonConverterFactory.create()).build().create(ImageApi::class.java)
            api.chat(ChatCompletionRequest(model = "vision-model", messages = listOf(
                ChatMessage("system", directorInterviewSystem(DirectorEngine.GROK, 1)),
                ChatMessage("user", directorMultimodalContent("参考图1=人物；参考图2=环境", listOf("YQ==", "Yg=="))))))
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            val body = com.google.gson.JsonParser.parseString(request.body.readUtf8()).asJsonObject
            val parts = body.getAsJsonArray("messages")[1].asJsonObject.getAsJsonArray("content")
            assertEquals(3, parts.size())
            assertTrue(parts[0].asJsonObject["text"].asString.contains("参考图2=环境"))
            assertEquals("data:image/jpeg;base64,YQ==", parts[1].asJsonObject.getAsJsonObject("image_url")["url"].asString)
            assertEquals("data:image/jpeg;base64,Yg==", parts[2].asJsonObject.getAsJsonObject("image_url")["url"].asString)
            assertEquals("/v1/chat/completions", request.path)
        }
    }
}