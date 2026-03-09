package com.boj.intellij.submit

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * JCEF 브라우저에서 백준 채점 결과를 JavaScript 폴링으로 감지한다.
 */
class SubmitResultDetector(
    private val browser: JBCefBrowser,
    parentDisposable: Disposable,
) : Disposable {

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "BOJ-SubmitResultDetector").apply { isDaemon = true }
    }
    private var pollingTask: ScheduledFuture<*>? = null
    private var onResult: ((SubmitResult) -> Unit)? = null

    private val jsQuery: JBCefJSQuery = JBCefJSQuery.create(browser as JBCefBrowserBase).also { query ->
        query.addHandler { jsonString ->
            handleResultJson(jsonString)
            null
        }
    }

    init {
        Disposer.register(parentDisposable, this)
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1000L
        private const val MAX_POLL_DURATION_MS = 300_000L  // 5분
    }

    fun startDetection(problemId: String, callback: (SubmitResult) -> Unit) {
        stopDetection()
        onResult = callback
        val startTime = System.currentTimeMillis()

        pollingTask = scheduler.scheduleAtFixedRate({
            if (System.currentTimeMillis() - startTime > MAX_POLL_DURATION_MS) {
                stopDetection()
                return@scheduleAtFixedRate
            }
            ApplicationManager.getApplication().invokeLater {
                injectPollingScript(problemId)
            }
        }, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    fun stopDetection() {
        pollingTask?.cancel(false)
        pollingTask = null
        onResult = null
    }

    private fun injectPollingScript(problemId: String) {
        val callbackJs = jsQuery.inject("json")

        val js = """
            (function() {
                try {
                    var table = document.getElementById('status-table');
                    if (!table) return;
                    var row = table.querySelector('tbody tr');
                    if (!row) return;

                    var cells = row.querySelectorAll('td');
                    if (cells.length < 9) return;

                    var submissionId = cells[0].textContent.trim();
                    var resultCell = cells[3];
                    var resultSpan = resultCell.querySelector('.result-text');
                    if (!resultSpan) return;
                    var result = resultSpan.textContent.trim();

                    if (result === '' || result.indexOf('채점') >= 0 || result.indexOf('기다리는') >= 0 || result.indexOf('준비') >= 0) return;

                    var memory = cells[4].textContent.trim();
                    var time = cells[5].textContent.trim();
                    var language = cells[6].textContent.trim();
                    var codeLength = cells[7].textContent.trim();

                    var json = JSON.stringify({
                        submissionId: submissionId,
                        problemId: '$problemId',
                        result: result,
                        memory: memory,
                        time: time,
                        language: language,
                        codeLength: codeLength
                    });
                    $callbackJs
                } catch(e) {}
            })();
        """.trimIndent()

        try {
            browser.cefBrowser.executeJavaScript(js, browser.cefBrowser.url, 0)
        } catch (_: Exception) {}
    }

    private fun handleResultJson(jsonString: String) {
        val result = parseSubmitResult(jsonString) ?: return
        if (!result.isFinalResult()) return

        val callback = onResult
        stopDetection()
        callback?.invoke(result)
    }

    private fun parseSubmitResult(json: String): SubmitResult? {
        return try {
            SubmitResult(
                submissionId = extractJsonValue(json, "submissionId") ?: return null,
                problemId = extractJsonValue(json, "problemId") ?: return null,
                result = extractJsonValue(json, "result") ?: return null,
                memory = extractJsonValue(json, "memory") ?: "",
                time = extractJsonValue(json, "time") ?: "",
                language = extractJsonValue(json, "language") ?: "",
                codeLength = extractJsonValue(json, "codeLength") ?: "",
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = """"${Regex.escape(key)}"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
    }

    override fun dispose() {
        stopDetection()
        scheduler.shutdownNow()
        Disposer.dispose(jsQuery)
    }
}
