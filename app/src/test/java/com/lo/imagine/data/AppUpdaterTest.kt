package com.lo.imagine.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** 版本比较、资产挑选与更新说明清洗：更新功能的纯逻辑部分。 */
class AppUpdaterTest {

    @Test fun `version comparison ignores v prefix and suffix`() {
        assertEquals(0, compareVersion("v1.0", "1.0"))
        assertEquals(0, compareVersion("1.0.0", "v1.0"))
        assertTrue(compareVersion("1.1", "1.0") > 0)
        assertTrue(compareVersion("v2.0", "1.9.9") > 0)
        assertTrue(compareVersion("1.0", "1.0.1") < 0)
        // 预发布后缀不参与比较：1.0-beta 视为 1.0
        assertEquals(0, compareVersion("1.0-beta", "1.0"))
        assertEquals(0, compareVersion("v1.2.3+build7", "1.2.3"))
        // 位数不同时按 0 补齐，不能因为段数少就判小
        assertEquals(0, compareVersion("1.0", "1.0.0"))
        assertTrue(compareVersion("1.10", "1.9") > 0)
    }

    @Test fun `apk asset is picked case insensitively and missing one is reported`() {
        val assets = listOf(
            ReleaseAsset(name = "notes.txt", downloadUrl = "https://x/notes.txt", size = 10),
            ReleaseAsset(name = "Imagine-1.1.APK", downloadUrl = "https://x/a.apk", size = 100)
        )
        val picked = pickApkAsset(assets)
        assertNotNull(picked)
        assertEquals("https://x/a.apk", picked!!.downloadUrl)

        assertNull(pickApkAsset(null))
        assertNull(pickApkAsset(emptyList()))
        assertNull(pickApkAsset(listOf(ReleaseAsset(name = "notes.txt"))))
    }

    @Test fun `release notes are flattened to plain text`() {
        val md = """
            ## 绘世 Imagine 1.1

            ### 内容

            | 模块 | 说明 |
            | --- | --- |
            | 设置 | 新增应用更新 |

            - 修复：**弹窗**颜色
            ---
        """.trimIndent()
        val plain = releaseNotesToPlainText(md)
        assertFalse("标题符号必须去掉", plain.contains("##"))
        assertFalse("强调标记必须去掉", plain.contains("**"))
        assertFalse("表格分隔线必须去掉", plain.contains("--- | ---"))
        assertTrue(plain.contains("绘世 Imagine 1.1"))
        assertTrue(plain.contains("设置 · 新增应用更新"))
        assertTrue(plain.contains("修复：弹窗颜色"))
    }

    @Test fun `empty notes do not crash the dialog`() {
        assertEquals("", releaseNotesToPlainText(""))
        assertEquals("", releaseNotesToPlainText("   \n\n  "))
    }

    @Test fun `published date is trimmed to the day`() {
        assertEquals("2026-09-20", formatPublishedDate("2026-09-20T12:10:53Z"))
        assertEquals("", formatPublishedDate(""))
        assertEquals("weird", formatPublishedDate("weird"))
    }

    @Test fun `update source points at the public release feed`() {
        assertEquals("lzhhhhc/imagine", UpdateSource.REPO)
        assertTrue(UpdateSource.LATEST_API.startsWith("https://api.github.com/repos/"))
        assertTrue(UpdateSource.LATEST_API.endsWith("/releases/latest"))
        assertFalse("下载地址不能写死到某个版本", UpdateSource.LATEST_API.contains("/download/"))
    }

    @Test fun `settings screen wires the update card into about`() {
        val screen = File("src/main/java/com/lo/imagine/ui/settings/SettingsScreen.kt").readText()
        assertTrue("更新卡必须出现在关于分区", screen.contains("UpdateCard()"))
        val card = File("src/main/java/com/lo/imagine/ui/settings/UpdateCard.kt").readText()
        assertTrue("必须展示本次更新内容", card.contains("本次更新内容"))
        assertTrue("必须支持下载并安装", card.contains("下载并安装"))
        assertTrue("必须复用应用自有弹窗样式", card.contains("BorderStroke(2.dp, celInk())"))
        assertFalse("弹窗内不得使用黄色 secondary 色对", card.contains("secondaryContainer"))
    }

    @Test fun `apk path is exposed to the system installer through file provider`() {
        val paths = File("src/main/res/xml/file_paths.xml").readText()
        assertTrue("必须把 cache/apk 暴露给 FileProvider", paths.contains("path=\"apk/\""))
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue("安装器需要 INTERNET 拉取 release", manifest.contains("android.permission.INTERNET"))
        // 回归锁：canRequestPackageInstalls() 缺少该权限会直接抛 SecurityException 把应用打崩
        assertTrue(
            "必须声明 REQUEST_INSTALL_PACKAGES，否则权限查询会崩",
            manifest.contains("android.permission.REQUEST_INSTALL_PACKAGES")
        )
    }

    @Test fun `permission probe never crashes the app`() {
        val src = File("src/main/java/com/lo/imagine/data/AppUpdater.kt").readText()
        assertTrue(
            "canInstallPackages 必须用 runCatching 兜住 SecurityException",
            src.contains("fun canInstallPackages(): Boolean = runCatching {")
        )
        assertTrue(
            "兜底值必须是「未授权」而不是「已授权」",
            src.contains(".getOrDefault(false)")
        )
    }

    @Test fun `install flow degrades to a permission prompt instead of crashing`() {
        val card = File("src/main/java/com/lo/imagine/ui/settings/UpdateCard.kt").readText()
        assertTrue("必须有「待授权」状态", card.contains("NEED_PERMISSION"))
        assertTrue(
            "未授权时应引导用户去设置页",
            card.contains("Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES")
        )
        assertTrue(
            "跳转设置页失败要有兜底，不能抛异常",
            card.contains("Settings.ACTION_APPLICATION_DETAILS_SETTINGS")
        )
        assertTrue("应保留已下载的安装包以便重试", card.contains("var apk by remember"))
        assertTrue(
            "重试安装不得重新下载",
            card.contains("UpdatePhase.FAILED -> if (apk != null) tryInstall")
        )
    }
}