package com.lo.imagine.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.lo.imagine.data.AppSettings
import com.lo.imagine.data.SettingsRepository
import com.lo.imagine.data.ThemeMode
import com.lo.imagine.data.UiMood
import com.lo.imagine.ui.ImageImportRoute
import com.lo.imagine.ui.ImageImportSource
import com.lo.imagine.ui.resolveImageImport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsPreferencesTest {
    @get:Rule val folder = TemporaryFolder()

    private fun withRepository(block: suspend (SettingsRepository) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = folder.root.resolve("settings.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try { block(SettingsRepository(store)) } finally { scope.cancel() }
    }

    @Test fun `connection draft cannot reset appearance output templates or concurrency`() = withRepository { repo ->
        val stale = AppSettings(baseUrl = "https://old.invalid", apiKey = "test-key", model = "old-model")
        repo.save(stale.copy(maxParallel = 4))
        repo.saveInterface(themeMode = "ark_dark", moodKey = "soft_illust", upscaleEnabled = true, autoSaveGallery = false)
        repo.savePromptTemplates("reverse-custom", "polish-custom")
        repo.saveConnections(stale.copy(baseUrl = "https://new.invalid", model = "new-model"))
        val actual = repo.settings.first()
        assertEquals("https://new.invalid", actual.baseUrl)
        assertEquals("new-model", actual.model)
        assertEquals("ark_dark", actual.themeMode)
        assertEquals("soft_illust", actual.moodKey)
        assertTrue(actual.upscaleEnabled)
        assertFalse(actual.autoSaveGallery)
        assertEquals(4, actual.maxParallel)
        assertEquals("reverse-custom", actual.reversePromptTemplate)
        assertEquals("polish-custom", actual.polishPromptTemplate)
    }

    @Test fun `independent rapid preference changes compose without wiping connections`() = withRepository { repo ->
        repo.save(AppSettings(baseUrl = "https://image.invalid", apiKey = "image-key", model = "image-model",
            llmBaseUrl = "https://llm.invalid", llmApiKey = "llm-key", llmModel = "llm-model"))
        coroutineScope {
            launch { repo.saveInterface(themeMode = "ark_dark") }
            launch { repo.saveInterface(moodKey = "dark_tactic") }
            launch { repo.saveInterface(autoSaveGallery = false) }
            launch { repo.saveInterface(upscaleEnabled = true) }
        }
        val actual = repo.settings.first()
        assertEquals("ark_dark", actual.themeMode)
        assertEquals("dark_tactic", actual.moodKey)
        assertTrue(actual.upscaleEnabled)
        assertFalse(actual.autoSaveGallery)
        assertEquals("image-key", actual.apiKey)
        assertEquals("image-model", actual.model)
        assertEquals("llm-key", actual.llmApiKey)
        assertEquals("llm-model", actual.llmModel)
    }

    @Test fun `each day night and mood combination round trips independently`() = withRepository { repo ->
        for (mode in ThemeMode.entries) for (mood in UiMood.entries) {
            repo.saveInterface(themeMode = mode.id, moodKey = mood.id)
            val actual = repo.settings.first()
            assertEquals(mode.id, actual.themeMode)
            assertEquals(mood.id, actual.moodKey)
        }
    }

    @Test fun `saved mood survives recreating repository and reopening data store`() = runBlocking {
        val file = folder.root.resolve("reopen.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val firstStore = PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file })
        SettingsRepository(firstStore).saveInterface(themeMode = "ark_dark", moodKey = "soft_illust", autoSaveGallery = false)
        firstScope.coroutineContext[Job]!!.cancelAndJoin()
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondStore = PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file })
        try {
            val actual = SettingsRepository(secondStore).settings.first()
            assertEquals("soft_illust", actual.moodKey)
            assertEquals("ark_dark", actual.themeMode)
            assertFalse(actual.autoSaveGallery)
        } finally { secondScope.cancel() }
    }

    @Test fun `import clicks follow the saved source instead of their old picker`() {
        listOf(
            "src/main/java/com/lo/imagine/ui/edit/EditScreen.kt",
            "src/main/java/com/lo/imagine/ui/studio/ReversePromptDialog.kt",
            "src/main/java/com/lo/imagine/ui/studio/comfy/ComfyWorkspaceScreen.kt",
            "src/main/java/com/lo/imagine/ui/director/DirectorScreen.kt"
        ).forEach { path ->
            val source = java.io.File(path).readText()
            assertTrue(path, source.contains("rememberImageImport("))
            assertFalse(path, source.contains("launch(\"image/*\")"))
            assertFalse(path, source.contains("PickVisualMediaRequest"))
        }
        val settings = java.io.File("src/main/java/com/lo/imagine/ui/settings/SettingsScreen.kt").readText()
        assertTrue(settings.contains("LibrarySettingsSection("))
        assertTrue(settings.contains("library = {"))
        val library = java.io.File("src/main/java/com/lo/imagine/ui/ImageImport.kt").readText()
        assertTrue(library.contains("openPublicImageFolder(context)"))
        assertFalse(library.contains("goTo(\"works\")"))
        val folder = java.io.File("src/main/java/com/lo/imagine/util/ImageUtils.kt").readText()
        assertTrue(folder.contains("primary:\${Environment.DIRECTORY_PICTURES}/Imagine"))
        val preview = java.io.File("src/main/java/com/lo/imagine/ui/preview/PreviewScreen.kt").readText()
        assertTrue(preview.contains("deleteHistory(entry)"))
        assertTrue(preview.contains("contentDescription = \"删除这个作品\""))
        assertFalse(preview.contains("Color.White"))
        assertFalse(preview.contains("Color.Black"))
        assertFalse(preview.contains("Color(0x"))
    }

    @Test fun `image import source is one path and survives a reopen`() = withRepository { repo ->
        assertEquals(ImageImportSource.ASK, ImageImportSource.fromId(null))
        assertEquals(ImageImportSource.ASK, ImageImportSource.fromId("nope"))
        assertEquals(ImageImportRoute.LOCAL, resolveImageImport(ImageImportSource.LOCAL))
        assertEquals(ImageImportRoute.GALLERY, resolveImageImport(ImageImportSource.GALLERY))
        assertEquals(ImageImportRoute.ASK, resolveImageImport(ImageImportSource.ASK))
        repo.saveInterface(imageImportSource = ImageImportSource.LOCAL.id)
        assertEquals("local", repo.settings.first().imageImportSource)
        repo.saveConnections(AppSettings(baseUrl = "https://image.invalid", apiKey = "k", model = "m"))
        assertEquals("local", repo.settings.first().imageImportSource)
    }

    @Test fun `rail selection follows content and reaches a short final section`() {
        assertEquals(SettingsSection.APPEARANCE, settingsSectionAt(0, false))
        assertEquals(SettingsSection.APPEARANCE, settingsSectionAt(1, false))
        assertEquals(SettingsSection.OUTPUT, settingsSectionAt(2, false))
        assertEquals(SettingsSection.LIBRARY, settingsSectionAt(3, false))
        assertEquals(SettingsSection.CHANNELS, settingsSectionAt(4, false))
        assertEquals(SettingsSection.ABOUT, settingsSectionAt(4, true))
    }
}