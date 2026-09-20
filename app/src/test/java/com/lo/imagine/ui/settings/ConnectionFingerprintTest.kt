package com.lo.imagine.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 连接参数指纹：设置页用它区分「存储被本页之外改过」与「本页自己刚保存的回显」。
 * 判错任一方向都会出问题——漏判会把用户刚切的预设写回旧值，误判会打断正在输入的字段。
 */
class ConnectionFingerprintTest {

    /** 只差首尾空格不算改动：否则本页保存与存储回显会来回互相覆盖。 */
    @Test
    fun `leading and trailing spaces are ignored`() {
        assertEquals(
            connectionFingerprint("https://a.com", "sk-1", "m", "generations_image"),
            connectionFingerprint("  https://a.com ", "sk-1\n", " m ", "generations_image ")
        )
    }

    /** 四段任意一段变化都必须产生不同指纹（URL/Key/模型/协议都要能被检测到）。 */
    @Test
    fun `each field change is detected`() {
        val base = connectionFingerprint("https://a.com", "sk-1", "m", "generations_image")
        assertNotEquals(base, connectionFingerprint("https://b.com", "sk-1", "m", "generations_image"))
        assertNotEquals(base, connectionFingerprint("https://a.com", "sk-2", "m", "generations_image"))
        assertNotEquals(base, connectionFingerprint("https://a.com", "sk-1", "m2", "generations_image"))
        assertNotEquals(base, connectionFingerprint("https://a.com", "sk-1", "m", "edits_multipart"))
    }

    /** 分段拼接不能靠字符串直接相连：否则「ab|cd」与「abc|d」会被当成同一份。 */
    @Test
    fun `field boundaries are not ambiguous`() {
        assertNotEquals(
            connectionFingerprint("ab", "cd", "", ""),
            connectionFingerprint("abc", "d", "", "")
        )
    }

    /** 相同输入必须稳定：检测逻辑依赖可重复比较。 */
    @Test
    fun `same input is stable`() {
        assertEquals(
            connectionFingerprint("https://a.com", "sk-1", "m", "generations_image"),
            connectionFingerprint("https://a.com", "sk-1", "m", "generations_image")
        )
    }
}