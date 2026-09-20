package com.lo.imagine.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

/** The accepted light-theme glyph table is also the night-theme glyph table. */
class IconIdentityTest {
    private val baseline = listOf(
        PopHome to ArkHome,
        PopBrush to ArkBrush,
        PopSpark to ArkSpark,
        PopGallery to ArkGallery,
        PopImageAdd to ArkImageAdd,
        PopFilm to ArkFilm,
        PopFrame to ArkFrame,
        PopClock to ArkClock,
        PopSliders to ArkSliders,
        PopPalette to ArkPalette,
        PopGear to ArkGear,
        PopRetouch to ArkRetouch,
        PopEdit to ArkEdit,
        PopPencil to ArkPencil,
        PopWand to ArkWand,
        PopDownload to ArkDownload,
        PopRefresh to ArkRefresh,
        PopCopy to ArkCopy,
        PopShare to ArkShare,
        PopTrash to ArkTrash,
        PopClose to ArkClose,
        PopCheck to ArkCheck,
        PopBack to ArkBack,
        PopChevron to ArkChevron,
        PopChevronDown to ArkChevronDown,
        PopChevronUp to ArkChevronUp,
        PopAlert to ArkAlert,
        PopInfo to ArkInfo,
        PopPerson to ArkPerson,
        PopCube to ArkCube,
        PopBox to ArkBox,
        PopCircle to ArkCircle,
        PopGrid to ArkGrid,
        PopBookmark to ArkBookmark,
        PopPlug to ArkPlug,
        PopKey to ArkKey,
        PopTranslate to ArkTranslate,
        PopInspect to ArkInspect,
        PopWave to ArkWave,
        PopBolt to ArkBolt,
        PopMoon to ArkMoon,
        PopHeart to ArkHeart,
        PopHeartOutline to ArkHeartOutline
    )

    @Test fun `every existing semantic icon keeps the accepted vector identity`() {
        assertEquals(43, baseline.size)
        baseline.forEach { (input, expected) -> assertSame(input.name, expected, themedIcon(input)) }
    }

    @Test fun `mapped icons keep a fixed viewport and are not remapped again`() {
        baseline.forEach { (_, icon) ->
            assertSame(icon.name, icon, themedIcon(icon))
            assertEquals(24.dp, icon.defaultWidth)
            assertEquals(24.dp, icon.defaultHeight)
            assertEquals(24f, icon.viewportWidth, 0f)
            assertEquals(24f, icon.viewportHeight, 0f)
        }
    }

    @Test fun `day and night chooser symbols retain their distinct meaning`() {
        assertSame(Icons.Outlined.LightMode, themedIcon(Icons.Outlined.LightMode))
        assertSame(Icons.Outlined.DarkMode, themedIcon(Icons.Outlined.DarkMode))
        assertNotEquals(themedIcon(Icons.Outlined.LightMode), themedIcon(Icons.Outlined.DarkMode))
    }
}
