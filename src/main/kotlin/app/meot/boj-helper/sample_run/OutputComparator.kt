package com.boj.intellij.sample_run

class OutputComparator {
    fun compare(expectedOutput: String, actualOutput: String): OutputComparisonResult {
        val normalizedExpected = normalize(expectedOutput)
        val normalizedActual = normalize(actualOutput)
        return OutputComparisonResult(
            passed = normalizedExpected == normalizedActual,
            normalizedExpected = normalizedExpected,
            normalizedActual = normalizedActual,
        )
    }

    private fun normalize(text: String): String {
        val unixLineEndingText = text.replace("\r\n", "\n").replace("\r", "\n")
        val lines = unixLineEndingText.split("\n").map { it.trimEnd(' ', '\t') }.toMutableList()
        while (lines.isNotEmpty() && lines.last().isEmpty()) {
            lines.removeAt(lines.lastIndex)
        }
        return lines.joinToString("\n")
    }
}
