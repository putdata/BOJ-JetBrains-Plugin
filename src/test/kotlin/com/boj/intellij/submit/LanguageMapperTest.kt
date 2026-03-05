package com.boj.intellij.submit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageMapperTest {

    @Test
    fun `maps java extension to Java 11`() {
        assertEquals("Java 11", LanguageMapper.toBojLanguageName("java"))
    }

    @Test
    fun `maps kt extension to Kotlin (JVM)`() {
        assertEquals("Kotlin (JVM)", LanguageMapper.toBojLanguageName("kt"))
    }

    @Test
    fun `maps py extension to Python 3`() {
        assertEquals("Python 3", LanguageMapper.toBojLanguageName("py"))
    }

    @Test
    fun `maps cpp extension to C++17`() {
        assertEquals("C++17", LanguageMapper.toBojLanguageName("cpp"))
    }

    @Test
    fun `maps c extension to C99`() {
        assertEquals("C99", LanguageMapper.toBojLanguageName("c"))
    }

    @Test
    fun `maps js extension to node_js`() {
        assertEquals("node.js", LanguageMapper.toBojLanguageName("js"))
    }

    @Test
    fun `maps go extension to Go`() {
        assertEquals("Go", LanguageMapper.toBojLanguageName("go"))
    }

    @Test
    fun `returns null for unknown extension`() {
        assertNull(LanguageMapper.toBojLanguageName("txt"))
    }

    @Test
    fun `case insensitive matching`() {
        assertEquals("Java 11", LanguageMapper.toBojLanguageName("JAVA"))
        assertEquals("Python 3", LanguageMapper.toBojLanguageName("PY"))
    }
}
