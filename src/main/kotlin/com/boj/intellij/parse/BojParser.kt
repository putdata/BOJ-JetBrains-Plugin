package com.boj.intellij.parse

interface BojParser {
    fun parse(problemPageHtml: String): ParsedProblem
}
