package com.boj.intellij.ui

import com.boj.intellij.fetch.BojFetchService
import com.boj.intellij.fetch.HttpBojFetchService
import com.boj.intellij.parse.BojHtmlParser
import com.boj.intellij.parse.BojParser
import com.boj.intellij.parse.ParsedProblem
import com.boj.intellij.parse.ParsedSamplePair
import com.boj.intellij.sample_run.OutputComparisonResult
import com.boj.intellij.sample_run.ProcessSampleRunService
import com.boj.intellij.sample_run.SampleCase
import com.boj.intellij.sample_run.SampleRunResult
import com.boj.intellij.sample_run.SampleRunService
import com.boj.intellij.service.TestResultService
import com.boj.intellij.ui.testresult.BojTestResultPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.UIManager

class BojToolWindowPanel(
    private val project: Project,
    private val fetchService: BojFetchService = HttpBojFetchService(),
    private val parser: BojParser = BojHtmlParser(),
    private val sampleRunServiceFactory: (command: String, workingDirectory: File?) -> SampleRunService =
        { command, workingDirectory -> ProcessSampleRunService(command = command, workingDirectory = workingDirectory) },
) : JPanel(BorderLayout()), Disposable {
    private val problemNumberField = JTextField(10)
    private val fetchButton = JButton("문제 불러오기")
    private val fetchStatusLabel = JLabel("문제 번호를 입력하거나 현재 클래스명에서 자동 인식해 불러오세요.")

    private val problemViewBrowser: JBCefBrowser? = createProblemViewBrowserOrNull()
    private val problemViewFallbackArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private var currentProblemNumber: String? = null

    // JS↔Kotlin 통신용 JBCefJSQuery
    private val jsCefQuery: JBCefJSQuery? = problemViewBrowser?.let { browser ->
        JBCefJSQuery.create(browser as JBCefBrowserBase).also { query ->
            query.addHandler { request ->
                handleJsQuery(request)
                null // null 반환 시 성공으로 처리
            }
        }
    }

    private var currentSamples: List<ParsedSamplePair> = emptyList()
    private var isFetchInProgress: Boolean = false
    private var lastFetchedProblemNumber: String? = null

    private val baseLabelColor = UIManager.getColor("Label.foreground") ?: Color(0x333333)
    private val failColor = JBColor(Color(0xB3261E), Color(0xFF8A80))

    init {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        add(buildFetchHeader(), BorderLayout.NORTH)
        add(buildMainContent(), BorderLayout.CENTER)

        wireEvents()
        wireCurrentFileTracking()
        resetProblemFields()
        setFetchStatus("문제 번호를 입력하거나 현재 클래스명에서 자동 인식해 불러오세요.", isError = false)
        autoFetchProblemFromCurrentClassName()
    }

    private fun buildFetchHeader(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createEmptyBorder(0, 0, 10, 0)

        val constraints = GridBagConstraints()
        constraints.insets = Insets(0, 0, 0, 8)
        constraints.anchor = GridBagConstraints.WEST

        constraints.gridx = 0
        constraints.gridy = 0
        panel.add(JLabel("문제 번호"), constraints)

        constraints.gridx = 1
        constraints.weightx = 1.0
        constraints.fill = GridBagConstraints.HORIZONTAL
        panel.add(problemNumberField, constraints)

        constraints.gridx = 2
        constraints.weightx = 0.0
        constraints.fill = GridBagConstraints.NONE
        constraints.insets = Insets(0, 0, 0, 0)
        panel.add(fetchButton, constraints)

        constraints.gridx = 0
        constraints.gridy = 1
        constraints.gridwidth = 3
        constraints.weightx = 1.0
        constraints.fill = GridBagConstraints.HORIZONTAL
        constraints.insets = Insets(6, 0, 0, 0)
        panel.add(fetchStatusLabel, constraints)

        return panel
    }

    private fun buildMainContent(): JComponent {
        return problemViewBrowser?.component ?: JScrollPane(problemViewFallbackArea)
    }

    private fun createProblemViewBrowserOrNull(): JBCefBrowser? {
        val application = ApplicationManager.getApplication() ?: return null
        if (application.isUnitTestMode) {
            return null
        }

        return runCatching {
            if (JBCefApp.isSupported()) {
                JBCefBrowser()
            } else {
                null
            }
        }.getOrNull()
    }

    private fun wireEvents() {
        fetchButton.addActionListener { fetchProblem() }
        problemNumberField.addActionListener { fetchProblem() }
    }

    private fun wireCurrentFileTracking() {
        runCatching {
            project.messageBus.connect(project).subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                object : FileEditorManagerListener {
                    override fun selectionChanged(event: FileEditorManagerEvent) {
                        autoFetchProblemFromCurrentClassName(forceSyncToCurrentFile = true)
                    }
                },
            )
        }
    }

    private fun fetchProblem() {
        if (isFetchInProgress) {
            return
        }

        val typedProblemNumber = problemNumberField.text.trim()
        val inferredProblemNumber = if (typedProblemNumber.isBlank()) resolveProblemNumberFromCurrentClassName() else null
        val problemNumber = if (typedProblemNumber.isNotBlank()) typedProblemNumber else inferredProblemNumber

        if (problemNumber == null) {
            setFetchStatus("문제 번호를 입력하거나 클래스명에 문제 번호를 포함하세요.", isError = true)
            return
        }

        if (!problemNumber.all(Char::isDigit)) {
            setFetchStatus("문제 번호는 숫자만 입력하세요.", isError = true)
            return
        }

        val inferredFromClassName = typedProblemNumber.isBlank()
        if (inferredFromClassName) {
            problemNumberField.text = problemNumber
        }

        isFetchInProgress = true
        setFetchInProgress(true)
        setFetchStatus(
            if (inferredFromClassName) {
                "현재 클래스명에서 추출한 문제 $problemNumber 를 불러오는 중입니다..."
            } else {
                "문제 $problemNumber 를 불러오는 중입니다..."
            },
            isError = false,
        )

        runInBackground {
            try {
                val parsedProblem = parser.parse(fetchService.fetchProblemPage(problemNumber))
                runOnEdt {
                    lastFetchedProblemNumber = problemNumber
                    currentProblemNumber = problemNumber
                    bindProblem(parsedProblem)
                    setFetchStatus(
                        if (inferredFromClassName) {
                            "클래스명에서 추출한 문제 $problemNumber 를 성공적으로 불러왔습니다."
                        } else {
                            "문제 $problemNumber 를 성공적으로 불러왔습니다."
                        },
                        isError = false,
                    )
                }
            } catch (exception: Exception) {
                val message = exception.message ?: exception::class.simpleName.orEmpty()
                runOnEdt {
                    resetProblemFields()
                    setFetchStatus("문제 불러오기에 실패했습니다: $message", isError = true)
                }
            } finally {
                runOnEdt {
                    isFetchInProgress = false
                    setFetchInProgress(false)
                }
            }
        }
    }

    private fun autoFetchProblemFromCurrentClassName(forceSyncToCurrentFile: Boolean = false) {
        val inferredProblemNumber = resolveProblemNumberFromCurrentClassName() ?: return

        val currentFieldValue = problemNumberField.text.trim()
        if (!shouldAutoFetchFromDetectedProblemNumber(
                forceSyncToCurrentFile = forceSyncToCurrentFile,
                currentFieldValue = currentFieldValue,
                inferredProblemNumber = inferredProblemNumber,
                lastFetchedProblemNumber = lastFetchedProblemNumber,
            )) {
            return
        }

        problemNumberField.text = inferredProblemNumber
        fetchProblem()
    }

    private fun bindProblem(problem: ParsedProblem) {
        val colors = ThemeColors.fromCurrentTheme()
        val number = currentProblemNumber ?: ""
        val queryInjection = jsCefQuery?.inject("request") ?: ""

        problemViewBrowser?.loadHTML(
            ProblemViewHtmlBuilder.buildHtml(
                problem = problem,
                problemNumber = number,
                colors = colors,
                cefQueryInjection = queryInjection,
            )
        )
        problemViewFallbackArea.text = ProblemViewHtmlBuilder.buildFallbackText(problem = problem, problemNumber = number)
        currentSamples = problem.samplePairs

        // 자동 감지된 실행 명령어를 JS에 전달
        val inferredCommand = resolveCurrentFileRunCommand()
        if (inferredCommand != null) {
            problemViewBrowser?.cefBrowser?.executeJavaScript(
                "if(window.setCommand) window.setCommand(\"${escapeJsString(inferredCommand)}\")", "", 0
            )
        }

        // 하단 ToolWindow에 예제 정보 전달
        val testService = findTestResultService()
        testService?.clearResults()
        testService?.clearSampleInfo()
        problem.samplePairs.forEachIndexed { index, pair ->
            testService?.setSampleInfo(index, pair.input, pair.output)
        }
    }

    // --- JS↔Kotlin 통신 처리 ---

    private fun handleJsQuery(request: String) {
        runInBackground {
            try {
                val action = extractJsonString(request, "action") ?: return@runInBackground
                val command = extractJsonString(request, "command") ?: ""
                val index = extractJsonInt(request, "index")

                when (action) {
                    "runSample" -> {
                        handleRunSample(index ?: return@runInBackground, command)
                    }
                    "runAll" -> handleRunAll(command)
                }
            } catch (_: Exception) {
                // JS 측 onFailure 콜백에서 처리
            }
        }
    }

    private fun handleRunSample(index: Int, rawCommand: String) {
        val sample = currentSamples.getOrNull(index) ?: return
        val command = resolveRunCommand(rawCommand)
        val workingDirectory = project.basePath?.takeIf(String::isNotBlank)?.let(::File)

        try {
            val sampleCase = SampleCase(input = sample.input, expectedOutput = sample.output)
            val result = sampleRunServiceFactory(command, workingDirectory).runSample(sampleCase)
            runOnEdt { sendResultToJs(index, result) }
        } catch (exception: Exception) {
            val message = exception.message ?: exception::class.simpleName.orEmpty()
            val errorResult = SampleRunResult(
                passed = false,
                actualOutput = "",
                expectedOutput = sample.output,
                standardError = message,
                exitCode = null,
                timedOut = false,
                comparison = OutputComparisonResult(
                    passed = false,
                    normalizedExpected = sample.output,
                    normalizedActual = "",
                ),
            )
            runOnEdt { sendResultToJs(index, errorResult) }
        }
    }

    private fun handleRunAll(rawCommand: String) {
        val command = resolveRunCommand(rawCommand)
        val workingDirectory = project.basePath?.takeIf(String::isNotBlank)?.let(::File)
        var passedCount = 0

        // 재실행 시 이전 결과 초기화
        runOnEdt { findTestResultService()?.clearResults() }

        for ((index, sample) in currentSamples.withIndex()) {
            try {
                val sampleCase = SampleCase(input = sample.input, expectedOutput = sample.output)
                val result = sampleRunServiceFactory(command, workingDirectory).runSample(sampleCase)
                if (result.passed) passedCount++
                runOnEdt { sendResultToJs(index, result) }
            } catch (exception: Exception) {
                val message = exception.message ?: exception::class.simpleName.orEmpty()
                val errorResult = SampleRunResult(
                    passed = false,
                    actualOutput = "",
                    expectedOutput = sample.output,
                    standardError = message,
                    exitCode = null,
                    timedOut = false,
                    comparison = OutputComparisonResult(
                        passed = false,
                        normalizedExpected = sample.output,
                        normalizedActual = "",
                    ),
                )
                runOnEdt { sendResultToJs(index, errorResult) }
            }
        }

        val totalCount = currentSamples.size
        val finalPassedCount = passedCount
        runOnEdt {
            problemViewBrowser?.cefBrowser?.executeJavaScript(
                "if(window.onRunAllComplete) window.onRunAllComplete($finalPassedCount, $totalCount)", "", 0
            )
            findTestResultService()?.notifyRunAllComplete(finalPassedCount, totalCount)
        }
    }

    private fun sendResultToJs(index: Int, result: SampleRunResult) {
        val escapedActual = escapeJsString(result.actualOutput)
        val escapedStderr = escapeJsString(result.standardError)
        val exitCodeJs = result.exitCode?.toString() ?: "null"

        problemViewBrowser?.cefBrowser?.executeJavaScript(
            """window.onSampleResult($index, {
                passed: ${result.passed},
                stdout: "$escapedActual",
                stderr: "$escapedStderr",
                exitCode: $exitCodeJs,
                timedOut: ${result.timedOut}
            })""".trimIndent(),
            "", 0
        )

        // 하단 ToolWindow에 결과 전달 및 자동 열기
        findTestResultService()?.addSampleResult(index, result)
        val toolWindow = ToolWindowManager
            .getInstance(project)
            .getToolWindow("BOJ 테스트")
        if (toolWindow != null && !toolWindow.isVisible) {
            toolWindow.show()
        }
    }

    internal fun escapeJsString(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    private fun findTestResultService(): TestResultService? {
        return runCatching {
            val toolWindow = ToolWindowManager
                .getInstance(project)
                .getToolWindow("BOJ 테스트") ?: return null
            val content = toolWindow.contentManager.getContent(0) ?: return null
            val panel = content.component as? BojTestResultPanel ?: return null
            panel.getTestResultService()
        }.getOrNull()
    }

    // --- 기타 헬퍼 ---

    private fun setFetchInProgress(inProgress: Boolean) {
        problemNumberField.isEnabled = !inProgress
        fetchButton.isEnabled = !inProgress
    }

    private fun setFetchStatus(message: String, isError: Boolean) {
        fetchStatusLabel.text = message
        fetchStatusLabel.foreground = if (isError) failColor else baseLabelColor
    }

    private fun resolveRunCommand(rawCommand: String): String {
        val trimmed = rawCommand.trim()
        if (trimmed.isNotBlank()) {
            return trimmed
        }

        val inferredFromCurrentFile = resolveCurrentFileRunCommand()
        if (inferredFromCurrentFile != null) {
            return inferredFromCurrentFile
        }

        return DEFAULT_RUN_COMMAND
    }

    private fun resolveCurrentFileRunCommand(): String? {
        val selectedFilePath =
            runCatching {
                FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.path
            }.getOrNull() ?: return null

        return inferCommandFromFilePath(selectedFilePath)
    }

    private fun resolveProblemNumberFromCurrentClassName(): String? {
        val selectedFileName =
            runCatching {
                FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.name
            }.getOrNull() ?: return null

        return extractProblemNumberFromFileName(selectedFileName)
    }

    private fun resetProblemFields() {
        problemViewBrowser?.loadHTML("")
        problemViewFallbackArea.text = ""

        currentSamples = emptyList()
        currentProblemNumber = null
        lastFetchedProblemNumber = null
    }

    private fun runInBackground(task: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread(task)
    }

    private fun runOnEdt(task: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(task)
    }

    override fun dispose() {
        jsCefQuery?.let(Disposer::dispose)
        problemViewBrowser?.let(Disposer::dispose)
    }

    companion object {
        private const val DEFAULT_RUN_COMMAND = "./main"

        @JvmStatic
        internal fun inferCommandFromFilePath(filePath: String): String? {
            val normalizedPath = filePath.replace('\\', '/')
            val file = File(normalizedPath)
            val stem = file.nameWithoutExtension.takeIf(String::isNotBlank) ?: return null

            return when (file.extension.lowercase()) {
                "java" -> "java ${quoteForShell(file.path)}"
                "kt" -> "kotlin ${inferJvmMainClassName(normalizedPath, "${stem}Kt")}"
                "py" -> "python3 ${quoteForShell(file.path)}"
                "js" -> "node ${quoteForShell(file.path)}"
                "ts" -> "ts-node ${quoteForShell(file.path)}"
                "go" -> "go run ${quoteForShell(file.path)}"
                "sh" -> "bash ${quoteForShell(file.path)}"
                "c", "cc", "cpp", "cxx" -> quoteForShell(file.path.removeSuffix(".${file.extension}"))
                else -> null
            }
        }

        @JvmStatic
        internal fun extractProblemNumberFromClassName(className: String): String? {
            return PROBLEM_NUMBER_PATTERN.find(className)?.value
        }

        @JvmStatic
        internal fun extractProblemNumberFromFileName(fileName: String): String? {
            val className = fileName.substringBeforeLast('.', fileName)
            return extractProblemNumberFromClassName(className)
        }

        @JvmStatic
        internal fun shouldAutoFetchFromDetectedProblemNumber(
            forceSyncToCurrentFile: Boolean,
            currentFieldValue: String,
            inferredProblemNumber: String?,
            lastFetchedProblemNumber: String?,
        ): Boolean {
            if (inferredProblemNumber.isNullOrBlank()) {
                return false
            }
            if (!inferredProblemNumber.all(Char::isDigit)) {
                return false
            }
            if (!forceSyncToCurrentFile && currentFieldValue.isNotBlank()) {
                return false
            }
            if (currentFieldValue == inferredProblemNumber && lastFetchedProblemNumber == inferredProblemNumber) {
                return false
            }
            return true
        }

        private fun inferJvmMainClassName(normalizedPath: String, simpleClassName: String): String {
            val sourceRootMarker = SOURCE_ROOT_MARKERS.firstOrNull { normalizedPath.contains(it) } ?: return simpleClassName
            val relativePath = normalizedPath.substringAfter(sourceRootMarker)
            val packageDirectory = relativePath.substringBeforeLast('/', "")
            if (packageDirectory.isBlank()) {
                return simpleClassName
            }

            val packageName = packageDirectory.split('/').filter(String::isNotBlank).joinToString(".")
            if (packageName.isBlank()) {
                return simpleClassName
            }
            return "$packageName.$simpleClassName"
        }

        private fun quoteForShell(path: String): String {
            val escaped = path.replace("\\", "\\\\").replace("\"", "\\\"")
            return "\"$escaped\""
        }

        private val SOURCE_ROOT_MARKERS =
            listOf(
                "/src/main/java/",
                "/src/test/java/",
                "/src/main/kotlin/",
                "/src/test/kotlin/",
            )

        private val PROBLEM_NUMBER_PATTERN = Regex("\\d+")

        // JSON 이스케이프된 문자열 값 추출 (\" 포함 처리)
        private val JSON_STRING_PATTERN = """(?:[^"\\]|\\.)*"""

        @JvmStatic
        internal fun extractJsonString(json: String, key: String): String? {
            val pattern = """"${Regex.escape(key)}"\s*:\s*"($JSON_STRING_PATTERN)"""".toRegex()
            val raw = pattern.find(json)?.groupValues?.get(1) ?: return null
            return unescapeJsonString(raw)
        }

        @JvmStatic
        internal fun extractJsonInt(json: String, key: String): Int? {
            val pattern = """"${Regex.escape(key)}"\s*:\s*(\d+)""".toRegex()
            return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
        }

        private fun unescapeJsonString(value: String): String =
            value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
    }
}
