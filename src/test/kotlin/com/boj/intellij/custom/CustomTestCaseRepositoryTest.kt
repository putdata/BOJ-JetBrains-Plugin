package com.boj.intellij.custom

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class CustomTestCaseRepositoryTest {

    @Test
    fun `save and load single custom case`() {
        val baseDir = createTempDirectory("boj-test").toFile()
        val repo = CustomTestCaseRepository(baseDir)

        val case = CustomTestCase(input = "5\n3 1 2 5 4", expectedOutput = "1 2 3 4 5")
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
        val baseDir = createTempDirectory("boj-test").toFile()
        val repo = CustomTestCaseRepository(baseDir)

        val case = CustomTestCase(input = "hello", expectedOutput = null)
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
        val baseDir = createTempDirectory("boj-test").toFile()
        val repo = CustomTestCaseRepository(baseDir)

        val loaded = repo.load("9999")
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `delete removes case files`() {
        val baseDir = createTempDirectory("boj-test").toFile()
        val repo = CustomTestCaseRepository(baseDir)

        repo.save("3000", "삭제할케이스", CustomTestCase("input", "output"))
        repo.delete("3000", "삭제할케이스")

        val inFile = File(baseDir, "custom-cases/3000/삭제할케이스.in")
        val outFile = File(baseDir, "custom-cases/3000/삭제할케이스.out")
        assertFalse(inFile.exists())
        assertFalse(outFile.exists())

        val loaded = repo.load("3000")
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `nextAutoName generates incrementing names`() {
        val baseDir = createTempDirectory("boj-test").toFile()
        val repo = CustomTestCaseRepository(baseDir)

        assertEquals("커스텀 1", repo.nextAutoName("4000"))
        repo.save("4000", "커스텀 1", CustomTestCase("a", null))
        assertEquals("커스텀 2", repo.nextAutoName("4000"))
        repo.save("4000", "커스텀 2", CustomTestCase("b", null))
        assertEquals("커스텀 3", repo.nextAutoName("4000"))
    }

    @Test
    fun `sanitizeFileName replaces invalid characters`() {
        assertEquals("test_case", CustomTestCaseRepository.sanitizeFileName("test/case"))
        assertEquals("a_b_c", CustomTestCaseRepository.sanitizeFileName("a\\b:c"))
    }
}
