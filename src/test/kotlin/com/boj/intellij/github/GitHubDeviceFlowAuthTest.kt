package com.boj.intellij.github

import kotlin.test.Test
import kotlin.test.assertEquals

class GitHubDeviceFlowAuthTest {

    @Test
    fun `parseDeviceCodeResponse extracts fields correctly`() {
        val json = """{"device_code":"dc_abc123","user_code":"ABCD-1234","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""
        val result = GitHubDeviceFlowAuth.parseDeviceCodeResponse(json)
        assertEquals("dc_abc123", result.deviceCode)
        assertEquals("ABCD-1234", result.userCode)
        assertEquals("https://github.com/login/device", result.verificationUri)
        assertEquals(5, result.interval)
    }

    @Test
    fun `parseAccessTokenResponse extracts token`() {
        val response = "access_token=gho_abc123&token_type=bearer&scope=repo"
        val token = GitHubDeviceFlowAuth.parseAccessToken(response)
        assertEquals("gho_abc123", token)
    }

    @Test
    fun `parseAccessTokenError extracts error type`() {
        val response = "error=authorization_pending&error_description=waiting"
        val error = GitHubDeviceFlowAuth.parseError(response)
        assertEquals("authorization_pending", error)
    }
}
