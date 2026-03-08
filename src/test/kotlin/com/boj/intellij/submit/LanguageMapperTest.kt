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

    @Test
    fun `toExtension maps BOJ language names to file extensions`() {
        assertEquals("java", LanguageMapper.toExtension("Java 11"))
        assertEquals("java", LanguageMapper.toExtension("Java 8"))
        assertEquals("java", LanguageMapper.toExtension("Java 15"))
        assertEquals("java", LanguageMapper.toExtension("Java 8 (OpenJDK)"))
        assertEquals("kt", LanguageMapper.toExtension("Kotlin (JVM)"))
        assertEquals("kt", LanguageMapper.toExtension("Kotlin (Native)"))
        assertEquals("py", LanguageMapper.toExtension("Python 3"))
        assertEquals("py", LanguageMapper.toExtension("PyPy3"))
        assertEquals("cpp", LanguageMapper.toExtension("C++17"))
        assertEquals("cpp", LanguageMapper.toExtension("C++14 (Clang)"))
        assertEquals("c", LanguageMapper.toExtension("C99"))
        assertEquals("c", LanguageMapper.toExtension("C11 (Clang)"))
        assertEquals("js", LanguageMapper.toExtension("node.js"))
        assertEquals("go", LanguageMapper.toExtension("Go"))
        assertEquals("rs", LanguageMapper.toExtension("Rust 2021"))
        assertEquals("ts", LanguageMapper.toExtension("TypeScript"))
        assertEquals("rb", LanguageMapper.toExtension("Ruby"))
        assertEquals("swift", LanguageMapper.toExtension("Swift"))
        assertEquals("cs", LanguageMapper.toExtension("C#"))
        assertEquals("sh", LanguageMapper.toExtension("Bash"))
        assertEquals("txt", LanguageMapper.toExtension("Text"))
        assertNull(LanguageMapper.toExtension("UnknownLanguage"))
    }
}
