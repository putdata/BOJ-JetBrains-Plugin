package com.boj.intellij.github

import com.boj.intellij.submit.SubmitResult
import kotlin.test.Test
import kotlin.test.assertEquals

class GitHubUploadServiceTest {

    @Test
    fun `resolveUploadPath renders path template with variables`() {
        val result = SubmitResult(
            submissionId = "1", problemId = "1000", result = "맞았습니다!!",
            memory = "14512", time = "132", language = "Java 11", codeLength = "512",
        )
        val path = GitHubUploadService.resolveUploadPath(
            template = "{language}/{problemId}.{ext}",
            submitResult = result,
            title = "A+B",
            extension = "java",
        )
        assertEquals("Java 11/1000.java", path)
    }

    @Test
    fun `resolveCommitMessage renders commit template with variables`() {
        val result = SubmitResult(
            submissionId = "1", problemId = "1000", result = "맞았습니다!!",
            memory = "14512", time = "132", language = "Java 11", codeLength = "512",
        )
        val message = GitHubUploadService.resolveCommitMessage(
            template = "[{problemId}] {title} ({memory} KB, {time} ms)",
            submitResult = result,
            title = "A+B",
            extension = "java",
        )
        assertEquals("[1000] A+B (14512 KB, 132 ms)", message)
    }
}
