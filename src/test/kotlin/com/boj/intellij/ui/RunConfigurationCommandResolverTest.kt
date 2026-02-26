package com.boj.intellij.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RunConfigurationCommandResolverTest {

    @Test
    fun `resolveCommandFromFilePath returns python3 command for py files`() {
        val result = RunConfigurationCommandResolver.resolveCommandFromFilePath("/home/user/main.py")
        assertEquals("python3 \"/home/user/main.py\"", result)
    }

    @Test
    fun `resolveCommandFromFilePath returns java command for java files`() {
        val result = RunConfigurationCommandResolver.resolveCommandFromFilePath("/home/user/Main.java")
        assertEquals("java \"/home/user/Main.java\"", result)
    }

    @Test
    fun `resolveCommandFromFilePath returns null for unknown extension`() {
        val result = RunConfigurationCommandResolver.resolveCommandFromFilePath("/home/user/file.xyz")
        assertNull(result)
    }

    @Test
    fun `resolveCommandFromFilePath returns compiled binary path for cpp files`() {
        val result = RunConfigurationCommandResolver.resolveCommandFromFilePath("/home/user/main.cpp")
        assertEquals("\"/home/user/main\"", result)
    }

    @Test
    fun `getDisplayName returns file name for file path`() {
        val result = RunConfigurationCommandResolver.getDisplayName("/home/user/main.py")
        assertEquals("main.py", result)
    }

    @Test
    fun `getDisplayName returns input as-is when no separator`() {
        val result = RunConfigurationCommandResolver.getDisplayName("main.py")
        assertEquals("main.py", result)
    }
}
