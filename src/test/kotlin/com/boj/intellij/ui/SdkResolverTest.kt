package com.boj.intellij.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SdkResolverTest {

    @Test
    fun `getAllSdks returns empty list when ProjectJdkTable is unavailable`() {
        val sdks = SdkResolver.getAllSdks()
        assertTrue(sdks.isEmpty())
    }

    @Test
    fun `filterByExtension filters java sdks`() {
        val sdks = listOf(
            SdkEntry("corretto-17", "/jdk17", "17.0.8", "JavaSDK"),
            SdkEntry("Python 3.11", "/usr/bin/python3.11", "3.11.0", "Python SDK"),
            SdkEntry("temurin-21", "/jdk21", "21.0.1", "JavaSDK"),
        )
        val javaSdks = SdkResolver.filterByExtension(sdks, "java")
        assertEquals(2, javaSdks.size)
        assertTrue(javaSdks.all { it.typeName == "JavaSDK" })
    }

    @Test
    fun `filterByExtension filters python sdks`() {
        val sdks = listOf(
            SdkEntry("corretto-17", "/jdk17", "17.0.8", "JavaSDK"),
            SdkEntry("Python 3.11", "/usr/bin/python3.11", "3.11.0", "Python SDK"),
        )
        val pythonSdks = SdkResolver.filterByExtension(sdks, "py")
        assertEquals(1, pythonSdks.size)
        assertEquals("Python SDK", pythonSdks[0].typeName)
    }

    @Test
    fun `filterByExtension returns empty for unsupported extension`() {
        val sdks = listOf(SdkEntry("corretto-17", "/jdk17", "17.0.8", "JavaSDK"))
        assertTrue(SdkResolver.filterByExtension(sdks, "js").isEmpty())
    }

    @Test
    fun `isSdkSelectableExtension returns true for java and py`() {
        assertTrue(SdkResolver.isSdkSelectableExtension("java"))
        assertTrue(SdkResolver.isSdkSelectableExtension("py"))
        assertFalse(SdkResolver.isSdkSelectableExtension("js"))
        assertFalse(SdkResolver.isSdkSelectableExtension("cpp"))
    }

    @Test
    fun `extensionToSdkTypeKey returns correct keys`() {
        assertEquals("java", SdkResolver.extensionToSdkTypeKey("java"))
        assertEquals("python", SdkResolver.extensionToSdkTypeKey("py"))
        assertNull(SdkResolver.extensionToSdkTypeKey("go"))
    }

    @Test
    fun `resolveJavaBinary returns bin java path`() {
        val path = SdkResolver.resolveJavaBinary("/usr/lib/jvm/java-17")
        assertTrue(path.contains("/bin/java"))
    }

    @Test
    fun `resolvePythonBinary returns homePath as-is`() {
        assertEquals("/usr/bin/python3.11", SdkResolver.resolvePythonBinary("/usr/bin/python3.11"))
    }
}
