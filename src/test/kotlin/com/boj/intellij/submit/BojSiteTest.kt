package com.boj.intellij.submit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BojSiteTest {

    @Test
    fun `recognizes boj urls with or without www`() {
        assertTrue(BojSite.isBojUrl("https://acmicpc.net/submit/1000"))
        assertTrue(BojSite.isBojUrl("https://acmicpc.net:443/submit/1000"))
        assertTrue(BojSite.isBojUrl("https://www.acmicpc.net/status?user_id=a"))
        assertTrue(BojSite.isBojUrl("https://ACMICPC.NET/"))
    }

    @Test
    fun `rejects non boj urls`() {
        assertFalse(BojSite.isBojUrl(null))
        assertFalse(BojSite.isBojUrl(""))
        assertFalse(BojSite.isBojUrl("about:blank"))
        assertFalse(BojSite.isBojUrl("https://example.com/acmicpc.net/submit/1000"))
        assertFalse(BojSite.isBojUrl("https://acmicpc.net.example.com/"))
    }

    @Test
    fun `parses login state from page`() {
        assertEquals(BojSite.LoginState(false, null), BojSite.parseLoginState("LOGGED_OUT"))
        assertEquals(BojSite.LoginState(true, "putdata"), BojSite.parseLoginState("LOGGED_IN:putdata"))
        assertEquals(BojSite.LoginState(true, null), BojSite.parseLoginState("LOGGED_IN:"))
        assertNull(BojSite.parseLoginState(null))
        assertNull(BojSite.parseLoginState("something else"))
    }

    @Test
    fun `cookie lookup prefers current host`() {
        assertEquals("https://acmicpc.net", BojSite.COOKIE_URLS.first())
    }
}
