package com.lo.imagine.ui

import com.lo.imagine.ui.launch.filmSurfaceSize
import org.junit.Assert.*
import org.junit.Test

class OpeningSessionTest {
    @Test fun `full film covers phones landscape and tablets without distortion`() {
        for ((width, height) in listOf(360 to 800, 390 to 844, 720 to 1280, 1272 to 2800, 1280 to 720, 1600 to 2560)) {
            val (surfaceWidth, surfaceHeight) = filmSurfaceSize(width, height, 720f, 1280f)
            assertTrue(surfaceWidth >= width)
            assertTrue(surfaceHeight >= height)
            assertEquals("face proportions must match the source",
                720f / 1280f, surfaceWidth.toFloat() / surfaceHeight, .02f)
        }
    }

    @Test fun `source frame rate drives the render tick`() {
        assertEquals("render cadence must match the 24fps source", 42, 1000 / com.lo.imagine.ui.launch.OpeningFilmFrames.SOURCE_FPS + 1)
        assertEquals(24, com.lo.imagine.ui.launch.OpeningFilmFrames.SOURCE_FPS)
    }

    @Test fun `opening plays the first five seconds of the film`() {
        assertEquals(5_000, com.lo.imagine.ui.launch.OpeningFilmFrames.TOTAL_MS)
    }

    @Test fun `the ending is deterministic on the 120th frame`() {
        assertEquals(120, com.lo.imagine.ui.launch.OpeningFilmView.TOTAL_FRAMES)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unmeasured surfaces never produce an invalid film layout`() {
        filmSurfaceSize(0, 1280, 720f, 1280f)
    }
}
