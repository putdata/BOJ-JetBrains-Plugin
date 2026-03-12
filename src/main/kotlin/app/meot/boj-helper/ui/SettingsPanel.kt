package com.boj.intellij.ui

import com.boj.intellij.github.GitHubSettingsDialog
import com.boj.intellij.github.TemplateEngine
import com.boj.intellij.settings.BojSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class SettingsPanel(
    private val project: Project,
) : JPanel(BorderLayout()), Disposable {

    private val timeoutSpinner = JSpinner(SpinnerNumberModel(10, 1, 300, 1))
    private val githubButton = JButton("GitHub 설정 ...")
    private val pathTemplateField = JTextField(30)
    private val pathPreviewLabel = JLabel(" ")
    private val languageComboBox = JComboBox<String>()
    private val templateTextArea = JTextArea(12, 40).apply {
        isEditable = false
        font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
    }
    private val addButton = JButton("추가 ...")
    private val editButton = JButton("편집")
    private val deleteButton = JButton("삭제")
    private val applyButton = JButton("적용")
    private val resetButton = JButton("초기화")

    init {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        loadSettings()
        add(buildContent(), BorderLayout.CENTER)
        add(buildBottomBar(), BorderLayout.SOUTH)
        wireEvents()
        refreshTemplatePreview()
        updatePathPreview()
    }

    private fun buildContent(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = Insets(4, 4, 4, 4)
        gbc.anchor = GridBagConstraints.WEST
        var row = 0

        // --- 일반 섹션 ---
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(createSectionLabel("일반"), gbc)
        row++

        gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        gbc.gridx = 0; gbc.gridy = row
        panel.add(JLabel("타임아웃 (초):"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(timeoutSpinner, gbc)
        row++

        // --- GitHub 섹션 ---
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(12, 4, 4, 4)
        panel.add(createSectionLabel("GitHub"), gbc)
        gbc.insets = Insets(4, 4, 4, 4)
        row++

        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE
        panel.add(githubButton, gbc)
        row++

        // --- 보일러플레이트 섹션 ---
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(12, 4, 4, 4)
        panel.add(createSectionLabel("보일러플레이트"), gbc)
        gbc.insets = Insets(4, 4, 4, 4)
        row++

        // 경로 템플릿
        gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        gbc.gridx = 0; gbc.gridy = row
        panel.add(JLabel("경로 템플릿:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(pathTemplateField, gbc)
        row++

        // 경로 미리보기
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        pathPreviewLabel.foreground = java.awt.Color.GRAY
        panel.add(pathPreviewLabel, gbc)
        row++

        // 언어 선택 + 버튼
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        val langBar = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0))
        langBar.add(languageComboBox)
        langBar.add(editButton)
        langBar.add(deleteButton)
        langBar.add(addButton)
        panel.add(langBar, gbc)
        row++

        // 템플릿 미리보기
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0
        panel.add(JScrollPane(templateTextArea), gbc)
        row++

        // 변수 안내
        gbc.gridy = row; gbc.weighty = 0.0; gbc.fill = GridBagConstraints.HORIZONTAL
        val helpLabel = JLabel("<html>사용 가능 변수: {problemId}, {title}, {ext}</html>")
        helpLabel.foreground = java.awt.Color.GRAY
        panel.add(helpLabel, gbc)

        return panel
    }

    private fun buildBottomBar(): JComponent {
        val panel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 4))
        panel.add(resetButton)
        panel.add(applyButton)
        return panel
    }

    private fun createSectionLabel(text: String): JComponent {
        val label = JLabel(text)
        label.font = label.font.deriveFont(java.awt.Font.BOLD)
        val panel = JPanel(BorderLayout())
        panel.add(label, BorderLayout.WEST)
        panel.add(JSeparator(), BorderLayout.CENTER)
        return panel
    }

    private fun loadSettings() {
        val settings = BojSettings.getInstance()
        timeoutSpinner.value = settings.state.timeoutSeconds
        pathTemplateField.text = settings.state.boilerplatePathTemplate
        refreshLanguageComboBox()
    }

    private fun refreshLanguageComboBox() {
        val settings = BojSettings.getInstance()
        val selected = languageComboBox.selectedItem as? String
        languageComboBox.removeAllItems()
        settings.state.boilerplateTemplates.keys.sorted().forEach { languageComboBox.addItem(it) }
        if (selected != null && settings.state.boilerplateTemplates.containsKey(selected)) {
            languageComboBox.selectedItem = selected
        }
        refreshTemplatePreview()
    }

    private fun refreshTemplatePreview() {
        val ext = languageComboBox.selectedItem as? String
        val settings = BojSettings.getInstance()
        templateTextArea.text = if (ext != null) {
            settings.state.boilerplateTemplates[ext] ?: ""
        } else {
            ""
        }
        templateTextArea.caretPosition = 0
    }

    private fun updatePathPreview() {
        val sampleVars = mapOf("problemId" to "1000", "title" to "A+B", "ext" to "java")
        val relativePath = TemplateEngine.render(pathTemplateField.text, sampleVars)
        val fullPath = project.basePath?.let { java.io.File(it, relativePath).path } ?: relativePath
        pathPreviewLabel.text = "미리보기: $fullPath"
    }

    private fun wireEvents() {
        githubButton.addActionListener {
            GitHubSettingsDialog(project).show()
        }

        languageComboBox.addActionListener {
            refreshTemplatePreview()
        }

        editButton.addActionListener {
            val ext = languageComboBox.selectedItem as? String ?: return@addActionListener
            val settings = BojSettings.getInstance()
            val currentTemplate = settings.state.boilerplateTemplates[ext] ?: return@addActionListener
            val result = showTemplateEditDialog(ext, currentTemplate)
            if (result != null) {
                settings.state.boilerplateTemplates[ext] = result
                refreshTemplatePreview()
            }
        }

        deleteButton.addActionListener {
            val ext = languageComboBox.selectedItem as? String ?: return@addActionListener
            val confirm = JOptionPane.showConfirmDialog(
                this, "'$ext' 템플릿을 삭제하시겠습니까?", "템플릿 삭제",
                JOptionPane.YES_NO_OPTION,
            )
            if (confirm == JOptionPane.YES_OPTION) {
                BojSettings.getInstance().state.boilerplateTemplates.remove(ext)
                refreshLanguageComboBox()
            }
        }

        addButton.addActionListener {
            val ext = JOptionPane.showInputDialog(this, "파일 확장자 입력 (예: rs, go):", "템플릿 추가", JOptionPane.PLAIN_MESSAGE)
            if (!ext.isNullOrBlank()) {
                val trimmed = ext.trim().lowercase()
                val result = showTemplateEditDialog(trimmed, "")
                if (result != null) {
                    BojSettings.getInstance().state.boilerplateTemplates[trimmed] = result
                    refreshLanguageComboBox()
                    languageComboBox.selectedItem = trimmed
                }
            }
        }

        applyButton.addActionListener {
            applySettings()
        }

        resetButton.addActionListener {
            val confirm = JOptionPane.showConfirmDialog(
                this, "모든 보일러플레이트 설정을 초기화하시겠습니까?", "초기화",
                JOptionPane.YES_NO_OPTION,
            )
            if (confirm == JOptionPane.YES_OPTION) {
                val settings = BojSettings.getInstance()
                settings.state.boilerplatePathTemplate = "{problemId}/Main.{ext}"
                settings.state.boilerplateTemplates = BojSettings.DEFAULT_BOILERPLATE_TEMPLATES.toMutableMap()
                loadSettings()
                updatePathPreview()
            }
        }

        pathTemplateField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updatePathPreview()
            override fun removeUpdate(e: DocumentEvent?) = updatePathPreview()
            override fun changedUpdate(e: DocumentEvent?) = updatePathPreview()
        })
    }

    private fun showTemplateEditDialog(extension: String, currentTemplate: String): String? {
        val textArea = JTextArea(currentTemplate, 15, 50)
        textArea.font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
        val scrollPane = JScrollPane(textArea)
        val hintLabel = JLabel("사용 가능한 변수: {problemId}, {title}, {ext}")
        hintLabel.font = hintLabel.font.deriveFont(java.awt.Font.ITALIC, 11f)
        val panel = JPanel(java.awt.BorderLayout(0, 4))
        panel.add(scrollPane, java.awt.BorderLayout.CENTER)
        panel.add(hintLabel, java.awt.BorderLayout.SOUTH)
        val result = JOptionPane.showConfirmDialog(
            this, panel, "$extension 템플릿 편집",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
        )
        return if (result == JOptionPane.OK_OPTION) textArea.text else null
    }

    private fun applySettings() {
        val settings = BojSettings.getInstance()
        settings.state.timeoutSeconds = timeoutSpinner.value as? Int ?: 10
        settings.state.boilerplatePathTemplate = pathTemplateField.text.trim().ifBlank { "{problemId}/Main.{ext}" }
    }

    fun onTabSelected() {
        loadSettings()
        updatePathPreview()
    }

    override fun dispose() {}
}
