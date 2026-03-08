package com.boj.intellij.github

import com.boj.intellij.submit.SubmitResult

object TemplateEngine {

    private val VARIABLE_PATTERN = Regex("""\{(\w+)}""")

    fun render(template: String, variables: Map<String, String>): String {
        return VARIABLE_PATTERN.replace(template) { match ->
            val key = match.groupValues[1]
            variables[key] ?: match.value
        }
    }

    fun buildVariables(
        submitResult: SubmitResult,
        title: String,
        extension: String,
    ): Map<String, String> {
        return mapOf(
            "problemId" to submitResult.problemId,
            "title" to title,
            "language" to submitResult.language,
            "ext" to extension,
            "memory" to submitResult.memory,
            "time" to submitResult.time,
        )
    }
}
