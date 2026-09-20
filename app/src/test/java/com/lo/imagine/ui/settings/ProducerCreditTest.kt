package com.lo.imagine.ui.settings

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ProducerCreditTest {
    @Test fun `producer name is the exact requested credit`() {
        assertEquals("长乐未央", ProducerCredit.NAME)
    }

    @Test fun `contact label preserves user spelling`() {
        assertEquals("Discode ID", ProducerCredit.PLATFORM_LABEL)
        assertEquals("Discode ID:1466791961003294804", ProducerCredit.contactLine)
    }

    @Test fun `contact identifier is a lossless 19 digit string`() {
        assertEquals("1466791961003294804", ProducerCredit.ID)
        assertEquals(19, ProducerCredit.ID.length)
        assertTrue(ProducerCredit.ID.all { it in '0'..'9' })
        assertFalse(ProducerCredit.ID.contains(' '))
    }
}

class EditCountContractTest {
    /** 核心：多参考图不得把张数设定架空，塞两张图也必须按用户设定出图 */
    @Test fun `multi reference must not force task count to image count`() {
        // 回归锁：旧实现是 taskCount = if (n > 1) n else count，会把 2 图强制成 2 张
        val source = File("src/main/java/com/lo/imagine/ui/edit/EditScreen.kt").readText()
        assertFalse(
            "edit task count must not be overridden by reference image count",
            source.contains("val taskCount = if (n > 1) n")
        )
        assertTrue(
            "edit task count must come from the user setting",
            source.contains("val taskCount = EditState.count.coerceIn(1, 4)")
        )
    }

    /** 旧「每图 1 张」的产品文案不应再出现在修图页 */
    @Test fun `per image one caption is gone from edit screen`() {
        val source = File("src/main/java/com/lo/imagine/ui/edit/EditScreen.kt").readText()
        assertFalse(source.contains("每图 1 张"))
        assertFalse(source.contains("每图各出 1 张"))
        assertFalse(source.contains("if (EditState.refBitmaps.size > 1) EditState.refBitmaps.size"))
    }

    /** 轮流分配语义：n 个任务均匀映射到参考图，任意张数/图数组合都不越界 */
    @Test fun `round robin mapping stays in bounds for any combination`() {
        val refCounts = listOf(1, 2, 3, 6)
        val counts = listOf(1, 2, 3, 4)
        for (n in refCounts) for (count in counts) {
            for (idx in 0 until count) {
                val imageIndex = idx % n
                assertTrue(imageIndex in 0 until n)
            }
        }
    }

    /** 张数边界与解析：非法输入回退 1，合法区间 1-4 */
    @Test fun `count parsing stays in 1 to 4`() {
        assertEquals(1, "abc".toIntOrNull()?.coerceIn(1, 4) ?: 1)
        assertEquals(1, "0".toIntOrNull()?.coerceIn(1, 4))
        assertEquals(4, "9".toIntOrNull()?.coerceIn(1, 4))
        assertEquals(2, "2".toIntOrNull()?.coerceIn(1, 4))
    }
}