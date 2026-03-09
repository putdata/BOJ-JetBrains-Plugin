package com.boj.intellij.submit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SubmitResultTest {

    @Test
    fun `creates SubmitResult with all fields`() {
        val result = SubmitResult(
            submissionId = "12345678",
            problemId = "1000",
            result = "맞았습니다!!",
            memory = "14512",
            time = "132",
            language = "Java 11",
            codeLength = "512",
        )
        assertEquals("12345678", result.submissionId)
        assertEquals("1000", result.problemId)
        assertEquals("맞았습니다!!", result.result)
        assertEquals("14512", result.memory)
        assertEquals("132", result.time)
        assertEquals("Java 11", result.language)
        assertEquals("512", result.codeLength)
    }

    @Test
    fun `isAccepted returns true for AC result`() {
        val result = SubmitResult(
            submissionId = "1", problemId = "1000",
            result = "맞았습니다!!", memory = "0", time = "0",
            language = "Java 11", codeLength = "0",
        )
        assertTrue(result.isAccepted())
    }

    @Test
    fun `isAccepted returns false for WA result`() {
        val result = SubmitResult(
            submissionId = "1", problemId = "1000",
            result = "틀렸습니다", memory = "0", time = "0",
            language = "Java 11", codeLength = "0",
        )
        assertFalse(result.isAccepted())
    }

    @Test
    fun `isFinalResult returns true for terminal states`() {
        val terminalResults = listOf(
            "맞았습니다!!", "틀렸습니다", "시간 초과", "메모리 초과",
            "출력 초과", "런타임 에러", "컴파일 에러",
        )
        terminalResults.forEach { resultText ->
            val result = SubmitResult(
                submissionId = "1", problemId = "1000",
                result = resultText, memory = "0", time = "0",
                language = "Java 11", codeLength = "0",
            )
            assertTrue(result.isFinalResult(), "Expected '$resultText' to be final")
        }
    }

    @Test
    fun `isFinalResult returns false for pending states`() {
        val pendingResults = listOf("기다리는 중", "채점 중", "채점 준비 중")
        pendingResults.forEach { resultText ->
            val result = SubmitResult(
                submissionId = "1", problemId = "1000",
                result = resultText, memory = "0", time = "0",
                language = "Java 11", codeLength = "0",
            )
            assertFalse(result.isFinalResult(), "Expected '$resultText' to NOT be final")
        }
    }
}
