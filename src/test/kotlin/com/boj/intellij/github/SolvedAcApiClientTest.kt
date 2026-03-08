package com.boj.intellij.github

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SolvedAcApiClientTest {

    @Test
    fun `parseTags extracts tag names from API response`() {
        val json = """{"tags":[{"key":"dp","displayNames":[{"language":"ko","name":"다이나믹 프로그래밍","short":"DP"}]},{"key":"graph","displayNames":[{"language":"ko","name":"그래프 이론","short":"그래프"}]}]}"""
        val tags = SolvedAcApiClient.parseTags(json)
        assertEquals(listOf("다이나믹 프로그래밍", "그래프 이론"), tags)
    }

    @Test
    fun `parseTags returns empty list for no tags`() {
        val json = """{"tags":[]}"""
        val tags = SolvedAcApiClient.parseTags(json)
        assertTrue(tags.isEmpty())
    }

    @Test
    fun `parseTags falls back to en if ko not found`() {
        val json = """{"tags":[{"key":"dp","displayNames":[{"language":"en","name":"Dynamic Programming","short":"DP"}]}]}"""
        val tags = SolvedAcApiClient.parseTags(json)
        assertEquals(listOf("Dynamic Programming"), tags)
    }
}
