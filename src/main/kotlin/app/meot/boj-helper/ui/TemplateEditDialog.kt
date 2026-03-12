package com.boj.intellij.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.*

class TemplateEditDialog(
    project: Project?,
    private val extension: String,
    private val currentTemplate: String,
) : DialogWrapper(project) {

    private val textArea = JTextArea(currentTemplate, 35, 80).apply {
        font = Font("Monospaced", Font.PLAIN, 12)
    }

    init {
        title = "$extension 템플릿 편집"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 4))

        val scrollPane = JScrollPane(textArea)
        scrollPane.preferredSize = Dimension(800, 600)
        panel.add(scrollPane, BorderLayout.CENTER)

        val hintLabel = JLabel("사용 가능한 변수: {problemId}, {title}, {ext}")
        hintLabel.font = hintLabel.font.deriveFont(Font.ITALIC, 11f)
        panel.add(hintLabel, BorderLayout.SOUTH)

        return panel
    }

    fun getTemplateText(): String = textArea.text
}
