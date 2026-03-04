package com.boj.intellij.submit

object LanguageMapper {

    private val EXTENSION_TO_LANGUAGE = mapOf(
        "java" to "Java 11",
        "kt" to "Kotlin (JVM)",
        "py" to "Python 3",
        "cpp" to "C++17",
        "cc" to "C++17",
        "cxx" to "C++17",
        "c" to "C99",
        "js" to "node.js",
        "go" to "Go",
    )

    fun toBojLanguageName(extension: String): String? {
        return EXTENSION_TO_LANGUAGE[extension.lowercase()]
    }
}
