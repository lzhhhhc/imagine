package com.lo.imagine.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NaiFuseTest {
    private fun card(
        name: String,
        traits: String = "silver hair",
        enabled: Boolean = true,
        fused: String = ""
    ) = NaiCharacterPrompt(name = name, traits = traits, enabled = enabled, fusedCaption = fused)

    @Test
    fun `blank middle segment still occupies its slot`() {
        // 两个启用角色，LLM 对第 1 个没有产出：空段必须占位，否则第 2 段会错位写进角色 1
        val segments = mapFusedSegments(" | blue scarf", 2)
        assertNotNull(segments)
        assertEquals(listOf("", "blue scarf"), segments)
    }

    @Test
    fun `segment count mismatch is rejected instead of shifting captions`() {
        assertNull(mapFusedSegments("only one", 2))
        assertNull(mapFusedSegments("a|b|c", 2))
        assertNotNull(mapFusedSegments("a|b", 2))
    }

    @Test
    fun `fused captions land on the matching cards`() {
        val snapshot = NaiWorkspaceConfig(
            prompt = "garden",
            characterCards = listOf(card("A"), card("B", traits = "", enabled = false), card("C"))
        )
        val segments = listOf("a tags", "c tags")
        val merged = applyFusedCaptions(snapshot, snapshot, segments)
        assertNotNull(merged)
        assertEquals("a tags", merged!!.characterCards[0].fusedCaption)
        assertEquals("", merged.characterCards[1].fusedCaption)
        assertEquals("c tags", merged.characterCards[2].fusedCaption)
    }

    @Test
    fun `edits during the task discard the stale fusion`() {
        val snapshot = NaiWorkspaceConfig(prompt = "garden", characterCards = listOf(card("A")))
        val edited = snapshot.copy(prompt = "sunset garden")
        assertNull(applyFusedCaptions(snapshot, edited, listOf("a tags")))
        val editedCard = snapshot.copy(characterCards = listOf(card("A", traits = "red hair")))
        assertNull(applyFusedCaptions(snapshot, editedCard, listOf("a tags")))
    }

    @Test
    fun `request and preview share one active character filter`() {
        // 只带融合结果、原始 caption 为空的角色同样参与请求与预览
        val c = NaiWorkspaceConfig(
            prompt = "garden",
            characterCards = listOf(
                card("A", traits = "", fused = "a tags"),
                card("B", traits = "", enabled = false)
            )
        )
        val active = activeNaiCharacterCards(c)
        assertEquals(1, active.size)
        assertEquals("A", active.single().name)
        // 融合产物优先于机械拼接，且角色字段不残留 1girl
        assertEquals("a tags", naiCharacterFieldCaption(active.single()))
        val payload = naiNativePayload(c, 0)
        assertTrue(payload["input"].toString().contains("garden"))
        val p = payload["parameters"] as Map<*, *>
        val chars = p["characterPrompts"] as List<*>
        assertEquals(1, chars.size)
        assertEquals("a tags", (chars.single() as Map<*, *>)["prompt"])
        val v4 = p["v4_prompt"] as Map<*, *>
        val captionMap = v4["caption"] as Map<*, *>
        val charCaptions = captionMap["char_captions"] as List<*>
        assertEquals(1, charCaptions.size)
        assertEquals("a tags", (charCaptions.single() as Map<*, *>)["char_caption"])
        // 展示与请求同源
        assertEquals(payload["input"].toString() + " | a tags", naiDisplayPrompt(c, payload["input"].toString()))
    }
}