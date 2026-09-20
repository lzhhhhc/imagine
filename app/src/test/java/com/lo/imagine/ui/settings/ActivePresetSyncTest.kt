package com.lo.imagine.ui.settings

import com.lo.imagine.data.CustomLlmPreset
import com.lo.imagine.data.CustomPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「完成并返回」要把改动同步进哪个预设。
 * 回归场景：改完模型直接返回，首页预设列表若没被更新，看起来就是「没保存」。
 */
class ActivePresetSyncTest {
    private fun preset(name: String, model: String = "old-model") = CustomPreset(
        name = name, baseUrl = "https://a.com", apiKey = "sk-1", model = model, editMode = "edits_multipart"
    )

    /** 命中当前预设：返回对象本身，供调用方原位替换。 */
    @Test
    fun `returns the active preset`() {
        val list = listOf(preset("agnes"), preset("nai"))
        assertEquals("agnes", activePresetForSync(list, "agnes") { it.name }?.name)
    }

    /** 名字按 trim 比较：历史数据里存在纯换行的空名与带尾空白的名字。 */
    @Test
    fun `names are matched after trimming`() {
        val list = listOf(preset("  apizzz\n"))
        assertEquals("  apizzz\n", activePresetForSync(list, "apizzz") { it.name }?.name)
        assertEquals(list[0], activePresetForSync(list, " apizzz ") { it.name })
    }

    /** 没有当前预设（未存为预设的自定义连接）时不写回任何条目。 */
    @Test
    fun `null active name selects nothing`() {
        assertNull(activePresetForSync(listOf(preset("agnes")), null) { it.name })
    }

    /** 当前名指向已被删除/改名的预设时也不写回，避免误写另一条通道。 */
    @Test
    fun `missing preset name selects nothing`() {
        assertNull(activePresetForSync(listOf(preset("agnes")), "deleted") { it.name })
    }

    /** 空名预设仍要能被同步（否则改动会被静默丢弃）。 */
    @Test
    fun `blank-named preset can be targeted`() {
        val list = listOf(preset("\n"))
        assertNull(activePresetForSync(list, null) { it.name })
        assertEquals("\n", activePresetForSync(list, "") { it.name }?.name)
        assertEquals("\n", activePresetForSync(list, "   ") { it.name }?.name)
    }

    /** LLM 档案走同一套匹配语义。 */
    @Test
    fun `llm profiles use the same matching`() {
        val list = listOf(CustomLlmPreset(name = " new ", baseUrl = "https://l.com", apiKey = "sk-2", model = "m"))
        assertEquals(" new ", activePresetForSync(list, "new") { it.name }?.name)
    }

    /** 改了模型就必须写回，否则首页预设列表仍是旧值。 */
    @Test
    fun `model change requires writeback`() {
        assertTrue(presetDiffersFromDraft("https://a.com", "sk-1", "old", "https://a.com", "sk-1", "new"))
    }

    /** 打开弹窗什么都没改就返回：不算差异，不该谎称「已同步」。 */
    @Test
    fun `unchanged draft is not a difference`() {
        assertFalse(presetDiffersFromDraft("https://a.com", "sk-1", "m", "https://a.com", "sk-1", "m"))
    }

    /** 首尾空白差异不算改动：本页草稿 trim 后与存储原值本就可能不同，否则每次返回都误报同步。 */
    @Test
    fun `whitespace-only difference is ignored`() {
        assertFalse(presetDiffersFromDraft("  https://a.com ", "sk-1\n", " m ", "https://a.com", "sk-1", "m"))
    }

    /** 地址或 Key 改了同样要同步（预设是整套连接的快照）。 */
    @Test
    fun `url and key changes count`() {
        assertTrue(presetDiffersFromDraft("https://a.com", "sk-1", "m", "https://b.com", "sk-1", "m"))
        assertTrue(presetDiffersFromDraft("https://a.com", "sk-1", "m", "https://a.com", "sk-2", "m"))
    }

    /** 修图协议只有绘图预设参与比较；LLM 档案不传协议时不得误判。 */
    @Test
    fun `edit mode compares only when both sides provide it`() {
        assertTrue(presetDiffersFromDraft("u", "k", "m", "u", "k", "m", "edits_multipart", "generations_image"))
        assertFalse(presetDiffersFromDraft("u", "k", "m", "u", "k", "m", "edits_multipart"))
        assertFalse(presetDiffersFromDraft("u", "k", "m", "u", "k", "m"))
    }
}
