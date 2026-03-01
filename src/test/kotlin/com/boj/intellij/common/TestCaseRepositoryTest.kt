package com.boj.intellij.common

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class TestCaseRepositoryTest {

    @TempDir
    lateinit var baseDir: File

    @Test
    fun `save and load single custom case`() {
        val repo = TestCaseRepository(baseDir, TestCaseRepositoryConfig.CUSTOM)

        val case = TestCase(input = "5\n3 1 2 5 4", expectedOutput = "1 2 3 4 5")
        repo.save("1000", "기본 테스트", case)

        // .in/.out 파일이 생성되었는지 확인
        val inFile = File(baseDir, "custom-cases/1000/기본 테스트.in")
        val outFile = File(baseDir, "custom-cases/1000/기본 테스트.out")
        assertTrue(inFile.exists())
        assertTrue(outFile.exists())
        assertEquals("5\n3 1 2 5 4", inFile.readText())
        assertEquals("1 2 3 4 5", outFile.readText())

        val loaded = repo.load("1000")
        assertEquals(1, loaded.size)
        assertEquals("5\n3 1 2 5 4", loaded["기본 테스트"]?.input)
        assertEquals("1 2 3 4 5", loaded["기본 테스트"]?.expectedOutput)
    }

    @Test
    fun `save case with null expectedOutput`() {
        val repo = TestCaseRepository(baseDir, TestCaseRepositoryConfig.CUSTOM)

        val case = TestCase(input = "hello", expectedOutput = null)
        repo.save("2000", "디버깅용", case)

        // .out 파일이 생성되지 않아야 함
        val inFile = File(baseDir, "custom-cases/2000/디버깅용.in")
        val outFile = File(baseDir, "custom-cases/2000/디버깅용.out")
        assertTrue(inFile.exists())
        assertFalse(outFile.exists())

        val loaded = repo.load("2000")
        assertEquals("hello", loaded["디버깅용"]?.input)
        assertNull(loaded["디버깅용"]?.expectedOutput)
    }

    @Test
    fun `load returns empty map for nonexistent problem`() {
        val repo = TestCaseRepository(baseDir, TestCaseRepositoryConfig.CUSTOM)

        val loaded = repo.load("9999")
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `delete removes case files`() {
        val repo = TestCaseRepository(baseDir, TestCaseRepositoryConfig.CUSTOM)

        repo.save("3000", "삭제할케이스", TestCase("input", "output"))
        repo.delete("3000", "삭제할케이스")

        val inFile = File(baseDir, "custom-cases/3000/삭제할케이스.in")
        val outFile = File(baseDir, "custom-cases/3000/삭제할케이스.out")
        assertFalse(inFile.exists())
        assertFalse(outFile.exists())

        val loaded = repo.load("3000")
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `nextAutoName generates incrementing names for custom`() {
        val repo = TestCaseRepository(baseDir, TestCaseRepositoryConfig.CUSTOM)

        assertEquals("커스텀 1", repo.nextAutoName("4000"))
        repo.save("4000", "커스텀 1", TestCase("a", null))
        assertEquals("커스텀 2", repo.nextAutoName("4000"))
        repo.save("4000", "커스텀 2", TestCase("b", null))
        assertEquals("커스텀 3", repo.nextAutoName("4000"))
    }

    @Test
    fun `nextAutoName generates incrementing names for general`() {
        val repo = TestCaseRepository(baseDir, TestCaseRepositoryConfig.GENERAL)

        assertEquals("1", repo.nextAutoName("main.py"))
        repo.save("main.py", "1", TestCase("a", "b"))
        assertEquals("2", repo.nextAutoName("main.py"))
    }

    @Test
    fun `general config loads missing output as empty string`() {
        val repo = TestCaseRepository(baseDir, TestCaseRepositoryConfig.GENERAL)

        // .in 파일만 직접 생성 (.out 없이)
        val dir = File(baseDir, "general/main.py")
        dir.mkdirs()
        File(dir, "1.in").writeText("hello")

        val loaded = repo.load("main.py")
        assertEquals("hello", loaded["1"]?.input)
        assertEquals("", loaded["1"]?.expectedOutput)
    }

    @Test
    fun `sanitizeFileName replaces invalid characters`() {
        assertEquals("test_case", TestCaseRepository.sanitizeFileName("test/case"))
        assertEquals("a_b_c", TestCaseRepository.sanitizeFileName("a\\b:c"))
    }
}
