package com.boj.intellij.github

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * GitHub Device Flow 인증.
 * https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow
 */
object GitHubDeviceFlowAuth {

    private const val CLIENT_ID = "Iv23lilkydYwKorZcjw5"
    private const val SCOPE = "repo"

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    data class DeviceCodeResponse(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val interval: Int,
    )

    /**
     * Step 1: device code를 요청한다.
     * 반환된 userCode를 사용자에게 보여주고, verificationUri를 브라우저로 연다.
     */
    fun requestDeviceCode(): DeviceCodeResponse {
        val body = "client_id=$CLIENT_ID&scope=$SCOPE"
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://github.com/login/device/code"))
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("Device code 요청 실패: ${response.statusCode()}")
        }
        return parseDeviceCodeResponse(response.body())
    }

    /**
     * Step 2: access token을 폴링한다.
     * 반환: access_token 문자열, 아직 인증 안 됐으면 null, 에러면 예외.
     */
    fun pollForToken(deviceCode: String): String? {
        val body = "client_id=$CLIENT_ID&device_code=$deviceCode&grant_type=urn:ietf:params:oauth:grant-type:device_code"
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://github.com/login/oauth/access_token"))
            .header("Accept", "application/x-www-form-urlencoded")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val responseBody = response.body()

        val token = parseAccessToken(responseBody)
        if (token != null) return token

        val error = parseError(responseBody)
        return when (error) {
            "authorization_pending", "slow_down" -> null
            "expired_token" -> throw RuntimeException("인증 코드가 만료되었습니다. 다시 시도해주세요.")
            "access_denied" -> throw RuntimeException("사용자가 인증을 거부했습니다.")
            else -> throw RuntimeException("인증 오류: $error")
        }
    }

    fun parseDeviceCodeResponse(json: String): DeviceCodeResponse {
        fun extractString(key: String): String {
            val pattern = """"$key"\s*:\s*"([^"]+)"""".toRegex()
            return pattern.find(json)?.groupValues?.get(1) ?: ""
        }
        fun extractInt(key: String): Int {
            val pattern = """"$key"\s*:\s*(\d+)""".toRegex()
            return pattern.find(json)?.groupValues?.get(1)?.toInt() ?: 5
        }
        return DeviceCodeResponse(
            deviceCode = extractString("device_code"),
            userCode = extractString("user_code"),
            verificationUri = extractString("verification_uri"),
            interval = extractInt("interval"),
        )
    }

    fun parseAccessToken(response: String): String? {
        val pattern = """access_token=([^&]+)""".toRegex()
        return pattern.find(response)?.groupValues?.get(1)
    }

    fun parseError(response: String): String? {
        val pattern = """error=([^&]+)""".toRegex()
        return pattern.find(response)?.groupValues?.get(1)
    }
}
