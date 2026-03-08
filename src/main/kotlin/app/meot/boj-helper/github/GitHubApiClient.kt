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
            .timeout(REQUEST_TIMEOUT)
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
            .timeout(REQUEST_TIMEOUT)
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
     * Git Data API를 사용해 다중 파일을 단일 커밋으로 업로드한다.
     */
    fun uploadMultipleFiles(
        repo: String,
        branch: String,
        commitMessage: String,
        files: Map<String, String>,
    ): UploadResult {
        // 1. 브랜치 최신 커밋 SHA 조회
        val refUrl = "https://api.github.com/repos/$repo/git/ref/heads/$branch"
        val refResponse = sendGet(refUrl)
        val commitSha = parseJsonValue(refResponse, "sha")
            ?: throw GitHubApiException(0, "브랜치 커밋 SHA를 가져올 수 없습니다")

        // 커밋에서 tree SHA 조회
        val commitUrl = "https://api.github.com/repos/$repo/git/commits/$commitSha"
        val commitResponse = sendGet(commitUrl)
        val treeSha = parseTreeSha(commitResponse)
            ?: throw GitHubApiException(0, "트리 SHA를 가져올 수 없습니다")

        // 2. 새 Tree 생성
        val treeUrl = "https://api.github.com/repos/$repo/git/trees"
        val treeBody = buildCreateTreeRequestBody(treeSha, files)
        val treeResponse = sendPost(treeUrl, treeBody)
        val newTreeSha = parseJsonValue(treeResponse, "sha")
            ?: throw GitHubApiException(0, "새 트리를 생성할 수 없습니다")

        // 3. 새 커밋 생성
        val newCommitUrl = "https://api.github.com/repos/$repo/git/commits"
        val commitBody = buildCreateCommitRequestBody(newTreeSha, commitSha, commitMessage)
        val newCommitResponse = sendPost(newCommitUrl, commitBody)
        val newCommitSha = parseJsonValue(newCommitResponse, "sha")
            ?: throw GitHubApiException(0, "새 커밋을 생성할 수 없습니다")

        // 4. 브랜치 포인터 이동
        val updateRefBody = """{"sha":"$newCommitSha"}"""
        sendPatch(refUrl, updateRefBody)

        return UploadResult(success = true)
    }

    private fun sendGet(url: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.v3+json")
            .timeout(REQUEST_TIMEOUT)
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw GitHubApiException(response.statusCode(), parseErrorMessage(response.body()))
        }
        return response.body()
    }

    private fun sendPost(url: String, body: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.v3+json")
            .timeout(REQUEST_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw GitHubApiException(response.statusCode(), parseErrorMessage(response.body()))
        }
        return response.body()
    }

    private fun sendPatch(url: String, body: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.v3+json")
            .timeout(REQUEST_TIMEOUT)
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw GitHubApiException(response.statusCode(), parseErrorMessage(response.body()))
        }
        return response.body()
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
            .timeout(REQUEST_TIMEOUT)
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
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(15)

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

        fun buildCreateTreeRequestBody(
            baseTreeSha: String,
            files: Map<String, String>,
        ): String {
            val treeEntries = files.entries.joinToString(",") { (path, content) ->
                """{"path":"${escapeJson(path)}","mode":"100644","type":"blob","content":"${escapeJson(content)}"}"""
            }
            return """{"base_tree":"${escapeJson(baseTreeSha)}","tree":[$treeEntries]}"""
        }

        fun buildCreateCommitRequestBody(
            treeSha: String,
            parentSha: String,
            message: String,
        ): String {
            return """{"message":"${escapeJson(message)}","tree":"${escapeJson(treeSha)}","parents":["${escapeJson(parentSha)}"]}"""
        }

        private fun parseTreeSha(commitJson: String): String? {
            val treePattern = """"tree"\s*:\s*\{[^}]*"sha"\s*:\s*"([^"]+)"""".toRegex()
            return treePattern.find(commitJson)?.groupValues?.get(1)
        }

        private fun parseJsonValue(json: String, key: String): String? {
            val pattern = """"${Regex.escape(key)}"\s*:\s*"([^"]+)"""".toRegex()
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
