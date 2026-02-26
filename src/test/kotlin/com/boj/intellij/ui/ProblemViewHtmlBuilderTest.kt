package com.boj.intellij.ui

import com.boj.intellij.parse.ParsedProblem
import com.boj.intellij.parse.ParsedSamplePair
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProblemViewHtmlBuilderTest {
    private val sampleProblem = ParsedProblem(
        title = "A+B",
        timeLimit = "1 초",
        memoryLimit = "128 MB",
        submitCount = "12345",
        answerCount = "6789",
        solvedCount = "6000",
        correctRate = "54.123%",
        problemDescription = "두 정수 A와 B를 입력받은 다음, A+B를 출력하는 프로그램을 작성하시오.",
        inputDescription = "첫째 줄에 A와 B가 주어진다.",
        outputDescription = "첫째 줄에 A+B를 출력한다.",
        problemDescriptionHtml = "<p>두 정수 A와 B를 입력받은 다음, A+B를 출력하는 프로그램을 작성하시오.</p>",
        inputDescriptionHtml = "<p>첫째 줄에 A와 B가 주어진다.</p>",
        outputDescriptionHtml = "<p>첫째 줄에 A+B를 출력한다.</p>",
        samplePairs = listOf(ParsedSamplePair(input = "1 2", output = "3")),
    )

    private val defaultColors = ThemeColors(
        panelBg = "#ffffff",
        labelFg = "#202124",
        borderColor = "#d0d7de",
        editorBg = "#f6f8fa",
        editorFg = "#1f2328",
        secondaryFg = "#666666",
    )

    @Test
    fun `builds html containing title and problem number`() {
        val html = ProblemViewHtmlBuilder.buildHtml(
            problem = sampleProblem,
            problemNumber = "1000",
            colors = defaultColors,
        )

        assertTrue(html.contains("A+B"))
        assertTrue(html.contains("#1000"))
    }

    @Test
    fun `builds html containing meta info`() {
        val html = ProblemViewHtmlBuilder.buildHtml(
            problem = sampleProblem,
            problemNumber = "1000",
            colors = defaultColors,
        )

        assertTrue(html.contains("1 초"))
        assertTrue(html.contains("128 MB"))
        assertTrue(html.contains("54.123%"))
    }

    @Test
    fun `builds html containing problem input output sections`() {
        val html = ProblemViewHtmlBuilder.buildHtml(
            problem = sampleProblem,
            problemNumber = "1000",
            colors = defaultColors,
        )

        assertTrue(html.contains("<p>두 정수 A와 B를 입력받은 다음"))
        assertTrue(html.contains("<p>첫째 줄에 A와 B가 주어진다.</p>"))
        assertTrue(html.contains("<p>첫째 줄에 A+B를 출력한다.</p>"))
    }

    @Test
    fun `injects theme colors as css variables`() {
        val colors = ThemeColors(
            panelBg = "#1e1e1e",
            labelFg = "#cccccc",
            borderColor = "#444444",
            editorBg = "#2d2d2d",
            editorFg = "#d4d4d4",
            secondaryFg = "#888888",
        )

        val html = ProblemViewHtmlBuilder.buildHtml(
            problem = sampleProblem,
            problemNumber = "1000",
            colors = colors,
        )

        assertTrue(html.contains("--bg: #1e1e1e"))
        assertTrue(html.contains("--fg: #cccccc"))
    }

    @Test
    fun `includes mathjax script`() {
        val html = ProblemViewHtmlBuilder.buildHtml(
            problem = sampleProblem,
            problemNumber = "1000",
            colors = defaultColors,
        )

        assertTrue(html.contains("mathjax@3"))
        assertTrue(html.contains("tex-svg.js"))
    }

    @Test
    fun `strips script tags from section html`() {
        val problem = sampleProblem.copy(
            problemDescriptionHtml = "<p>Safe</p><script>alert('xss')</script>",
        )

        val html = ProblemViewHtmlBuilder.buildHtml(
            problem = problem,
            problemNumber = "1000",
            colors = defaultColors,
        )

        assertTrue(html.contains("<p>Safe</p>"))
        assertFalse(html.contains("alert('xss')"))
    }

    @Test
    fun `escapes html in title and meta fields`() {
        val problem = sampleProblem.copy(title = "<script>alert('xss')</script>")

        val html = ProblemViewHtmlBuilder.buildHtml(
            problem = problem,
            problemNumber = "1000",
            colors = defaultColors,
        )

        assertFalse(html.contains("<script>alert"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun `builds fallback plain text with all sections`() {
        val text = ProblemViewHtmlBuilder.buildFallbackText(
            problem = sampleProblem,
            problemNumber = "1000",
        )

        assertTrue(text.contains("A+B (#1000)"))
        assertTrue(text.contains("1 초"))
        assertTrue(text.contains("128 MB"))
        assertTrue(text.contains("두 정수 A와 B를"))
        assertTrue(text.contains("첫째 줄에 A와 B가"))
        assertTrue(text.contains("첫째 줄에 A+B를"))
    }

    // --- Task 1: 예제 섹션 HTML 생성 ---

    @Test
    fun `builds html containing sample sections with input and output`() {
        val problem = sampleProblem.copy(
            samplePairs = listOf(
                ParsedSamplePair(input = "1 2", output = "3"),
                ParsedSamplePair(input = "10 20", output = "30"),
            ),
        )
        val html = ProblemViewHtmlBuilder.buildHtml(
            problem = problem,
            problemNumber = "1000",
            colors = defaultColors,
        )

        assertTrue(html.contains("예제 입력 1"))
        assertTrue(html.contains("예제 출력 1"))
        assertTrue(html.contains("1 2"))
        assertTrue(html.contains("3"))
        assertTrue(html.contains("예제 입력 2"))
        assertTrue(html.contains("예제 출력 2"))
        assertTrue(html.contains("10 20"))
        assertTrue(html.contains("30"))
        assertTrue(html.contains("sample-group"))
    }

    @Test
    fun `builds html with no sample section when samples are empty`() {
        val problem = sampleProblem.copy(samplePairs = emptyList())
        val html = ProblemViewHtmlBuilder.buildHtml(
            problem = problem,
            problemNumber = "1000",
            colors = defaultColors,
        )

        assertFalse(html.contains("sample-group"))
        assertFalse(html.contains("예제 입력"))
    }

    @Test
    fun `escapes html in sample input and output`() {
        val problem = sampleProblem.copy(
            samplePairs = listOf(ParsedSamplePair(input = "<script>x</script>", output = "<b>y</b>")),
        )
        val html = ProblemViewHtmlBuilder.buildHtml(
            problem = problem,
            problemNumber = "1000",
            colors = defaultColors,
        )

        assertFalse(html.contains("<script>x</script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun `builds html with pass and fail css variables from theme colors`() {
        val darkColors = defaultColors.copy(
            passBg = "#1a3a2a",
            passFg = "#8DD694",
            failBg = "#3a1a1a",
            failFg = "#FF8A80",
            failBorder = "#FF8A80",
        )
        val html = ProblemViewHtmlBuilder.buildHtml(
            problem = sampleProblem,
            problemNumber = "1000",
            colors = darkColors,
        )

        assertTrue(html.contains("--pass-bg: #1a3a2a"))
        assertTrue(html.contains("--pass-fg: #8DD694"))
        assertTrue(html.contains("--fail-bg: #3a1a1a"))
        assertTrue(html.contains("--fail-fg: #FF8A80"))
        assertTrue(html.contains("--fail-border: #FF8A80"))
    }

    @Test
    fun `builds html with responsive css media query`() {
        val html = ProblemViewHtmlBuilder.buildHtml(
            problem = sampleProblem,
            problemNumber = "1000",
            colors = defaultColors,
        )

        assertTrue(html.contains("@media"))
        assertTrue(html.contains("max-width"))
        assertTrue(html.contains("grid-template-columns: 1fr"))
    }

    // --- Task 2: 실행 바 및 CSS 스타일 ---

    @Test
    fun `builds html containing run bar with command input and run all button`() {
        val html = ProblemViewHtmlBuilder.buildHtml(
            problem = sampleProblem,
            problemNumber = "1000",
            colors = defaultColors,
        )

        assertTrue(html.contains("run-bar"))
        assertTrue(html.contains("commandInput"))
        assertTrue(html.contains("runAllBtn"))
        assertTrue(html.contains("전체 실행"))
    }

    @Test
    fun `builds html with sample css styles`() {
        val html = ProblemViewHtmlBuilder.buildHtml(
            problem = sampleProblem,
            problemNumber = "1000",
            colors = defaultColors,
        )

        assertTrue(html.contains(".sample-group"))
        assertTrue(html.contains(".sample-columns"))
        assertTrue(html.contains(".run-bar"))
        assertTrue(html.contains(".result-badge"))
    }

    // --- Task 3: JavaScript 통신 코드 ---

    @Test
    fun `builds html containing javascript bridge functions`() {
        val html = ProblemViewHtmlBuilder.buildHtml(
            problem = sampleProblem,
            problemNumber = "1000",
            colors = defaultColors,
        )

        assertTrue(html.contains("function runSingle"))
        assertTrue(html.contains("function runAll"))
        assertTrue(html.contains("window.onSampleResult"))
        assertTrue(html.contains("window.onRunAllComplete"))
        assertTrue(html.contains("cefQuery"))
    }

    @Test
    fun `injects custom cef query code when provided`() {
        val html = ProblemViewHtmlBuilder.buildHtml(
            problem = sampleProblem,
            problemNumber = "1000",
            colors = defaultColors,
            cefQueryInjection = "window.customQuery_123({request: request})",
        )

        assertTrue(html.contains("window.customQuery_123"))
        assertFalse(html.contains("window.cefQuery"))
    }
}
