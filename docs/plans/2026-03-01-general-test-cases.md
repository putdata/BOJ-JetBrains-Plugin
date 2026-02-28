# General Test Cases 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 백준 문제 없이 임의의 소스 파일에 대해 커스텀 테스트케이스를 만들어 실행할 수 있는 "일반" 탭 추가

**Architecture:** BOJ 우측 도구창에 ContentFactory 탭을 2개("백준"/"일반")로 분리. 일반 탭은 파일 단위로 .in/.out 테스트케이스를 관리하고, 기존 SampleRunService와 하단 BojTestResultPanel을 재사용하여 실행/결과 표시

**Tech Stack:** Kotlin, IntelliJ Platform SDK (ToolWindow ContentFactory, JBTextArea, JBScrollPane), 기존 ProcessSampleRunService

**Design Doc:** `docs/plans/2026-03-01-general-test-cases-design.md`

---

### Task 1: TestCaseKey에 General 타입 추가

**Files:**
- Modify: `src/main/kotlin/app/meot/boj-helper/service/TestCaseKey.kt:3-6`

**Step 1: General 타입 추가**

`TestCaseKey.kt`에 `General` data class를 추가:

```kotlin
sealed class TestCaseKey {
    data class Sample(val index: Int) : TestCaseKey()
    data class Custom(val name: String) : TestCaseKey()
    data class General(val name: String) : TestCaseKey()
}
```

**Step 2: when 분기 컴파일 에러 확인**

Run: `./gradlew compileKotlin 2>&1 | head -50`
Expected: `when` exhaustive 체크로 인한 컴파일 에러 발생 (BojTestResultPanel, BojToolWindowPanel 등에서)

이 에러들은 이후 Task에서 수정.

**Step 3: 커밋**

```bash
git add src/main/kotlin/app/meot/boj-helper/service/TestCaseKey.kt
git commit -m "feat: TestCaseKey에 General 타입 추가"
```

---

### Task 2: BojTestResultPanel에서 General 타입 지원

**Files:**
- Modify: `src/main/kotlin/app/meot/boj-helper/ui/testresult/BojTestResultPanel.kt`

**Step 1: `updateResultByKey`의 when 분기에 General 추가 (215-218줄)**

```kotlin
val hasExpectedOutput = when (key) {
    is TestCaseKey.Sample -> true
    is TestCaseKey.Custom -> testResultService.getCaseExpectedOutput(key) != null
    is TestCaseKey.General -> true  // General은 항상 기대 출력 필수
}
```

**Step 2: `showDetail`의 when 분기에 General 추가 (270-279줄)**

input 부분 (270-273줄):
```kotlin
val input = when (entry.key) {
    is TestCaseKey.Sample -> testResultService.getSampleInput(entry.key.index) ?: ""
    is TestCaseKey.Custom -> testResultService.getCaseInput(entry.key) ?: ""
    is TestCaseKey.General -> testResultService.getCaseInput(entry.key) ?: ""
}
```

expectedArea 부분 (277-280줄):
```kotlin
expectedArea.text = when (entry.key) {
    is TestCaseKey.Sample -> testResultService.getSampleExpectedOutput((entry.key).index) ?: ""
    is TestCaseKey.Custom -> testResultService.getCaseExpectedOutput(entry.key) ?: ""
    is TestCaseKey.General -> testResultService.getCaseExpectedOutput(entry.key) ?: ""
}
```

**Step 3: `TestResultEntry.toString()`에 General 추가 (445-448줄)**

```kotlin
override fun toString(): String = when (key) {
    is TestCaseKey.Sample -> "예제 ${key.index + 1}"
    is TestCaseKey.Custom -> key.name
    is TestCaseKey.General -> key.name
}
```

**Step 4: `populateEntries` 메서드에 generalKeys 파라미터 추가 (130줄)**

