package com.boj.intellij.ui.testresult

import com.boj.intellij.sample_run.SampleRunResult
import com.boj.intellij.service.TestResultService
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
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
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.ListCellRenderer
import javax.swing.UIManager

class BojTestResultPanel(
    private val project: Project,
) : JPanel(BorderLayout()), Disposable {

    private val testResultService = TestResultService()
    private val listModel = DefaultListModel<TestResultEntry>()
    private val resultList = JBList(listModel)
    private val summaryLabel = JBLabel("실행 대기 중")
    private val inputArea = createReadOnlyTextArea()
    private val expectedArea = createReadOnlyTextArea()
    private val actualArea = createReadOnlyTextArea()
    private val stderrArea = createReadOnlyTextArea()
    private val stderrPanel = JPanel(BorderLayout())

    init {
        border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

        val splitter = JBSplitter(false, 0.3f)
        splitter.firstComponent = buildListPanel()
        splitter.secondComponent = buildDetailPanel()

        add(summaryLabel, BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)

        resultList.cellRenderer = TestResultCellRenderer()
        resultList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                val selected = resultList.selectedValue
                if (selected != null) {
                    showDetail(selected)
                }
            }
        }

        testResultService.addListener(object : TestResultService.Listener {
            override fun onSampleResult(index: Int, result: SampleRunResult) {
                updateResult(index, result)
            }

            override fun onAllResultsCleared() {
                clearAll()
            }

            override fun onRunAllComplete(passedCount: Int, totalCount: Int) {
                summaryLabel.text = "$passedCount / $totalCount 통과"
            }
        })
    }

    fun getTestResultService(): TestResultService = testResultService

    private fun buildListPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JBScrollPane(resultList), BorderLayout.CENTER)
        return panel
    }

    private fun buildDetailPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createEmptyBorder(0, 8, 0, 0)
        val c = GridBagConstraints()
        c.fill = GridBagConstraints.BOTH
        c.weightx = 1.0
        c.gridx = 0
        c.gridwidth = 2
        c.insets = Insets(0, 0, 4, 0)

        // 입력
        c.gridy = 0; c.weighty = 0.0
        panel.add(createSectionLabel("예제 입력"), c)
        c.gridy = 1; c.weighty = 0.2
        panel.add(JBScrollPane(inputArea), c)

        // 기대 출력 / 실행 결과 (나란히)
        c.gridwidth = 1; c.gridy = 2; c.weighty = 0.0
        c.gridx = 0
        panel.add(createSectionLabel("기대 출력"), c)
        c.gridx = 1
        panel.add(createSectionLabel("실행 결과"), c)

        c.gridy = 3; c.weighty = 0.3
        c.gridx = 0; c.insets = Insets(0, 0, 4, 4)
        panel.add(JBScrollPane(expectedArea), c)
        c.gridx = 1; c.insets = Insets(0, 4, 4, 0)
        panel.add(JBScrollPane(actualArea), c)

        // 표준 에러
        c.gridx = 0; c.gridwidth = 2; c.gridy = 4; c.weighty = 0.0
        c.insets = Insets(0, 0, 4, 0)
        stderrPanel.add(createSectionLabel("표준 에러"), BorderLayout.NORTH)
        stderrPanel.add(JBScrollPane(stderrArea), BorderLayout.CENTER)
        stderrPanel.isVisible = false
        c.gridy = 4; c.weighty = 0.2
        panel.add(stderrPanel, c)

        return panel
    }

    private fun updateResult(index: Int, result: SampleRunResult) {
        val entry = TestResultEntry(
            index = index,
            passed = result.passed,
            timedOut = result.timedOut,
            result = result,
        )

        // 기존 항목 업데이트 또는 추가
        var found = false
        for (i in 0 until listModel.size()) {
            if (listModel.getElementAt(i).index == index) {
                listModel.setElementAt(entry, i)
                found = true
                break
            }
        }
        if (!found) {
            // 인덱스 순서대로 삽입
            var insertAt = listModel.size()
            for (i in 0 until listModel.size()) {
                if (listModel.getElementAt(i).index > index) {
                    insertAt = i
                    break
                }
            }
            listModel.insertElementAt(entry, insertAt)
        }

        // 첫 번째 결과면 자동 선택
        if (resultList.selectedIndex == -1) {
            resultList.selectedIndex = 0
        }

        // 현재 선택된 항목이 업데이트된 경우 디테일 패널 갱신
        val selected = resultList.selectedValue
        if (selected != null && selected.index == index) {
            showDetail(entry)
        }
    }

    private fun clearAll() {
        listModel.clear()
        summaryLabel.text = "실행 대기 중"
        inputArea.text = ""
        expectedArea.text = ""
        actualArea.text = ""
        stderrArea.text = ""
        stderrPanel.isVisible = false
    }

    private fun showDetail(entry: TestResultEntry) {
        val result = entry.result
        val input = testResultService.getSampleInput(entry.index) ?: ""
        inputArea.text = input
        expectedArea.text = result.expectedOutput
        actualArea.text = result.actualOutput

        if (result.standardError.isNotBlank()) {
            stderrArea.text = result.standardError
            stderrPanel.isVisible = true
        } else {
            stderrPanel.isVisible = false
        }

        // FAIL인 경우 실행 결과 텍스트 색상 변경
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
            font = Font("JetBrains Mono", Font.PLAIN, 12)
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

    override fun dispose() {}
}

data class TestResultEntry(
    val index: Int,
    val passed: Boolean,
    val timedOut: Boolean,
    val result: SampleRunResult,
) {
    override fun toString(): String = "예제 ${index + 1}"
}

class TestResultCellRenderer : ListCellRenderer<TestResultEntry> {
    override fun getListCellRendererComponent(
        list: JList<out TestResultEntry>,
        value: TestResultEntry,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val label = JLabel()
        val statusIcon = if (value.passed) "\u2713" else "\u2717"
        val statusText = when {
            value.timedOut -> "TLE"
            value.passed -> "PASS"
            else -> "FAIL"
        }
        label.text = "$statusIcon  예제 ${value.index + 1}  [$statusText]"
        label.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
        label.isOpaque = true

        if (isSelected) {
            label.background = list.selectionBackground
            label.foreground = list.selectionForeground
        } else {
            label.background = list.background
            label.foreground = if (value.passed) {
                JBColor(Color(0x155724), Color(0x8DD694))
            } else {
                JBColor(Color(0xB3261E), Color(0xFF8A80))
            }
        }

        return label
    }
}
