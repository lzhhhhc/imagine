package com.lo.imagine.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EditOutputSelectionTest {
    @Test
    fun `default edit quality is 1_5k not the stale 1024 square`() {
        assertEquals("1536x1536", editOutputPixels("1:1", "high"))
    }

    @Test
    fun `pixels follow the selected aspect and quality`() {
        val portrait = ASPECT_OPTIONS.first { it.label == "9:16" }
        assertEquals(portrait.sizeFor(2048), editOutputPixels("9:16", "master"))
    }

    @Test
    fun `unknown labels fall back to the same defaults as the edit screen`() {
        assertEquals(editOutputPixels("1:1", "high"), editOutputPixels("nope", "missing"))
    }
}
