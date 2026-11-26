package com.boj.intellij.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import javax.swing.JComponent

/**
 * 문제 보기용 JCEF 브라우저와 JS → Kotlin 통신 쿼리를 감싼다.
 *
 * JCEF 타입은 이 클래스 안에만 둔다. [BojToolWindowPanel]의 시그니처에 JCEF 타입이 드러나면
 * JCEF를 쓸 수 없는 환경에서 패널 생성 자체가 실패해 폴백 UI도 표시할 수 없다.
 * [JcefSupport.isAvailable]이 true일 때만 생성한다.
 */
class ProblemBrowserView(onJsQuery: (String) -> Unit) : Disposable {
    private val browser = JBCefBrowser()

    private val jsQuery = JBCefJSQuery.create(browser as JBCefBrowserBase).also { query ->
        query.addHandler { request ->
            onJsQuery(request)
            null // null 반환 시 성공으로 처리
        }
    }

    val component: JComponent
        get() = browser.component

    fun loadHtml(html: String) {
        browser.loadHTML(html)
    }

    /** JS 코드에서 [paramName] 변수 값을 Kotlin 쪽으로 보내는 스크립트 조각을 반환한다. */
    fun injectQuery(paramName: String): String = jsQuery.inject(paramName)

    override fun dispose() {
        Disposer.dispose(jsQuery)
        Disposer.dispose(browser)
    }
}
