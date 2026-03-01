package com.boj.intellij.integration

import com.boj.intellij.fetch.BojFetchService
import com.boj.intellij.parse.BojHtmlParser
import com.boj.intellij.sample_run.ProcessSampleRunService
import com.boj.intellij.sample_run.SampleCase
import com.boj.intellij.sample_run.SampleRunService
import com.boj.intellij.ui.BojToolWindowPanel
import com.boj.intellij.ui.RunBarPanel
import com.boj.intellij.parse.ParsedSamplePair
import com.intellij.openapi.project.Project
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import javax.swing.JTextArea
import javax.swing.SwingUtilities

class BojWorkflowIntegrationTest {
    private val parser = BojHtmlParser()

    @Test
    fun `fetch parse and ui binding expose required boj fields and sample auto fill`() {
        val html = bojProblemHtml()
        val fetchService = object : BojFetchService {
            override fun fetchProblemPage(problemNumber: String): String {
                assertEquals("1000", problemNumber)
                return html
            }
        }

        val parsed = parser.parse(fetchService.fetchProblemPage("1000"))

        assertEquals("A+B", parsed.title)
        assertEquals("1 초", parsed.timeLimit)
        assertEquals("128 MB", parsed.memoryLimit)
        assertEquals("12345", parsed.submitCount)
        assertEquals("6789", parsed.answerCount)
        assertEquals("6000", parsed.solvedCount)
        assertEquals("54.123%", parsed.correctRate)
        assertTrue(parsed.limitHtml.contains("0 &lt; A, B &lt; 10") || parsed.limitHtml.contains("0 < A, B < 10"))
        assertEquals(1, parsed.sampleExplains.size)
        assertTrue(parsed.sampleExplains[1]!!.contains("1 + 2 = 3이다."))
        assertEquals("두 정수 A와 B를 입력받은 다음, A+B를 출력하는 프로그램을 작성하시오.", parsed.problemDescription)
        assertEquals("첫째 줄에 A와 B가 주어진다.", parsed.inputDescription)
        assertEquals("첫째 줄에 A+B를 출력한다.", parsed.outputDescription)
        assertEquals(
            "두 정수 A와 B를 입력받은 다음, A+B를 출력하는 프로그램을 작성하시오.\n\n" +
                "첫째 줄에 A와 B가 주어진다.\n\n" +
                "첫째 줄에 A+B를 출력한다.",
            parsed.mainBody,
        )

        val panel = createPanel(fetchService)

        onEdt {
            // currentProblemNumber를 설정하고 bindProblem 호출
            setField(panel, "currentProblemNumber", "1000")
            invokePrivate(panel, "bindProblem", parsed)

            // 폴백 텍스트에 제목/메타 정보 포함 검증
            val fallbackArea = field<JTextArea>(panel, "problemViewFallbackArea")
            assertTrue(fallbackArea.text.contains("A+B"))
            assertTrue(fallbackArea.text.contains("1 초"))
            assertTrue(fallbackArea.text.contains("128 MB"))
            assertTrue(fallbackArea.text.contains("54.123%"))

            // 예제 바인딩 검증
            @Suppress("UNCHECKED_CAST")
            val samples = field<List<ParsedSamplePair>>(panel, "currentSamples")
            assertEquals(2, samples.size)
            assertEquals("1 2", samples[0].input)
            assertEquals("3", samples[0].output)
            assertEquals("10 20", samples[1].input)
            assertEquals("30", samples[1].output)
        }
    }

    @Test
    fun `sample execution compares output and returns correct result`() {
        val parsed = parser.parse(bojProblemHtml())

        val service = ProcessSampleRunService(command = "sh -c \"awk '{print \\$1 + \\$2}'\"", timeoutMillis = 2_000)
        val sample = parsed.samplePairs.first()

        val passResult = service.runSample(SampleCase(input = sample.input, expectedOutput = sample.output))
        assertTrue(passResult.passed)
        assertEquals("3", passResult.actualOutput.trim())

        val failResult = service.runSample(SampleCase(input = sample.input, expectedOutput = "different output"))
        assertFalse(failResult.passed)
        assertFalse(failResult.comparison.passed)
    }

    @Test
    fun `runBarPanel getSelectedCommand returns null when no commands are set`() {
        val panel = createPanel(object : BojFetchService {
            override fun fetchProblemPage(problemNumber: String): String = bojProblemHtml()
        })

        onEdt {
            val runBarPanel = field<RunBarPanel>(panel, "runBarPanel")
            assertNull(runBarPanel.getSelectedCommand())
        }
    }

    @Test
    fun `infers auto run command from current file path`() {
        val javaCommand = BojToolWindowPanel.inferCommandFromFilePath("/workspace/src/main/java/com/boj/Main.java")
        assertEquals("java \"/workspace/src/main/java/com/boj/Main.java\"", javaCommand)

        val kotlinCommand = BojToolWindowPanel.inferCommandFromFilePath("/workspace/src/main/kotlin/com/boj/Solver.kt")
        assertEquals("kotlin com.boj.SolverKt", kotlinCommand)

        val pythonCommand = BojToolWindowPanel.inferCommandFromFilePath("/workspace/solutions/my script.py")
        assertEquals("\"python3\" \"/workspace/solutions/my script.py\"", pythonCommand)

        val unknownCommand = BojToolWindowPanel.inferCommandFromFilePath("/workspace/notes/problem.txt")
        assertNull(unknownCommand)
    }

