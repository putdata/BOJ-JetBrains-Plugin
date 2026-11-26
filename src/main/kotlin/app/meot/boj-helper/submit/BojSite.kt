package com.boj.intellij.submit

import java.net.URI

/**
 * 백준 사이트 주소와 로그인 상태 판별.
 * 백준 재오픈 이후 www.acmicpc.net 요청은 acmicpc.net으로 301 리다이렉트된다.
 */
object BojSite {
    const val BASE_URL = "https://acmicpc.net"
    const val LOGGED_OUT = "LOGGED_OUT"
    const val LOGGED_IN_PREFIX = "LOGGED_IN:"

    private val HOSTS = listOf("acmicpc.net", "www.acmicpc.net")

    /** 로그인 쿠키를 찾을 주소. 현재 주소(acmicpc.net)를 먼저 본다. */
    val COOKIE_URLS: List<String> = HOSTS.map { "https://$it" }

    data class LoginState(val loggedIn: Boolean, val username: String?)

    /** www 유무와 관계없이 백준 페이지 URL인지 확인한다. */
    fun isBojUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = runCatching { URI(url).host }.getOrNull() ?: return false
        return host.lowercase() in HOSTS
    }

    /** 페이지에서 읽은 로그인 상태 문자열(LOGGED_OUT 또는 LOGGED_IN:사용자명)을 해석한다. 알 수 없는 값이면 null. */
    fun parseLoginState(raw: String?): LoginState? = when {
        raw == LOGGED_OUT -> LoginState(loggedIn = false, username = null)
        raw != null && raw.startsWith(LOGGED_IN_PREFIX) ->
            LoginState(loggedIn = true, username = raw.removePrefix(LOGGED_IN_PREFIX).trim().ifEmpty { null })
        else -> null
    }
}
