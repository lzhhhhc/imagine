package com.lo.imagine.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ApiConfigurationTest {
    private val configured = AppSettings(
        baseUrl = "https://relay.example/v1", apiKey = "test-key", model = "test-image-model"
    )

    @Test fun emptySettingsExplainEveryMissingField() {
        val error = imageApiConfigurationError(AppSettings()) ?: error("Empty settings accepted")
        listOf("API 地址", "API Key", "模型", "设置 → 绘图 API").forEach {
            assertTrue("Missing explanation for $it", error.contains(it))
        }
    }

    @Test fun eachRequiredImageFieldIsValidatedIndependently() {
        listOf(
            configured.copy(baseUrl = " \n ") to "API 地址",
            configured.copy(apiKey = "\t") to "API Key",
            configured.copy(model = "  ") to "模型"
        ).forEach { (settings, field) ->
            val error = imageApiConfigurationError(settings) ?: error("Accepted missing $field")
            assertTrue(error.contains(field))
        }
        assertNull(imageApiConfigurationError(configured))
    }

    @Test fun malformedEndpointsFailBeforeRetrofitCanThrow() {
        listOf(
            "relay.example/v1", "https://", "https:///", "ftp://relay.example/v1",
            "https://bad host/v1", "https://relay.example:99999/v1",
            "https://relay.example/v1?key=secret", "https://relay.example/v1#section"
        ).forEach { url ->
            assertNotNull("Accepted malformed endpoint: $url", imageApiConfigurationError(configured.copy(baseUrl = url)))
        }
    }

    @Test fun supportedLocalAndRemoteConnectionsRemainValid() {
        listOf("https://relay.example/v1/", "http://127.0.0.1:8080/v1", "http://[::1]:8080/v1", " https://relay.example/api \n").forEach {
            assertNull(imageApiConfigurationError(configured.copy(baseUrl = it, apiKey = " test-key \n")))
        }
    }

    @Test fun badHeaderCharactersAreReportedWithoutExposingTheKey() {
        listOf("test\nsecret", "test secret", "测试secret").forEach { key ->
            val message = imageApiConfigurationError(configured.copy(apiKey = key)) ?: error("Bad key accepted")
            assertTrue(message.contains("API Key 格式"))
            assertFalse(message.contains("secret"))
        }
    }

    @Test fun directorLlmAndImageApiStayIndependent() {
        assertNull(imageApiConfigurationError(configured))
        assertTrue(llmApiConfigurationError(configured)!!.contains("提示词润色 LLM"))
        val llmOnly = AppSettings(llmBaseUrl = "https://text.example/v1", llmApiKey = "test-llm-key", llmModel = "test-llm")
        assertNull(llmApiConfigurationError(llmOnly))
        assertNotNull(imageApiConfigurationError(llmOnly))
    }

    @Test fun naiFollowModeRejectsEmptyBackendInsteadOfUsingAnOldConnection() {
        val nai = NaiWorkspaceConfig(endpoint = "https://old.example/ai/generate-image", token = "old-token")
        assertEquals("", naiEffectiveEndpoint(nai, AppSettings()))
        assertEquals("", naiEffectiveToken(nai, AppSettings()))
        assertNotNull(naiApiConfigurationError(nai, AppSettings()))
        assertNotNull(naiApiConfigurationError(nai, configured.copy(apiKey = "")))
        assertNotNull(naiApiConfigurationError(nai, configured.copy(baseUrl = "")))
    }

    @Test fun naiIndependentModeUsesOnlyItsOwnConnection() {
        val nai = NaiWorkspaceConfig(useBackendApi = false, token = "own-token")
        assertNull(naiApiConfigurationError(nai, AppSettings()))
        assertEquals("own-token", naiEffectiveToken(nai, configured))
        assertNotNull(naiApiConfigurationError(nai.copy(token = ""), configured))
        // NAI has its own model selector, so a blank homepage model must not invalidate NAI.
        assertNull(naiApiConfigurationError(nai.copy(useBackendApi = true), configured.copy(model = "")))
    }

    @Test fun nativeGenerationWithoutAnApiReturnsFailureWithoutStartingANetworkCall() = runBlocking {
        val client = NaiNativeClient()
        val result = client.generate(NaiWorkspaceConfig(prompt = "a cat"), 42L, AppSettings())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("API Key"))
        assertFalse(client.busy)
    }
}