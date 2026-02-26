package com.boj.intellij.custom

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomTestCaseRepositoryTest {
    @Test
    fun `save and load single custom case`() {
        val baseDir = createTempDirectory("boj-custom").toFile()
        val repo = CustomTestCaseRepository(baseDir)

        val case = CustomTestCase(input = "1 2", expectedOutput = "3")
        repo.save("1000", "기본 테스트", case)

        val loaded = repo.load("1000")
        assertEquals(1, loaded.size)
        assertEquals("기본 테스트", loaded.keys.first())
        assertEquals(case, loaded.values.first())
    }

    @Test
    fun `save case with null expectedOutput`() {
        val baseDir = createTempDirectory("boj-custom").toFile()
        val repo = CustomTestCaseRepository(baseDir)

        val case = CustomTestCase(input = "5", expectedOutput = null)
        repo.save("1000", "디버깅용", case)

        val loaded = repo.load("1000")
        assertEquals(null, loaded["디버깅용"]?.expectedOutput)
    }

    @Test
    fun `load returns empty map for nonexistent problem`() {
        val baseDir = createTempDirectory("boj-custom").toFile()
        val repo = CustomTestCaseRepository(baseDir)

        val loaded = repo.load("9999")
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `delete removes case file`() {
        val baseDir = createTempDirectory("boj-custom").toFile()
        val repo = CustomTestCaseRepository(baseDir)

        repo.save("1000", "삭제할케이스", CustomTestCase("1", "2"))
        repo.delete("1000", "삭제할케이스")

        val loaded = repo.load("1000")
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `nextAutoName generates incrementing names`() {
        val baseDir = createTempDirectory("boj-custom").toFile()
        val repo = CustomTestCaseRepository(baseDir)

        assertEquals("커스텀 1", repo.nextAutoName("1000"))

        repo.save("1000", "커스텀 1", CustomTestCase("a", "b"))
        assertEquals("커스텀 2", repo.nextAutoName("1000"))

        repo.save("1000", "커스텀 2", CustomTestCase("c", "d"))
        assertEquals("커스텀 3", repo.nextAutoName("1000"))
    }

    @Test
    fun `sanitizeFileName replaces invalid characters`() {
        assertEquals("test_case", CustomTestCaseRepository.sanitizeFileName("test/case"))
        assertEquals("test_case", CustomTestCaseRepository.sanitizeFileName("test\\case"))
        assertEquals("test_case", CustomTestCaseRepository.sanitizeFileName("test:case"))
    }
}
