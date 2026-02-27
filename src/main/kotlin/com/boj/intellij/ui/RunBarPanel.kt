package com.boj.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.JBColor
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import java.util.Timer
import kotlin.concurrent.schedule

class RunBarPanel(
    private val onRunAll: (command: String) -> Unit,
    private val onStop: () -> Unit = {},
    private val onCopyForSubmit: () -> Unit = {},
) : JPanel(BorderLayout()) {

    data class CommandEntry(
        val displayName: String,
        val command: String,
    ) {
        override fun toString(): String = displayName
    }

    private val commandComboBox = JComboBox<CommandEntry>()
    private val statusLabel = JLabel("실행 대기 중")

    private var isRunning = false
    private var toolbar: ActionToolbar? = null

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, resolveTopBorderColor()),
            BorderFactory.createEmptyBorder(4, 8, 4, 8),
        )

        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        leftPanel.isOpaque = false
        leftPanel.add(commandComboBox)

        runCatching {
            val actionGroup = DefaultActionGroup().apply {
                add(RunAllAction())
                add(StopAction())
            }
            val actionToolbar = ActionManager.getInstance()
                .createActionToolbar("BojRunBar", actionGroup, true)
            actionToolbar.targetComponent = this
            toolbar = actionToolbar
            leftPanel.add(actionToolbar.component)
        }

        leftPanel.add(statusLabel)

        add(leftPanel, BorderLayout.WEST)

        runCatching {
            val copyGroup = DefaultActionGroup().apply {
                add(CopyForSubmitAction())
            }
            val copyToolbar = ActionManager.getInstance()
                .createActionToolbar("BojRunBarCopy", copyGroup, true)
            copyToolbar.targetComponent = this
            add(copyToolbar.component, BorderLayout.EAST)
        }
    }

    fun setAvailableCommands(commands: List<CommandEntry>) {
        commandComboBox.removeAllItems()
        commands.forEach { commandComboBox.addItem(it) }
        toolbar?.updateActionsAsync()
    }

    fun getSelectedCommand(): String? {
        return (commandComboBox.selectedItem as? CommandEntry)?.command
    }

    fun updateStatus(text: String) {
        statusLabel.text = text
    }

    fun showCopyFeedback() {
        val previousText = statusLabel.text
        statusLabel.text = "복사됨"
        Timer().schedule(2000) {
            javax.swing.SwingUtilities.invokeLater {
                if (statusLabel.text == "복사됨") {
                    statusLabel.text = previousText
                }
            }
        }
    }

    fun getStatusText(): String = statusLabel.text

    fun isRunAllEnabled(): Boolean = !isRunning && commandComboBox.itemCount > 0

    fun isStopEnabled(): Boolean = isRunning

    fun setRunning(running: Boolean) {
        isRunning = running
        toolbar?.updateActionsAsync()
        if (running) {
            statusLabel.text = "실행 중..."
        }
    }

    /** 테스트용: 전체 실행 버튼 클릭 시뮬레이션 */
    internal fun simulateRunAllClick() {
        val selected = commandComboBox.selectedItem as? CommandEntry ?: return
        onRunAll(selected.command)
    }

    private inner class RunAllAction : AnAction(
        "전체 실행",
        "모든 테스트 케이스 실행",
        AllIcons.Actions.Execute,
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            val selected = commandComboBox.selectedItem as? CommandEntry ?: return
            onRunAll(selected.command)
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = !isRunning && commandComboBox.itemCount > 0
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    private inner class StopAction : AnAction(
        "중지",
        "실행 중인 테스트 중지",
        AllIcons.Actions.Suspend,
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            onStop()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = isRunning
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    private inner class CopyForSubmitAction : AnAction(
        "제출용 복사",
        "백준 제출용 코드 복사 (Java: 클래스명→Main)",
        AllIcons.Actions.Copy,
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            onCopyForSubmit()
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
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
