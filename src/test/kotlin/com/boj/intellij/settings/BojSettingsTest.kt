package com.boj.intellij.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BojSettingsTest {

    @Test
    fun `State has default boilerplate path template`() {
        val state = BojSettings.State()
        assertEquals("{problemId}/Main.{ext}", state.boilerplatePathTemplate)
    }

    @Test
    fun `State has default boilerplate templates for four languages`() {
        val state = BojSettings.State()
        assertTrue(state.boilerplateTemplates.containsKey("java"))
        assertTrue(state.boilerplateTemplates.containsKey("py"))
        assertTrue(state.boilerplateTemplates.containsKey("cpp"))
        assertTrue(state.boilerplateTemplates.containsKey("kt"))
        assertEquals(4, state.boilerplateTemplates.size)
    }

    @Test
    fun `State boilerplate templates contain expected content`() {
        val state = BojSettings.State()
        assertTrue(state.boilerplateTemplates["java"]!!.contains("BufferedReader"))
        assertTrue(state.boilerplateTemplates["py"]!!.contains("sys.stdin"))
        assertTrue(state.boilerplateTemplates["cpp"]!!.contains("bits/stdc++.h"))
        assertTrue(state.boilerplateTemplates["kt"]!!.contains("BufferedReader"))
    }
}