```kotlin
fun populateEntries(
    sampleCount: Int,
    customKeys: List<TestCaseKey.Custom>,
    generalKeys: List<TestCaseKey.General> = emptyList(),
) {
    listModel.clear()
    for (i in 0 until sampleCount) {
        listModel.addElement(TestResultEntry(key = TestCaseKey.Sample(i)))
    }
    for (customKey in customKeys) {
        listModel.addElement(TestResultEntry(key = customKey))
    }
    for (generalKey in generalKeys) {
        listModel.addElement(TestResultEntry(key = generalKey))
    }
    if (listModel.size() > 0) {
        resultList.selectedIndex = 0
    }
    summaryText = "실행 대기 중"
    inputArea.text = ""
    expectedArea.text = ""
    actualArea.text = ""
}
```

**Step 5: 컴파일 확인**

Run: `./gradlew compileKotlin 2>&1 | head -50`
Expected: BojTestResultPanel 관련 에러 해소. BojToolWindowPanel의 `handleRunSingle` when 분기 에러는 남아있을 수 있음.

**Step 6: 커밋**

```bash
git add src/main/kotlin/app/meot/boj-helper/ui/testresult/BojTestResultPanel.kt
git commit -m "feat: BojTestResultPanel에서 General 테스트케이스 타입 지원"
```

---

### Task 3: BojToolWindowPanel의 when 분기 수정

**Files:**
- Modify: `src/main/kotlin/app/meot/boj-helper/ui/BojToolWindowPanel.kt:364-402`

**Step 1: `handleRunSingle`의 when 분기에 General 추가**

`handleRunSingle` 메서드 (364줄)의 `when (key)` 블록에 General 분기 추가. General 키는 BojToolWindowPanel에서 사용하지 않으므로 no-op 처리:

```kotlin
when (key) {
    is TestCaseKey.Sample -> {
        // ... 기존 코드 그대로
    }
    is TestCaseKey.Custom -> {
        // ... 기존 코드 그대로
    }
    is TestCaseKey.General -> {
        // General 케이스는 GeneralTestPanel에서 처리 - 여기서는 무시
    }
}
```

**Step 2: 컴파일 확인**

Run: `./gradlew compileKotlin 2>&1 | head -30`
Expected: BUILD SUCCESSFUL (모든 when exhaustive 에러 해소)

**Step 3: 커밋**

```bash
git add src/main/kotlin/app/meot/boj-helper/ui/BojToolWindowPanel.kt
git commit -m "feat: BojToolWindowPanel에서 General TestCaseKey 분기 처리"
```

---

### Task 4: TestResultService에 General 키 관리 메서드 추가

**Files:**
- Modify: `src/main/kotlin/app/meot/boj-helper/service/TestResultService.kt`

**Step 1: getGeneralKeys() 메서드 추가 (92줄 근처)**

`getCustomKeys()` 아래에 추가:

```kotlin
fun getGeneralKeys(): List<TestCaseKey.General> =
    caseInputs.keys.filterIsInstance<TestCaseKey.General>()
```

**Step 2: clearGeneralCaseInfo() 메서드 추가 (clearCustomCaseInfo() 아래)**

```kotlin
fun clearGeneralCaseInfo() {
    val generalKeys = caseInputs.keys.filterIsInstance<TestCaseKey.General>()
    for (key in generalKeys) {
        caseInputs.remove(key)
        caseExpectedOutputs.remove(key)
        keyedResults.remove(key)
    }
}
```

**Step 3: 컴파일 확인**

Run: `./gradlew compileKotlin 2>&1 | head -20`
Expected: BUILD SUCCESSFUL

**Step 4: 커밋**

```bash
git add src/main/kotlin/app/meot/boj-helper/service/TestResultService.kt
git commit -m "feat: TestResultService에 General 키 관리 메서드 추가"
```

---

### Task 5: GeneralTestCaseRepository 구현

**Files:**
- Create: `src/main/kotlin/app/meot/boj-helper/general/GeneralTestCaseRepository.kt`

**Step 1: Repository 클래스 작성**

