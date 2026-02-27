package com.boj.intellij.ui

import com.boj.intellij.parse.ParsedProblem

object ProblemViewHtmlBuilder {
    fun buildHtml(
        problem: ParsedProblem,
        problemNumber: String,
        colors: ThemeColors,
        cefQueryInjection: String = "",
    ): String {
        val sanitizedProblemHtml = sanitizeHtml(problem.problemDescriptionHtml)
        val sanitizedInputHtml = sanitizeHtml(problem.inputDescriptionHtml)
        val sanitizedOutputHtml = sanitizeHtml(problem.outputDescriptionHtml)
        val hasSamples = problem.samplePairs.isNotEmpty()
        val sampleCss = if (hasSamples) buildSampleCss() else ""
        val samplesHtml = buildSamplesHtml(problem)
        val bridgeScript = if (hasSamples) buildBridgeScript(cefQueryInjection) else ""

        val baseCss = loadCssResource("/css/problem-view.css")
        val themeVars = """
            :root {
              --bg: ${colors.panelBg};
              --fg: ${colors.labelFg};
              --border: ${colors.borderColor};
              --code-bg: ${colors.editorBg};
              --code-fg: ${colors.editorFg};
              --heading-fg: ${colors.labelFg};
              --meta-fg: ${colors.secondaryFg};
              --table-stripe: ${colors.editorBg};
              --pass-bg: ${colors.passBg}; --pass-fg: ${colors.passFg};
              --fail-bg: ${colors.failBg}; --fail-fg: ${colors.failFg};
              --fail-border: ${colors.failBorder};
            }
        """.trimIndent()

        return """
            <!doctype html>
            <html lang="ko">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <style>
                $themeVars
                $baseCss
                $sampleCss
              </style>
            </head>
            <body>
              <header>
                <h1>${escapeHtml(problem.title)} <span class="problem-id">#${escapeHtml(problemNumber)}</span></h1>
                <div class="meta">
                  <span>${escapeHtml(problem.timeLimit)}</span>
                  <span>${escapeHtml(problem.memoryLimit)}</span>
                  <span>정답률 ${escapeHtml(problem.correctRate)}</span>
                </div>
              </header>
              <hr>
              <section>
                <h2>문제</h2>
                $sanitizedProblemHtml
              </section>
              <section>
                <h2>입력</h2>
                $sanitizedInputHtml
              </section>
              <section>
                <h2>출력</h2>
                $sanitizedOutputHtml
              </section>
              $samplesHtml
              $bridgeScript
              <script>
                window.MathJax = {
                  loader: { load: [] },
                  tex: {
                    inlineMath: [['$', '$'], ['\\(', '\\)']],
                    displayMath: [['$$', '$$'], ['\\[', '\\]']]
                  },
                  svg: { fontCache: 'global' }
                };
              </script>
              <script>${MATHJAX_SCRIPT}</script>
            </body>
            </html>
        """.trimIndent()
    }

    fun buildFallbackText(problem: ParsedProblem, problemNumber: String): String {
        return buildString {
            appendLine("${problem.title} (#$problemNumber)")
            appendLine("시간 제한: ${problem.timeLimit} | 메모리 제한: ${problem.memoryLimit} | 정답률: ${problem.correctRate}")
            appendLine("──────────────────")
            appendLine("[문제]")
            appendLine(problem.problemDescription)
            appendLine()
            appendLine("[입력]")
            appendLine(problem.inputDescription)
            appendLine()
            appendLine("[출력]")
            appendLine(problem.outputDescription)
        }.trimEnd()
    }

    /** 예제 입출력 섹션 HTML 생성 */
    private fun buildSamplesHtml(problem: ParsedProblem): String {
        if (problem.samplePairs.isEmpty()) return ""

        return buildString {
            problem.samplePairs.forEachIndexed { index, pair ->
                val num = index + 1
                appendLine("""<div class="sample-group" id="sample-$index">""")
                appendLine("""  <div class="sample-header">""")
                appendLine("""    <h3>예제 $num</h3>""")
                appendLine("""    <button class="run-btn" onclick="runSingle($index)">▶</button>""")
                appendLine("""  </div>""")
                appendLine("""  <div class="sample-columns">""")
                appendLine("""    <div class="sample-col">""")
                appendLine("""      <div class="sample-label">예제 입력 $num</div>""")
                appendLine("""      <div class="sample-code"><pre>${escapeHtml(pair.input)}</pre></div>""")
                appendLine("""    </div>""")
                appendLine("""    <div class="sample-col">""")
                appendLine("""      <div class="sample-label">예제 출력 $num</div>""")
                appendLine("""      <div class="sample-code"><pre>${escapeHtml(pair.output)}</pre></div>""")
                appendLine("""    </div>""")
                appendLine("""  </div>""")
                appendLine("""</div>""")
            }
        }
    }

    /** 예제 실행 관련 CSS 스타일 생성 */
    private fun buildSampleCss(): String =
        loadCssResource("/css/problem-view-samples.css")

    /** JavaScript 브릿지 코드 생성 (Kotlin ↔ JCEF 통신) */
    private fun buildBridgeScript(cefQueryInjection: String): String {
        val cefQueryCall = if (cefQueryInjection.isNotEmpty()) {
            cefQueryInjection
        } else {
            "window.cefQuery({request: request})"
        }

        return """
            <script>
              function runSingle(index) {
                var request = JSON.stringify({action: 'runSample', index: index});
                $cefQueryCall;
              }
            </script>
        """.trimIndent()
    }

    private fun sanitizeHtml(rawHtml: String): String =
        rawHtml
            .replace(SCRIPT_TAG_REGEX, "")
            .replace(STYLE_TAG_REGEX, "")
            .trim()

    private fun escapeHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun loadCssResource(path: String): String =
        ProblemViewHtmlBuilder::class.java.getResourceAsStream(path)
            ?.reader()
            ?.readText()
            ?: ""

    private val SCRIPT_TAG_REGEX = Regex("(?is)<script[^>]*>.*?</script>")
    private val STYLE_TAG_REGEX = Regex("(?is)<style[^>]*>.*?</style>")

    private val MATHJAX_SCRIPT: String by lazy {
        ProblemViewHtmlBuilder::class.java.getResourceAsStream("/js/tex-svg-full.js")
            ?.reader()
            ?.readText()
            ?: ""
    }
}
