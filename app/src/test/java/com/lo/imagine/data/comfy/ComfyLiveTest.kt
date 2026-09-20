package com.lo.imagine.data.comfy

import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/** Explicit opt-in; uses only this benign text-to-image fixture, never a user-provided graph. */
class ComfyLiveTest {
    @Test fun `local ComfyUI creates two still life images through the production coordinator`() = runBlocking {
        val url = System.getenv("IMAGINE_COMFY_LIVE_URL")
        assumeTrue("Set IMAGINE_COMFY_LIVE_URL and IMAGINE_COMFY_LIVE_DIR to opt in", !url.isNullOrBlank())
        val directory = File(requireNotNull(System.getenv("IMAGINE_COMFY_LIVE_DIR"))).apply { mkdirs() }
        val connection = ComfyConnection(baseUrl = requireNotNull(url), waitMinutes = 5)
        val backend = ComfyClient()
        println(backend.inspect(connection))
        val model = requireNotNull(System.getenv("IMAGINE_COMFY_LIVE_MODEL"))
        val schema = backend.nodeInfo(connection, "CheckpointLoaderSimple")
        assertTrue(schema.getAsJsonObject("input").getAsJsonObject("required").getAsJsonArray("ckpt_name")[0].asJsonArray.any { it.asString == model })
        val graph = sampleGraph().apply {
            getAsJsonObject("1").getAsJsonObject("inputs").addProperty("ckpt_name", model)
            getAsJsonObject("2").getAsJsonObject("inputs").addProperty("text", "still life photograph of a blue ceramic vase and a folded paper crane on a wooden table, soft daylight, no people, detailed")
            getAsJsonObject("3").getAsJsonObject("inputs").addProperty("text", "blurry, low quality, text, watermark, people")
            getAsJsonObject("4").getAsJsonObject("inputs").addProperty("batch_size", 2)
            getAsJsonObject("5").getAsJsonObject("inputs").addProperty("steps", 16)
            getAsJsonObject("7").getAsJsonObject("inputs").addProperty("filename_prefix", "Imagine_Client_Smoke")
        }
        File(directory, "still-life-api.json").writeText(graph.toString())
        val repository = ComfyRepository(ComfyStore(File(directory, "state"), File(directory, "private/connection.json")))
        withTimeout(3000) { repository.state.first { it.ready } }
        repository.saveConnection(connection)
        repository.saveWorkflow(ComfyWorkflow(name = "静物 · 花瓶与纸鹤", graph = graph,
            parameters = ComfyWorkflowEngine.suggest(graph), outputNodes = listOf("7")))
        val archive = object : ComfyArchive {
            override fun temporary(job: ComfyJob, image: RemoteImage) = File(directory, "${image.key}.part")
            override suspend fun save(job: ComfyJob, image: RemoteImage, temporary: File): String {
                val decoded = ImageIO.read(temporary) ?: error("Downloaded image failed decoding")
                assertEquals(512, decoded.width); assertEquals(512, decoded.height)
                val output = File(directory, "result-${image.key}.png")
                temporary.copyTo(output, overwrite = true)
                return output.absolutePath
            }
            override suspend fun gallery(path: String): Boolean = error("Android gallery is outside this JVM smoke test")
        }
        val coordinator = ComfyTaskCoordinator(repository, backend, archive, { launch { it() } })
        val progress = launch {
            repository.state.collect { println("Comfy smoke: busy=${it.busy} status=${it.status} phase=${it.jobs.firstOrNull()?.phase} error=${it.error}") }
        }
        try {
            coordinator.generate(false)
            withTimeout(330_000) { repository.state.first { !it.busy } }
            val state = repository.state.value
            assertNull(state.error)
            val job = state.jobs.single()
            assertEquals(job.message, ComfyPhase.SUCCEEDED, job.phase)
            assertEquals(2, job.saved.size)
            assertNotNull(job.promptId)
            File(directory, "result.txt").writeText("prompt_id=${job.promptId}\nphase=${job.phase}\noutputs=${job.saved.values.joinToString()}\n")
            println("Live smoke passed: ${job.promptId}, ${job.saved.size} images")
        } finally { coordinator.pause(); progress.cancelAndJoin() }
    }
}