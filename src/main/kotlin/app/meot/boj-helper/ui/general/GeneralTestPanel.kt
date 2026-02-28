package com.boj.intellij.ui.general

import com.boj.intellij.general.GeneralTestCase
import com.boj.intellij.general.GeneralTestCaseRepository
import com.boj.intellij.sample_run.OutputComparisonResult
import com.boj.intellij.sample_run.ProcessSampleRunService
import com.boj.intellij.sample_run.SampleCase
import com.boj.intellij.sample_run.SampleRunResult
import com.boj.intellij.sample_run.SampleRunService
import com.boj.intellij.service.TestCaseKey
import com.boj.intellij.service.TestResultService
import com.boj.intellij.settings.BojSettings
import com.boj.intellij.ui.BojToolWindowPanel
import com.boj.intellij.ui.PythonInterpreterResolver
import com.boj.intellij.ui.RunBarPanel
import com.boj.intellij.ui.RunConfigurationCommandResolver
import com.boj.intellij.ui.testresult.BojTestResultPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager

class GeneralTestPanel(
    private val project: Project,
    private val sampleRunServiceFactory: (command: String, workingDirectory: File?) -> SampleRunService =
        { command, workingDirectory ->
            val timeoutMs = runCatching {
                BojSettings.getInstance().state.timeoutSeconds * 1000L
            }.getOrDefault(SampleRunService.DEFAULT_TIMEOUT_MILLIS)
            ProcessSampleRunService(command = command, timeoutMillis = timeoutMs, workingDirectory = workingDirectory)
        },
) : JPanel(BorderLayout()), Disposable {

    private val repository: GeneralTestCaseRepository by lazy {
        val basePath = project.basePath ?: throw IllegalStateException("Project basePath is null")
        GeneralTestCaseRepository(File(basePath, ".boj"))
    }

    private var currentFileKey: String? = null
    private val fileLabel = JLabel("파일을 열어주세요")
    private val testCaseListPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    private val testCaseEntries = mutableListOf<TestCaseEntryPanel>()

    private val runBarPanel = RunBarPanel(
        onRunAll = { command -> runInBackground { handleRunAll(command) } },
        onStop = { handleStop() },
        showCopyButton = false,
    )

    @Volatile
    private var cancelRequested = false

    init {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        add(buildHeader(), BorderLayout.NORTH)
        add(buildCenter(), BorderLayout.CENTER)
        add(runBarPanel, BorderLayout.SOUTH)

        wireCurrentFileTracking()
        detectCurrentFile()
    }

    // --- Layout ---

    private fun buildHeader(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(0, 0, 8, 0)

        fileLabel.font = fileLabel.font.deriveFont(Font.BOLD, 13f)
        panel.add(fileLabel, BorderLayout.CENTER)

        val refreshButton = JButton(AllIcons.Actions.Refresh)
        refreshButton.toolTipText = "새로고침"
        refreshButton.isBorderPainted = false
        refreshButton.isContentAreaFilled = false
        refreshButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        refreshButton.addActionListener { reloadTestCases() }
        panel.add(refreshButton, BorderLayout.EAST)

        return panel
    }

    private fun buildCenter(): JPanel {
        val centerPanel = JPanel(BorderLayout())

        val scrollPane = JBScrollPane(testCaseListPanel)
        scrollPane.border = BorderFactory.createEmptyBorder()
        centerPanel.add(scrollPane, BorderLayout.CENTER)

        val addButton = JButton("+ 테스트 추가")
        addButton.addActionListener { addNewTestCase() }
        val addPanel = JPanel(FlowLayout(FlowLayout.CENTER))
        addPanel.add(addButton)
        centerPanel.add(addPanel, BorderLayout.SOUTH)

        return centerPanel
    }

    // --- File tracking ---

    private fun isActiveTab(): Boolean {
        return runCatching {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("BOJ") ?: return false
            toolWindow.contentManager.selectedContent?.component === this
        }.getOrDefault(false)
    }

    private fun wireCurrentFileTracking() {
        runCatching {
            project.messageBus.connect(project).subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                object : FileEditorManagerListener {
                    override fun selectionChanged(event: FileEditorManagerEvent) {
                        if (!isActiveTab()) return
                        onFileChanged()
                    }
                },
            )
        }
    }

    private fun resolveFileKey(file: VirtualFile): String? {
        val basePath = project.basePath ?: return file.name
        return file.path.removePrefix(basePath).removePrefix("/")
    }

    private fun detectCurrentFile() {
        val selectedFile = runCatching {
            FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        }.getOrNull() ?: return
        val fileKey = resolveFileKey(selectedFile) ?: return
        switchToFile(fileKey, selectedFile.name)
    }

    fun onTabSelected() {
        onFileChanged()
    }

    private fun onFileChanged() {
        val selectedFile = runCatching {
            FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        }.getOrNull()
        val fileKey = selectedFile?.let { resolveFileKey(it) }
        if (fileKey != currentFileKey) {
            saveAllTestCases()
            switchToFile(fileKey, selectedFile?.name)
        }
        updateRunBarCommands()
        syncTestResultPanel()
    }

    private fun syncTestResultPanel() {
        val testService = findTestResultService() ?: return
        val panel = findTestResultPanel() ?: return

        testService.clearResults()
        testService.clearSampleInfo()

        val fileKey = currentFileKey
        val cases = if (fileKey != null) repository.load(fileKey) else emptyMap()

        if (cases.isEmpty()) {
            panel.populateEntries(0, emptyList(), emptyList())
            return
        }

        testService.clearGeneralCaseInfo()
        for ((name, case) in cases) {
            val key = TestCaseKey.General(name)
            testService.setCaseInfo(key, case.input, case.expectedOutput)
        }

        val generalKeys = cases.keys.map { TestCaseKey.General(it) }
        panel.populateEntries(0, emptyList(), generalKeys)

        panel.onRunSingle = { key ->
            val command = runBarPanel.getSelectedCommand() ?: resolveCurrentFileRunCommand()
            if (command != null) {
                runInBackground { handleRunSingle((key as TestCaseKey.General).name, command) }
            }
        }
        panel.onRunAll = {
            val command = runBarPanel.getSelectedCommand() ?: resolveCurrentFileRunCommand()
            if (command != null) {
                runInBackground { handleRunAll(command) }
            }
        }
        panel.onStop = { handleStop() }
        panel.onAddCustom = {}
        panel.onManageCustom = {}
        panel.onEditCustom = {}
        panel.onDeleteCustom = {}
    }

    private fun switchToFile(fileKey: String?, displayName: String? = null) {
        currentFileKey = fileKey
        if (fileKey == null) {
            fileLabel.text = "파일을 열어주세요"
            clearTestCaseEntries()
            return
        }
        fileLabel.text = displayName ?: fileKey
        updateRunBarCommands()
        if (resolveCurrentFileRunCommand() == null) {
            clearTestCaseEntries()
            return
        }
        loadTestCases()
    }

    // --- Test case management ---

    private fun loadTestCases() {
        clearTestCaseEntries()
        val fileKey = currentFileKey ?: return
        val cases = repository.load(fileKey)
        if (cases.isEmpty()) {
            addNewTestCase()
        } else {
            for ((name, case) in cases) {
                addTestCaseEntry(name, case.input, case.expectedOutput)
            }
        }
        testCaseListPanel.revalidate()
        testCaseListPanel.repaint()
    }

    private fun reloadTestCases() {
        saveAllTestCases()
        loadTestCases()
    }

    private fun clearTestCaseEntries() {
        testCaseEntries.clear()
        testCaseListPanel.removeAll()
        testCaseListPanel.revalidate()
        testCaseListPanel.repaint()
    }

    private fun addNewTestCase() {
        val fileKey = currentFileKey ?: return
        if (resolveCurrentFileRunCommand() == null) return
        val name = repository.nextAutoName(fileKey)
        addTestCaseEntry(name, "", "")
        testCaseListPanel.revalidate()
        testCaseListPanel.repaint()
    }

    private fun addTestCaseEntry(name: String, input: String, expectedOutput: String) {
        val entry = TestCaseEntryPanel(
            testName = name,
            input = input,
            expectedOutput = expectedOutput,
            onRun = { entryPanel -> runSingleTestCase(entryPanel) },
            onDelete = { entryPanel -> deleteTestCase(entryPanel) },
        )
        testCaseEntries.add(entry)
        testCaseListPanel.add(entry)
    }

    private fun deleteTestCase(entry: TestCaseEntryPanel) {
        val fileKey = currentFileKey ?: return
        val result = Messages.showYesNoDialog(
            project,
            "테스트 '${entry.testName}'을(를) 삭제하시겠습니까?",
            "테스트 삭제",
            Messages.getQuestionIcon(),
        )
        if (result != Messages.YES) return
        repository.delete(fileKey, entry.testName)
        testCaseEntries.remove(entry)
        testCaseListPanel.remove(entry)
        testCaseListPanel.revalidate()
        testCaseListPanel.repaint()
        syncTestResultPanel()
    }

    fun saveAllTestCases() {
        val fileKey = currentFileKey ?: return
        for (entry in testCaseEntries) {
            val case = GeneralTestCase(
                input = entry.getInput(),
                expectedOutput = entry.getExpectedOutput(),
            )
            repository.save(fileKey, entry.testName, case)
        }
    }

    // --- Run ---

    private fun runSingleTestCase(entry: TestCaseEntryPanel) {
        val command = runBarPanel.getSelectedCommand() ?: resolveCurrentFileRunCommand() ?: return
        runInBackground { handleRunSingle(entry.testName, command) }
    }

    private fun handleRunSingle(testName: String, command: String) {
        saveActiveEditorDocument()
        saveAllTestCasesOnEdt()
        val fileKey = currentFileKey ?: return
        val workingDirectory = project.basePath?.takeIf(String::isNotBlank)?.let(::File)
        val case = repository.load(fileKey)[testName] ?: return
        val key = TestCaseKey.General(testName)

        runOnEdt {
            val testService = findTestResultService()
            testService?.clearGeneralCaseInfo()
            testService?.setCaseInfo(key, case.input, case.expectedOutput)
            val panel = findTestResultPanel()
            panel?.populateEntries(0, emptyList(), listOf(key))
            panel?.setRunning(key)
            panel?.setRunningState(true)
            showTestResultToolWindow()
        }

        try {
            val sampleCase = SampleCase(input = case.input, expectedOutput = case.expectedOutput)
            val result = sampleRunServiceFactory(command, workingDirectory).runSample(sampleCase)
            runOnEdt {
                findTestResultService()?.addResult(key, result)
                showTestResultToolWindow()
            }
        } catch (exception: Exception) {
            val message = exception.message ?: exception::class.simpleName.orEmpty()
            runOnEdt {
                findTestResultService()?.addResult(key, createErrorResult(case.expectedOutput, message))
                showTestResultToolWindow()
            }
        } finally {
            runOnEdt { findTestResultPanel()?.setRunningState(false) }
        }
    }

    private fun handleRunAll(command: String) {
        saveActiveEditorDocument()
        saveAllTestCasesOnEdt()
        val fileKey = currentFileKey ?: return
        val workingDirectory = project.basePath?.takeIf(String::isNotBlank)?.let(::File)
        val cases = repository.load(fileKey)
        if (cases.isEmpty()) return

        var passedCount = 0
        var judgedCount = 0
        cancelRequested = false

        val generalKeys = cases.keys.map { TestCaseKey.General(it) }

        runOnEdt {
            runBarPanel.setRunning(true)
            val testService = findTestResultService()
            testService?.clearResults()
            testService?.clearGeneralCaseInfo()
            for ((name, case) in cases) {
                val key = TestCaseKey.General(name)
                testService?.setCaseInfo(key, case.input, case.expectedOutput)
            }
            val panel = findTestResultPanel()
            panel?.populateEntries(0, emptyList(), generalKeys)
            panel?.setRunningState(true)
            panel?.setAllRunning()
            showTestResultToolWindow()
        }

        for ((name, case) in cases) {
            if (cancelRequested) break
            val key = TestCaseKey.General(name)
            runOnEdt { findTestResultPanel()?.setRunning(key) }

            val sampleCase = SampleCase(input = case.input, expectedOutput = case.expectedOutput)
            try {
                val result = sampleRunServiceFactory(command, workingDirectory).runSample(sampleCase)
                if (cancelRequested) break
                judgedCount++
                if (result.passed) passedCount++
                runOnEdt { findTestResultService()?.addResult(key, result) }
            } catch (exception: Exception) {
                if (cancelRequested) break
                val message = exception.message ?: exception::class.simpleName.orEmpty()
                val errorResult = createErrorResult(case.expectedOutput, message)
                judgedCount++
                runOnEdt { findTestResultService()?.addResult(key, errorResult) }
            }
        }

        val finalPassedCount = passedCount
        val finalJudgedCount = judgedCount
        val wasCancelled = cancelRequested
        runOnEdt {
            runBarPanel.setRunning(false)
            findTestResultPanel()?.setRunningState(false)
            if (wasCancelled) {
                findTestResultPanel()?.markRemainingAsCancelled()
                runBarPanel.updateStatus("중지됨")
            } else {
                runBarPanel.updateStatus("$finalPassedCount/$finalJudgedCount 통과")
                findTestResultService()?.notifyRunAllComplete(finalPassedCount, finalJudgedCount)
            }
        }
    }

    private fun handleStop() {
        cancelRequested = true
    }

    // --- Helpers ---

    private fun createErrorResult(expectedOutput: String, errorMessage: String): SampleRunResult {
        return SampleRunResult(
            passed = false,
            actualOutput = "",
            expectedOutput = expectedOutput,
            standardError = errorMessage,
            exitCode = null,
            timedOut = false,
            comparison = OutputComparisonResult(
                passed = false,
                normalizedExpected = expectedOutput,
                normalizedActual = "",
            ),
        )
    }

    private fun updateRunBarCommands() {
        val commands = mutableListOf<RunBarPanel.CommandEntry>()
        val inferredCommand = resolveCurrentFileRunCommand()
        if (inferredCommand != null) {
            val selectedFilePath = runCatching {
                FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.path
            }.getOrNull()
            val displayName = if (selectedFilePath != null) {
                "(자동) ${RunConfigurationCommandResolver.getDisplayName(selectedFilePath)}"
            } else {
                "(자동 감지)"
            }
            commands.add(RunBarPanel.CommandEntry(displayName, inferredCommand))
        }
        runBarPanel.setAvailableCommands(commands)
    }

    private fun resolveCurrentFileRunCommand(): String? {
        val selectedFilePath = runCatching {
            FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.path
        }.getOrNull() ?: return null

        val pythonInterpreter = PythonInterpreterResolver.resolve(project)
        return BojToolWindowPanel.inferCommandFromFilePath(selectedFilePath, pythonInterpreter)
    }

    private fun findTestResultService(): TestResultService? {
        return runCatching {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("BOJ 테스트") ?: return null
            val content = toolWindow.contentManager.getContent(0) ?: return null
            val panel = content.component as? BojTestResultPanel ?: return null
            panel.getTestResultService()
        }.getOrNull()
    }

    private fun findTestResultPanel(): BojTestResultPanel? {
        return runCatching {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("BOJ 테스트") ?: return null
            val content = toolWindow.contentManager.getContent(0) ?: return null
            content.component as? BojTestResultPanel
        }.getOrNull()
    }

    private fun showTestResultToolWindow() {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("BOJ 테스트")
        if (toolWindow != null && !toolWindow.isVisible) {
            toolWindow.show()
        }
    }

    private fun saveActiveEditorDocument() {
        runOnEdt {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            editor?.document?.let { FileDocumentManager.getInstance().saveDocument(it) }
        }
    }

    private fun saveAllTestCasesOnEdt() {
        ApplicationManager.getApplication().invokeAndWait { saveAllTestCases() }
    }

    private fun runInBackground(task: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread(task)
    }

    private fun runOnEdt(task: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(task)
    }

    override fun dispose() {
        saveAllTestCases()
    }

    // --- Inner class: TestCaseEntryPanel ---

    class TestCaseEntryPanel(
        val testName: String,
        input: String,
        expectedOutput: String,
        private val onRun: (TestCaseEntryPanel) -> Unit,
        private val onDelete: (TestCaseEntryPanel) -> Unit,
    ) : JPanel(BorderLayout()) {

        private val inputArea = JBTextArea(input, 3, 0).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            lineWrap = true
        }
        private val expectedOutputArea = JBTextArea(expectedOutput, 3, 0).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            lineWrap = true
        }

        init {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, resolveEntryBorderColor()),
                BorderFactory.createEmptyBorder(6, 0, 6, 0),
            )
            maximumSize = Dimension(Int.MAX_VALUE, MAX_ENTRY_HEIGHT)

            val contentPanel = JPanel(GridBagLayout())
            val c = GridBagConstraints()

            // Header row: name + run + delete (spans 2 columns)
            c.gridx = 0; c.gridy = 0
            c.gridwidth = 2
            c.weightx = 1.0; c.weighty = 0.0
            c.fill = GridBagConstraints.HORIZONTAL
            c.insets = Insets(0, 0, 4, 0)
            contentPanel.add(buildHeader(), c)

            // Left column: 입력
            c.gridwidth = 1
            c.gridx = 0; c.gridy = 1
            c.weightx = 0.5; c.weighty = 0.0
            c.fill = GridBagConstraints.HORIZONTAL
            c.insets = Insets(0, 0, 2, 4)
            contentPanel.add(createFieldLabel("입력"), c)

            c.gridy = 2; c.weighty = 1.0; c.fill = GridBagConstraints.BOTH
            c.insets = Insets(0, 0, 0, 4)
            contentPanel.add(JBScrollPane(inputArea).apply {
                preferredSize = Dimension(0, TEXTAREA_HEIGHT)
                minimumSize = Dimension(0, TEXTAREA_MIN_HEIGHT)
            }, c)

            // Right column: 기대 출력
            c.gridx = 1; c.gridy = 1
            c.weightx = 0.5; c.weighty = 0.0
            c.fill = GridBagConstraints.HORIZONTAL
            c.insets = Insets(0, 4, 2, 0)
            contentPanel.add(createFieldLabel("기대 출력"), c)

            c.gridy = 2; c.weighty = 1.0; c.fill = GridBagConstraints.BOTH
            c.insets = Insets(0, 4, 0, 0)
            contentPanel.add(JBScrollPane(expectedOutputArea).apply {
                preferredSize = Dimension(0, TEXTAREA_HEIGHT)
                minimumSize = Dimension(0, TEXTAREA_MIN_HEIGHT)
            }, c)

            add(contentPanel, BorderLayout.CENTER)
        }

        private fun buildHeader(): JPanel {
            val panel = JPanel(BorderLayout())

            val nameLabel = JLabel("Test $testName")
            nameLabel.font = nameLabel.font.deriveFont(Font.BOLD)
            panel.add(nameLabel, BorderLayout.CENTER)

            val buttonsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0))
            buttonsPanel.isOpaque = false

            val runButton = JButton(AllIcons.Actions.Execute)
            runButton.toolTipText = "실행"
            runButton.isBorderPainted = false
            runButton.isContentAreaFilled = false
            runButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            runButton.preferredSize = Dimension(24, 24)
            runButton.addActionListener { onRun(this@TestCaseEntryPanel) }
            buttonsPanel.add(runButton)

            val deleteButton = JButton(AllIcons.Actions.Close)
            deleteButton.toolTipText = "삭제"
            deleteButton.isBorderPainted = false
            deleteButton.isContentAreaFilled = false
            deleteButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            deleteButton.preferredSize = Dimension(24, 24)
            deleteButton.addActionListener { onDelete(this@TestCaseEntryPanel) }
            buttonsPanel.add(deleteButton)

            panel.add(buttonsPanel, BorderLayout.EAST)
            return panel
        }

        private fun createFieldLabel(text: String): JLabel {
            return JLabel(text).apply {
                foreground = UIManager.getColor("Label.disabledForeground")
                font = font.deriveFont(11f)
            }
        }

        fun getInput(): String = inputArea.text
        fun getExpectedOutput(): String = expectedOutputArea.text

        companion object {
            private const val MAX_ENTRY_HEIGHT = 200
            private const val TEXTAREA_HEIGHT = 80
            private const val TEXTAREA_MIN_HEIGHT = 40

            private fun resolveEntryBorderColor(): Color {
                return runCatching { JBColor.border() }.getOrElse { Color(0xD0D7DE) }
            }
        }
    }
}
