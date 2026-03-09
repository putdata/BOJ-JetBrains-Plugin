package com.boj.intellij.github

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

object GitHubCredentialStore {

    private val credentialAttributes = CredentialAttributes(
        generateServiceName("BOJ Helper", "GitHubToken"),
    )

    private val usernameAttributes = CredentialAttributes(
        generateServiceName("BOJ Helper", "GitHubUsername"),
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

    fun getUsername(): String? {
        return PasswordSafe.instance.getPassword(usernameAttributes)
    }

    fun setUsername(username: String) {
        PasswordSafe.instance.set(usernameAttributes, Credentials("github", username))
    }

    fun removeUsername() {
        PasswordSafe.instance.set(usernameAttributes, null)
    }

    fun clearAll() {
        removeToken()
        removeUsername()
    }
}
