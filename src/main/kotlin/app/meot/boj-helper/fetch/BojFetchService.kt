package com.boj.intellij.fetch

interface BojFetchService {
    fun fetchProblemPage(problemNumber: String): String

    fun fetchProblemPage(problemNumber: Int): String = fetchProblemPage(problemNumber.toString())
}
