package com.boj.intellij.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.jcef.JBCefApp

/**
 * JCEF(내장 브라우저)를 쓸 수 있는지 안전하게 확인한다.
 *
 * IntelliJ 2026.2부터 JCEF는 번들 플러그인 "Web Browser (JCEF)"(com.intellij.modules.jcef)로 분리되었다.
 * 이 플러그인이 꺼져 있으면 JCEF 클래스 자체를 로드할 수 없다. JCEF 타입을 필드나 메서드 시그니처에 가진
 * Swing 컴포넌트는 AWT Component 생성자의 리플렉션 단계에서 바로 NoClassDefFoundError를 내므로,
 * 그런 클래스는 이 검사를 통과했을 때만 생성해야 한다.
 */
object JcefSupport {
    private val LOG = Logger.getInstance(JcefSupport::class.java)

    fun isAvailable(): Boolean {
        val application = ApplicationManager.getApplication() ?: return false
        if (application.isUnitTestMode) return false

        return try {
            JBCefApp.isSupported()
        } catch (e: LinkageError) {
            LOG.warn("JCEF classes are not available: $e")
            false
        } catch (e: Exception) {
            LOG.warn("Failed to check JCEF support", e)
            false
        }
    }
}
