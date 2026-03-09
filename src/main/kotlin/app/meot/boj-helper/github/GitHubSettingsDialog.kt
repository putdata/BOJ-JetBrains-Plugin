package com.boj.intellij.github

import com.boj.intellij.settings.BojSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.net.URI
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class GitHubSettingsDialog(
    private val project: Project?,
) : DialogWrapper(project) {

    private val enabledCheckbox = JCheckBox("GitHub 업로드 활성화")
    private val loginButton = JButton("GitHub 로그인")
    private val logoutButton = JButton("로그아웃")
    private val authStatusLabel = JLabel(" ")
    private val repoComboBox = JComboBox<String>()
    private val branchField = JTextField(30)
    private val pathTemplateField = JTextField(30)
    private val commitTemplateField = JTextField(30)
    private val pathPreviewLabel = JLabel(" ")
    private val commitPreviewLabel = JLabel(" ")
    private val readmeCheckbox = JCheckBox("README.md 생성 (폴더 모드)")

    init {
        title = "GitHub 설정"
        setupLoginButton()
        setupLogoutButton()
        loadCurrentSettings()
        setupPreviewListeners()
        init()
        updatePreviews()
    }

    private fun loadCurrentSettings() {
        val settings = BojSettings.getInstance()
        enabledCheckbox.isSelected = settings.state.githubEnabled
        branchField.text = settings.state.githubBranch
        pathTemplateField.text = settings.state.githubPathTemplate
        commitTemplateField.text = settings.state.githubCommitTemplate
        readmeCheckbox.isSelected = settings.state.githubReadmeEnabled

        updateAuthUI()
        if (GitHubCredentialStore.hasToken()) {
            loadRepoList()
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.preferredSize = Dimension(500, 400)
        val gbc = GridBagConstraints()
        gbc.insets = Insets(4, 4, 4, 4)
        gbc.anchor = GridBagConstraints.WEST
        var row = 0

        // 인증 영역: loginButton 왼쪽, authPanel 오른쪽
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(loginButton, gbc)
        val authPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        authPanel.add(authStatusLabel)
        authPanel.add(logoutButton)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(authPanel, gbc)
        row++

        // 구분선
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(JSeparator(), gbc)
        row++

        // 활성화 체크박스
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(enabledCheckbox, gbc)
        row++

        // 저장소
        gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        gbc.gridx = 0; gbc.gridy = row
        panel.add(JLabel("저장소:"), gbc)
        repoComboBox.isEditable = false
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(repoComboBox, gbc)
        row++

        // 브랜치
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("브랜치:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(branchField, gbc)
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

        // README 체크박스
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(readmeCheckbox, gbc)
        row++

        // 사용 가능한 변수 안내
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        val helpLabel = JLabel("<html>사용 가능한 변수: {problemId}, {title}, {language}, {ext}, {memory}, {time}, {tier}, {tierNum}<br>수정자: {변수:u} 대문자, {변수:l} 소문자, {변수:c} 첫글자 대문자</html>")
        helpLabel.foreground = java.awt.Color.GRAY
        panel.add(helpLabel, gbc)
        row++

        // 빈 공간 채우기
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH
        panel.add(JPanel(), gbc)

        return panel
    }

    private fun updateAuthUI() {
        val username = GitHubCredentialStore.getUsername()
        val isLoggedIn = GitHubCredentialStore.hasToken() && username != null

        if (isLoggedIn) {
            loginButton.isVisible = false
            logoutButton.isVisible = true
            authStatusLabel.text = "\u2713 @$username 로그인됨"
            authStatusLabel.foreground = java.awt.Color(0x2E7D32)
        } else {
            loginButton.isVisible = true
            logoutButton.isVisible = false
            authStatusLabel.text = " "
        }

        // 설정 필드 활성화/비활성화
        val settingsEnabled = isLoggedIn
        enabledCheckbox.isEnabled = settingsEnabled
        repoComboBox.isEnabled = settingsEnabled
        branchField.isEnabled = settingsEnabled
        pathTemplateField.isEnabled = settingsEnabled
        commitTemplateField.isEnabled = settingsEnabled
        readmeCheckbox.isEnabled = settingsEnabled
    }

    private fun setupLoginButton() {
        loginButton.addActionListener {
            loginButton.isEnabled = false
            authStatusLabel.text = "인증 코드 요청 중..."
            authStatusLabel.foreground = java.awt.Color.GRAY

            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val deviceCode = GitHubDeviceFlowAuth.requestDeviceCode()

                    // 브라우저 열기
                    java.awt.Desktop.getDesktop().browse(URI.create(deviceCode.verificationUri))

                    // UI 스레드에서 코드 안내
                    ApplicationManager.getApplication().invokeLater({
                        authStatusLabel.text = "코드: ${deviceCode.userCode} — 브라우저에서 입력하세요"
                        authStatusLabel.foreground = java.awt.Color.BLUE
                    }, ModalityState.any())

                    // 폴링
                    val interval = (deviceCode.interval * 1000).toLong()
                    while (true) {
                        Thread.sleep(interval)
                        val token = GitHubDeviceFlowAuth.pollForToken(deviceCode.deviceCode)
                        if (token != null) {
                            GitHubCredentialStore.setToken(token)
                            val client = GitHubApiClient(token)
                            val username = client.getUser()
                            if (username != null) {
                                GitHubCredentialStore.setUsername(username)
                            }
                            ApplicationManager.getApplication().invokeLater({
                                updateAuthUI()
                                loadRepoList()
                                loginButton.isEnabled = true
                            }, ModalityState.any())
                            return@executeOnPooledThread
                        }
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater({
                        authStatusLabel.text = "인증 실패: ${e.message}"
                        authStatusLabel.foreground = java.awt.Color.RED
                        loginButton.isEnabled = true
                    }, ModalityState.any())
                }
            }
        }
    }

    private fun setupLogoutButton() {
        logoutButton.addActionListener {
            GitHubCredentialStore.clearAll()
            repoComboBox.removeAllItems()
            updateAuthUI()
        }
    }

    private fun loadRepoList() {
        val token = GitHubCredentialStore.getToken() ?: return
        repoComboBox.removeAllItems()

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val client = GitHubApiClient(token)
                val repos = client.listRepos()
                ApplicationManager.getApplication().invokeLater({
                    repos.forEach { repoComboBox.addItem(it) }
                    // 기존 설정값 선택
                    val currentRepo = BojSettings.getInstance().state.githubRepo
                    if (currentRepo.isNotBlank()) {
                        repoComboBox.selectedItem = currentRepo
                    }
                }, ModalityState.any())
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    authStatusLabel.text = "저장소 목록 조회 실패: ${e.message}"
                    authStatusLabel.foreground = java.awt.Color.RED
                }, ModalityState.any())
            }
        }
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
            "tier" to "Gold",
            "tierNum" to "5",
        )
        pathPreviewLabel.text = "미리보기: ${TemplateEngine.render(pathTemplateField.text, sampleVars)}"
        commitPreviewLabel.text = "미리보기: ${TemplateEngine.render(commitTemplateField.text, sampleVars)}"
    }

    override fun doOKAction() {
        val settings = BojSettings.getInstance()
        settings.state.githubEnabled = enabledCheckbox.isSelected
        settings.state.githubRepo = (repoComboBox.selectedItem as? String)?.trim() ?: ""
        settings.state.githubBranch = branchField.text.trim().ifBlank { "main" }
        settings.state.githubPathTemplate = pathTemplateField.text.trim().ifBlank { "{language}/{problemId}.{ext}" }
        settings.state.githubCommitTemplate = commitTemplateField.text.trim().ifBlank { "[{problemId}] {title}" }
        settings.state.githubReadmeEnabled = readmeCheckbox.isSelected
        super.doOKAction()
    }
}
