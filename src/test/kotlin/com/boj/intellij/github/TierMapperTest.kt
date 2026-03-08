package com.boj.intellij.github

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TierMapperTest {

    @Test
    fun `SVG 0 is Unrated`() {
        assertEquals("Unrated", TierMapper.tierName(0))
        assertEquals(0, TierMapper.tierNum(0))
    }

    @Test
    fun `SVG 1 is Bronze 5`() {
        assertEquals("Bronze", TierMapper.tierName(1))
        assertEquals(5, TierMapper.tierNum(1))
    }

    @Test
    fun `SVG 5 is Bronze 1`() {
        assertEquals("Bronze", TierMapper.tierName(5))
        assertEquals(1, TierMapper.tierNum(5))
    }

    @Test
    fun `SVG 6 is Silver 5`() {
        assertEquals("Silver", TierMapper.tierName(6))
        assertEquals(5, TierMapper.tierNum(6))
    }

    @Test
    fun `SVG 11 is Gold 5`() {
        assertEquals("Gold", TierMapper.tierName(11))
        assertEquals(5, TierMapper.tierNum(11))
    }

    @Test
    fun `SVG 15 is Gold 1`() {
        assertEquals("Gold", TierMapper.tierName(15))
        assertEquals(1, TierMapper.tierNum(15))
    }

    @Test
    fun `SVG 16 is Platinum 5`() {
        assertEquals("Platinum", TierMapper.tierName(16))
        assertEquals(5, TierMapper.tierNum(16))
    }

    @Test
    fun `SVG 21 is Diamond 5`() {
        assertEquals("Diamond", TierMapper.tierName(21))
        assertEquals(5, TierMapper.tierNum(21))
    }

    @Test
    fun `SVG 26 is Ruby 5`() {
        assertEquals("Ruby", TierMapper.tierName(26))
        assertEquals(5, TierMapper.tierNum(26))
    }

    @Test
    fun `SVG 30 is Ruby 1`() {
        assertEquals("Ruby", TierMapper.tierName(30))
        assertEquals(1, TierMapper.tierNum(30))
    }

    @Test
    fun `invalid SVG returns null`() {
        assertNull(TierMapper.tierName(-1))
        assertNull(TierMapper.tierName(31))
    }
}
