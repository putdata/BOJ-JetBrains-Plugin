package com.boj.intellij.ui.testresult

import com.boj.intellij.sample_run.SampleRunResult
import com.boj.intellij.service.TestCaseKey
import com.boj.intellij.service.TestResultService
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.ListCellRenderer
import javax.swing.UIManager

class BojTestResultPanel(
    private val project: Project,
) : SimpleToolWindowPanel(false), Disposable {

    var onAddCustom: () -> Unit = {}
    var onManageCustom: () -> Unit = {}
    var onEditCustom: (name: String) -> Unit = {}
    var onDeleteCustom: (name: String) -> Unit = {}
    var onRunSingle: (key: TestCaseKey) -> Unit = {}
    var onRunAll: () -> Unit = {}
    var onStop: () -> Unit = {}

    private var isRunning = false

    private val testResultService = TestResultService()
    private val listModel = DefaultListModel<TestResultEntry>()
    private val resultList = JBList(listModel)
    private var summaryText = "실행 대기 중"
    private val inputArea = createReadOnlyTextArea()
    private val expectedArea = createReadOnlyTextArea()
    private val actualArea = createReadOnlyTextArea()

    private var headerToolbar: ActionToolbar? = null

    init {
        val headerGroup = DefaultActionGroup().apply {
            add(RunAllAction())
            add(StopAction())
            add(SummaryAction())
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("BojTestResultHeader", headerGroup, true)
        toolbar.targetComponent = this
        headerToolbar = toolbar
        setToolbar(toolbar.component)

        val splitter = JBSplitter(false, 0.15f)
        splitter.firstComponent = buildListPanel()
        splitter.secondComponent = buildDetailPanel()
        splitter.border = BorderFactory.createEmptyBorder(0, 4, 4, 4)

        setContent(splitter)

        resultList.cellRenderer = TestResultCellRenderer()
        resultList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                val selected = resultList.selectedValue
                if (selected != null) {
                    showDetail(selected)
                }
            }
        }
        resultList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val idx = resultList.locationToIndex(e.point) ?: return
                val cellBounds = resultList.getCellBounds(idx, idx) ?: return
                val relativeX = e.x - cellBounds.x
                if (relativeX <= TestResultCellRenderer.RUN_BUTTON_WIDTH + 4) {
                    val clickedEntry = listModel.getElementAt(idx) ?: return
                    onRunSingle(clickedEntry.key)
                }
            }
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                if (e.isPopupTrigger) showContextMenu(e)
            }
            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                if (e.isPopupTrigger) showContextMenu(e)
            }
        })

        testResultService.addListener(object : TestResultService.Listener {
            override fun onSampleResult(index: Int, result: SampleRunResult) {
                // Keep for backward compat - but now also handled by onResult
            }

            override fun onAllResultsCleared() {
                clearAll()
            }

            override fun onRunAllComplete(passedCount: Int, totalCount: Int) {
                summaryText = "$passedCount / $totalCount 통과"
            }

            override fun onResult(key: TestCaseKey, result: SampleRunResult) {
                updateResultByKey(key, result)
            }
        })
    }

    fun getTestResultService(): TestResultService = testResultService

    fun populateEntries(sampleCount: Int, customKeys: List<TestCaseKey.Custom>) {
        listModel.clear()
        for (i in 0 until sampleCount) {
            listModel.addElement(TestResultEntry(key = TestCaseKey.Sample(i)))
        }
        for (customKey in customKeys) {
            listModel.addElement(TestResultEntry(key = customKey))
        }
        if (listModel.size() > 0) {
            resultList.selectedIndex = 0
        }
        summaryText = "실행 대기 중"
        inputArea.text = ""
        expectedArea.text = ""
        actualArea.text = ""
    }

    fun setRunning(key: TestCaseKey) {
        for (i in 0 until listModel.size()) {
            val entry = listModel.getElementAt(i)
            if (entry.key == key) {
                val updated = entry.copy(running = true, result = null, passed = null, elapsedMs = null)
                listModel.setElementAt(updated, i)
                resultList.selectedIndex = i
                resultList.ensureIndexIsVisible(i)
                showDetail(updated)
                break
            }
        }
    }

    fun setAllRunning() {
        for (i in 0 until listModel.size()) {
            val entry = listModel.getElementAt(i)
            listModel.setElementAt(entry.copy(running = true, result = null, passed = null, elapsedMs = null), i)
        }
    }

    private fun buildListPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        val listActionGroup = DefaultActionGroup().apply {
            add(AddCustomAction())
            add(ManageCustomAction())
        }
        val listToolbar = ActionManager.getInstance()
            .createActionToolbar("BojTestResultListHeader", listActionGroup, true)
        listToolbar.targetComponent = this

        panel.add(listToolbar.component, BorderLayout.NORTH)
        panel.add(JBScrollPane(resultList), BorderLayout.CENTER)
        return panel
    }

    private fun buildDetailPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createEmptyBorder(0, 8, 0, 0)
        val c = GridBagConstraints()
        c.fill = GridBagConstraints.BOTH
        c.weighty = 0.0
        c.insets = Insets(0, 0, 4, 0)

        // 레이블 행 (gridy=0)
        c.gridy = 0
        c.gridx = 0; c.weightx = 0.25; c.insets = Insets(0, 0, 4, 4)
        panel.add(createSectionLabel("예제 입력"), c)
        c.gridx = 1; c.weightx = 0.25; c.insets = Insets(0, 4, 4, 4)
        panel.add(createSectionLabel("기대 출력"), c)
        c.gridx = 2; c.weightx = 0.5; c.insets = Insets(0, 4, 4, 0)
        panel.add(createSectionLabel("실행 결과"), c)

        // 컨텐츠 행 (gridy=1) - 3칸 가로 배치
        // preferredSize를 (0,0)으로 설정하여 텍스트 내용에 관계없이 weightx 비율로만 너비 결정
        c.gridy = 1; c.weighty = 1.0
        c.gridx = 0; c.weightx = 0.25; c.insets = Insets(0, 0, 0, 4)
        panel.add(JBScrollPane(inputArea).apply { preferredSize = java.awt.Dimension(0, 0) }, c)
        c.gridx = 1; c.weightx = 0.25; c.insets = Insets(0, 4, 0, 4)
        panel.add(JBScrollPane(expectedArea).apply { preferredSize = java.awt.Dimension(0, 0) }, c)
        c.gridx = 2; c.weightx = 0.5; c.insets = Insets(0, 4, 0, 0)
        panel.add(JBScrollPane(actualArea).apply { preferredSize = java.awt.Dimension(0, 0) }, c)

        return panel
    }

    private fun updateResultByKey(key: TestCaseKey, result: SampleRunResult) {
        val hasExpectedOutput = when (key) {
            is TestCaseKey.Sample -> true
            is TestCaseKey.Custom -> testResultService.getCaseExpectedOutput(key) != null
        }
        val hasError = result.timedOut || result.exitCode == null || result.exitCode != 0
        val entry = TestResultEntry(
            key = key,
            passed = when {
                hasExpectedOutput -> result.passed
                hasError -> false
                else -> null
            },
            timedOut = result.timedOut,
            result = result,
            elapsedMs = result.elapsedMs,
        )

        var found = false
        for (i in 0 until listModel.size()) {
            if (listModel.getElementAt(i).key == key) {
                listModel.setElementAt(entry, i)
                found = true
                break
            }
        }
        if (!found) {
            listModel.addElement(entry)
        }

        if (resultList.selectedIndex == -1) {
            resultList.selectedIndex = 0
        }

        val selected = resultList.selectedValue
        if (selected != null && selected.key == key) {
            showDetail(entry)
        }
    }

    private fun clearAll() {
        for (i in 0 until listModel.size()) {
            val entry = listModel.getElementAt(i)
            listModel.setElementAt(
                entry.copy(passed = null, timedOut = false, result = null, running = false, elapsedMs = null),
                i,
            )
        }
        summaryText = "실행 대기 중"
        inputArea.text = ""
        expectedArea.text = ""
        actualArea.text = ""
    }

    private fun showDetail(entry: TestResultEntry) {
        val result = entry.result
        val input = when (entry.key) {
            is TestCaseKey.Sample -> testResultService.getSampleInput(entry.key.index) ?: ""
            is TestCaseKey.Custom -> testResultService.getCaseInput(entry.key) ?: ""
        }
        inputArea.text = input

        if (result == null) {
            expectedArea.text = when (entry.key) {
                is TestCaseKey.Sample -> testResultService.getSampleExpectedOutput((entry.key).index) ?: ""
                is TestCaseKey.Custom -> testResultService.getCaseExpectedOutput(entry.key) ?: ""
            }
            actualArea.text = if (entry.running) "실행 중..." else ""
            actualArea.foreground = UIManager.getColor("TextArea.foreground")
            return
        }

        expectedArea.text = result.expectedOutput

        val combined = buildString {
            append(result.actualOutput)
            if (result.standardError.isNotBlank()) {
                if (result.actualOutput.isNotBlank()) append("\n")
                append(result.standardError)
            }
        }
        actualArea.text = combined

        if (!result.passed) {
            actualArea.foreground = JBColor(Color(0xB3261E), Color(0xFF8A80))
        } else {
            actualArea.foreground = UIManager.getColor("TextArea.foreground")
        }
    }

    private fun createReadOnlyTextArea(): JTextArea {
        return JTextArea().apply {
            isEditable = false
            lineWrap = false
            font = com.intellij.util.ui.JBUI.Fonts.create(Font.MONOSPACED, 12)
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        }
    }

    private fun createSectionLabel(text: String): JBLabel {
        return JBLabel(text).apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = UIManager.getColor("Label.disabledForeground")
            border = BorderFactory.createEmptyBorder(4, 0, 2, 0)
        }
    }

    private fun showContextMenu(e: java.awt.event.MouseEvent) {
        val index = resultList.locationToIndex(e.point) ?: return
        val entry = listModel.getElementAt(index) ?: return
        val key = entry.key
        if (key !is TestCaseKey.Custom) return

        resultList.selectedIndex = index
        val menu = javax.swing.JPopupMenu()
        val editItem = javax.swing.JMenuItem("편집")
        val deleteItem = javax.swing.JMenuItem("삭제")
        editItem.addActionListener { onEditCustom(key.name) }
        deleteItem.addActionListener { onDeleteCustom(key.name) }
        menu.add(editItem)
        menu.add(deleteItem)
        menu.show(resultList, e.x, e.y)
    }

    fun setRunningState(running: Boolean) {
        isRunning = running
        headerToolbar?.updateActionsAsync()
    }

    private inner class RunAllAction : AnAction(
        " 전체 실행",
        "모든 테스트 케이스 실행",
        AllIcons.Actions.Execute,
    ) {
        init {
            templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
        }

        override fun actionPerformed(e: AnActionEvent) {
            onRunAll()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = !isRunning
        }

        override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
    }

    private inner class StopAction : AnAction(
        " 중지",
        "실행 중인 테스트 중지",
        AllIcons.Actions.Suspend,
    ) {
        init {
            templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
        }

        override fun actionPerformed(e: AnActionEvent) {
            onStop()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = isRunning
        }

        override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
    }

    private inner class SummaryAction : AnAction() {
        init {
            templatePresentation.putClientProperty(
                ActionUtil.SHOW_TEXT_IN_TOOLBAR, true
            )
            templatePresentation.text = " 실행 대기 중"
        }

        override fun actionPerformed(e: AnActionEvent) {
            // 텍스트 레이블 전용 - 클릭 동작 없음
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = false
            e.presentation.text = " $summaryText"
        }

        override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
    }

    private inner class AddCustomAction : AnAction(
        "커스텀 추가",
        "커스텀 테스트 케이스 추가",
        AllIcons.General.Add,
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            onAddCustom()
        }

        override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
    }

    private inner class ManageCustomAction : AnAction(
        "관리",
        "커스텀 테스트 케이스 관리",
        AllIcons.Actions.Edit,
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            onManageCustom()
        }

        override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
    }

    override fun dispose() {}
}

