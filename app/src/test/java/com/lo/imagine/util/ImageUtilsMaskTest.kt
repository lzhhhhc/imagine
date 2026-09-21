package com.lo.imagine.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun editOutputFrameStaysExactEvenWhenTheModelChangesAspect() {
        // 方图改竖图：放大后裁左右，上下不裁
        val portrait = ImageUtils.coverFrame(1024, 1024, 1024, 1792)
        assertTrue(portrait.coverWidth >= 1024 && portrait.coverHeight >= 1792)
        assertTrue(portrait.cropX > 0)
        assertEquals(0, portrait.cropY)
        assertTrue(portrait.cropX + 1024 <= portrait.coverWidth)
        assertTrue(portrait.cropY + 1792 <= portrait.coverHeight)
        // 上游竖图改横图：放大后裁上下，左右不裁
        val landscape = ImageUtils.coverFrame(608, 1088, 1792, 1024)
        assertTrue(landscape.coverWidth >= 1792 && landscape.coverHeight >= 1024)
        assertEquals(0, landscape.cropX)
        assertTrue(landscape.cropY > 0)
        assertTrue(landscape.cropX + 1792 <= landscape.coverWidth)
        assertTrue(landscape.cropY + 1024 <= landscape.coverHeight)
        val exact = ImageUtils.coverFrame(1024, 1792, 1024, 1792)
        assertEquals(1024, exact.coverWidth)
        assertEquals(1792, exact.coverHeight)
        assertEquals(0, exact.cropX)
        assertEquals(0, exact.cropY)
    }
}
