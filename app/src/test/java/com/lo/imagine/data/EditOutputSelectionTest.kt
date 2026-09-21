package com.lo.imagine.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EditOutputSelectionTest {
    private val square = ASPECT_OPTIONS.first { it.label == "1:1" }
    private val portrait = ASPECT_OPTIONS.first { it.label == "9:16" }
    private val edge = QUALITY_TIERS.first { it.id == "high" }.longEdge

    @Test
    fun `the first picture sets the output size`() {
        val kept = retainEditOutput(false, "1:1", "1024x1024", portrait, edge)
        assertEquals("9:16", kept.aspectLabel)
        assertEquals(portrait.sizeFor(edge), kept.size)
        assertEquals(true, kept.locked)
    }

    @Test
    fun `another picture does not replace a chosen size`() {
        val chosen = square.sizeFor(edge)
        val kept = retainEditOutput(true, "1:1", chosen, portrait, edge)
        assertEquals("1:1", kept.aspectLabel)
        assertEquals(chosen, kept.size)
        assertEquals(true, kept.locked)
    }
}
