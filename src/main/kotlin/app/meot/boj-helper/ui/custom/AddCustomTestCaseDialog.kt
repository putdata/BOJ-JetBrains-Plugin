package com.boj.intellij.ui.custom

import com.boj.intellij.custom.CustomTestCase
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField

class AddCustomTestCaseDialog(
    project: Project?,
    private val defaultName: String = "",
    private val editingCase: CustomTestCase? = null,
) : DialogWrapper(project) {

    private val nameField = JTextField(defaultName)
    private val inputArea = JBTextArea().apply {
        font = Font("JetBrains Mono", Font.PLAIN, 12)
        rows = 6
    }
    private val expectedOutputArea = JBTextArea().apply {
        font = Font("JetBrains Mono", Font.PLAIN, 12)
        rows = 6
    }

    init {
        title = if (editingCase != null) "커스텀 테스트 케이스 편집" else "커스텀 테스트 케이스 추가"
        setOKButtonText(if (editingCase != null) "저장" else "추가")

        editingCase?.let { case ->
            inputArea.text = case.input
            expectedOutputArea.text = case.expectedOutput ?: ""
        }

        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.preferredSize = Dimension(450, 400)
        val c = GridBagConstraints()
        c.fill = GridBagConstraints.HORIZONTAL
        c.weightx = 1.0
        c.gridx = 0
        c.insets = Insets(0, 0, 4, 0)

        c.gridy = 0; c.weighty = 0.0
        panel.add(JBLabel("케이스 이름 (빈칸이면 자동 생성):"), c)
        c.gridy = 1
        panel.add(nameField, c)

        c.gridy = 2; c.insets = Insets(8, 0, 4, 0)
        panel.add(JBLabel("입력:"), c)
        c.gridy = 3; c.weighty = 0.4; c.fill = GridBagConstraints.BOTH
        c.insets = Insets(0, 0, 4, 0)
        panel.add(JScrollPane(inputArea), c)

        c.gridy = 4; c.weighty = 0.0; c.fill = GridBagConstraints.HORIZONTAL
        c.insets = Insets(8, 0, 4, 0)
        panel.add(JBLabel("기대 출력 (선택):"), c)
        c.gridy = 5; c.weighty = 0.4; c.fill = GridBagConstraints.BOTH
        c.insets = Insets(0, 0, 0, 0)
        panel.add(JScrollPane(expectedOutputArea), c)

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        if (inputArea.text.isBlank()) {
            return ValidationInfo("입력은 비어있을 수 없습니다.", inputArea)
        }
        return null
    }

    fun getCaseName(): String = nameField.text.trim()

    fun getCustomTestCase(): CustomTestCase {
        val expectedOutput = expectedOutputArea.text.takeIf { it.isNotBlank() }
        return CustomTestCase(
            input = inputArea.text,
            expectedOutput = expectedOutput,
        )
    }
}
