package com.lo.imagine.ui

import com.lo.imagine.ui.launch.filmSurfaceSize
import org.junit.Assert.*
import org.junit.Test

class OpeningGeometryTest {
    @Test fun `portrait movie covers phones landscape and tablets without distortion`() {
        for ((width, height) in listOf(360 to 800, 390 to 844, 720 to 1280, 1272 to 2800, 1280 to 720, 1600 to 2560)) {
            val (surfaceWidth, surfaceHeight) = filmSurfaceSize(width, height, 720f, 1280f)
            assertTrue("must cover the viewport width", surfaceWidth >= width)
            assertTrue("must cover the viewport height", surfaceHeight >= height)
            assertEquals("face proportions must match the source",
                720f / 1280f, surfaceWidth.toFloat() / surfaceHeight, .02f)
        }
    }

    @Test fun `the poster follows the same cover geometry as the film`() {
        val (filmWidth, filmHeight) = filmSurfaceSize(390, 844, 720f, 1280f)
        val (posterWidth, posterHeight) = filmSurfaceSize(390, 844, 720f, 1280f)
        assertEquals(filmWidth, posterWidth)
        assertEquals(filmHeight, posterHeight)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unmeasured surfaces never produce an invalid film layout`() {
        filmSurfaceSize(0, 1280, 720f, 1280f)
    }
}