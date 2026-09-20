package com.lo.imagine.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VideoApiSettingsTest {
    @get:Rule val folder = TemporaryFolder()
    private val video = VideoApiSettings("https://video.example/v1", "video-key", "video-model")
    private val otherChannels = AppSettings(
        baseUrl = "https://image.example/v1", apiKey = "image-key", model = "image-model",
        llmBaseUrl = "https://text.example/v1", llmApiKey = "llm-key", llmModel = "llm-model",
        maxParallel = 3, themeMode = "ark_dark", moodKey = "soft_illust",
        autoSaveGallery = false, reversePromptTemplate = "reverse", polishPromptTemplate = "polish"
    )

    private fun withRepository(block: suspend (SettingsRepository) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = folder.root.resolve("video.preferences_pb")
        val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        try { block(repo) } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun `existing image and llm settings never populate an unconfigured video connection`() = withRepository { repo ->
        repo.save(otherChannels)
        assertEquals(VideoApiSettings(), repo.videoSettings.first())
        val settings = repo.settings.first()
        assertEquals("", settings.videoBaseUrl)
        assertEquals("", settings.videoApiKey)
        assertEquals("", settings.videoModel)
        assertNotNull(videoApiConfigurationError(settings))
    }

    @Test fun `video save preserves every other setting and both preset libraries`() = withRepository { repo ->
        val image = CustomPreset("image", otherChannels.baseUrl, otherChannels.apiKey, otherChannels.model, "edits_multipart")
        val llm = CustomLlmPreset("llm", otherChannels.llmBaseUrl, otherChannels.llmApiKey, otherChannels.llmModel)
        repo.save(otherChannels)
        repo.saveCustomPresets(listOf(image))
        repo.saveActivePreset(image.name)
        repo.saveCustomLlmPresets(listOf(llm))
        repo.saveActiveLlmPreset(llm.name)
        repo.saveVideoSettings(video)
        assertEquals(otherChannels.copy(videoBaseUrl = video.baseUrl, videoApiKey = video.apiKey, videoModel = video.model),
            repo.settings.first())
        assertEquals(listOf(image), repo.loadCustomPresets())
        assertEquals(image.name, repo.loadActivePreset())
        assertEquals(listOf(llm), repo.loadCustomLlmPresets())
        assertEquals(llm.name, repo.loadActiveLlmPreset())
    }

    @Test fun `stale image draft and rapid unrelated writes cannot clear video`() = withRepository { repo ->
        repo.save(otherChannels)
        repo.saveVideoSettings(video)
        coroutineScope {
            launch { repo.saveConnections(otherChannels.copy(model = "new-image-model")) }
            launch { repo.saveMaxParallel(4) }
            launch { repo.saveInterface(themeMode = "ark_light", upscaleEnabled = true) }
            launch { repo.savePromptTemplates("new-reverse", "new-polish") }
        }
        assertEquals(video, repo.videoSettings.first())
        repo.saveImageConnection(CustomPreset("picked", "https://new.example", "new-key", "picked-model", "edits_multipart"))
        assertEquals(video, repo.videoSettings.first())
        val actual = repo.settings.first()
        assertEquals("picked-model", actual.model)
        assertEquals("llm-key", actual.llmApiKey)
        assertEquals("picked", repo.loadActivePreset())
        assertEquals(4, actual.maxParallel)
        assertTrue(actual.upscaleEnabled)
        assertEquals("new-reverse", actual.reversePromptTemplate)
    }

    @Test fun `video configuration presets and current selection survive reopening data store`() = runBlocking {
        val file = folder.root.resolve("reopen.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = SettingsRepository(PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }))
        val draft = video.copy(baseUrl = " ${video.baseUrl} \n", apiKey = " video-key ", model = " video-model ",
            presets = listOf(CustomVideoPreset("视频 A")), activePresetName = "视频 A")
        first.saveVideoSettings(draft)
        firstScope.coroutineContext[Job]!!.cancelAndJoin()
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val second = SettingsRepository(PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file }))
        try {
            val actual = second.videoSettings.first()
            assertEquals(video.baseUrl, actual.baseUrl)
            assertEquals(video.apiKey, actual.apiKey)
            assertEquals(video.model, actual.model)
            assertEquals("视频 A", actual.activePresetName)
            assertEquals(listOf(CustomVideoPreset("视频 A", video.baseUrl, video.apiKey, video.model)), actual.presets)
            assertNull(videoApiConfigurationError(second.settings.first()))
        } finally { secondScope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun `switch before debounce retains edited preset and loads only target credentials`() = withRepository { repo ->
        val first = CustomVideoPreset("A", video.baseUrl, video.apiKey, "old-model")
        val second = CustomVideoPreset("B", "https://b.example", "b-key", "b-model")
        val draft = video.copy(presets = listOf(first, second), activePresetName = "A")
        repo.saveVideoSettings(draft.selectPreset("B"))
        val actual = repo.videoSettings.first()
        assertEquals("B", actual.activePresetName)
        assertEquals(second.baseUrl, actual.baseUrl)
        assertEquals(second.apiKey, actual.apiKey)
        assertEquals(second.model, actual.model)
        assertEquals("video-model", actual.presets.first().model)
        assertEquals("video-model", actual.selectPreset("A").model)
    }

    @Test fun `new preset is blank and uniquely named without overwriting previous draft`() {
        val original = video.copy(presets = listOf(CustomVideoPreset("新视频预设")), activePresetName = "新视频预设")
        val blank = original.createBlankPreset()
        assertEquals("新视频预设 2", blank.activePresetName)
        assertEquals("", blank.baseUrl)
        assertEquals("", blank.apiKey)
        assertEquals("", blank.model)
        assertEquals(video.model, blank.presets.first().model)
        assertEquals("新视频预设 3", blank.createBlankPreset().activePresetName)
    }

    @Test fun `rename rejects collisions and delete retains a custom connection`() = withRepository { repo ->
        val draft = video.copy(presets = listOf(CustomVideoPreset("A"), CustomVideoPreset("B")), activePresetName = "A")
        try {
            draft.renameActivePreset("B")
            fail("Same-name preset would be overwritten")
        } catch (_: IllegalArgumentException) { }
        val renamed = draft.renameActivePreset(" 视频主服务 ")
        assertEquals("视频主服务", renamed.activePresetName)
        assertEquals(2, renamed.presets.size)
        assertEquals(video.apiKey, renamed.presets.first().apiKey)
        repo.saveVideoSettings(renamed.deleteActivePreset())
        val actual = repo.videoSettings.first()
        assertNull(actual.activePresetName)
        assertEquals(listOf(CustomVideoPreset("B")), actual.presets)
        assertEquals(video.apiKey, actual.apiKey)
        assertEquals(video.baseUrl, actual.baseUrl)
    }

    @Test fun `incomplete and cleared video settings persist without borrowing credentials`() = withRepository { repo ->
        repo.save(otherChannels)
        repo.saveVideoSettings(video.copy(apiKey = "", model = ""))
        assertEquals("", repo.settings.first().videoApiKey)
        assertEquals("", repo.settings.first().videoModel)
        repo.saveVideoSettings(VideoApiSettings())
        assertEquals(VideoApiSettings(), repo.videoSettings.first())
        assertEquals("image-key", repo.settings.first().apiKey)
    }

    @Test fun `full settings serialization includes independent video fields`() = withRepository { repo ->
        val all = otherChannels.copy(videoBaseUrl = video.baseUrl, videoApiKey = video.apiKey, videoModel = video.model)
        repo.save(all)
        assertEquals(all, repo.settings.first())
        assertEquals(video, repo.videoSettings.first())
    }

    @Test fun `video validation identifies each missing field and does not require other channels`() {
        listOf(video.copy(baseUrl = " \n") to "API 地址", video.copy(apiKey = " ") to "API Key",
            video.copy(model = "\t") to "模型").forEach { (value, field) ->
            val error = videoApiConfigurationError(value) ?: error("Missing $field was accepted")
            assertTrue(error.contains(field))
            assertTrue(error.contains("视频 API"))
        }
        assertNull(videoApiConfigurationError(video))
        val videoOnly = AppSettings(videoBaseUrl = video.baseUrl, videoApiKey = video.apiKey, videoModel = video.model)
        assertNull(videoApiConfigurationError(videoOnly))
        assertNotNull(imageApiConfigurationError(videoOnly))
        assertNotNull(llmApiConfigurationError(videoOnly))
    }

    @Test fun `video rejects malformed endpoints and keys without disclosing credentials`() {
        listOf("video.example", "https://", "ftp://video.example", "https://bad host/v1",
            "https://video.example:99999", "https://video.example?key=secret", "https://video.example#part")
            .forEach { assertNotNull(videoApiConfigurationError(video.copy(baseUrl = it))) }
        listOf("a secret", "a\nsecret", "密钥secret").forEach { key ->
            val error = videoApiConfigurationError(video.copy(apiKey = key))!!
            assertFalse(error.contains("secret"))
            assertTrue(error.contains("API Key 格式"))
            assertFalse(apiKeyFormatError(key)!!.contains("secret"))
        }
        assertNull(videoApiConfigurationError(video.copy(baseUrl = "http://127.0.0.1:8080/v1")))
    }
}