```kotlin
package com.boj.intellij.general

import java.io.File

data class GeneralTestCase(
    val input: String,
    val expectedOutput: String,
)

class GeneralTestCaseRepository(
    private val baseDir: File,
) {
    fun load(fileName: String): Map<String, GeneralTestCase> {
        val dir = caseDir(fileName)
        if (!dir.isDirectory) return emptyMap()

        val inFiles = dir.listFiles()?.filter { it.extension == "in" } ?: return emptyMap()
        return inFiles.associate { inFile ->
            val name = inFile.nameWithoutExtension
            val input = inFile.readText()
            val outFile = File(dir, "$name.out")
            val expectedOutput = if (outFile.exists()) outFile.readText() else ""
            name to GeneralTestCase(input = input, expectedOutput = expectedOutput)
        }
    }

    fun save(fileName: String, testName: String, case: GeneralTestCase) {
        val dir = caseDir(fileName)
        dir.mkdirs()
        val safeName = sanitizeFileName(testName)
        File(dir, "$safeName.in").writeText(case.input)
        File(dir, "$safeName.out").writeText(case.expectedOutput)
    }

    fun delete(fileName: String, testName: String) {
        val dir = caseDir(fileName)
        val safeName = sanitizeFileName(testName)
        File(dir, "$safeName.in").delete()
        File(dir, "$safeName.out").delete()
    }

    fun nextAutoName(fileName: String): String {
        val existing = load(fileName).keys
        var counter = 1
        while ("테스트 $counter" in existing) {
            counter++
        }
        return "테스트 $counter"
    }

    private fun caseDir(fileName: String): File =
        File(baseDir, "general-cases/${sanitizeFileName(fileName)}")

    companion object {
        fun sanitizeFileName(name: String): String =
            name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
    }
}
```

**Step 2: 컴파일 확인**

Run: `./gradlew compileKotlin 2>&1 | head -20`
Expected: BUILD SUCCESSFUL

**Step 3: 커밋**

```bash
git add src/main/kotlin/app/meot/boj-helper/general/GeneralTestCaseRepository.kt
git commit -m "feat: GeneralTestCaseRepository 구현 (.in/.out 파일 기반)"
```

---

### Task 6: GeneralTestPanel UI 구현

**Files:**
- Create: `src/main/kotlin/app/meot/boj-helper/ui/general/GeneralTestPanel.kt`

**Step 1: GeneralTestPanel 클래스 작성**

이 패널은 "일반" 탭의 메인 UI. 구성:
- 상단: 현재 바인딩된 파일명 표시
- 중앙: 테스트케이스 목록 (스크롤 가능, 각 항목에 입력/기대출력 텍스트 영역)
- 하단: RunBar (명령어 선택 + 전체 실행/중지)