    @Test
    fun `extracts problem number from class name and file name`() {
        assertEquals("1000", BojToolWindowPanel.extractProblemNumberFromClassName("Main1000"))
        assertEquals("2557", BojToolWindowPanel.extractProblemNumberFromClassName("Boj_2557_AplusB"))
        assertNull(BojToolWindowPanel.extractProblemNumberFromClassName("Main"))

        assertEquals("11720", BojToolWindowPanel.extractProblemNumberFromFileName("Boj11720.java"))
        assertEquals("2748", BojToolWindowPanel.extractProblemNumberFromFileName("Problem2748.kt"))
        assertNull(BojToolWindowPanel.extractProblemNumberFromFileName("Solution.kt"))
    }

    @Test
    fun `json string extraction handles escaped quotes and backslashes`() {
        val json = """{"action":"runSample","index":0,"command":"java \"C:\\path\\Main.java\""}"""
        assertEquals("runSample", BojToolWindowPanel.extractJsonString(json, "action"))
        assertEquals("java \"C:\\path\\Main.java\"", BojToolWindowPanel.extractJsonString(json, "command"))
        assertEquals(0, BojToolWindowPanel.extractJsonInt(json, "index"))

        val simple = """{"action":"runAll","command":"python3 main.py"}"""
        assertEquals("runAll", BojToolWindowPanel.extractJsonString(simple, "action"))
        assertEquals("python3 main.py", BojToolWindowPanel.extractJsonString(simple, "command"))
    }

    @Test
    fun `auto fetch decision follows file-switch sync and dedupe rules`() {
        assertFalse(
            BojToolWindowPanel.shouldAutoFetchFromDetectedProblemNumber(
                forceSyncToCurrentFile = false,
                currentFieldValue = "1000",
                inferredProblemNumber = "2557",
                lastFetchedProblemNumber = "1000",
            ),
        )

        assertTrue(
            BojToolWindowPanel.shouldAutoFetchFromDetectedProblemNumber(
                forceSyncToCurrentFile = true,
                currentFieldValue = "1000",
                inferredProblemNumber = "2557",
                lastFetchedProblemNumber = "1000",
            ),
        )

        assertFalse(
            BojToolWindowPanel.shouldAutoFetchFromDetectedProblemNumber(
                forceSyncToCurrentFile = true,
                currentFieldValue = "2557",
                inferredProblemNumber = "2557",
                lastFetchedProblemNumber = "2557",
            ),
        )

        assertFalse(
            BojToolWindowPanel.shouldAutoFetchFromDetectedProblemNumber(
                forceSyncToCurrentFile = true,
                currentFieldValue = "",
                inferredProblemNumber = "Main1000",
                lastFetchedProblemNumber = null,
            ),
        )
    }

    private fun createPanel(fetchService: BojFetchService): BojToolWindowPanel {
        val projectBasePath = createTempDirectory("boj-workflow").toFile().absolutePath
        val project = fakeProject(projectBasePath)
        return onEdt {
            BojToolWindowPanel(
                project = project,
                fetchService = fetchService,
                parser = parser,
                sampleRunServiceFactory = { command, _ ->
                    object : SampleRunService {
                        override fun runSample(sampleCase: SampleCase) =
                            ProcessSampleRunService(command = command).runSample(sampleCase)
                    }
                },
            )
        }
    }

    private fun fakeProject(basePath: String): Project {
        val handler = InvocationHandler { proxy: Any, method: Method, args: Array<Any?>? ->
            when (method.name) {
                "getBasePath" -> basePath
                "isDisposed" -> false
                "getName" -> "boj-workflow-test"
                "toString" -> "FakeProject(boj-workflow-test)"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> defaultValue(method.returnType)
            }
        }
        return Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java), handler) as Project
    }

    private fun defaultValue(returnType: Class<*>): Any? {
        if (!returnType.isPrimitive) {
            return null
        }
        return when (returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            java.lang.Void.TYPE -> Unit
            else -> null
        }
    }

    private fun bojProblemHtml(): String =
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
            <section id="problem_limit">
              <h2>제한</h2>
              <ul><li>0 &lt; A, B &lt; 10</li></ul>
            </section>
            <section id="sampleinput1"><pre>1 2</pre></section>
            <section id="sampleoutput1"><pre>3</pre></section>
            <section id="sample_explain_1">
              <div id="problem_sample_explain_1" class="problem-text">
                <p>1 + 2 = 3이다.</p>
              </div>
            </section>
            <section id="sampleinput2"><pre>10 20</pre></section>
            <section id="sampleoutput2"><pre>30</pre></section>
          </body>
        </html>
        """.trimIndent()

    private fun invokePrivate(target: Any, methodName: String, vararg args: Any) {
        val method = target::class.java.getDeclaredMethod(methodName, *args.map { it::class.java }.toTypedArray())
        method.isAccessible = true
        method.invoke(target, *args)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(target: Any, fieldName: String): T {
        val field = target::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target) as T
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        val field = target::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun <T> onEdt(block: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait {
            result = runCatching(block)
        }
        return result!!.getOrThrow()
    }
}
