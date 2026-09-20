package com.lo.imagine.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import com.lo.imagine.data.ASPECT_OPTIONS
import org.junit.Assert.*
import org.junit.Test

class ReferenceUiTest {
    private fun allIcons(): List<ImageVector> = RefIcons::class.java.declaredMethods
        .filter { it.parameterCount == 0 && it.returnType == ImageVector::class.java }
        .map { it.invoke(RefIcons) as ImageVector }

    @Test fun `all 53 reference glyphs parse at the same 24 unit viewport`() {
        val icons = allIcons()
        assertEquals(53, icons.size)
        assertEquals(53, icons.map { it.name }.toSet().size)
        icons.forEach { icon ->
            assertEquals(24.dp, icon.defaultWidth)
            assertEquals(24.dp, icon.defaultHeight)
            assertEquals(24f, icon.viewportWidth, 0f)
            assertEquals(24f, icon.viewportHeight, 0f)
            assertTrue(icon.root.size > 0)
            for (index in 0 until icon.root.size) {
                assertTrue((icon.root[index] as VectorPath).pathData.isNotEmpty())
            }
        }
    }

    @Test fun `outline paths use one round stroke grammar without hidden solid fill`() {
        allIcons().forEach { icon ->
            for (index in 0 until icon.root.size) {
                val path = icon.root[index] as VectorPath
                if (path.stroke != null) {
                    assertEquals(icon.name, 1.7f, path.strokeLineWidth, 0f)
                    assertEquals(StrokeCap.Round, path.strokeLineCap)
                    assertEquals(StrokeJoin.Round, path.strokeLineJoin)
                    assertNull("${icon.name} outline must not be filled", path.fill)
                } else {
                    assertNotNull("${icon.name} solid symbol must have a fill", path.fill)
                }
            }
        }
    }

    @Test fun `legacy symbols resolve directly to the new family`() {
        assertSame(RefIcons.Settings, ArkGear)
        assertSame(RefIcons.Crop, ArkRetouch)
        assertSame(RefIcons.Translate, themedIcon(PopTranslate))
        assertSame(RefIcons.Film, themedIcon(PopFilm))
        allIcons().forEach { assertSame(it, themedIcon(it)) }
    }

    @Test fun `reference touch and icon dimensions remain stable`() {
        assertEquals(22.dp, RefUiTokens.tileIcon)
        assertEquals(22.dp, RefUiTokens.dockIcon)
        assertEquals(58.dp, RefUiTokens.tileHeight)
        assertEquals(56.dp, RefUiTokens.actionHeight)
        assertEquals(78.dp, RefUiTokens.dockHeight)
        assertTrue(RefUiTokens.controlHeight >= 48.dp)
    }

    @Test fun `aspect frame preserves every supported ratio and leaves stroke clearance`() {
        for (aspect in ASPECT_OPTIONS) {
            val ratio = aspect.ratioW / aspect.ratioH
            for ((width, height) in listOf(22f to 22f, 27f to 20f)) {
                val frame = fitAspectFrame(ratio, width, height, 1.55f)
                assertEquals(aspect.label, ratio, frame.width / frame.height, .00001f)
                assertTrue(frame.width + 1.55f <= width + .0001f)
                assertTrue(frame.height + 1.55f <= height + .0001f)
                assertTrue(frame.width > 0f && frame.height > 0f)
            }
        }
    }

