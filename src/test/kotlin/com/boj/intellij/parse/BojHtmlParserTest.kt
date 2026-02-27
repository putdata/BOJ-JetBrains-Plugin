package com.boj.intellij.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BojHtmlParserTest {
    private val parser = BojHtmlParser()

    @Test
    fun `parses title metadata body and sample pairs in order`() {
        val html =
            """
            <html>
              <body>
                <span id="problem_title">A+B</span>
                <table id="problem-info">
                  <tr>
                    <th>시간 제한</th><td>1 초</td>
                    <th>메모리 제한</th><td>128 MB</td>
                  </tr>
                  <tr>
                    <th>제출</th><td>12345</td>
                    <th>정답</th><td>6789</td>
                  </tr>
                  <tr>
                    <th>맞힌 사람</th><td>6000</td>
                    <th>정답 비율</th><td>54.123%</td>
                  </tr>
                </table>
                <section id="problem_description">
                  <h2>문제</h2>
                  <p>두 정수 A와 B를 입력받은 다음, A+B를 출력하는 프로그램을 작성하시오.</p>
                </section>
                <section id="problem_input">
                  <h2>입력</h2>
                  <p>첫째 줄에 A와 B가 주어진다.</p>
                </section>
                <section id="problem_output">
                  <h2>출력</h2>
                  <p>첫째 줄에 A+B를 출력한다.</p>
                </section>
                <section id="sampleinput2">
                  <h2>예제 입력 2</h2>
                  <h3>unused</h3>
                  <pre>10 20</pre>
                </section>
                <section id="sampleoutput2">
                  <h2>예제 출력 2</h2>
                  <pre>30</pre>
                </section>
                <section id="sampleinput1">
                  <h2>예제 입력 1</h2>
                  <pre>1 2</pre>
                </section>
                <section id="sampleoutput1">
                  <h2>예제 출력 1</h2>
                  <pre>3</pre>
                </section>
              </body>
            </html>
            """.trimIndent()

        val parsed = parser.parse(html)

        assertEquals("A+B", parsed.title)
        assertEquals("1 초", parsed.timeLimit)
        assertEquals("128 MB", parsed.memoryLimit)
        assertEquals("12345", parsed.submitCount)
        assertEquals("6789", parsed.answerCount)
        assertEquals("6000", parsed.solvedCount)
        assertEquals("54.123%", parsed.correctRate)
        assertEquals("두 정수 A와 B를 입력받은 다음, A+B를 출력하는 프로그램을 작성하시오.", parsed.problemDescription)
        assertEquals("첫째 줄에 A와 B가 주어진다.", parsed.inputDescription)
        assertEquals("첫째 줄에 A+B를 출력한다.", parsed.outputDescription)
        assertTrue(parsed.problemDescriptionHtml.contains("<p>두 정수 A와 B를 입력받은 다음, A+B를 출력하는 프로그램을 작성하시오.</p>"))
        assertTrue(parsed.inputDescriptionHtml.contains("<p>첫째 줄에 A와 B가 주어진다.</p>"))
        assertTrue(parsed.outputDescriptionHtml.contains("<p>첫째 줄에 A+B를 출력한다.</p>"))
        assertEquals(
            "두 정수 A와 B를 입력받은 다음, A+B를 출력하는 프로그램을 작성하시오.\n\n" +
                "첫째 줄에 A와 B가 주어진다.\n\n" +
                "첫째 줄에 A+B를 출력한다.",
            parsed.mainBody,
        )

        assertEquals(
            listOf(
                ParsedSamplePair(input = "1 2", output = "3"),
                ParsedSamplePair(input = "10 20", output = "30"),
            ),
            parsed.samplePairs,
        )
    }

    @Test
    fun `returns empty strings and empty samples when sections are missing`() {
        val html = "<html><body></body></html>"

        val parsed = parser.parse(html)

        assertEquals("", parsed.title)
        assertEquals("", parsed.timeLimit)
        assertEquals("", parsed.memoryLimit)
        assertEquals("", parsed.submitCount)
        assertEquals("", parsed.answerCount)
        assertEquals("", parsed.solvedCount)
        assertEquals("", parsed.correctRate)
        assertEquals("", parsed.problemDescription)
        assertEquals("", parsed.inputDescription)
        assertEquals("", parsed.outputDescription)
        assertEquals("", parsed.problemDescriptionHtml)
        assertEquals("", parsed.inputDescriptionHtml)
        assertEquals("", parsed.outputDescriptionHtml)
        assertEquals("", parsed.mainBody)
        assertEquals(emptyList(), parsed.samplePairs)
    }

    @Test
    fun `parses problem info from thead tbody table shape`() {
        val html =
            """
            <html>
              <body>
                <span id="problem_title">시험 문제</span>
                <table id="problem-info">
                  <thead>
                    <tr>
                      <th>시간 제한</th>
                      <th>메모리 제한</th>
                      <th>제출</th>
                      <th>정답</th>
                      <th>맞힌 사람</th>
                      <th>정답 비율</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td>2 초</td>
                      <td>512 MB</td>
                      <td>9000</td>
                      <td>4500</td>
                      <td>4000</td>
                      <td>50.000%</td>
                    </tr>
                  </tbody>
                </table>
                <section id="problem_description"><p>설명 본문</p></section>
              </body>
            </html>
            """.trimIndent()

        val parsed = parser.parse(html)

        assertEquals("시험 문제", parsed.title)
        assertEquals("2 초", parsed.timeLimit)
        assertEquals("512 MB", parsed.memoryLimit)
        assertEquals("9000", parsed.submitCount)
        assertEquals("4500", parsed.answerCount)
        assertEquals("4000", parsed.solvedCount)
        assertEquals("50.000%", parsed.correctRate)
        assertEquals("설명 본문", parsed.problemDescription)
        assertTrue(parsed.problemDescriptionHtml.contains("설명 본문"))
        assertEquals("설명 본문", parsed.mainBody)
    }

    @Test
    fun `section html preserves math markup and strips scripts`() {
        val html =
            """
            <html>
              <body>
                <section id="problem_description">
                  <h2>문제</h2>
                  <p>수식: ${'$'}a+b${'$'}</p>
                  <script>alert('xss')</script>
                </section>
              </body>
            </html>
            """.trimIndent()

        val parsed = parser.parse(html)

        assertTrue(parsed.problemDescriptionHtml.contains("수식: ${'$'}a+b${'$'}"))
        assertFalse(parsed.problemDescriptionHtml.contains("<script"))
        assertEquals("수식: ${'$'}a+b${'$'}", parsed.problemDescription)
    }

    @Test
    fun `parses limit subtask hint and sample explain sections`() {
        val html =
            """
            <html>
              <body>
                <span id="problem_title">복잡한 문제</span>
                <table id="problem-info">
                  <tr><th>시간 제한</th><td>2 초</td><th>메모리 제한</th><td>256 MB</td></tr>
                  <tr><th>제출</th><td>100</td><th>정답</th><td>50</td></tr>
                  <tr><th>맞힌 사람</th><td>40</td><th>정답 비율</th><td>50%</td></tr>
                </table>
                <section id="problem_description"><h2>문제</h2><p>문제 본문</p></section>
                <section id="problem_input"><h2>입력</h2><p>입력 설명</p></section>
                <section id="problem_output"><h2>출력</h2><p>출력 설명</p></section>
                <section id="problem_limit"><h2>제한</h2><ul><li>1 ≤ N ≤ 500</li></ul></section>
                <section id="problem_subtask"><h2>서브태스크</h2><table><tr><td>1</td><td>10</td></tr></table></section>
                <section id="sampleinput1"><pre>1 2</pre></section>
                <section id="sampleoutput1"><pre>3</pre></section>
                <section id="sample_explain_1"><div id="problem_sample_explain_1" class="problem-text"><p>설명입니다.</p></div></section>
                <section id="sampleinput2"><pre>10 20</pre></section>
                <section id="sampleoutput2"><pre>30</pre></section>
                <section id="problem_hint"><h2>힌트</h2><p>힌트 내용</p></section>
              </body>
            </html>
            """.trimIndent()

        val parsed = parser.parse(html)

        assertTrue(parsed.limitHtml.contains("1 ≤ N ≤ 500"))
        assertTrue(parsed.subtaskHtml.contains("<table>"))
        assertTrue(parsed.hintHtml.contains("힌트 내용"))
        assertEquals(1, parsed.sampleExplains.size)
        assertNotNull(parsed.sampleExplains[1])
        assertTrue(parsed.sampleExplains[1]!!.contains("설명입니다."))
    }

    @Test
    fun `returns empty extra sections when not present`() {
        val html = "<html><body></body></html>"
        val parsed = parser.parse(html)

        assertEquals("", parsed.limitHtml)
        assertEquals("", parsed.subtaskHtml)
        assertEquals("", parsed.hintHtml)
        assertEquals(emptyMap(), parsed.sampleExplains)
    }
}
