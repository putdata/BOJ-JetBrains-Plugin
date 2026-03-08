package com.boj.intellij.github

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

object GitHubCredentialStore {

    private val credentialAttributes = CredentialAttributes(
        generateServiceName("BOJ Helper", "GitHubToken"),
    )

    fun getToken(): String? {
        return PasswordSafe.instance.getPassword(credentialAttributes)
    }

    fun setToken(token: String) {
        PasswordSafe.instance.set(credentialAttributes, Credentials("github", token))
    }

    fun removeToken() {
        PasswordSafe.instance.set(credentialAttributes, null)
    }

    fun hasToken(): Boolean {
        return !getToken().isNullOrBlank()
    }
}
