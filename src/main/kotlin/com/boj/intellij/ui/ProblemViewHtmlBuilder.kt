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
        val runBarHtml = if (hasSamples) buildRunBarHtml() else ""
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
              <script>
                window.MathJax = {
                  tex: {
                    inlineMath: [['$', '$'], ['\\(', '\\)']],
                    displayMath: [['$$', '$$'], ['\\[', '\\]']]
                  },
                  svg: { fontCache: 'global' }
                };
              </script>
              <script async src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-svg.js"></script>
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
              $runBarHtml
              $samplesHtml
              $bridgeScript
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
                appendLine("""    <span class="result-badge" id="badge-$index"></span>""")
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
                appendLine("""  <div class="result-area" id="result-$index">""")
                appendLine("""    <div class="actual-output"><div class="sample-label">실행 결과</div><pre id="actual-$index"></pre></div>""")
                appendLine("""    <div class="stderr-area" id="stderr-area-$index" style="display:none"><div class="sample-label">표준 에러</div><pre id="stderr-$index"></pre></div>""")
                appendLine("""  </div>""")
                appendLine("""</div>""")
            }
        }
    }

    /** 예제 실행 관련 CSS 스타일 생성 */
    private fun buildSampleCss(): String {
        return """
            .run-bar {
              display: flex; align-items: center; gap: 8px;
              margin: 16px 0 12px; padding: 8px 12px;
              border: 1px solid var(--border); border-radius: 6px;
              background: var(--code-bg);
            }
            .run-bar input {
              flex: 1; padding: 4px 8px;
              border: 1px solid var(--border); border-radius: 4px;
              background: var(--bg); color: var(--fg);
              font-family: "JetBrains Mono", "D2Coding", Consolas, monospace;
              font-size: 12px;
            }
            .run-bar button {
              padding: 4px 12px; border: 1px solid var(--border); border-radius: 4px;
              background: var(--bg); color: var(--fg); cursor: pointer;
              font-size: 12px;
            }
            .run-bar .status { color: var(--meta-fg); font-size: 12px; }
            .sample-group {
              margin: 16px 0; padding: 12px;
              border: 1px solid var(--border); border-radius: 6px;
            }
            .sample-header {
              display: flex; align-items: center; gap: 8px;
              margin-bottom: 8px;
            }
            .sample-header h3 { margin: 0; font-size: 14px; font-weight: 600; }
            .sample-columns {
              display: grid; grid-template-columns: 1fr 1fr; gap: 12px;
            }
            .sample-col { min-width: 0; }
            .sample-label {
              font-size: 12px; font-weight: 600; color: var(--meta-fg);
              margin-bottom: 4px;
            }
            .sample-code pre {
              margin: 0; white-space: pre-wrap; word-break: break-all;
            }
            .run-btn {
              padding: 2px 8px; border: 1px solid var(--border); border-radius: 4px;
              background: var(--bg); color: var(--fg); cursor: pointer;
              font-size: 12px;
            }
            .result-badge {
              font-size: 11px; padding: 1px 6px; border-radius: 4px;
              font-weight: 600;
            }
            .result-badge.pass { background: var(--pass-bg); color: var(--pass-fg); }
            .result-badge.fail { background: var(--fail-bg); color: var(--fail-fg); }
            .result-area {
              margin-top: 8px; display: none;
            }
            .actual-output pre {
              margin: 4px 0; border-left: 3px solid var(--border);
            }
            .stderr-area pre {
              margin: 4px 0; border-left: 3px solid var(--fail-border);
              color: var(--fail-border);
            }
            @media (max-width: 400px) {
              .sample-columns { grid-template-columns: 1fr; }
            }
        """.trimIndent()
    }

    /** 실행 바 HTML 생성 (명령어 입력 및 전체 실행 버튼) */
    private fun buildRunBarHtml(): String {
        return """
            <div class="run-bar">
              <input type="text" id="commandInput" placeholder="실행 명령어 (예: python main.py)">
              <button id="runAllBtn" onclick="runAll()">전체 실행</button>
              <span class="status" id="runStatus"></span>
            </div>
        """.trimIndent()
    }

    /** JavaScript 브릿지 코드 생성 (Kotlin ↔ JCEF 통신) */
    private fun buildBridgeScript(cefQueryInjection: String): String {
        val cefQueryCall = if (cefQueryInjection.isNotEmpty()) {
            cefQueryInjection
        } else {
            "window.cefQuery({request: request})"
        }

        return """
            <script>
              function getCommand() {
                return document.getElementById('commandInput').value;
              }

              window.setCommand = function(cmd) {
                document.getElementById('commandInput').value = cmd;
              };

              function runSingle(index) {
                var command = getCommand();
                var request = JSON.stringify({action: 'runSample', index: index, command: command});
                $cefQueryCall;
                document.getElementById('badge-' + index).textContent = '실행 중...';
                document.getElementById('badge-' + index).className = 'result-badge';
              }

              function runAll() {
                var command = getCommand();
                var request = JSON.stringify({action: 'runAll', command: command});
                $cefQueryCall;
                document.getElementById('runStatus').textContent = '실행 중...';
              }

              window.onSampleResult = function(index, result) {
                var badge = document.getElementById('badge-' + index);
                var resultArea = document.getElementById('result-' + index);
                var actualPre = document.getElementById('actual-' + index);
                var stderrArea = document.getElementById('stderr-area-' + index);
                var stderrPre = document.getElementById('stderr-' + index);

                resultArea.style.display = 'block';
                actualPre.textContent = result.stdout || '';

                if (result.passed) {
                  badge.textContent = 'PASS';
                  badge.className = 'result-badge pass';
                } else {
                  badge.textContent = 'FAIL';
                  badge.className = 'result-badge fail';
                }

                if (result.stderr) {
                  stderrArea.style.display = 'block';
                  stderrPre.textContent = result.stderr;
                } else {
                  stderrArea.style.display = 'none';
                }
              };

              window.onRunAllComplete = function(passedCount, totalCount) {
                var status = document.getElementById('runStatus');
                status.textContent = passedCount + '/' + totalCount + ' 통과';
              };
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
}
