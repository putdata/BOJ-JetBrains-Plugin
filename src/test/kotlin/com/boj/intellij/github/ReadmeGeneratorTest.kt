package com.boj.intellij.github

import com.boj.intellij.parse.ParsedProblem
import com.boj.intellij.submit.SubmitResult
import kotlin.test.Test
import kotlin.test.assertContains

class ReadmeGeneratorTest {

    private val sampleProblem = ParsedProblem(
        title = "A+B",
        timeLimit = "2초",
        memoryLimit = "128 MB",
        submitCount = "1000",
        answerCount = "500",
        solvedCount = "400",
        correctRate = "50%",
        problemDescription = "두 정수 A와 B를 입력받은 다음, A+B를 출력하는 프로그램을 작성하시오.",
        inputDescription = "첫째 줄에 A와 B가 주어진다.",
        outputDescription = "첫째 줄에 A+B를 출력한다.",
        problemDescriptionHtml = "<p>두 정수 A와 B를 입력받은 다음, A+B를 출력하는 프로그램을 작성하시오.</p>",
        inputDescriptionHtml = "<p>첫째 줄에 A와 B가 주어진다.</p>",
        outputDescriptionHtml = "<p>첫째 줄에 A+B를 출력한다.</p>",
        samplePairs = emptyList(),
    )

    private val sampleResult = SubmitResult(
        submissionId = "1", problemId = "1000", result = "맞았습니다!!",
        memory = "14512", time = "132", language = "Java 11", codeLength = "512",
    )

    @Test
    fun `generate includes title with problem link`() {
        val readme = ReadmeGenerator.generate(
            problemId = "1000", title = "A+B", tierLevel = 11,
            problemData = sampleProblem, submitResult = sampleResult,
            submittedAt = "2026-03-09 12:34:56",
        )
        assertContains(readme, "# 1000 - A+B")
        assertContains(readme, "https://www.acmicpc.net/problem/1000")
    }

    @Test
    fun `generate includes tier info`() {
        val readme = ReadmeGenerator.generate(
            problemId = "1000", title = "A+B", tierLevel = 11,
            problemData = sampleProblem, submitResult = sampleResult,
            submittedAt = "2026-03-09 12:34:56",
        )
        assertContains(readme, "Gold 5")
    }

    @Test
    fun `generate includes submission result`() {
        val readme = ReadmeGenerator.generate(
            problemId = "1000", title = "A+B", tierLevel = 11,
            problemData = sampleProblem, submitResult = sampleResult,
            submittedAt = "2026-03-09 12:34:56",
        )
        assertContains(readme, "14512")
        assertContains(readme, "132")
        assertContains(readme, "Java 11")
        assertContains(readme, "2026-03-09 12:34:56")
    }

    @Test
    fun `generate includes problem description`() {
        val readme = ReadmeGenerator.generate(
            problemId = "1000", title = "A+B", tierLevel = 11,
            problemData = sampleProblem, submitResult = sampleResult,
            submittedAt = "2026-03-09 12:34:56",
        )
        assertContains(readme, "두 정수 A와 B를 입력받은 다음")
    }

    @Test
    fun `generate includes algorithm tags`() {
        val readme = ReadmeGenerator.generate(
            problemId = "1000", title = "A+B", tierLevel = 11,
            problemData = sampleProblem, submitResult = sampleResult,
            submittedAt = "2026-03-09 12:34:56",
            tags = listOf("수학", "구현"),
        )
        assertContains(readme, "- 수학")
        assertContains(readme, "- 구현")
    }

    @Test
    fun `generate uses html conversion for problem description`() {
        val problemWithHtml = sampleProblem.copy(
            problemDescriptionHtml = "<p>두 정수 <strong>A</strong>와 <strong>B</strong>를 입력받는다.</p><ul><li>조건 1</li><li>조건 2</li></ul>",
        )
        val readme = ReadmeGenerator.generate(
            problemId = "1000", title = "A+B", tierLevel = 11,
            problemData = problemWithHtml, submitResult = sampleResult,
            submittedAt = "2026-03-09 12:34:56",
        )
        assertContains(readme, "**A**")
        assertContains(readme, "- 조건 1")
        assertContains(readme, "- 조건 2")
    }
}
