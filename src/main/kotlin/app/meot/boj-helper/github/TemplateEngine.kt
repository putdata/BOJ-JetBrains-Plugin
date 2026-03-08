package com.boj.intellij.github

import com.boj.intellij.submit.SubmitResult

object TemplateEngine {

    private val VARIABLE_PATTERN = Regex("""\{(\w+)(?::([ulc]))?\}""")

    fun render(template: String, variables: Map<String, String>): String {
        return VARIABLE_PATTERN.replace(template) { match ->
            val key = match.groupValues[1]
            val modifier = match.groupValues[2]
            val value = variables[key] ?: return@replace match.value
            applyModifier(value, modifier)
        }
    }

    private fun applyModifier(value: String, modifier: String): String {
        return when (modifier) {
            "u" -> value.uppercase()
            "l" -> value.lowercase()
            "c" -> value.lowercase().replaceFirstChar { it.uppercase() }
            "" -> value
            else -> value
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
