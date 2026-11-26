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
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter
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
    private val githubButton = JButton("GitHub")
    private var resultDetector: SubmitResultDetector? = null
    private var uploadJsQuery: JBCefJSQuery? = null
    private var loginStateJsQuery: JBCefJSQuery? = null

    private val browser: JBCefBrowser? = createBrowserOrNull()
    private var isLoggedIn = false
    private var username: String? = null
    private var browserReady = false
    private var pendingUrl: String? = null

    companion object {
        private const val BOJ_BASE_URL = BojSite.BASE_URL
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
            wireLifeSpanHandler()
            wireCurrentFileTracking()
            resultDetector = SubmitResultDetector(browser, this)
            setupUploadJsQuery()
            setupLoginStateJsQuery()
        } else {
            add(SubmitUnavailablePanel(), BorderLayout.CENTER)
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
        actionPanel.add(githubButton)
        panel.add(actionPanel, BorderLayout.EAST)

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
        githubButton.addActionListener {
            com.boj.intellij.github.GitHubSettingsDialog(project).show()
        }
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
                        val filePath = event.newFile?.path ?: return
                        val problemNumber = BojToolWindowPanel.extractProblemNumberFromFilePath(filePath, project.basePath) ?: return
                        val currentUrl = browser?.cefBrowser?.url
                        val targetSubmitPath = "/submit/$problemNumber"
                        submitButton.isEnabled = true
                        if (currentUrl == null || !currentUrl.contains(targetSubmitPath)) {
                            loadUrlSafe("$BOJ_SUBMIT_URL/$problemNumber")
                        }
                    }
                },
            )
        }
    }

    private fun wireLifeSpanHandler() {
        browser?.jbCefClient?.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onAfterCreated(cefBrowser: CefBrowser) {
                ApplicationManager.getApplication().invokeLater {
                    browserReady = true
                    pendingUrl?.let { url ->
                        browser.cefBrowser.loadURL(url)
                        pendingUrl = null
                    }
                }
            }
        }, browser.cefBrowser)
    }

    private fun loadUrlSafe(url: String) {
        if (browser == null) return
        if (browserReady) {
            browser.cefBrowser.loadURL(url)
        } else {
            pendingUrl = url
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
        // 재오픈 후 www.acmicpc.net이 acmicpc.net으로 리다이렉트되므로 호스트는 www 유무와 관계없이 확인한다.
        if (!BojSite.isBojUrl(url)) return
        when {
            url.contains("/login") -> {
                updateLoginState(false, null)
                updateCodeButton.isEnabled = false
            }
            url.contains("/submit/") -> {
                updateCodeButton.isEnabled = true
                refreshLoginState()
                injectSubmitFormData()
            }
            url.contains("/status") -> {
                updateCodeButton.isEnabled = false
                refreshLoginState()
                startResultDetection()
                injectGitHubUploadButtons()
            }
            else -> {
                updateCodeButton.isEnabled = false
                refreshLoginState()
            }
        }
    }

    private fun setupLoginStateJsQuery() {
        if (browser == null) return
        loginStateJsQuery = JBCefJSQuery.create(browser as JBCefBrowserBase).also { query ->
            query.addHandler { raw ->
                BojSite.parseLoginState(raw)?.let { state ->
                    ApplicationManager.getApplication().invokeLater {
                        updateLoginState(state.loggedIn, state.username)
                    }
                }
                null
            }
        }
    }

    /**
     * 페이지 상단 로그인 바로 로그인 여부를 확인한다. 로그아웃 상태면 로그인 바에 /login 링크가 있다.
     * (예전에는 URL이 www.acmicpc.net으로 시작하면 로그인으로 간주했는데, 재오픈 후 주소가 바뀌어 항상 미로그인으로 남았다.)
     */
    private fun refreshLoginState() {
        val query = loginStateJsQuery ?: return
        val js = """
            (function() {
                var bar = document.querySelector('.loginbar');
                if (!bar) return;
                var state;
                if (bar.querySelector('a[href^="/login"]')) {
                    state = '${BojSite.LOGGED_OUT}';
                } else {
                    var user = document.querySelector('a.username');
                    state = '${BojSite.LOGGED_IN_PREFIX}' + (user ? user.textContent.trim() : '');
                }
                ${query.inject("state")}
            })();
        """.trimIndent()
        browser?.cefBrowser?.executeJavaScript(js, browser.cefBrowser.url, 0)
    }

    private fun updateLoginState(loggedIn: Boolean, name: String?) {
        isLoggedIn = loggedIn
        username = name
        if (loggedIn) {
            // 헤더 공간이 좁아 닉네임만 표시하고, 전체 상태는 툴팁으로 보여준다.
            loginStatusLabel.text = name ?: "로그인됨"
            loginStatusLabel.toolTipText = if (name != null) "로그인됨: $name" else null
            loginButton.isVisible = false
            logoutButton.isVisible = true
            submitButton.isEnabled = findCurrentProblemNumber() != null
        } else {
            loginStatusLabel.text = "미로그인"
            loginStatusLabel.toolTipText = null
            loginButton.isVisible = true
            logoutButton.isVisible = false
            submitButton.isEnabled = findCurrentProblemNumber() != null
        }
    }

    private fun navigateToLogin() {
        loadUrlSafe(BOJ_LOGIN_URL)
    }

    private fun handleLogout() {
        // CEF 쿠키를 삭제하여 로그아웃 (빈 URL로 모든 쿠키 삭제)
        org.cef.network.CefCookieManager.getGlobalManager()
            ?.deleteCookies("", "")
        updateLoginState(false, null)
        // deleteCookies는 비동기이므로 삭제 완료 후 페이지 이동
        javax.swing.Timer(300) { navigateToLogin() }.apply {
            isRepeats = false
            start()
        }
    }

    private fun navigateToSubmit() {
        val problemNumber = findCurrentProblemNumber()
        if (problemNumber == null) {
            loginStatusLabel.text = "먼저 백준 탭에서 문제를 불러오세요."
            return
        }
        loadUrlSafe("$BOJ_SUBMIT_URL/$problemNumber")
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
                    // 재오픈 후 캡차가 Cloudflare Turnstile에서 보이지 않는 reCAPTCHA v3로 바뀌어 제출 버튼으로 이동한다.
                    // 버튼은 페이지 스크립트가 표시하기 전까지 숨겨져 있을 수 있어 잠시 재시도한다.
                    var tries = 0;
                    var attempt = function() {
                        var targets = ['#submit_button', '.cf-turnstile'];
                        for (var i = 0; i < targets.length; i++) {
                            var el = document.querySelector(targets[i]);
                            if (el && el.offsetParent !== null) {
                                el.scrollIntoView({behavior: 'smooth', block: 'center'});
                                return;
                            }
                        }
                        if (++tries < 15) setTimeout(attempt, 200);
                    };
                    setTimeout(attempt, 100);
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

    private fun startResultDetection() {
        val problemNumber = findCurrentProblemNumber() ?: return
        resultDetector?.startDetection(problemNumber) { _ -> }
    }

    private fun findProblemTitle(): String? {
        return runCatching {
            val toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                .getToolWindow("BOJ Helper") ?: return null
            val content = toolWindow.contentManager.getContent(0) ?: return null
            val bojPanel = content.component as? com.boj.intellij.ui.BojToolWindowPanel ?: return null
            bojPanel.getCurrentProblemTitle()
        }.getOrNull()
    }

    private fun findCurrentParsedProblem(): com.boj.intellij.parse.ParsedProblem? {
        return runCatching {
            val toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                .getToolWindow("BOJ Helper") ?: return null
            val content = toolWindow.contentManager.getContent(0) ?: return null
            val panel = content.component as? com.boj.intellij.ui.BojToolWindowPanel ?: return null
            panel.getCurrentParsedProblem()
        }.getOrNull()
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
                loadUrlSafe("$BOJ_SUBMIT_URL/$problemNumber")
            }
        } else if (currentUrl.isNullOrBlank() || currentUrl == "about:blank") {
            navigateToLogin()
        }
    }

    private fun setupUploadJsQuery() {
        if (browser == null) return
        uploadJsQuery = JBCefJSQuery.create(
            browser as JBCefBrowserBase
        ).also { query ->
            query.addHandler { jsonString ->
                handleUploadRequest(jsonString)
                null
            }
        }
    }

    private fun handleUploadRequest(jsonString: String) {
        val submissionId = extractJsonValue(jsonString, "submissionId") ?: return
        val problemId = extractJsonValue(jsonString, "problemId") ?: return
        val language = extractJsonValue(jsonString, "language") ?: return
        val memory = extractJsonValue(jsonString, "memory") ?: ""
        val time = extractJsonValue(jsonString, "time") ?: ""
        val codeLength = extractJsonValue(jsonString, "codeLength") ?: ""
        val tierLevel = extractJsonInt(jsonString, "tierLevel") ?: 0
        val submittedAt = extractJsonValue(jsonString, "submittedAt") ?: ""

        val settings = com.boj.intellij.settings.BojSettings.getInstance()
        if (!settings.state.githubEnabled) return
        if (settings.isSubmissionUploaded(submissionId)) return

        val extension = LanguageMapper.toExtension(language) ?: "txt"
        val title = findProblemTitle() ?: "Problem $problemId"
        val problemData = findCurrentParsedProblem()

        fetchSourceCode(submissionId) { sourceCode ->
            if (sourceCode.isBlank()) {
                ApplicationManager.getApplication().invokeLater {
                    browser?.cefBrowser?.executeJavaScript(
                        "if(window.__bojMarkFailed) __bojMarkFailed('$submissionId');",
                        browser?.cefBrowser?.url ?: "", 0
                    )
                }
                return@fetchSourceCode
            }

            com.boj.intellij.github.GitHubUploadService.upload(
                project = project,
                submitResult = SubmitResult(
                    submissionId = submissionId,
                    problemId = problemId,
                    result = "맞았습니다!!",
                    memory = memory,
                    time = time,
                    language = language,
                    codeLength = codeLength,
                ),
                sourceCode = sourceCode,
                title = title,
                extension = extension,
                tierLevel = tierLevel,
                submittedAt = submittedAt,
                problemData = problemData,
                onSuccess = {
                    settings.markSubmissionUploaded(submissionId)
                    ApplicationManager.getApplication().invokeLater {
                        browser?.cefBrowser?.executeJavaScript(
                            "if(window.__bojMarkUploaded) __bojMarkUploaded('$submissionId');",
                            browser?.cefBrowser?.url ?: "", 0
                        )
                    }
                },
                onFailure = {
                    ApplicationManager.getApplication().invokeLater {
                        browser?.cefBrowser?.executeJavaScript(
                            "if(window.__bojMarkFailed) __bojMarkFailed('$submissionId');",
                            browser?.cefBrowser?.url ?: "", 0
                        )
                    }
                },
            )
        }
    }

    private fun fetchSourceCode(submissionId: String, callback: (String) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val cookies = collectBojCookies()
                val request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("$BOJ_BASE_URL/source/download/$submissionId"))
                    .header("Cookie", cookies)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "$BOJ_BASE_URL/status")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .timeout(java.time.Duration.ofSeconds(15))
                    .GET()
                    .build()
                val response = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .build()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                callback(response.body() ?: "")
            } catch (e: Exception) {
                callback("")
            }
        }
    }

    /** 로그인 쿠키가 acmicpc.net / www.acmicpc.net 중 어디에 발급됐든 찾아서 모은다. */
    private fun collectBojCookies(): String {
        for (url in BojSite.COOKIE_URLS) {
            val cookies = collectCookies(url)
            if (cookies.isNotEmpty()) return cookies
        }
        return ""
    }

    private fun collectCookies(url: String): String {
        val cookies = mutableListOf<String>()
        val latch = java.util.concurrent.CountDownLatch(1)
        val cookieManager = org.cef.network.CefCookieManager.getGlobalManager()
        if (cookieManager == null) return ""
        cookieManager.visitUrlCookies(url, true) { cookie, _, total, _ ->
            cookies.add("${cookie.name}=${cookie.value}")
            if (cookies.size >= total) latch.countDown()
            true
        }
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        return cookies.joinToString("; ")
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = """"${Regex.escape(key)}"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
            ?.replace("\\n", "\n")
            ?.replace("\\r", "\r")
            ?.replace("\\t", "\t")
    }

    private fun extractJsonInt(json: String, key: String): Int? {
        val pattern = """"${Regex.escape(key)}"\s*:\s*(\d+)""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun injectGitHubUploadButtons() {
        if (browser == null || uploadJsQuery == null) return

        val settings = com.boj.intellij.settings.BojSettings.getInstance()
        if (!settings.state.githubEnabled) return

        val uploadedIds = settings.state.uploadedSubmissionIds
        val uploadedJson = uploadedIds.joinToString(",") { "\"$it\"" }
        val callbackJs = uploadJsQuery!!.inject("json")

        val js = """
        (function() {
            if (window.__bojGitHubInjected) return;
            window.__bojGitHubInjected = true;
            window.__bojUploaded = new Set([$uploadedJson]);

            function injectButtons() {
                var userLink = document.querySelector('a.username');
                if (!userLink) return;
                var myUsername = userLink.textContent.trim();
                var table = document.getElementById('status-table');
                if (!table) return;
                var rows = table.querySelectorAll('tbody tr');
                rows.forEach(function(row) {
                    var cells = row.querySelectorAll('td');
                    if (cells.length < 8) return;
                    var submitter = cells[1] ? cells[1].textContent.trim() : '';
                    if (submitter !== myUsername) return;
                    var resultCell = cells[3];
                    var resultSpan = resultCell.querySelector('.result-text');
                    if (!resultSpan) return;
                    if (resultSpan.textContent.trim() !== '\ub9de\uc558\uc2b5\ub2c8\ub2e4!!') return;
                    if (row.querySelector('.boj-gh-btn, .boj-gh-check')) return;

                    var submissionId = cells[0].textContent.trim();
                    if (window.__bojUploaded.has(submissionId)) {
                        var check = document.createElement('span');
                        check.className = 'boj-gh-check';
                        check.textContent = ' \u2705';
                        check.title = 'GitHub\uc5d0 \uc5c5\ub85c\ub4dc\ub428';
                        resultSpan.parentNode.appendChild(check);
                    } else {
                        var btn = document.createElement('button');
                        btn.className = 'boj-gh-btn';
                        btn.setAttribute('data-sid', submissionId);
                        btn.textContent = '\u2B06 GitHub';
                        btn.style.cssText = 'margin-left:8px;padding:1px 6px;font-size:11px;cursor:pointer;border:1px solid #ccc;border-radius:3px;background:#f8f8f8;';
                        btn.onclick = function() { __bojUpload(submissionId, cells); };
                        resultSpan.parentNode.appendChild(btn);
                    }
                });
            }

            window.__bojUpload = function(submissionId, cells) {
                var btn = document.querySelector('.boj-gh-btn[data-sid="' + submissionId + '"]');
                if (btn) { btn.textContent = '\uc5c5\ub85c\ub4dc \uc911...'; btn.disabled = true; }

                var problemLink = cells[2].querySelector('a');
                var problemId = problemLink ? problemLink.textContent.trim() : '';
                var langCell = cells[6];
                var langLink = langCell.querySelector('a');
                var language = langLink ? langLink.textContent.trim() : langCell.firstChild ? langCell.firstChild.textContent.trim() : langCell.textContent.trim();
                var memory = cells[4] ? cells[4].textContent.trim() : '';
                var time = cells[5] ? cells[5].textContent.trim() : '';
                var codeLength = cells[7] ? cells[7].textContent.trim() : '';

                // 티어 SVG 추출
                var tierImg = cells[2].querySelector('img[src*="tier/"]');
                var tierLevel = 0;
                if (tierImg) {
                    var tierMatch = tierImg.src.match(/tier\/(\d+)\.svg/);
                    if (tierMatch) tierLevel = parseInt(tierMatch[1], 10);
                }

                // 제출 일자 추출
                var submittedAt = '';
                var timeCell = cells[8];
                if (timeCell) {
                    var timeEl = timeCell.querySelector('a.real-time-update') || timeCell.querySelector('a');
                    if (timeEl) {
                        submittedAt = timeEl.getAttribute('data-original-title')
                            || timeEl.getAttribute('title')
                            || timeEl.textContent.trim();
                    } else {
                        submittedAt = timeCell.textContent.trim();
                    }
                }

                var json = JSON.stringify({
                    submissionId: submissionId,
                    problemId: problemId,
                    language: language,
                    memory: memory,
                    time: time,
                    codeLength: codeLength,
                    tierLevel: tierLevel,
                    submittedAt: submittedAt
                });
                $callbackJs
            };

            window.__bojMarkUploaded = function(sid) {
                window.__bojUploaded.add(sid);
                var btn = document.querySelector('.boj-gh-btn[data-sid="' + sid + '"]');
                if (btn) {
                    var check = document.createElement('span');
                    check.className = 'boj-gh-check';
                    check.textContent = ' \u2705';
                    check.title = 'GitHub\uc5d0 \uc5c5\ub85c\ub4dc\ub428';
                    btn.parentNode.replaceChild(check, btn);
                }
            };

            window.__bojMarkFailed = function(sid) {
                var btn = document.querySelector('.boj-gh-btn[data-sid="' + sid + '"]');
                if (btn) {
                    btn.textContent = '\u274C \uc2e4\ud328';
                    btn.style.color = 'red';
                    btn.disabled = false;
                    setTimeout(function() {
                        btn.textContent = '\u2B06 GitHub';
                        btn.style.color = '';
                    }, 3000);
                }
            };

            var table = document.getElementById('status-table');
            if (table) {
                var observer = new MutationObserver(function() { injectButtons(); });
                observer.observe(table, { childList: true, subtree: true });
            }

            injectButtons();
        })();
        """.trimIndent()

        browser.cefBrowser.executeJavaScript(js, browser.cefBrowser.url, 0)
    }

    override fun dispose() {
        uploadJsQuery?.let { Disposer.dispose(it) }
        loginStateJsQuery?.let { Disposer.dispose(it) }
        browser?.let(Disposer::dispose)
    }
}
