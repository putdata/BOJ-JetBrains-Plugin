package com.boj.intellij.github

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubApiClientTest {

    @Test
    fun `buildUploadRequestBody creates correct JSON for new file`() {
        val body = GitHubApiClient.buildUploadRequestBody(
            content = "hello world",
            commitMessage = "[1000] A+B",
            branch = "main",
            existingSha = null,
        )
        assertTrue(body.contains("\"message\":\"[1000] A+B\""))
        assertTrue(body.contains("\"branch\":\"main\""))
        assertTrue(body.contains("\"content\":\"aGVsbG8gd29ybGQ=\""))  // Base64
        assertTrue(!body.contains("\"sha\""))
    }

    @Test
    fun `buildUploadRequestBody includes sha for existing file`() {
        val body = GitHubApiClient.buildUploadRequestBody(
            content = "hello",
            commitMessage = "update",
            branch = "main",
            existingSha = "abc123",
        )
        assertTrue(body.contains("\"sha\":\"abc123\""))
    }

    @Test
    fun `parseFileSha extracts sha from API response`() {
        val json = """{"name":"1000.java","path":"java/1000.java","sha":"abc123def456","size":100,"type":"file"}"""
        val sha = GitHubApiClient.parseFileSha(json)
        assertEquals("abc123def456", sha)
    }

    @Test
    fun `parseFileSha returns null for invalid JSON`() {
        assertNull(GitHubApiClient.parseFileSha("not json"))
    }

    @Test
    fun `buildApiUrl creates correct URL`() {
        val url = GitHubApiClient.buildApiUrl("putdata/boj-solutions", "java/1000.java")
        assertEquals("https://api.github.com/repos/putdata/boj-solutions/contents/java/1000.java", url)
    }

    @Test
    fun `parseErrorMessage extracts message from error response`() {
        val json = """{"message":"Not Found","documentation_url":"https://docs.github.com"}"""
        assertEquals("Not Found", GitHubApiClient.parseErrorMessage(json))
    }

    @Test
    fun `buildCreateTreeRequestBody creates correct JSON`() {
        val body = GitHubApiClient.buildCreateTreeRequestBody(
            baseTreeSha = "abc123",
            files = mapOf(
                "GOLD 5/1000/solution.java" to "public class Main {}",
                "GOLD 5/1000/README.md" to "# 1000 - A+B",
            ),
        )
        assertTrue(body.contains("\"base_tree\":\"abc123\""))
        assertTrue(body.contains("\"path\":\"GOLD 5/1000/solution.java\""))
        assertTrue(body.contains("\"path\":\"GOLD 5/1000/README.md\""))
        assertTrue(body.contains("\"mode\":\"100644\""))
        assertTrue(body.contains("\"type\":\"blob\""))
    }

    @Test
    fun `buildCreateCommitRequestBody creates correct JSON`() {
        val body = GitHubApiClient.buildCreateCommitRequestBody(
            treeSha = "tree123",
            parentSha = "parent456",
            message = "[1000] A+B",
        )
        assertTrue(body.contains("\"tree\":\"tree123\""))
        assertTrue(body.contains("\"parents\":[\"parent456\"]"))
        assertTrue(body.contains("\"message\":\"[1000] A+B\""))
    }
}
