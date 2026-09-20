package com.lo.imagine.data.comfy

import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ComfyTaskTest {
    @get:Rule val temp = TemporaryFolder()
    private fun store(folder: File) = ComfyStore(File(folder, "data"), File(folder, "private/connection.json"))
    private suspend fun ready(repository: ComfyRepository) = withTimeout(3000) { repository.state.first { it.ready } }
    private suspend fun idle(repository: ComfyRepository) { withTimeout(5000) { repository.state.first { !it.busy } } }
    private class Backend : WorkflowBackend {
        override val providerId = COMFY_PROVIDER
        var submissions = 0
        var queries = 0
        var deletes = 0
        var result = RemoteStatus(ComfyPhase.SUCCEEDED, listOf(RemoteImage("7", "a.png"), RemoteImage("7", "b.png")), complete = true)
        var submitError: Exception? = null
        var missingImage = false
        var blockSecond = false
        val secondStarted = CompletableDeferred<Unit>()
        val downloads = mutableListOf<String>()
        override suspend fun inspect(connection: ComfyConnection) = "ok"
        override suspend fun nodeInfo(connection: ComfyConnection, classType: String) = JsonObject()
        override suspend fun submit(connection: ComfyConnection, graph: JsonObject, clientId: String): Submission {
            submissions++; submitError?.let { throw it }; return Submission("prompt-1")
        }
        override suspend fun status(connection: ComfyConnection, id: String, outputs: List<String>): RemoteStatus { queries++; return result }
        override suspend fun findSubmitted(connection: ComfyConnection, clientId: String) = listOf("prompt-1")
        override suspend fun removeQueued(connection: ComfyConnection, id: String) { deletes++ }
        override suspend fun download(connection: ComfyConnection, image: RemoteImage, destination: File) {
            downloads += image.filename
            if (image.filename == "b.png") {
                secondStarted.complete(Unit)
                if (blockSecond) awaitCancellation()
                if (missingImage) throw IOException("download interrupted")
            }
            destination.writeText(image.filename)
        }
    }
    private class Archive(val directory: File) : ComfyArchive {
        var galleryCalls = 0
        override fun temporary(job: ComfyJob, image: RemoteImage) = File(directory, "${image.key}.part")
        override suspend fun save(job: ComfyJob, image: RemoteImage, temporary: File): String {
            val out = File(directory, "${job.id}_${image.key}.png"); temporary.copyTo(out, overwrite = true); return out.path
        }
        override suspend fun gallery(path: String): Boolean { galleryCalls++; return true }
    }
    private suspend fun repository(directory: File): ComfyRepository {
        val repository = ComfyRepository(store(directory)); ready(repository)
        repository.saveConnection(ComfyConnection(baseUrl = "http://localhost:8188"))
        repository.saveWorkflow(sampleWorkflow()); return repository
    }
    @Test fun `unknown submissions are recovered by id without another post`() = runBlocking {
        val directory = temp.newFolder(); val repo = repository(directory); val backend = Backend()
        backend.submitError = SubmissionUncertain(IOException("lost reply"))
        val coordinator = ComfyTaskCoordinator(repo, backend, Archive(directory), { launch { it() } }, 1)
        coordinator.generate(false); idle(repo)
        val job = repo.state.value.jobs.single()
        assertEquals(ComfyPhase.UNKNOWN, job.phase); assertNull(job.promptId)
        coordinator.generate(false); idle(repo); assertEquals(1, backend.submissions)
        coordinator.resume(job.id); idle(repo)
        assertEquals(1, backend.submissions)
        assertEquals(ComfyPhase.SUCCEEDED, repo.state.value.jobs.single().phase)
        assertEquals("prompt-1", repo.state.value.jobs.single().promptId)
    }
    @Test fun `partial downloads resume only missing outputs and gallery copies`() = runBlocking {
        val directory = temp.newFolder(); val repo = repository(directory); val backend = Backend(); val archive = Archive(directory)
        backend.missingImage = true
        val coordinator = ComfyTaskCoordinator(repo, backend, archive, { launch { it() } }, 1)
        coordinator.generate(true); idle(repo)
        assertEquals(ComfyPhase.PARTIAL, repo.state.value.jobs.single().phase)
        assertEquals(1, repo.state.value.jobs.single().saved.size)
        backend.missingImage = false; coordinator.resume(repo.state.value.jobs.single().id); idle(repo)
        assertEquals(listOf("a.png", "b.png", "b.png"), backend.downloads)
        assertEquals(2, archive.galleryCalls); assertEquals(1, backend.submissions); assertEquals(1, backend.queries)
        assertEquals(ComfyPhase.SUCCEEDED, repo.state.value.jobs.single().phase)
    }
    @Test fun `pause during second download retains first output and can recover after process restart`() = runBlocking {
        val directory = temp.newFolder(); val repo = repository(directory); val backend = Backend(); val archive = Archive(directory)
        backend.blockSecond = true
        val coordinator = ComfyTaskCoordinator(repo, backend, archive, { launch { it() } }, 1)
        coordinator.generate(true)
        withTimeout(3000) { backend.secondStarted.await() }
        coordinator.pause(); idle(repo)
        val paused = repo.state.value.jobs.single()
        assertEquals(ComfyPhase.PAUSED, paused.phase); assertEquals(1, paused.saved.size); assertEquals(1, paused.gallerySaved.size)
        val reopened = ComfyRepository(store(directory)); ready(reopened)
        backend.blockSecond = false
        val next = ComfyTaskCoordinator(reopened, backend, archive, { launch { it() } }, 1)
        next.resume(paused.id); idle(reopened)
        assertEquals(ComfyPhase.SUCCEEDED, reopened.state.value.jobs.single().phase)
        assertEquals(1, backend.submissions); assertEquals(listOf("a.png", "b.png", "b.png"), backend.downloads)
        assertEquals(2, archive.galleryCalls)
    }
    @Test fun `rapid repeated generate allows only one post and keeps request snapshot`() = runBlocking {
        val directory = temp.newFolder(); val repo = repository(directory); val backend = Backend()
        val coordinator = ComfyTaskCoordinator(repo, backend, Archive(directory), { launch { it() } }, 1)
        val workflow = repo.state.value.selected!!
        coordinator.generate(false); coordinator.generate(false)
        repo.editParameter(workflow.id, workflow.parameters.first { it.kind == ParameterKind.PROMPT }.id, value = "future draft")
        idle(repo)
        assertEquals(1, backend.submissions)
        assertEquals("a paper bird on a wooden desk", repo.state.value.jobs.single().prompt)
    }
    @Test fun `different server cannot receive a recovery or cancellation request`() = runBlocking {
        val directory = temp.newFolder(); val repo = repository(directory); val backend = Backend()
        backend.submitError = SubmissionUncertain(IOException())
        val coordinator = ComfyTaskCoordinator(repo, backend, Archive(directory), { launch { it() } }, 1)
        coordinator.generate(false); idle(repo)
        val id = repo.state.value.jobs.single().id
        repo.saveConnection(ComfyConnection(baseUrl = "http://other:8188"))
        coordinator.resume(id); idle(repo)
        assertEquals(0, backend.queries); assertEquals(0, backend.deletes)
        assertTrue(repo.state.value.error!!.contains("原来"))
    }
    @Test fun `execution error with images keeps outputs but is not successful`() = runBlocking {
        val directory = temp.newFolder(); val repo = repository(directory); val backend = Backend()
        backend.result = backend.result.copy(phase = ComfyPhase.FAILED, error = "GPU failure")
        val coordinator = ComfyTaskCoordinator(repo, backend, Archive(directory), { launch { it() } }, 1)
        coordinator.generate(false); idle(repo)
        val job = repo.state.value.jobs.single()
        assertEquals(ComfyPhase.FAILED, job.phase); assertEquals(2, job.saved.size); assertTrue(job.message.contains("GPU failure"))
    }
    @Test fun `corrupt library is retained and blocks default overwrite`() = runBlocking {
        val directory = temp.newFolder(); val storage = store(directory)
        val file = File(directory, "data/library.json"); file.writeText("corrupt")
        val repo = ComfyRepository(storage)
        withTimeout(3000) { repo.state.first { it.error != null } }
        assertFalse(repo.state.value.ready)
        try { repo.saveWorkflow(sampleWorkflow()); fail() } catch (_: IllegalStateException) { }
        assertEquals("corrupt", file.readText())
    }
    @Test fun `corrupt job blocks new submissions and preserves the record`() = runBlocking {
        val directory = temp.newFolder(); val storage = store(directory)
        storage.saveConnection(ComfyConnection(baseUrl = "http://localhost:8188"))
        val workflow = sampleWorkflow()
        storage.saveLibrary(WorkflowLibrary(selectedId = workflow.id, workflows = listOf(workflow)))
        val file = File(directory, "data/jobs/broken.json").apply { parentFile!!.mkdirs(); writeText("{broken") }
        val repo = ComfyRepository(storage)
        withTimeout(3000) { repo.state.first { it.error != null } }
        assertFalse(repo.state.value.ready)
        val backend = Backend()
        ComfyTaskCoordinator(repo, backend, Archive(directory), { launch { it() } }, 1).generate(false)
        idle(repo); assertEquals(0, backend.submissions); assertEquals("{broken", file.readText())
    }
    @Test fun `invalid connection remains untouched until repaired and reloaded`() = runBlocking {
        val directory = temp.newFolder(); val storage = store(directory)
        val file = File(directory, "private/connection.json").apply { writeText("{\"baseUrl\":null}") }
        val original = file.readText()
        val repo = ComfyRepository(storage)
        withTimeout(3000) { repo.state.first { it.error != null } }
        assertFalse(repo.state.value.ready)
        try { repo.saveConnection(ComfyConnection(baseUrl = "http://localhost:8188")); fail() } catch (_: IllegalStateException) { }
        assertEquals(original, file.readText())
        storage.saveConnection(ComfyConnection(baseUrl = "http://localhost:8188"))
        repo.reload(); ready(repo)
        assertNull(repo.state.value.error)
        assertEquals("http://localhost:8188", repo.state.value.connection.baseUrl)
    }
    @Test fun `invalid numeric draft survives reopening so it can be corrected`() = runBlocking {
        val directory = temp.newFolder(); val repo = repository(directory)
        val workflow = repo.state.value.selected!!
        val parameter = workflow.parameters.first { it.kind == ParameterKind.STEPS }
        repo.editParameter(workflow.id, parameter.id, value = "")
        repo.flushDraft()
        val reopened = ComfyRepository(store(directory)); ready(reopened)
        assertEquals("", reopened.state.value.selected!!.parameters.first { it.id == parameter.id }.value)
    }
    @Test fun `crashed submitting state becomes unknown and credentials never enter jobs`() = runBlocking {
        val directory = temp.newFolder(); val storage = store(directory)
        storage.saveConnection(ComfyConnection(baseUrl = "http://localhost:8188", bearerToken = "private-example-value"))
        storage.saveJob(ComfyJob(origin = "http://localhost:8188/", graph = sampleGraph()))
        val repo = ComfyRepository(storage); ready(repo)
        assertEquals(ComfyPhase.UNKNOWN, repo.state.value.jobs.single().phase)
        assertTrue(File(directory, "data").walkTopDown().filter { it.isFile }.none { it.readText().contains("private-example-value") })
    }
}