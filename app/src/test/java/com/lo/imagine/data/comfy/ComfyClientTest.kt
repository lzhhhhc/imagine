package com.lo.imagine.data.comfy

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.TimeUnit

class ComfyClientTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var client: ComfyClient
    private lateinit var connection: ComfyConnection
    @Before fun setup() {
        server = MockWebServer(); server.start(); client = ComfyClient()
        connection = ComfyConnection(baseUrl = server.url("/proxy/comfy/").toString(), bearerToken = "test-only-not-a-secret")
    }
    @After fun tearDown() { server.shutdown() }
    private fun response(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    @Test fun `base prefix and explicit Bearer are reused on every endpoint`() = runBlocking {
        server.enqueue(response("""{"system":{"comfyui_version":"test"},"devices":[]}"""))
        assertTrue(client.inspect(connection).contains("test"))
        val req = server.takeRequest()
        assertEquals("/proxy/comfy/system_stats", req.path)
        assertEquals("Bearer test-only-not-a-secret", req.getHeader("Authorization"))
        server.enqueue(response("""{"custom/a b":{"input":{}}}"""))
        client.nodeInfo(connection, "custom/a b")
        assertEquals("/proxy/comfy/object_info/custom%2Fa%20b", server.takeRequest().path)
    }
    @Test fun `URLs reject embedded credentials queries fragments and missing scheme`() {
        for (url in listOf("192.168.1.1:8188", "http://user:pass@localhost", "http://localhost/?token=x", "http://localhost/#x"))
            assertThrows(IllegalArgumentException::class.java) { comfyBaseUrl(url) }
        assertEquals("http://localhost/prefix/", comfyBaseUrl("http://localhost/prefix").toString())
    }
    @Test fun `submit sends exact graph once and keeps a prompt id despite output warnings`() = runBlocking {
        server.enqueue(response("""{"prompt_id":"p1","node_errors":{"9":{"message":"bad output"}}}"""))
        val result = client.submit(connection, sampleGraph(), "client-1")
        assertEquals("p1", result.id); assertTrue(result.warning.contains("9"))
        val req = server.takeRequest()
        assertEquals("POST", req.method); assertEquals("/proxy/comfy/prompt", req.path)
        val body = JsonParser.parseString(req.body.readUtf8()).asJsonObject
        assertEquals("client-1", body.get("client_id").asString)
        assertEquals("9007199254740993", body.getAsJsonObject("prompt").getAsJsonObject("5").getAsJsonObject("inputs").get("seed").asString)
        assertEquals(1, server.requestCount)
    }
    @Test fun `503 with retry-after zero never resends a prompt`() = runBlocking {
        server.enqueue(response("{}").setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(response("""{"prompt_id":"duplicate"}"""))
        try { client.submit(connection, sampleGraph(), "client-1"); fail("expected uncertainty") } catch (_: SubmissionUncertain) { }
        assertEquals(1, server.requestCount)
    }
    @Test fun `disconnect after request and malformed success are uncertain without resubmitting`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        try { client.submit(connection, sampleGraph(), "client-1"); fail() } catch (_: SubmissionUncertain) { }
        assertEquals(1, server.requestCount)
        server.enqueue(response("{\"prompt_id\":{}}"))
        try { client.submit(connection, sampleGraph(), "client-2"); fail() } catch (_: SubmissionUncertain) { }
        assertEquals(2, server.requestCount)
    }
    @Test fun `validation rejection is definitive and includes node ids`() = runBlocking {
        server.enqueue(response("""{"error":{"message":"validation failed"},"node_errors":{"5":{}}}""").setResponseCode(400))
        try { client.submit(connection, sampleGraph(), "client-1"); fail() }
        catch (e: ComfyHttpException) { assertEquals(400, e.code); assertTrue(e.message!!.contains("5")) }
    }
    @Test fun `redirects do not forward credentials or submit elsewhere`() = runBlocking {
        MockWebServer().use { other ->
            other.start()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/prompt")))
            try { client.inspect(connection); fail() } catch (e: ComfyHttpException) { assertEquals(302, e.code) }
            assertEquals(0, other.requestCount)
        }
    }
    @Test fun `completed history selects requested outputs deduplicates and keeps partial failure`() = runBlocking {
        server.enqueue(response("""{"p":{"status":{"completed":false,"status_str":"error","messages":[["execution_error",{"exception_message":"GPU busy"}]]},"outputs":{"7":{"images":[{"filename":"a.png","subfolder":"中文 空格","type":"output"},{"filename":"a.png","subfolder":"中文 空格","type":"output"}]},"8":{"images":[{"filename":"preview.png","type":"temp"}]}}}}"""))
        val state = client.status(connection, "p", listOf("7"))
        assertTrue(state.complete); assertEquals(ComfyPhase.FAILED, state.phase); assertEquals("GPU busy", state.error)
        assertEquals(1, state.images.size); assertEquals("a.png", state.images.single().filename)
        assertEquals(1, server.requestCount)
    }
    @Test fun `queue distinguishes pending running and missing`() = runBlocking {
        for ((queue, phase) in listOf("queue_pending" to ComfyPhase.QUEUED, "queue_running" to ComfyPhase.RUNNING, "other" to ComfyPhase.TRACKING)) {
            server.enqueue(response("{}")); server.enqueue(response("""{"$queue":[[0,"p",{},{}]]}"""))
            assertEquals(phase, client.status(connection, "p", listOf("7")).phase)
        }
    }
    @Test fun `unknown submission recovery matches client id not other peoples prompts`() = runBlocking {
        server.enqueue(response("""{"queue_pending":[[1,"wrong",{}, {"client_id":"other"}],[2,"ours",{}, {"client_id":"same"}]]}"""))
        server.enqueue(response("""{"finished":{"prompt":[3,"finished",{}, {"client_id":"same"}]}}"""))
        assertEquals(listOf("ours", "finished"), client.findSubmitted(connection, "same"))
    }
    @Test fun `queue deletion addresses one id without global interrupt`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        client.removeQueued(connection, "only-me")
        val req = server.takeRequest()
        assertEquals("/proxy/comfy/queue", req.path)
        assertEquals("{\"delete\":[\"only-me\"]}", req.body.readUtf8())
    }
    @Test fun `download encodes descriptors and preserves original bytes`() = runBlocking {
        val bytes = byteArrayOf(0, 1, 2, -1, 127, 33)
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val file = temp.newFile()
        client.download(connection, RemoteImage("7", "hello &?#.png", "中文 空格", "temp"), file)
        assertArrayEquals(bytes, file.readBytes())
        val req = server.takeRequest()
        assertEquals("hello &?#.png", req.requestUrl!!.queryParameter("filename"))
        assertEquals("中文 空格", req.requestUrl!!.queryParameter("subfolder"))
        assertEquals("temp", req.requestUrl!!.queryParameter("type"))
    }
    @Test fun `cancelling a hung request stops the actual call`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = async(Dispatchers.IO) { client.inspect(connection) }
        assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
        withTimeout(2000) { job.cancelAndJoin() }
        assertEquals(1, server.requestCount)
    }
}