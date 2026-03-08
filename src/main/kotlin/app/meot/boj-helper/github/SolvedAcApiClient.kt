package com.boj.intellij.github

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object SolvedAcApiClient {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)

    fun fetchTags(problemId: String): List<String> {
        return try {
            val url = "https://solved.ac/api/v3/problem/show?problemId=$problemId"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                parseTags(response.body())
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseTags(json: String): List<String> {
        val tagPattern = """"displayNames"\s*:\s*\[(.*?)\]""".toRegex()
        val namePattern = """"language"\s*:\s*"(\w+)"\s*,\s*"name"\s*:\s*"([^"]+)"""".toRegex()

        val tags = mutableListOf<String>()
        for (tagMatch in tagPattern.findAll(json)) {
            val displayNamesBlock = tagMatch.groupValues[1]
            val names = namePattern.findAll(displayNamesBlock)
                .map { it.groupValues[1] to it.groupValues[2] }
                .toList()

            val koName = names.firstOrNull { it.first == "ko" }?.second
            val enName = names.firstOrNull { it.first == "en" }?.second
            val name = koName ?: enName
            if (name != null) {
                tags.add(name)
            }
        }
        return tags
    }
}
