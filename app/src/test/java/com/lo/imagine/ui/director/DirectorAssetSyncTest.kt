package com.lo.imagine.ui.director

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * 导演台素材互通契约：输入框上方的「人物/环境」选择弹窗与右上角「素材库」管理弹窗
 * 必须共享同一份素材快照——素材库里的增删要即时反映到选择弹窗，反之亦然。
 */
class DirectorAssetSyncTest {
    private val source get() = File("src/main/java/com/lo/imagine/ui/director/DirectorScreen.kt").readText()

    @Test fun `material library shares the screen wide asset snapshot`() {
        assertTrue(
            "素材库弹窗必须接收页面共享的素材列表",
            source.contains("assets: List<DirectorAsset>")
        )
        assertTrue(
            "素材库弹窗必须通过页面统一的持久化入口写回",
            source.contains("onPersist: (List<DirectorAsset>) -> Unit")
        )
        assertFalse(
            "素材库弹窗不得再私自加载第二份快照",
            source.contains("var assets by remember { mutableStateOf<List<DirectorAsset>>(emptyList()) }\n    var tab")
        )
    }

    @Test fun `page loads the asset snapshot exactly once and feeds both entries`() {
        // 页面级只允许一处 loadDirectorAssets（进页时的一次性加载）
        assertEquals(1, Regex("loadDirectorAssets\\(").findAll(source).count())
        // 右上角素材库入口必须把共享快照与统一落盘函数传进弹窗
        assertTrue(source.contains("assets = assets,"))
        assertTrue(source.contains("onPersist = { persistAssets(it) }"))
    }
}