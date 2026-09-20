package com.lo.imagine.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageUtilsMaskTest {
    @Test
    fun unpaintedMaskAreaStaysOpaque() {
        assertEquals(255, ImageUtils.maskOutputAlpha(0))
    }

    @Test
    fun fullyPaintedMaskAreaBecomesTransparent() {
        assertEquals(0, ImageUtils.maskOutputAlpha(255))
    }

    @Test
    fun partiallyPaintedMaskPreservesInverseAlpha() {
        assertEquals(127, ImageUtils.maskOutputAlpha(128))
    }

    @Test
    fun strokeAlphaIsClampedBeforeDstOutConversion() {
        assertEquals(255, ImageUtils.maskOutputAlpha(-1))
        assertEquals(0, ImageUtils.maskOutputAlpha(256))
    }
}
