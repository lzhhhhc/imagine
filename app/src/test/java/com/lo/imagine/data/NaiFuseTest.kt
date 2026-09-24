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
    fun `editing the scene marks a previous arrangement stale instead of hiding it`() {
        val base = NaiWorkspaceConfig(
            prompt = "garden",
            characterCards = listOf(card("A", fused = "close-up, silver hair"))
        )
        val edited = updateNaiArrangement(base, base.copy(prompt = "sunset garden"))
        assertTrue(edited.characterArrangementStale)
        assertTrue(edited.characterCards.all { it.fusedCaption.isBlank() })

        // 整理成功后回到有效态；写回自身（来源未变）不触发失效
        val applied = updateNaiArrangement(edited, edited.copy(characterArrangementStale = false))
        assertFalse(applied.characterArrangementStale)
        // 手动放弃整理结果（使用原始标签）后，失效提示一并消失
        val afterClear = updateNaiArrangement(applied, applied.copy(
            characterCards = applied.characterCards.map { it.copy(fusedCaption = "") },
            characterArrangementStale = false))
        assertFalse(afterClear.characterArrangementStale)
    }

    @Test
    fun `arranged role tags are visible in the main prompt and do not duplicate character fields`() {
        assertEquals(
            "女孩站在花园, silver hair, blue coat",
            naiArrangementPrompt("女孩站在花园", listOf("silver hair", "blue coat"))
        )
        val c = NaiWorkspaceConfig(
            prompt = "女孩站在花园, silver hair, blue coat",
            characterArrangementInPrompt = true,
            characterArrangementPolished = true,
            characterCards = listOf(card("A", fused = "silver hair, blue coat"))
        )
        assertTrue(activeNaiCharacterCards(c).isEmpty())
        val payload = naiNativePayload(c, 0)
        val p = payload["parameters"] as Map<*, *>
        assertTrue(payload["input"].toString().contains("silver hair"))
        assertTrue((p["characterPrompts"] as List<*>).isEmpty())
        assertTrue(naiDisplayPrompt(c, payload["input"].toString()).contains("blue coat"))
    }

    @Test
    fun `arrangement source can be restored without losing the user description`() {
        val source = "女孩站在花园"
        val c = NaiWorkspaceConfig(
            prompt = naiArrangementPrompt(source, listOf("silver hair")),
            characterArrangementInPrompt = true,
            characterArrangementBasePrompt = source
        )
        assertEquals(source, c.characterArrangementBasePrompt)
        assertEquals("女孩站在花园, silver hair", c.prompt)
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