```kotlin
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
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
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

    private var currentFileName: String? = null
    private val fileLabel = JBLabel("파일을 선택하세요")
    private val testCasesPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    private val testCaseEntries = mutableListOf<TestCaseEntryPanel>()

    @Volatile
    private var cancelRequested = false

    private val runBarPanel = RunBarPanel(
        onRunAll = { command -> runInBackground { handleRunAll(command) } },
        onStop = { cancelRequested = true },
    )

    init {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        add(buildHeader(), BorderLayout.NORTH)
        add(JBScrollPane(testCasesPanel), BorderLayout.CENTER)
        add(buildFooter(), BorderLayout.SOUTH)

        wireCurrentFileTracking()
        syncToCurrentFile()
    }

    private fun buildHeader(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(0, 0, 8, 0)

        fileLabel.font = fileLabel.font.deriveFont(Font.BOLD)
        panel.add(fileLabel, BorderLayout.CENTER)

        val refreshButton = JButton(AllIcons.Actions.Refresh)
        refreshButton.toolTipText = "현재 파일로 새로고침"
        refreshButton.isBorderPainted = false
        refreshButton.isContentAreaFilled = false
        refreshButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        refreshButton.addActionListener { syncToCurrentFile() }
        panel.add(refreshButton, BorderLayout.EAST)

        return panel
    }

    private fun buildFooter(): JPanel {
        val panel = JPanel(BorderLayout())

        val addButton = JButton("+ 테스트 추가")
        addButton.addActionListener { addNewTestCase() }
        val addPanel = JPanel(FlowLayout(FlowLayout.CENTER))
        addPanel.add(addButton)

        panel.add(addPanel, BorderLayout.NORTH)
        panel.add(runBarPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun wireCurrentFileTracking() {
        runCatching {
            project.messageBus.connect(project).subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                object : FileEditorManagerListener {
                    override fun selectionChanged(event: FileEditorManagerEvent) {
                        syncToCurrentFile()
                    }
                },
            )
        }
    }

    private fun syncToCurrentFile() {
        val selectedFile = runCatching {
            FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        }.getOrNull()

        val fileName = selectedFile?.name
        if (fileName == currentFileName) {
            updateRunBarCommands()
            return
        }

        currentFileName = fileName
        if (fileName != null) {
            fileLabel.text = fileName
            fileLabel.icon = AllIcons.FileTypes.Any_type
        } else {
            fileLabel.text = "파일을 선택하세요"
            fileLabel.icon = null
        }

        updateRunBarCommands()
        reloadTestCases()
    }

    private fun updateRunBarCommands() {
        val commands = mutableListOf<RunBarPanel.CommandEntry>()
        val selectedFilePath = runCatching {
            FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.path
        }.getOrNull()

        if (selectedFilePath != null) {
            val pythonInterpreter = PythonInterpreterResolver.resolve(project)
            val inferredCommand = BojToolWindowPanel.inferCommandFromFilePath(selectedFilePath, pythonInterpreter)
            if (inferredCommand != null) {
                val displayName = "(자동) ${RunConfigurationCommandResolver.getDisplayName(selectedFilePath)}"
                commands.add(RunBarPanel.CommandEntry(displayName, inferredCommand))
            }
        }
        runBarPanel.setAvailableCommands(commands)
    }

    private fun reloadTestCases() {
        saveAllTestCases()
        testCasesPanel.removeAll()
        testCaseEntries.clear()

        val fileName = currentFileName ?: return
        val cases = repository.load(fileName)
        for ((name, case) in cases.toSortedMap()) {
            addTestCaseEntry(name, case.input, case.expectedOutput)
        }

        testCasesPanel.revalidate()
        testCasesPanel.repaint()
    }

    private fun addNewTestCase() {
        val fileName = currentFileName ?: return
        val name = repository.nextAutoName(fileName)
        addTestCaseEntry(name, "", "")
        testCasesPanel.revalidate()
        testCasesPanel.repaint()
    }

    private fun addTestCaseEntry(name: String, input: String, expectedOutput: String) {
        val entry = TestCaseEntryPanel(
            name = name,
            input = input,
            expectedOutput = expectedOutput,
            onDelete = { entryPanel -> removeTestCaseEntry(entryPanel) },
            onRunSingle = { entryPanel -> runSingleTestCase(entryPanel) },
        )
        testCaseEntries.add(entry)
        testCasesPanel.add(entry)
    }

    private fun removeTestCaseEntry(entry: TestCaseEntryPanel) {
        val fileName = currentFileName ?: return
        repository.delete(fileName, entry.testName)
        testCaseEntries.remove(entry)
        testCasesPanel.remove(entry)
        testCasesPanel.revalidate()
        testCasesPanel.repaint()
    }

    private fun saveAllTestCases() {
        val fileName = currentFileName ?: return
        for (entry in testCaseEntries) {
            repository.save(
                fileName,
                entry.testName,
                GeneralTestCase(input = entry.getInput(), expectedOutput = entry.getExpectedOutput()),
            )
        }
    }

    private fun runSingleTestCase(entry: TestCaseEntryPanel) {
        saveAllTestCases()
        val command = runBarPanel.getSelectedCommand() ?: return
        runInBackground {
            handleRunSingle(TestCaseKey.General(entry.testName), command)
        }
    }

    private fun handleRunSingle(key: TestCaseKey.General, command: String) {
        saveActiveEditorDocument()
        val workingDirectory = project.basePath?.takeIf(String::isNotBlank)?.let(::File)
        val fileName = currentFileName ?: return
        val case = repository.load(fileName)[key.name] ?: return
        val sampleCase = SampleCase(input = case.input, expectedOutput = case.expectedOutput)

        runOnEdt {
            val panel = findTestResultPanel()
            panel?.setRunning(key)
            panel?.setRunningState(true)
        }

        try {
            val result = sampleRunServiceFactory(command, workingDirectory).runSample(sampleCase)
            runOnEdt {
                findTestResultService()?.addResult(key, result)
                showTestResultToolWindow()
            }
        } catch (exception: Exception) {
            val message = exception.message ?: exception::class.simpleName.orEmpty()
            runOnEdt {
                findTestResultService()?.addResult(key, createErrorResult(case.expectedOutput, message))
            }
        } finally {
            runOnEdt { findTestResultPanel()?.setRunningState(false) }
        }
    }

    private fun handleRunAll(command: String) {
        saveAllTestCases()
        saveActiveEditorDocument()
        val workingDirectory = project.basePath?.takeIf(String::isNotBlank)?.let(::File)
        val fileName = currentFileName ?: return
        val cases = repository.load(fileName).toSortedMap()
        if (cases.isEmpty()) return

        var passedCount = 0
        var totalCount = 0
        cancelRequested = false

        // TestResultService에 케이스 정보 세팅
        runOnEdt {
            val testService = findTestResultService()
            testService?.clearResults()
            testService?.clearGeneralCaseInfo()
            for ((name, case) in cases) {
                testService?.setCaseInfo(TestCaseKey.General(name), case.input, case.expectedOutput)
            }
            val generalKeys = cases.keys.map { TestCaseKey.General(it) }
            findTestResultPanel()?.populateEntries(0, emptyList(), generalKeys)

            runBarPanel.setRunning(true)
            findTestResultPanel()?.setRunningState(true)
            findTestResultPanel()?.setAllRunning()
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
                totalCount++
                if (result.passed) passedCount++
                runOnEdt { findTestResultService()?.addResult(key, result) }
            } catch (exception: Exception) {
                if (cancelRequested) break
                val message = exception.message ?: exception::class.simpleName.orEmpty()
                totalCount++
                runOnEdt {
                    findTestResultService()?.addResult(key, createErrorResult(case.expectedOutput, message))
                }
            }
        }

        val finalPassed = passedCount
        val finalTotal = totalCount
        val wasCancelled = cancelRequested
        runOnEdt {
            runBarPanel.setRunning(false)
            findTestResultPanel()?.setRunningState(false)
            if (wasCancelled) {
                findTestResultPanel()?.markRemainingAsCancelled()
                runBarPanel.updateStatus("중지됨")
            } else {
                runBarPanel.updateStatus("$finalPassed/$finalTotal 통과")
                findTestResultService()?.notifyRunAllComplete(finalPassed, finalTotal)
            }
        }
    }

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

    private fun showTestResultToolWindow() {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("BOJ 테스트")
        if (toolWindow != null && !toolWindow.isVisible) {
            toolWindow.show()
        }
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

    private fun saveActiveEditorDocument() {
        runOnEdt {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            editor?.document?.let { FileDocumentManager.getInstance().saveDocument(it) }
        }
    }

    private fun runInBackground(task: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread(task)
    }

    private fun runOnEdt(task: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(task)
    }

    override fun dispose() {}
}

/**
 * 개별 테스트케이스 항목 UI.
 * 이름, 입력 텍스트영역, 기대출력 텍스트영역, 실행/삭제 버튼으로 구성.
 */
class TestCaseEntryPanel(
    val testName: String,
    input: String,
    expectedOutput: String,
    private val onDelete: (TestCaseEntryPanel) -> Unit,
    private val onRunSingle: (TestCaseEntryPanel) -> Unit,
) : JPanel(BorderLayout()) {

    private val inputArea = JBTextArea(input, 3, 0)
    private val expectedOutputArea = JBTextArea(expectedOutput, 3, 0)

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.separatorColor") ?: java.awt.Color.GRAY),
            BorderFactory.createEmptyBorder(6, 0, 6, 0),
        )
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)

        val content = JPanel(GridBagLayout())
        val c = GridBagConstraints()

        // 헤더: 이름 + 실행/삭제 버튼
        c.gridx = 0; c.gridy = 0; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL
        c.insets = Insets(0, 0, 4, 0)
        val headerPanel = JPanel(BorderLayout())
        val nameLabel = JBLabel(testName)
        nameLabel.font = nameLabel.font.deriveFont(Font.BOLD)
        headerPanel.add(nameLabel, BorderLayout.WEST)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0))
        val runButton = JButton(AllIcons.Actions.Execute)
        runButton.toolTipText = "이 테스트 실행"
        runButton.isBorderPainted = false
        runButton.isContentAreaFilled = false
        runButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        runButton.addActionListener { onRunSingle(this) }
        buttonPanel.add(runButton)

        val deleteButton = JButton(AllIcons.Actions.Close)
        deleteButton.toolTipText = "삭제"
        deleteButton.isBorderPainted = false
        deleteButton.isContentAreaFilled = false
        deleteButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        deleteButton.addActionListener { onDelete(this) }
        buttonPanel.add(deleteButton)
        headerPanel.add(buttonPanel, BorderLayout.EAST)
        content.add(headerPanel, c)

        // 입력 레이블
        c.gridy = 1; c.weightx = 1.0; c.weighty = 0.0
        c.insets = Insets(0, 0, 2, 0)
        val inputLabel = JBLabel("입력")
        inputLabel.font = inputLabel.font.deriveFont(11f)
        inputLabel.foreground = UIManager.getColor("Label.disabledForeground")
        content.add(inputLabel, c)

        // 입력 텍스트영역
        c.gridy = 2; c.weighty = 1.0; c.fill = GridBagConstraints.BOTH
        c.insets = Insets(0, 0, 4, 0)
        inputArea.font = com.intellij.util.ui.JBUI.Fonts.create(Font.MONOSPACED, 12)
        content.add(JBScrollPane(inputArea), c)

        // 기대 출력 레이블
        c.gridy = 3; c.weighty = 0.0; c.fill = GridBagConstraints.HORIZONTAL
        c.insets = Insets(0, 0, 2, 0)
        val expectedLabel = JBLabel("기대 출력")
        expectedLabel.font = expectedLabel.font.deriveFont(11f)
        expectedLabel.foreground = UIManager.getColor("Label.disabledForeground")
        content.add(expectedLabel, c)

        // 기대 출력 텍스트영역
        c.gridy = 4; c.weighty = 1.0; c.fill = GridBagConstraints.BOTH
        c.insets = Insets(0, 0, 0, 0)
        expectedOutputArea.font = com.intellij.util.ui.JBUI.Fonts.create(Font.MONOSPACED, 12)
        content.add(JBScrollPane(expectedOutputArea), c)

        add(content, BorderLayout.CENTER)
    }

    fun getInput(): String = inputArea.text
    fun getExpectedOutput(): String = expectedOutputArea.text
}
```

