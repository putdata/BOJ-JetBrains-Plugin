package com.boj.intellij.fetch

import java.time.Duration
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class HttpBojFetchService(
    private val httpClient: HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
    private val problemUrlPrefix: String = "https://www.acmicpc.net/problem/",
    private val requestProfiles: List<RequestProfile> = DEFAULT_REQUEST_PROFILES,
    private val retryOnForbiddenCount: Int = 1,
) : BojFetchService {
    override fun fetchProblemPage(problemNumber: String): String {
        require(problemNumber.all(Char::isDigit) && problemNumber.isNotBlank()) {
            "problemNumber must be non-empty digits only"
        }

        val problemUri = URI.create("$problemUrlPrefix$problemNumber")
        val profiles = requestProfiles.ifEmpty { DEFAULT_REQUEST_PROFILES }
        val maxAttempts = minOf(profiles.size, retryOnForbiddenCount.coerceAtLeast(0) + 1)

        var lastStatusCode = -1
        for (attemptIndex in 0 until maxAttempts) {
            val response =
                httpClient.send(
                    buildRequest(problemUri, profiles[attemptIndex]),
                    HttpResponse.BodyHandlers.ofString(),
                )

            val statusCode = response.statusCode()
            if (statusCode in 200..299) {
                return response.body()
            }

            lastStatusCode = statusCode
            if (statusCode != 403) {
                break
            }
        }

        if (lastStatusCode == 403) {
            throw IllegalStateException(
                "Failed to fetch BOJ problem page: HTTP 403 (request was blocked; retry in a moment)",
            )
        }
        throw IllegalStateException("Failed to fetch BOJ problem page: HTTP $lastStatusCode")
    }

    private fun buildRequest(problemUri: URI, requestProfile: RequestProfile): HttpRequest {
        return HttpRequest.newBuilder()
            .uri(problemUri)
            .timeout(DEFAULT_REQUEST_TIMEOUT)
            .GET()
            .header("User-Agent", requestProfile.userAgent)
            .header("Accept", HTML_ACCEPT_HEADER)
            .header("Accept-Language", requestProfile.acceptLanguage)
            .header("Referer", BOJ_REFERER)
            .build()
    }

    data class RequestProfile(
        val userAgent: String,
        val acceptLanguage: String,
    )

    private companion object {
        private val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        private val DEFAULT_REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)

        private const val HTML_ACCEPT_HEADER =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
        private const val BOJ_REFERER = "https://www.acmicpc.net/"

        private val DEFAULT_REQUEST_PROFILES: List<RequestProfile> =
            listOf(
                RequestProfile(
                    userAgent =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
                    acceptLanguage = "ko,en-US;q=0.9,en;q=0.8",
                ),
                RequestProfile(
                    userAgent =
                        "Mozilla/5.0 (X11; Linux x86_64; rv:124.0) " +
                            "Gecko/20100101 Firefox/124.0",
                    acceptLanguage = "ko,en-US;q=0.9,en;q=0.8",
                ),
            )
    }
}
