package com.boj.intellij.service

sealed class TestCaseKey {
    data class Sample(val index: Int) : TestCaseKey()
    data class Custom(val name: String) : TestCaseKey()
    data class General(val name: String) : TestCaseKey()
}
