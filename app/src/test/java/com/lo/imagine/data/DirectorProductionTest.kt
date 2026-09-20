package com.lo.imagine.data

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class DirectorProductionTest {
    private val storyboard = """{"summary":"午后窗台，安静环境声","aspect_ratio":"9:16","shots":[{"seconds":3,"prompt":"小猫睁眼，固定近景，窗外鸟鸣"},{"seconds":2,"prompt":"同一只猫伸懒腰，缓慢拉远，保持光线一致"}]}"""
    @Test fun `structured completion creates editable shots with exact sum and aspect`() {
        val result = parseDirectorStoryboard(storyboard, 5, "9:16")
        assertEquals(listOf("3", "2"), result.shots.map { it.seconds })
        assertEquals(2, result.shots.map { it.id }.distinct().size)
        assertTrue(result.script().contains("分镜2"))
        assertEquals("调整动作", result.shots.first().copy(prompt = "调整动作").prompt)
    }
    @Test fun `invalid model output cannot change confirmed parameters`() {
        listOf(storyboard.replace("9:16", "16:9"), storyboard.replace("\"seconds\":2", "\"seconds\":4"),
            storyboard.replace("\"seconds\":3", "\"seconds\":3.5"),
            storyboard.replace("\"seconds\":3", "\"seconds\":0"),
            """{"summary":"画面","aspect_ratio":"9:16","shots":[]}""",
            """{"summary":"画面","aspect_ratio":"9:16","shots":[{"seconds":5,"prompt":""}]}""")
            .forEach { assertTrue(runCatching { parseDirectorStoryboard(it, 5, "9:16") }.isFailure) }
    }
    @Test fun `same description does not activate a different asset and selected order is stable`() {
        val a = DirectorAsset("one", "人物", "白衣", "/a.jpg")
        val b = a.copy(id = "two", imagePath = "/b.jpg")
        val scene = DirectorAsset("scene", "海边", "阳光", "/c.jpg", "scene")
        assertEquals(listOf(scene, b), selectedDirectorAssets(listOf("scene", "two", "two"), listOf(a, b, scene)))
        assertTrue(runCatching { selectedDirectorAssets(listOf("missing"), listOf(a)) }.isFailure)
    }
    @Test fun `multimodal input preserves image order and never returns text alone when selected`() {
        val json = JsonParser.parseString(com.google.gson.Gson().toJson(directorMultimodalContent(
            "参考图1人物；参考图2环境", listOf("YQ==", "Yg==")))).asJsonArray
        assertEquals(3, json.size())
        assertEquals("data:image/jpeg;base64,YQ==", json[1].asJsonObject.getAsJsonObject("image_url")["url"].asString)
        assertEquals("data:image/jpeg;base64,Yg==", json[2].asJsonObject.getAsJsonObject("image_url")["url"].asString)
    }
    @Test fun `all frame sizes preserve selected aspect without landscape default`() {
        listOf("16:9", "9:16", "1:1", "4:3", "3:4", "21:9", "3:2", "2:3").forEach { aspect ->
            val ratio = aspect.split(":").map { it.toDouble() }
            val size = directorFrameSize(aspect).split("x").map { it.toDouble() }
            assertEquals(ratio[0] / ratio[1], size[0] / size[1], .001)
        }
        assertTrue(runCatching { directorFrameSize("other") }.isFailure)
    }
    @Test fun `production prompts pin confirmed timing and ask for independent shot prompts`() {
        DirectorEngine.entries.forEach {
            val prompt = directorProductionSystem(it, 8, "4:3")
            assertTrue(prompt.contains("8 秒"))
            assertTrue(prompt.contains("4:3"))
            assertTrue(prompt.contains("不是首尾帧") || prompt.contains("不把人物图或环境图自动当作首尾帧"))
        }
    }
}