data class TestResultEntry(
    val key: TestCaseKey,
    val passed: Boolean? = null,       // null = 기대 출력 없음 (RUN 상태) 또는 미실행
    val timedOut: Boolean = false,
    val result: SampleRunResult? = null,  // null = 아직 미실행 (PENDING)
    val running: Boolean = false,
    val elapsedMs: Long? = null,
) {
    val index: Int get() = (key as? TestCaseKey.Sample)?.index ?: -1

    val isPending: Boolean get() = result == null && !running

    override fun toString(): String = when (key) {
        is TestCaseKey.Sample -> "예제 ${key.index + 1}"
        is TestCaseKey.Custom -> key.name
    }
}

class TestResultCellRenderer : ListCellRenderer<TestResultEntry> {

    companion object {
        const val RUN_BUTTON_WIDTH = 20
        private val PASS_COLOR = JBColor(Color(0x155724), Color(0x8DD694))
        private val FAIL_COLOR = JBColor(Color(0xB3261E), Color(0xFF8A80))
        private val PENDING_COLOR = JBColor(Color(0x999999), Color(0x666666))
    }

    override fun getListCellRendererComponent(
        list: JList<out TestResultEntry>,
        value: TestResultEntry,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val panel = JPanel(BorderLayout(4, 0))
        panel.border = BorderFactory.createEmptyBorder(2, 4, 2, 8)
        panel.isOpaque = true

        // 왼쪽: 실행 버튼 아이콘 영역
        val runIconLabel = JLabel(AllIcons.Actions.Execute)
        runIconLabel.preferredSize = java.awt.Dimension(RUN_BUTTON_WIDTH, 16)
        panel.add(runIconLabel, BorderLayout.WEST)

        // 가운데: 상태 아이콘 + 이름 + 상태 텍스트
        val statusIcon: Icon = when {
            value.running -> AnimatedIcon.Default()
            value.isPending -> AllIcons.RunConfigurations.TestNotRan
            value.timedOut -> AllIcons.General.Warning
            value.passed == null -> AllIcons.RunConfigurations.TestNotRan
            value.passed -> AllIcons.RunConfigurations.TestPassed
            else -> AllIcons.RunConfigurations.TestFailed
        }

        val statusText = when {
            value.running -> "실행 중..."
            value.isPending -> ""
            value.timedOut -> "TLE"
            value.passed == null -> "RUN"
            value.passed -> "PASS"
            else -> "FAIL"
        }

        val displayName = value.toString()
        val centerLabel = JLabel(
            if (statusText.isNotEmpty()) "$displayName  [$statusText]" else displayName
        )
        centerLabel.icon = statusIcon
        centerLabel.iconTextGap = 6

        val statusColor = when {
            value.running -> UIManager.getColor("Label.foreground") ?: Color.GRAY
            value.isPending -> PENDING_COLOR
            value.passed == null -> UIManager.getColor("Label.foreground") ?: Color.GRAY
            value.passed -> PASS_COLOR
            else -> FAIL_COLOR
        }
        centerLabel.foreground = statusColor
        panel.add(centerLabel, BorderLayout.CENTER)

        // 오른쪽: 실행 시간
        if (value.elapsedMs != null && !value.running && !value.isPending) {
            val timeText = formatElapsedTime(value.elapsedMs)
            val timeLabel = JLabel(timeText)
            timeLabel.foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
            timeLabel.font = timeLabel.font.deriveFont(11f)
            panel.add(timeLabel, BorderLayout.EAST)
        }

        if (isSelected) {
            panel.background = list.selectionBackground
        } else {
            panel.background = list.background
        }

        return panel
    }

    private fun formatElapsedTime(ms: Long): String {
        return if (ms < 1000) "${ms}ms" else String.format("%.1fs", ms / 1000.0)
    }
}
