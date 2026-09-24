package com.lo.imagine.data

import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

class VideoProductionTest {
    @get:Rule val folder = TemporaryFolder()
    private val a = "data:image/jpeg;base64,YQ=="
    private val b = "data:image/jpeg;base64,Yg=="
    private val refs = listOf(VideoReference("人物 · 白衣旅者", a), VideoReference("环境 · 海边", b))
    private val input = VideoInput("人物缓缓走向海边，固定镜头，海浪声", 8, "9:16", refs)
    private fun obj(body: String) = JsonParser.parseString(body).asJsonObject

    @Test fun `grok preserves every reference and parameters on the wire`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"request_id":"request-1"}"""))
            server.enqueue(MockResponse().setBody("""{"status":"pending"}"""))
            server.enqueue(MockResponse().setBody("""{"status":"done","video":{"url":"https://cdn.example/output.mp4"}}"""))
            val settings = VideoApiSettings(server.url("/v1").toString(), "video-secret", "custom-model-id")
            val client = VideoClient()
            val id = client.submit(settings, VideoProtocol.GROK15, input.copy(firstFrame = a, lastFrame = b))
            assertEquals("request-1", id)
            val post = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("POST", post.method)
            assertEquals("/v1/videos/generations", post.path)
            assertEquals("Bearer video-secret", post.getHeader("Authorization"))
            val body = obj(post.body.readUtf8())
            assertEquals("custom-model-id", body["model"].asString)
            assertEquals(8, body["duration"].asInt)
            assertEquals("9:16", body["aspect_ratio"].asString)
            assertEquals("720p", body["resolution"].asString)
            assertEquals(a, body.getAsJsonArray("reference_images")[0].asJsonObject["url"].asString)
            assertEquals(b, body.getAsJsonArray("reference_images")[1].asJsonObject["url"].asString)
            assertEquals(a, body.getAsJsonObject("image")["url"].asString)
            assertEquals(b, body.getAsJsonObject("last_frame")["url"].asString)
            assertTrue(body["prompt"].asString.contains("<IMAGE_2>：环境"))
            assertEquals("pending", client.query(settings, VideoProtocol.GROK15, id).status)
            assertEquals("https://cdn.example/output.mp4", client.query(settings, VideoProtocol.GROK15, id).url)
            repeat(2) {
                val get = server.takeRequest(2, TimeUnit.SECONDS)!!
                assertEquals("GET", get.method)
                assertEquals("/v1/videos/request-1", get.path)
            }
        }
    }
    @Test fun `seedance uses content roles and ratio instead of grok fields`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"id":"cgt-test"}"""))
            server.enqueue(MockResponse().setBody("""{"status":"succeeded","content":{"video_url":"https://cdn.example/seed.mp4"}}"""))
            val settings = VideoApiSettings(server.url("/").toString(), "key", "ep-selected")
            val client = VideoClient()
            val id = client.submit(settings, VideoProtocol.SEEDANCE, input)
            val req = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/api/v3/contents/generations/tasks", req.path)
            val body = obj(req.body.readUtf8())
            assertEquals("9:16", body["ratio"].asString)
            assertFalse(body.has("aspect_ratio"))
            val content = body.getAsJsonArray("content")
            assertEquals("text", content[0].asJsonObject["type"].asString)
            assertTrue(content[0].asJsonObject["text"].asString.contains("@Image2：环境"))
            assertEquals("reference_image", content[1].asJsonObject["role"].asString)
            assertEquals(a, content[1].asJsonObject.getAsJsonObject("image_url")["url"].asString)
            assertEquals(b, content[2].asJsonObject.getAsJsonObject("image_url")["url"].asString)
            assertEquals("done", client.query(settings, VideoProtocol.SEEDANCE, id).status)
            assertEquals("/api/v3/contents/generations/tasks/cgt-test", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
        }
    }
    @Test fun `seedance frame roles are explicit and reference images never become frames`() {
        val body = obj(videoRequestBody(VideoProtocol.SEEDANCE, "ep-model", input.copy(references = emptyList(), firstFrame = a, lastFrame = b)))
        val content = body.getAsJsonArray("content")
        assertEquals(listOf("first_frame", "last_frame"), content.drop(1).map { it.asJsonObject["role"].asString })
        assertEquals(3, content.size())
    }
    @Test fun `unsupported input combinations fail before any network request`() = runBlocking {
        MockWebServer().use { server ->
            val config = VideoApiSettings(server.url("/v1").toString(), "key", "model")
            val client = VideoClient()
            listOf(VideoProtocol.GROK to input.copy(lastFrame = a),
                VideoProtocol.GROK to input.copy(firstFrame = a),
                VideoProtocol.GROK15 to input.copy(aspect = "21:9"),
                VideoProtocol.SEEDANCE to input.copy(seconds = 3),
                VideoProtocol.SEEDANCE to input.copy(firstFrame = a),
                VideoProtocol.SEEDANCE to input.copy(references = emptyList(), lastFrame = b),
                VideoProtocol.GROK15 to input.copy(seconds = 16),
                VideoProtocol.GROK15 to input.copy(references = List(8) { refs[0] }),
                VideoProtocol.SEEDANCE to input.copy(references = List(10) { refs[0] }))
                .forEach { (protocol, value) ->
                    assertTrue(runCatching { client.submit(config, protocol, value) }.isFailure)
                }
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun `http errors do not retry post or switch protocol and do not echo secrets`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(400).setBody("secret-token invalid body"))
            val config = VideoApiSettings(server.url("/v1").toString(), "secret-token", "model")
            val error = runCatching { VideoClient().submit(config, VideoProtocol.GROK, input) }.exceptionOrNull()!!
            assertTrue(error.message!!.contains("400"))
            assertFalse(error.message!!.contains("secret-token"))
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun `disconnect after post does not send a second generation`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val config = VideoApiSettings(server.url("/v1").toString(), "key", "model")
            assertTrue(runCatching { VideoClient().submit(config, VideoProtocol.GROK, input) }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun `api redirects cannot forward authorization or create a different task`() = runBlocking {
        MockWebServer().use { source -> MockWebServer().use { target ->
            source.enqueue(MockResponse().setResponseCode(307).addHeader("Location", target.url("/elsewhere")))
            val config = VideoApiSettings(source.url("/v1").toString(), "key", "model")
            assertTrue(runCatching { VideoClient().submit(config, VideoProtocol.GROK, input) }.isFailure)
            assertEquals(0, target.requestCount)
        } }
    }
    @Test fun `completed video downloads atomically without authorization`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().addHeader("Content-Type", "video/mp4").setBody("video-bytes"))
            val target = folder.root.resolve("out/video.mp4")
            VideoClient().download(server.url("/output.mp4").toString(), target)
            assertEquals("video-bytes", target.readText())
            assertFalse(target.parentFile!!.resolve("video.mp4.part").exists())
            assertNull(server.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("Authorization"))
        }
    }
    @Test fun `task journal survives recreation and never persists secrets or image bytes`() {
        val file = folder.root.resolve("tasks.json")
        val store = DirectorVideoTaskStore(file)
        val task = DirectorVideoTask(engineId = "grok", shotId = 12, shotLabel = "分镜1", protocolId = "grok15",
            baseUrl = "https://video.example/v1", model = "m", seconds = 8, aspect = "9:16", prompt = "cat", referenceLabels = listOf("人物", "环境"))
        store.save(task)
        assertEquals("submitting", DirectorVideoTaskStore(file).load().single().status)
        store.save(task.copy(requestId = "request-1", status = "pending"))
        val saved = DirectorVideoTaskStore(file).load()
        assertEquals(1, saved.size)
        assertEquals("request-1", saved.single().requestId)
        assertFalse(file.readText().contains("apiKey"))
        assertFalse(file.readText().contains("base64"))
    }
    @Test fun `corrupt task journal is reported rather than silently allowing duplicate submission`() {
        val file = folder.newFile("bad.json").apply { writeText("{broken") }
        assertTrue(runCatching { DirectorVideoTaskStore(file).load() }.isFailure)
    }
    @Test fun `compatible protocol accepts a custom prefix model and either result shape`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"task_id":"task-9"}"""))
            server.enqueue(MockResponse().setBody("""{"state":"completed","video_url":"https://cdn.example/custom.mp4"}"""))
            val settings = VideoApiSettings(server.url("/relay/openai").toString(), "relay-key", "any-video-model")
            val id = VideoClient().submit(settings, VideoProtocol.COMPATIBLE, input.copy(aspect = "21:9", firstFrame = a, lastFrame = b))
            assertEquals("task-9", id)
            val post = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/relay/openai/videos/generations", post.path)
            assertEquals("Bearer relay-key", post.getHeader("Authorization"))
            val body = obj(post.body.readUtf8())
            assertEquals("any-video-model", body["model"].asString)
            assertEquals("21:9", body["aspect_ratio"].asString)
            assertTrue(body["prompt"].asString.contains("参考图2：环境"))
            assertEquals("https://cdn.example/custom.mp4", VideoClient().query(settings, VideoProtocol.COMPATIBLE, id).url)
            assertEquals("/relay/openai/videos/task-9", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
        }
    }
    @Test fun `model list is read from the versioned root and keeps distinct ids`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(
                """{"data":[{"id":" grok-video "},{"id":"grok-video"},{"name":"other"},{"id":" "}]}"""
            ))
            val settings = VideoApiSettings(server.url("/v1").toString(), "video-secret", protocolId = "grok")
            assertEquals(listOf("grok-video", "other"), fetchVideoModels(settings).getOrThrow())
            val get = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("GET", get.method)
            assertEquals("/v1/models", get.path)
            assertEquals("Bearer video-secret", get.getHeader("Authorization"))
        }
    }
    @Test fun `model list paths stay on each protocol root and do not guess another host`() {
        assertEquals("/v1/models", videoModelsEndpoint("https://video.example", VideoProtocol.GROK).encodedPath)
        assertEquals("/proxy/v1/models", videoModelsEndpoint("https://video.example/proxy/v1/", VideoProtocol.GROK15).encodedPath)
        assertEquals("/api/v3/models", videoModelsEndpoint("https://video.example/api/v3", VideoProtocol.SEEDANCE).encodedPath)
        assertEquals("/relay/openai/models", videoModelsEndpoint("https://video.example/relay/openai", VideoProtocol.COMPATIBLE).encodedPath)
        assertTrue(runCatching { videoModelsEndpoint("https://video.example/v1", VideoProtocol.SEEDANCE) }.isFailure)
    }
    @Test fun `model list reports an empty catalog and skips the server when the connection is incomplete`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"models":[]}"""))
            server.enqueue(MockResponse().setResponseCode(401).setBody("secret-token"))
            val base = server.url("/relay/openai").toString()
            val empty = fetchVideoModels(VideoApiSettings(base, "key", "kept-model", protocolId = "compatible"))
            assertTrue(empty.isFailure)
            assertTrue(empty.exceptionOrNull()!!.message!!.contains("没有返回可用模型"))
            val denied = fetchVideoModels(VideoApiSettings(base, "secret-token", protocolId = "compatible"))
            assertTrue(denied.exceptionOrNull()!!.message!!.contains("401"))
            assertFalse(denied.exceptionOrNull()!!.message!!.contains("secret-token"))
            assertTrue(fetchVideoModels(VideoApiSettings(base, "key", protocolId = null)).isFailure)
            assertTrue(fetchVideoModels(VideoApiSettings(base, "", protocolId = "grok")).isFailure)
            assertEquals(2, server.requestCount)
            assertEquals("/relay/openai/models", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
        }
    }
    @Test fun `endpoints preserve proxy prefix and reject mismatched base paths`() {
        assertEquals("/proxy/v1/videos/generations", videoEndpoint("https://video.example/proxy/v1/", VideoProtocol.GROK).encodedPath)
        assertEquals("/api/v3/contents/generations/tasks/id", videoEndpoint("https://video.example/api/v3", VideoProtocol.SEEDANCE, "id").encodedPath)
        assertTrue(runCatching { videoEndpoint("https://video.example/v1", VideoProtocol.SEEDANCE) }.isFailure)
        assertTrue(runCatching { videoEndpoint("https://video.example/v1?key=secret", VideoProtocol.GROK) }.isFailure)
    }
    @Test fun `pending terminal and malformed results have explicit states`() {
        listOf("failed", "expired", "cancelled").forEach {
            assertTrue(parseVideoTask(VideoProtocol.GROK, obj("""{"status":"$it"}""")).finished)
        }
        assertFalse(parseVideoTask(VideoProtocol.SEEDANCE, obj("""{"status":"running"}""")).finished)
        assertTrue(runCatching { parseVideoTask(VideoProtocol.GROK, obj("""{"status":"done"}""")) }.isFailure)
        assertTrue(runCatching { parseVideoTask(VideoProtocol.GROK, obj("""{"status":"unknown"}""")) }.isFailure)
    }
}