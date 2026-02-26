package com.boj.intellij.sample_run

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.io.path.createTempDirectory

class OutputComparatorTest {
    private val comparator = OutputComparator()

    @Test
    fun `normalization ignores line endings trailing spaces and trailing blank lines`() {
        val expected = "first line\nsecond line\n"
        val actual = "first line  \r\nsecond line\t\r\n\r\n"

        val result = comparator.compare(expected, actual)

        assertTrue(result.passed)
    }

    @Test
    fun `comparison fails when normalized content is different`() {
        val result = comparator.compare("1 2 3\n", "1 2 4\n")

        assertFalse(result.passed)
        assertEquals("1 2 3", result.normalizedExpected)
        assertEquals("1 2 4", result.normalizedActual)
    }
}

class ProcessSampleRunServiceTest {
    @Test
    fun `runs command with sample stdin and returns pass result`() {
        val service = ProcessSampleRunService(command = "cat", timeoutMillis = 2_000)

        val result = service.runSample(SampleCase(input = "hello\n", expectedOutput = "hello\n"))

        assertTrue(result.passed)
        assertFalse(result.timedOut)
        assertEquals(0, result.exitCode)
        assertEquals("hello\n", result.actualOutput)
    }

    @Test
    fun `captures stderr and exit code for diagnostics`() {
        val service = ProcessSampleRunService(command = "sh -c \"echo boom 1>&2; exit 7\"", timeoutMillis = 2_000)

        val result = service.runSample(SampleCase(input = "", expectedOutput = ""))

        assertFalse(result.passed)
        assertFalse(result.timedOut)
        assertEquals(7, result.exitCode)
        assertContains(result.standardError, "boom")
    }

    @Test
    fun `timeout marks result as timed out and does not hang`() {
        val service = ProcessSampleRunService(command = "sh -c \"sleep 2\"", timeoutMillis = 100)

        val result = service.runSample(SampleCase(input = "", expectedOutput = ""))

        assertTrue(result.timedOut)
        assertFalse(result.passed)
    }

    @Test
    fun `command tokenizer supports quoted arguments`() {
        val tokens = ProcessSampleRunService.tokenizeCommand("python3 -c \"print('a b')\" --flag")

        assertEquals(listOf("python3", "-c", "print('a b')", "--flag"), tokens)
    }

    @Test
    fun `runs command in provided working directory`() {
        val workingDirectory = createTempDirectory("sample-run-working-dir").toFile()
        workingDirectory.resolve("input.txt").writeText("42\n")

        val service = ProcessSampleRunService(
            command = "cat input.txt",
            timeoutMillis = 2_000,
            workingDirectory = workingDirectory,
        )

        val result = service.runSample(SampleCase(input = "", expectedOutput = "42\n"))

        assertTrue(result.passed)
        assertEquals("42\n", result.actualOutput)
    }
}
