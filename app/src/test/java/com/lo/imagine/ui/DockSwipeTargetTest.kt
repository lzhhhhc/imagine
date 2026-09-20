package com.lo.imagine.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 底栏左右滑动的目标页面规则。
 * 这里只锁纯映射（不碰 Compose 手势），防止以后加/减 tab 时把顺序或边界改错。
 */
class DockSwipeTargetTest {

    /** 手指左滑（dx < 0）→ 下一个 tab；右滑（dx > 0）→ 上一个 tab。 */
    @Test
    fun `left swipe goes to next tab and right swipe to previous`() {
        assertEquals("edit", dockSwipeTarget("studio", -120f))
        assertEquals("director", dockSwipeTarget("edit", -120f))
        assertEquals("studio", dockSwipeTarget("edit", 120f))
        assertEquals("works", dockSwipeTarget("settings", 120f))
    }

    /** 两端不越界：第一格右滑、最后一格左滑都不跳页。 */
    @Test
    fun `edges do not wrap around`() {
        assertNull(dockSwipeTarget("studio", 120f))
        assertNull(dockSwipeTarget("settings", -120f))
    }

    /** NAI 是「标准」的另一种模式，按第 01 格算。 */
    @Test
    fun `nai counts as the first tab`() {
        assertEquals("edit", dockSwipeTarget("nai", -120f))
        assertNull(dockSwipeTarget("nai", 120f))
    }

    @Test fun `comfy is the first dock item with studio and nai`() {
        assertEquals("edit", dockSwipeTarget("comfy", -120f))
        assertNull(dockSwipeTarget("comfy", 120f))
        assertEquals("studio", StudioMode.dockRoute("comfy"))
        assertEquals(3, StudioMode.options.size)
    }

    /** 预览页等不在底栏序列里的路由不响应滑动；零位移也不是滑动。 */
    @Test
    fun `unknown route and zero delta do nothing`() {
        assertNull(dockSwipeTarget("preview", -120f))
        assertNull(dockSwipeTarget(null, -120f))
        assertNull(dockSwipeTarget("studio", 0f))
    }
}
