package com.boj.intellij.github

import com.boj.intellij.settings.BojSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class GitHubSettingsDialog(
    private val project: Project?,
) : DialogWrapper(project) {

    private val enabledCheckbox = JCheckBox("GitHub 업로드 활성화")
    private val tokenField = JPasswordField(30)
    private val repoField = JTextField(30)
    private val branchField = JTextField(30)
    private val pathTemplateField = JTextField(30)
    private val commitTemplateField = JTextField(30)
    private val pathPreviewLabel = JLabel(" ")
    private val commitPreviewLabel = JLabel(" ")
    private val testConnectionButton = JButton("연결 테스트")
    private val testResultLabel = JLabel(" ")

    init {
        title = "GitHub 설정"
        loadCurrentSettings()
        setupPreviewListeners()
        setupTestConnectionButton()

        init()
        updatePreviews()
    }

    private fun loadCurrentSettings() {
        val settings = BojSettings.getInstance()
        enabledCheckbox.isSelected = settings.state.githubEnabled
        repoField.text = settings.state.githubRepo
        branchField.text = settings.state.githubBranch
        pathTemplateField.text = settings.state.githubPathTemplate
        commitTemplateField.text = settings.state.githubCommitTemplate

        val existingToken = GitHubCredentialStore.getToken()
        if (!existingToken.isNullOrBlank()) {
            tokenField.text = existingToken
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.preferredSize = Dimension(500, 400)
        val gbc = GridBagConstraints()
        gbc.insets = Insets(4, 4, 4, 4)
        gbc.anchor = GridBagConstraints.WEST
        var row = 0

        // 활성화 체크박스
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(enabledCheckbox, gbc)
        row++

        // 구분선
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(JSeparator(), gbc)
        row++

        // 토큰
        gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE
        gbc.gridx = 0; gbc.gridy = row
        panel.add(JLabel("Personal Access Token:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(tokenField, gbc)
        row++

        // 리포지토리
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("리포지토리 (owner/repo):"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(repoField, gbc)
        row++

        // 브랜치
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("브랜치:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(branchField, gbc)
        row++

        // 연결 테스트
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE
        panel.add(testConnectionButton, gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(testResultLabel, gbc)
        row++

        // 구분선
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(JSeparator(), gbc)
        row++

        // 파일 경로 템플릿
        gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        gbc.gridx = 0; gbc.gridy = row
        panel.add(JLabel("파일 경로 템플릿:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(pathTemplateField, gbc)
        row++

        // 파일 경로 미리보기
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        pathPreviewLabel.foreground = java.awt.Color.GRAY
        panel.add(pathPreviewLabel, gbc)
        row++

        // 커밋 메시지 템플릿
        gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        gbc.gridx = 0; gbc.gridy = row
        panel.add(JLabel("커밋 메시지 템플릿:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(commitTemplateField, gbc)
        row++

        // 커밋 메시지 미리보기
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        commitPreviewLabel.foreground = java.awt.Color.GRAY
        panel.add(commitPreviewLabel, gbc)
        row++

        // 사용 가능한 변수 안내
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        val helpLabel = JLabel("<html>사용 가능한 변수: {problemId}, {title}, {language}, {ext}, {memory}, {time}</html>")
        helpLabel.foreground = java.awt.Color.GRAY
        panel.add(helpLabel, gbc)
        row++

        // 빈 공간 채우기
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH
        panel.add(JPanel(), gbc)

        return panel
    }

    private fun setupPreviewListeners() {
        val listener = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updatePreviews()
            override fun removeUpdate(e: DocumentEvent?) = updatePreviews()
            override fun changedUpdate(e: DocumentEvent?) = updatePreviews()
        }
        pathTemplateField.document.addDocumentListener(listener)
        commitTemplateField.document.addDocumentListener(listener)
    }

    private fun updatePreviews() {
        val sampleVars = mapOf(
            "problemId" to "1000",
            "title" to "A+B",
            "language" to "Java 11",
            "ext" to "java",
            "memory" to "14512",
            "time" to "132",
        )
        pathPreviewLabel.text = "미리보기: ${TemplateEngine.render(pathTemplateField.text, sampleVars)}"
        commitPreviewLabel.text = "미리보기: ${TemplateEngine.render(commitTemplateField.text, sampleVars)}"
    }

    private fun setupTestConnectionButton() {
        testConnectionButton.addActionListener {
            val token = String(tokenField.password).trim()
            val repo = repoField.text.trim()

            if (token.isBlank() || repo.isBlank()) {
                testResultLabel.text = "토큰과 리포지토리를 입력해주세요"
                testResultLabel.foreground = java.awt.Color.RED
                return@addActionListener
            }

            testResultLabel.text = "연결 테스트 중..."
            testResultLabel.foreground = java.awt.Color.GRAY
            testConnectionButton.isEnabled = false

            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val client = GitHubApiClient(token)
                    val result = client.testConnection(repo)
                    ApplicationManager.getApplication().invokeLater({
                        if (result.success) {
                            testResultLabel.text = if (result.canPush) "연결 성공 (push 권한 확인)" else "연결 성공"
                            testResultLabel.foreground = java.awt.Color(0x2E7D32)
                        } else {
                            testResultLabel.text = result.errorMessage ?: "연결 실패"
                            testResultLabel.foreground = java.awt.Color.RED
                        }
                        testConnectionButton.isEnabled = true
                    }, ModalityState.any())
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater({
                        testResultLabel.text = "오류: ${e.message}"
                        testResultLabel.foreground = java.awt.Color.RED
                        testConnectionButton.isEnabled = true
                    }, ModalityState.any())
                }
            }
        }
    }

    override fun doOKAction() {
        val settings = BojSettings.getInstance()
        settings.state.githubEnabled = enabledCheckbox.isSelected
        settings.state.githubRepo = repoField.text.trim()
        settings.state.githubBranch = branchField.text.trim().ifBlank { "main" }
        settings.state.githubPathTemplate = pathTemplateField.text.trim().ifBlank { "{language}/{problemId}.{ext}" }
        settings.state.githubCommitTemplate = commitTemplateField.text.trim().ifBlank { "[{problemId}] {title}" }
        val token = String(tokenField.password).trim()
        if (token.isNotBlank()) {
            GitHubCredentialStore.setToken(token)
        } else {
            GitHubCredentialStore.removeToken()
        }

        super.doOKAction()
    }
}
