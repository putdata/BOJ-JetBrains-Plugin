package com.boj.intellij.github

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/**
 * GitHub Contents API 클라이언트.
 * API 문서: https://docs.github.com/en/rest/repos/contents
 */
class GitHubApiClient(
    private val token: String,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
) {
    /**
     * 파일의 현재 SHA를 조회한다. 파일이 없으면 null 반환.
     */
    fun getFileSha(repo: String, path: String, branch: String): String? {
        val url = "${buildApiUrl(repo, path)}?ref=$branch"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.v3+json")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return when (response.statusCode()) {
            200 -> parseFileSha(response.body())
            404 -> null
            else -> throw GitHubApiException(response.statusCode(), parseErrorMessage(response.body()))
        }
    }

    /**
     * 파일을 생성하거나 업데이트한다.
     */
    fun uploadFile(
        repo: String,
        path: String,
        content: String,
        commitMessage: String,
        branch: String,
        existingSha: String?,
    ): UploadResult {
        val url = buildApiUrl(repo, path)
        val body = buildUploadRequestBody(content, commitMessage, branch, existingSha)

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.v3+json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return when (response.statusCode()) {
            200, 201 -> {
                val htmlUrl = parseHtmlUrl(response.body())
                UploadResult(success = true, htmlUrl = htmlUrl)
            }
            else -> throw GitHubApiException(response.statusCode(), parseErrorMessage(response.body()))
        }
    }

    /**
     * 토큰 유효성과 리포지토리 접근 권한을 확인한다.
     */
    fun testConnection(repo: String): ConnectionTestResult {
        val url = "https://api.github.com/repos/$repo"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.v3+json")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return when (response.statusCode()) {
            200 -> {
                val canPush = response.body().contains("\"push\":true")
                ConnectionTestResult(success = true, canPush = canPush)
            }
            401 -> ConnectionTestResult(success = false, errorMessage = "토큰이 유효하지 않습니다")
            404 -> ConnectionTestResult(success = false, errorMessage = "리포지토리를 찾을 수 없습니다")
            else -> ConnectionTestResult(
                success = false,
                errorMessage = parseErrorMessage(response.body()),
            )
        }
    }

    data class UploadResult(
        val success: Boolean,
        val htmlUrl: String? = null,
    )

    data class ConnectionTestResult(
        val success: Boolean,
        val canPush: Boolean = false,
        val errorMessage: String? = null,
    )

    class GitHubApiException(val statusCode: Int, message: String?) :
        RuntimeException("GitHub API 오류 ($statusCode): ${message ?: "알 수 없는 오류"}")

    companion object {
        fun buildApiUrl(repo: String, path: String): String {
            return "https://api.github.com/repos/$repo/contents/$path"
        }

        fun buildUploadRequestBody(
            content: String,
            commitMessage: String,
            branch: String,
            existingSha: String?,
        ): String {
            val encodedContent = Base64.getEncoder().encodeToString(content.toByteArray())
            val escapedMessage = escapeJson(commitMessage)
            val sb = StringBuilder()
            sb.append("{")
            sb.append("\"message\":\"$escapedMessage\"")
            sb.append(",\"content\":\"$encodedContent\"")
            sb.append(",\"branch\":\"${escapeJson(branch)}\"")
            if (existingSha != null) {
                sb.append(",\"sha\":\"${escapeJson(existingSha)}\"")
            }
            sb.append("}")
            return sb.toString()
        }

        fun parseFileSha(json: String): String? {
            val pattern = """"sha"\s*:\s*"([^"]+)"""".toRegex()
            return pattern.find(json)?.groupValues?.get(1)
        }

        fun parseErrorMessage(json: String): String? {
            val pattern = """"message"\s*:\s*"([^"]+)"""".toRegex()
            return pattern.find(json)?.groupValues?.get(1)
        }

        private fun parseHtmlUrl(json: String): String? {
            val pattern = """"html_url"\s*:\s*"([^"]+)"""".toRegex()
            return pattern.find(json)?.groupValues?.get(1)
        }

        private fun escapeJson(value: String): String {
            return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
        }
    }
}
