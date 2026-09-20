package com.lo.imagine.ui.theme
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.lo.imagine.data.ThemeMode
import com.lo.imagine.data.UiMood
import org.junit.Assert.*
import org.junit.Test

/** Geometry is mood-only; day/night selects colors, not a second icon system. */
class IconAppearanceTest {
    @Test fun `cool white uses the light baseline for every component tier`() {
        assertEquals(RoundedCornerShape(10.dp), moodShape(UiMood.COOL_WHITE, PopRadius.chip))
        for (tier in listOf(PopRadius.field, PopRadius.card, PopRadius.sheet)) {
            assertEquals(RoundedCornerShape(tier), moodShape(UiMood.COOL_WHITE, tier))
            assertEquals(CircleShape, moodShape(UiMood.COOL_WHITE, tier, pill = true))
        }
    }

    @Test fun `mood identity is retained without a night-only shape branch`() {
        for (tier in listOf(PopRadius.chip, PopRadius.field, PopRadius.card, PopRadius.sheet)) {
            assertTrue(moodShape(UiMood.DARK_TACTIC, tier) is ArkCutShape)
            assertTrue(moodShape(UiMood.DARK_TACTIC, tier, pill = true) is ArkCutShape)
        }
        assertEquals(RoundedCornerShape(14.dp), moodShape(UiMood.SOFT_ILLUST, PopRadius.chip))
        assertEquals(RoundedCornerShape(20.dp), moodShape(UiMood.SOFT_ILLUST, PopRadius.card))
        assertEquals(CircleShape, moodShape(UiMood.SOFT_ILLUST, PopRadius.chip, pill = true))
    }

    @Test fun `all six palettes meet icon contrast on shared control surfaces`() {
        for (mode in ThemeMode.entries) for (mood in UiMood.entries) {
            val c = themeColors(mode, mood)
            val surfaces = listOf(c.background, c.surface, c.surfaceContainer, c.surfaceContainerLow)
            for (surface in surfaces) {
                for (ink in listOf(c.onSurface, c.onSurfaceVariant, c.primary)) {
                    assertContrast("$mode/$mood normal icon", ink, surface, 3f)
                }
            }
            assertContrast("$mode/$mood active tile", c.primary, c.primaryContainer, 3f)
            assertContrast("$mode/$mood filled control", c.onPrimary, c.primary, 3f)
            assertContrast("$mode/$mood dialog marker", c.onErrorContainer, c.errorContainer, 4.5f)
        }
    }

    @Test fun `dock icon contrast holds over the composed selected gradient`() {
        for (mode in ThemeMode.entries) for (mood in UiMood.entries) {
            val c = themeColors(mode, mood)
            for (fraction in listOf(0f, .25f, .5f, .75f, 1f)) {
                val surface = c.primaryContainer.copy(alpha = .15f + .85f * fraction).compositeOver(c.background)
                assertContrast("$mode/$mood selected dock", c.primary, surface, 3f)
            }
            assertContrast("$mode/$mood inactive dock", c.primary, c.background, 3f)
        }
    }

    @Test fun `preview icon scrim remains legible over the brightest image`() {
        val surface = Color.Black.copy(alpha = .56f).compositeOver(Color.White)
        assertContrast("preview white icon", Color.White, surface, 4.5f)
    }

    private fun assertContrast(label: String, foreground: Color, background: Color, minimum: Float) {
        val a = foreground.compositeOver(background).luminance()
        val b = background.luminance()
        val ratio = (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
        assertTrue("$label: $ratio < $minimum", ratio >= minimum)
    }
}
