package com.hmv.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RangeParserTest {

    @Test
    fun `null or blank returns null`() {
        assertNull(RangeParser.parse(null))
        assertNull(RangeParser.parse(""))
        assertNull(RangeParser.parse("   "))
    }

    @Test
    fun `non bytes unit returns null`() {
        assertNull(RangeParser.parse("items=0-5"))
    }

    @Test
    fun `multi range returns null`() {
        assertNull(RangeParser.parse("bytes=0-10,20-30"))
    }

    @Test
    fun `open range bytes=start returns end -1`() {
        val r = RangeParser.parse("bytes=500-")
        assertEquals(500L, r!!.rangeStart)
        assertEquals(-1L, r.rangeEnd)
        assertNull(null)
    }

    @Test
    fun `closed range parses both ends`() {
        val r = RangeParser.parse("bytes=0-499")
        assertEquals(0L, r!!.rangeStart)
        assertEquals(499L, r.rangeEnd)
        assertEquals(false, r.isSuffix)
    }

    @Test
    fun `suffix range bytes=-N`() {
        val r = RangeParser.parse("bytes=-100")
        assertEquals(true, r!!.isSuffix)
        assertEquals(100L, r.suffixLength)
    }

    @Test
    fun `case insensitive bytes prefix`() {
        val r = RangeParser.parse("BYTES=10-20")
        assertEquals(10L, r!!.rangeStart)
        assertEquals(20L, r.rangeEnd)
    }

    @Test
    fun `reversed range returns null`() {
        assertNull(RangeParser.parse("bytes=500-100"))
    }

    @Test
    fun `negative start returns null`() {
        assertNull(RangeParser.parse("bytes=-5-10"))
    }

    @Test
    fun `non numeric returns null`() {
        assertNull(RangeParser.parse("bytes=abc-"))
    }
}