    @Test fun `square aspect really is square`() {
        val square = fitAspectFrame(1f, 27f, 20f, 1.55f)
        assertEquals(square.width, square.height, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid aspect is rejected rather than disguised`() {
        fitAspectFrame(0f, 22f, 22f, 1.55f)
    }

    @Test fun `glass action labels and arrow retain contrast in every theme and mood`() {
        for (mode in com.lo.imagine.data.ThemeMode.entries) {
            for (mood in com.lo.imagine.data.UiMood.entries) {
                val colors = com.lo.imagine.ui.theme.themeColors(mode, mood)
                val palette = frostedActionPalette(colors)
                for (step in 0..20) {
                    val gradient = if (step <= 10) lerp(palette.top, palette.body, step / 10f)
                        else lerp(palette.body, palette.bottom, (step - 10) / 10f)
                    for (behind in listOf(colors.background, colors.surface, colors.primaryContainer)) {
                        for (glint in listOf(0f, .36f)) {
                        val base = palette.shine.copy(alpha = glint * palette.shine.alpha)
                            .compositeOver(gradient.compositeOver(behind))
                        for (pressed in listOf(false, true)) {
                            val surface = if (pressed) palette.edge.copy(alpha = .09f).compositeOver(base) else base
                            assertContrast("glass title $mode $mood/$step", palette.ink, surface, 4.5f)
                            assertContrast("glass subtitle $mode $mood/$step", palette.ink.copy(alpha = .82f), surface, 4.5f)
                            assertContrast("glass arrow $mode $mood/$step", palette.edge, surface, 3f)
                        }
                        }
                    }
                }
            }
        }
    }
    @Test fun `header action capsules share the mode switch skin`() {
        val source = java.io.File("src/main/java/com/lo/imagine/ui/ArkRefUi.kt").readText()
        assertFalse("header action must drop the hardcoded reference blue", source.contains("0xFF35495F"))
        assertTrue("header action must share the translucent mode-switch skin",
            source.contains("color = c.surface.copy(alpha = .82f)") &&
            source.contains("BorderStroke(.7.dp, c.outlineVariant)"))
        assertTrue("header action glyph must use the theme primary",
            source.contains("tint = c.primary"))
        assertFalse("header action must not force an oversized minimum width",
            source.contains("widthIn(min = 98.dp"))
        val nai = java.io.File("src/main/java/com/lo/imagine/ui/studio/NaiWorkspaceScreen.kt").readText()
        assertTrue("nai workspace header must use the same action capsule as home",
            nai.contains("ArkBlockAction("))
    }

    @Test fun `preset pickers use the app ink bordered dialog language`() {
        val ui = java.io.File("src/main/java/com/lo/imagine/ui/ArkRefUi.kt").readText()
        assertTrue("shared preset dialog must exist", ui.contains("fun ArkPresetDialog("))
        assertTrue("dialog must use theme background with the ink border like the preset library",
            ui.contains("color = MaterialTheme.colorScheme.background") &&
            ui.contains("BorderStroke(2.dp, celInk())"))
        val home = java.io.File("src/main/java/com/lo/imagine/ui/studio/StudioScreen.kt").readText()
        val nai = java.io.File("src/main/java/com/lo/imagine/ui/studio/NaiWorkspaceScreen.kt").readText()
        assertFalse("stock alert dialog must not serve the home preset list",
            home.contains("PopAlertDialog(\n                title = \"切换模型预设\""))
        assertFalse("stock alert dialog must not serve the nai preset list",
            nai.contains("PopAlertDialog(\n                title = \"NAI 通道预设\""))
        assertTrue("both pages must render their lists in the shared dialog",
            home.contains("ArkPresetDialog(") && nai.contains("ArkPresetDialog("))
    }

    @Test fun `preset dialogs keep the yellow secondary pair out`() {
        val home = java.io.File("src/main/java/com/lo/imagine/ui/studio/StudioScreen.kt").readText()
        val nai = java.io.File("src/main/java/com/lo/imagine/ui/studio/NaiWorkspaceScreen.kt").readText()
        assertFalse("home preset rows must not use the yellow secondary container",
            home.contains("secondaryContainer"))
        assertFalse("nai preset rows must not use the yellow secondary container",
            nai.contains("secondaryContainer"))
        assertTrue("home selection must use the shared primaryContainer language",
            home.contains("color = if (selected) MaterialTheme.colorScheme.primaryContainer"))
        assertTrue("nai selection must use the shared primaryContainer language",
            nai.contains("color = if (selected) MaterialTheme.colorScheme.primaryContainer"))
        val ui = java.io.File("src/main/java/com/lo/imagine/ui/ArkRefUi.kt").readText()
        assertTrue("shared dialog shell must stay theme-backed",
            ui.contains("color = MaterialTheme.colorScheme.background") &&
            ui.contains("BorderStroke(2.dp, celInk())"))
    }

    private fun assertContrast(label: String, foreground: Color, background: Color, minimum: Float) {
        val a = foreground.compositeOver(background).luminance()
        val b = background.luminance()
        val ratio = (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
        assertTrue("$label: $ratio < $minimum", ratio >= minimum)
    }
}