**Step 2: 컴파일 확인**

Run: `./gradlew compileKotlin 2>&1 | head -30`
Expected: BUILD SUCCESSFUL

**Step 3: 커밋**

```bash
git add src/main/kotlin/app/meot/boj-helper/ui/general/GeneralTestPanel.kt
git commit -m "feat: GeneralTestPanel UI 구현 (일반 탭 메인 패널)"
```

---

### Task 7: BojToolWindowFactory에 Content Tab 2개 생성

**Files:**
- Modify: `src/main/kotlin/app/meot/boj-helper/ui/BojToolWindowFactory.kt`

**Step 1: Factory 수정하여 2개 탭 생성**

```kotlin
package com.boj.intellij.ui

import com.boj.intellij.ui.general.GeneralTestPanel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class BojToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ActionToolbarCompat.getContentFactory()

        val bojPanel = BojToolWindowPanel(project)
        val bojContent = contentFactory.createContent(bojPanel, "백준", false)
        bojContent.setDisposer(bojPanel)
        toolWindow.contentManager.addContent(bojContent)

        val generalPanel = GeneralTestPanel(project)
        val generalContent = contentFactory.createContent(generalPanel, "일반", false)
        generalContent.setDisposer(generalPanel)
        toolWindow.contentManager.addContent(generalContent)
    }
}
```

