package com.boj.intellij.submit

import com.boj.intellij.ui.BojToolWindowPanel
import com.boj.intellij.ui.CopyForSubmitUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class BojSubmitPanel(
    private val project: Project,
) : JPanel(BorderLayout()), Disposable {

    private val loginStatusLabel = JLabel("미로그인")
    private val loginButton = JButton("로그인")
    private val logoutButton = JButton("로그아웃").apply { isVisible = false }
    private val submitButton = JButton("제출 페이지").apply { isEnabled = false }
    private val updateCodeButton = JButton("소스코드 업데이트").apply { isEnabled = false }
    private val languageSettingsButton = JButton("언어 설정")

    private val browser: JBCefBrowser? = createBrowserOrNull()
    private var isLoggedIn = false
    private var username: String? = null

    companion object {
        private const val BOJ_BASE_URL = "https://www.acmicpc.net"
        private const val BOJ_LOGIN_URL = "$BOJ_BASE_URL/login"
        private const val BOJ_SUBMIT_URL = "$BOJ_BASE_URL/submit"
    }

    init {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        if (browser != null) {
            add(buildHeader(), BorderLayout.NORTH)
            add(browser.component, BorderLayout.CENTER)
            wireEvents()
            wireLoadHandler()
            wireCurrentFileTracking()
        } else {
            add(buildJcefNotSupportedPanel(), BorderLayout.CENTER)
        }
    }

    private fun buildHeader(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(0, 0, 8, 0)

        val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        statusPanel.add(loginStatusLabel)
        statusPanel.add(loginButton)
        statusPanel.add(logoutButton)
        panel.add(statusPanel, BorderLayout.WEST)

        val actionPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        actionPanel.add(updateCodeButton)
        actionPanel.add(submitButton)
        actionPanel.add(languageSettingsButton)
        panel.add(actionPanel, BorderLayout.EAST)

        return panel
    }

    private fun buildJcefNotSupportedPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        val label = JLabel("이 IDE에서는 제출 기능을 지원하지 않습니다. (JCEF 미지원)")
        label.horizontalAlignment = JLabel.CENTER
        panel.add(label, BorderLayout.CENTER)
        return panel
    }

    private fun createBrowserOrNull(): JBCefBrowser? {
        val application = ApplicationManager.getApplication() ?: return null
        if (application.isUnitTestMode) return null
        return runCatching {
            if (JBCefApp.isSupported()) JBCefBrowser() else null
        }.getOrNull()
    }

    private fun wireEvents() {
        loginButton.addActionListener { navigateToLogin() }
        logoutButton.addActionListener { handleLogout() }
        submitButton.addActionListener { navigateToSubmit() }
        updateCodeButton.addActionListener {
            val updated = injectSubmitFormData()
            if (updated) {
                updateCodeButton.text = "업데이트 완료"
                updateCodeButton.isEnabled = false
                javax.swing.Timer(1500) {
                    updateCodeButton.text = "소스코드 업데이트"
                    updateCodeButton.isEnabled = true
                }.apply { isRepeats = false; start() }
            }
        }
        languageSettingsButton.addActionListener { LanguageSettingsDialog(project).show() }
    }

    private fun isActiveTab(): Boolean {
        return runCatching {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("BOJ Helper") ?: return false
            toolWindow.contentManager.selectedContent?.component === this
        }.getOrDefault(false)
    }

    private fun wireCurrentFileTracking() {
        runCatching {
            project.messageBus.connect(project).subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                object : FileEditorManagerListener {
                    override fun selectionChanged(event: FileEditorManagerEvent) {
                        if (!isActiveTab()) return
                        val fileName = event.newFile?.name ?: return
                        val problemNumber = BojToolWindowPanel.extractProblemNumberFromFileName(fileName) ?: return
                        val currentUrl = browser?.cefBrowser?.url
                        val targetSubmitPath = "/submit/$problemNumber"
                        submitButton.isEnabled = true
                        if (currentUrl == null || !currentUrl.contains(targetSubmitPath)) {
                            browser?.cefBrowser?.loadURL("$BOJ_SUBMIT_URL/$problemNumber")
                        }
                    }
                },
            )
        }
    }

    private fun wireLoadHandler() {
        browser?.jbCefClient?.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (!frame.isMain) return
                val url = cefBrowser.url ?: return
                ApplicationManager.getApplication().invokeLater {
                    handleUrlChanged(url)
                }
            }
        }, browser.cefBrowser)
    }

    private fun handleUrlChanged(url: String) {
        when {
            url.contains("/login") -> {
                updateLoginState(false, null)
                updateCodeButton.isEnabled = false
            }
            url.contains("/submit/") -> {
                updateCodeButton.isEnabled = true
                extractUsername()
                injectSubmitFormData()
            }
            url.startsWith(BOJ_BASE_URL) && !url.contains("/login") -> {
                updateCodeButton.isEnabled = false
                extractUsername()
            }
        }
    }

    private fun extractUsername() {
        browser?.cefBrowser?.executeJavaScript(
            """
            (function() {
                var userLink = document.querySelector('a.username');
                if (userLink) {
                    document.title = 'BOJ_USER:' + userLink.textContent.trim();
                }
            })();
            """.trimIndent(),
            browser.cefBrowser.url, 0
        )
        // title 변경을 감지하여 username 추출
        // JBCefBrowser는 title 변경을 직접 감지하기 어려우므로
        // 약간의 딜레이 후 title을 확인
        ApplicationManager.getApplication().executeOnPooledThread {
            Thread.sleep(500)
            ApplicationManager.getApplication().invokeLater {
                // 대안: JS에서 직접 쿠키/로그인 상태 확인
                checkLoginStatus()
            }
        }
    }

    private fun checkLoginStatus() {
        browser?.cefBrowser?.executeJavaScript(
            """
            (function() {
                var userLink = document.querySelector('a.username');
                if (userLink) {
                    window.__boj_username = userLink.textContent.trim();
                }
            })();
            """.trimIndent(),
            browser.cefBrowser.url, 0
        )
        // 간단한 방법: URL이 /login이 아니면 로그인된 것으로 간주
        val url = browser?.cefBrowser?.url ?: return
        if (url.startsWith(BOJ_BASE_URL) && !url.contains("/login")) {
            updateLoginState(true, null)
        }
    }

    private fun updateLoginState(loggedIn: Boolean, name: String?) {
        isLoggedIn = loggedIn
        username = name
        if (loggedIn) {
            loginStatusLabel.text = if (name != null) "로그인됨: $name" else "로그인됨"
            loginButton.isVisible = false
            logoutButton.isVisible = true
            submitButton.isEnabled = findCurrentProblemNumber() != null
        } else {
            loginStatusLabel.text = "미로그인"
            loginButton.isVisible = true
            logoutButton.isVisible = false
            submitButton.isEnabled = findCurrentProblemNumber() != null
        }
    }

    private fun navigateToLogin() {
        browser?.cefBrowser?.loadURL(BOJ_LOGIN_URL)
    }

    private fun handleLogout() {
        // CEF 쿠키를 삭제하여 로그아웃
        org.cef.network.CefCookieManager.getGlobalManager()
            ?.deleteCookies("https://www.acmicpc.net", "")
        updateLoginState(false, null)
        navigateToLogin()
    }

    private fun navigateToSubmit() {
        val problemNumber = findCurrentProblemNumber()
        if (problemNumber == null) {
            loginStatusLabel.text = "먼저 백준 탭에서 문제를 불러오세요."
            return
        }
        // JBCefBrowser.loadURL()은 사용자 내부 네비게이션 후 동작하지 않으므로
        // CefBrowser.loadURL()을 직접 호출
        browser?.cefBrowser?.loadURL("$BOJ_SUBMIT_URL/$problemNumber")
    }

    private fun injectSubmitFormData(): Boolean {
        val editor = runCatching {
            FileEditorManager.getInstance(project).selectedTextEditor
        }.getOrNull() ?: return false

        val document = editor.document
        val code = document.text
        val virtualFile = FileDocumentManager.getInstance().getFile(document)
        val extension = virtualFile?.extension

        // CopyForSubmitUtil로 변환 (Java의 경우 클래스명을 Main으로 변경)
        val transformedCode = CopyForSubmitUtil.transformForSubmit(code, extension)
        val languageName = extension?.let { LanguageMapper.toBojLanguageName(it) }
        val languageId = extension?.let { LanguageMapper.toBojLanguageId(it) }

        // JavaScript 이스케이프
        val escapedCode = transformedCode
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\$", "\\\$")

        val js = buildString {
            append("var __bojLangChanged = false;\n")

            // 1. 언어 선택 (ID 기반 - 숨긴 언어도 선택 가능)
            if (languageId != null) {
                append("""
                    try { (function() {
                        var ${'$'} = window.jQuery;
                        var select = document.getElementById('language');
                        if (!select) return;
                        var targetId = '$languageId';
                        if (${'$'}(select).val() === targetId) return;
                        __bojLangChanged = true;
                        var found = false;
                        for (var i = 0; i < select.options.length; i++) {
                            if (select.options[i].value === targetId) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            var opt = document.createElement('option');
                            opt.value = targetId;
                            opt.text = '${languageName ?: ""}';
                            select.appendChild(opt);
                        }
                        if (${'$'}) {
                            ${'$'}(select).val(targetId).trigger('chosen:updated').trigger('change');
                        } else {
                            select.value = targetId;
                            select.dispatchEvent(new Event('change', { bubbles: true }));
                        }
                    })(); } catch(e) {}
                """.trimIndent())
            }

            // 2. 코드 에디터에 코드 삽입 (언어 변경 시에만 딜레이)
            append("""
                var __bojScrollToSubmit = function() {
                    var el = document.querySelector('.cf-turnstile');
                    if (el) el.scrollIntoView({behavior: 'smooth', block: 'center'});
                };
                var __bojInjectCode = function() {
                    // CodeMirror
                    var cmElement = document.querySelector('.CodeMirror');
                    if (cmElement && cmElement.CodeMirror) {
                        cmElement.CodeMirror.setValue(`$escapedCode`);
                        __bojScrollToSubmit();
                        return;
                    }
                    // Ace Editor
                    var aceElement = document.querySelector('.ace_editor');
                    if (aceElement && ace) {
                        var editor = ace.edit(aceElement);
                        editor.setValue(`$escapedCode`, -1);
                        __bojScrollToSubmit();
                        return;
                    }
                    // textarea fallback
                    var textarea = document.querySelector('textarea[name="source"]');
                    if (textarea) {
                        textarea.value = `$escapedCode`;
                    }
                    __bojScrollToSubmit();
                };
                if (__bojLangChanged) {
                    setTimeout(__bojInjectCode, 500);
                } else {
                    __bojInjectCode();
                }
            """.trimIndent())
        }

        browser?.cefBrowser?.executeJavaScript(js, browser.cefBrowser.url, 0)
        return true
    }

    private fun findCurrentProblemNumber(): String? {
        return runCatching {
            val toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow("BOJ Helper") ?: return null
            val content = toolWindow.contentManager.getContent(0) ?: return null
            val bojPanel = content.component as? BojToolWindowPanel ?: return null
            bojPanel.getCurrentProblemNumber()
        }.getOrNull()
    }

    fun onTabSelected() {
        val problemNumber = findCurrentProblemNumber()
        submitButton.isEnabled = problemNumber != null

        if (browser == null) return

        val currentUrl = browser.cefBrowser.url
        updateCodeButton.isEnabled = currentUrl?.contains("/submit/") == true

        if (problemNumber != null) {
            val targetSubmitPath = "/submit/$problemNumber"
            // 이미 해당 문제의 제출 페이지면 재이동하지 않음
            if (currentUrl == null || !currentUrl.contains(targetSubmitPath)) {
                browser.cefBrowser.loadURL("$BOJ_SUBMIT_URL/$problemNumber")
            }
        } else if (currentUrl.isNullOrBlank() || currentUrl == "about:blank") {
            navigateToLogin()
        }
    }

    override fun dispose() {
        browser?.let(Disposer::dispose)
    }
}
