package com.lo.imagine.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageUtilsTimeTest {
    @Test
    fun timestampFormatContainsDateAndMinute() {
        val value = ImageUtils.formatTimestamp(System.currentTimeMillis())
        assertTrue(value.matches(Regex("\\d{2}-\\d{2} \\d{2}:\\d{2}")))
    }

    @Test
    fun zeroTimestampIsNotRenderedAsARealTime() {
        assertEquals("", ImageUtils.formatTimestamp(0L))
    }
}