**Step 2: 컴파일 확인**

Run: `./gradlew compileKotlin 2>&1 | head -20`
Expected: BUILD SUCCESSFUL

**Step 3: 커밋**

```bash
git add src/main/kotlin/app/meot/boj-helper/ui/BojToolWindowFactory.kt
git commit -m "feat: BOJ 도구창에 백준/일반 Content Tab 분리"
```

---

### Task 8: 통합 빌드 및 수동 테스트

**Step 1: 전체 빌드 확인**

Run: `./gradlew build 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 2: 플러그인 실행 테스트 체크리스트**

Run: `./gradlew runIde`

수동으로 확인할 항목:
1. BOJ 도구창에 [백준] [일반] 탭이 표시되는지
2. [백준] 탭에서 기존 기능이 정상 동작하는지 (문제 불러오기, 예제 실행)
3. [일반] 탭에서 현재 파일명이 표시되는지
4. [+ 테스트 추가] 버튼으로 테스트케이스를 추가할 수 있는지
5. 입력/기대출력을 입력하고 개별 실행 시 하단 결과창에 결과가 표시되는지
6. 전체 실행 시 모든 테스트가 순차적으로 실행되는지
7. 파일 전환 시 해당 파일의 테스트케이스로 전환되는지
8. `.boj/general-cases/` 디렉토리에 .in/.out 파일이 생성되는지

**Step 3: 커밋 (필요 시 수정 후)**

수동 테스트에서 발견된 이슈가 있으면 수정 후 커밋.

---

### Task 9: 하단 결과창에서 General 테스트 콜백 연결

**Files:**
- Modify: `src/main/kotlin/app/meot/boj-helper/ui/general/GeneralTestPanel.kt`

**Step 1: wireTestResultPanelCallbacks 추가**

GeneralTestPanel에 하단 결과창의 콜백을 연결하는 메서드 추가. handleRunAll 실행 전에 호출:

```kotlin
private fun wireTestResultPanelCallbacks() {
    val panel = findTestResultPanel() ?: return
    panel.onRunSingle = { key ->
        if (key is TestCaseKey.General) {
            val command = runBarPanel.getSelectedCommand() ?: return@let
            runInBackground { handleRunSingle(key, command) }
        }
    }
    panel.onRunAll = {
        val command = runBarPanel.getSelectedCommand() ?: return@let
        runInBackground { handleRunAll(command) }
    }
    panel.onStop = { cancelRequested = true }
}
```

handleRunAll과 handleRunSingle 시작 시 `wireTestResultPanelCallbacks()` 호출 추가.

**Step 2: 컴파일 확인**

Run: `./gradlew compileKotlin 2>&1 | head -20`
Expected: BUILD SUCCESSFUL

**Step 3: 커밋**

```bash
git add src/main/kotlin/app/meot/boj-helper/ui/general/GeneralTestPanel.kt
git commit -m "feat: 하단 결과창에서 General 테스트 실행/중지 콜백 연결"
```
