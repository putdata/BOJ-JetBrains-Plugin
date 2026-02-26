package com.boj.intellij.ui

import com.intellij.ui.JBColor
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel

class RunBarPanel(
    private val onRunAll: (command: String) -> Unit,
    private val onAddCustom: () -> Unit = {},
) : JPanel(BorderLayout()) {

    data class CommandEntry(
        val displayName: String,
        val command: String,
    ) {
        override fun toString(): String = displayName
    }

    private val commandComboBox = JComboBox<CommandEntry>()
    private val runAllButton = JButton("▶ 전체 실행")
    private val statusLabel = JLabel("실행 대기 중")

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, resolveTopBorderColor()),
            BorderFactory.createEmptyBorder(4, 8, 4, 8),
        )

        val addCustomButton = JButton("+ 커스텀")
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        leftPanel.isOpaque = false
        leftPanel.add(commandComboBox)
        leftPanel.add(runAllButton)
        leftPanel.add(addCustomButton)
        addCustomButton.addActionListener { onAddCustom() }

        add(leftPanel, BorderLayout.WEST)
        add(statusLabel, BorderLayout.EAST)

        runAllButton.isEnabled = false
        runAllButton.addActionListener {
            val selected = commandComboBox.selectedItem as? CommandEntry ?: return@addActionListener
            onRunAll(selected.command)
        }
    }

    fun setAvailableCommands(commands: List<CommandEntry>) {
        commandComboBox.removeAllItems()
        commands.forEach { commandComboBox.addItem(it) }
        runAllButton.isEnabled = commands.isNotEmpty()
    }

    fun getSelectedCommand(): String? {
        return (commandComboBox.selectedItem as? CommandEntry)?.command
    }

    fun updateStatus(text: String) {
        statusLabel.text = text
    }

    fun getStatusText(): String = statusLabel.text

    fun isRunAllEnabled(): Boolean = runAllButton.isEnabled

    fun setRunning(running: Boolean) {
        runAllButton.isEnabled = !running && commandComboBox.itemCount > 0
        if (running) {
            statusLabel.text = "실행 중..."
        }
    }

    /** 테스트용: 전체 실행 버튼 클릭 시뮬레이션 */
    internal fun simulateRunAllClick() {
        runAllButton.doClick()
    }

    companion object {
        /**
         * JBColor.border()는 IntelliJ UI 시스템이 초기화되지 않은 테스트 환경에서
         * 실패할 수 있으므로, 안전하게 fallback 색상을 반환한다.
         */
        private fun resolveTopBorderColor(): Color {
            return runCatching { JBColor.border() }.getOrElse { Color(0xD0D7DE) }
        }
    }
}
