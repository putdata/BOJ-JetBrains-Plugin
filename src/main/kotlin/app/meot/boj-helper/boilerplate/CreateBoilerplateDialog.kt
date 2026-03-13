package com.boj.intellij.boilerplate

import com.boj.intellij.settings.BojSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.*

class CreateBoilerplateDialog(
    private val project: Project,
    private val baseDir: File,
    problemNumber: String,
    private val problemTitle: String = "",
) : DialogWrapper(project) {

    private val problemNumberField = JTextField(problemNumber, 15).apply { isEditable = false }
    private val languageComboBox = JComboBox<String>()
    private val pathPreviewLabel = JLabel(" ")

    init {
        title = "보일러플레이트 생성"
        val settings = BojSettings.getInstance()
        settings.state.boilerplateTemplates.keys.sorted().forEach { languageComboBox.addItem(it) }
        val last = settings.state.lastSelectedLanguage
        if (last.isNotBlank() && settings.state.boilerplateTemplates.containsKey(last)) {
            languageComboBox.selectedItem = last
        }
        init()
        updatePreview()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = Insets(4, 4, 4, 4)
        gbc.anchor = GridBagConstraints.WEST
        var row = 0

        // 문제 번호
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("문제 번호:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(problemNumberField, gbc)
        row++

        // 언어
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("언어:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(languageComboBox, gbc)
        row++

        // 생성 경로 미리보기
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        pathPreviewLabel.foreground = java.awt.Color.GRAY
        panel.add(pathPreviewLabel, gbc)

        // 이벤트 연결
        languageComboBox.addActionListener { updatePreview() }

        return panel
    }

    private fun updatePreview() {
        val problemId = problemNumberField.text.trim()
        val ext = languageComboBox.selectedItem as? String ?: ""
        if (problemId.isBlank()) {
            pathPreviewLabel.text = "생성 경로: (문제 번호를 입력하세요)"
            return
        }
        val settings = BojSettings.getInstance()
        val relativePath = BoilerplateService.resolvePath(
            template = settings.state.boilerplatePathTemplate,
            problemId = problemId,
            extension = ext,
            title = problemTitle,
        )
        pathPreviewLabel.text = "생성 경로: ${File(baseDir, relativePath).path}"
    }

    fun getProblemNumber(): String = problemNumberField.text.trim()

    fun getSelectedExtension(): String = languageComboBox.selectedItem as? String ?: ""

    override fun doValidate(): ValidationInfo? {
        if (languageComboBox.selectedItem == null) {
            return ValidationInfo("언어를 선택하세요.")
        }
        return null
    }
}
