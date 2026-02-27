package com.boj.intellij.custom

import java.io.File

class CustomTestCaseRepository(
    private val baseDir: File,
) {
    fun load(problemId: String): Map<String, CustomTestCase> {
        val dir = problemDir(problemId)
        if (!dir.isDirectory) return emptyMap()

        return dir.listFiles()
            ?.filter { it.extension == "json" }
            ?.associate { file ->
                val name = file.nameWithoutExtension
                val case = parseJson(file.readText())
                name to case
            }
            ?: emptyMap()
    }

    fun save(problemId: String, name: String, case: CustomTestCase) {
        val dir = problemDir(problemId)
        dir.mkdirs()
        val fileName = sanitizeFileName(name)
        val file = File(dir, "$fileName.json")
        file.writeText(toJson(case))
    }

    fun delete(problemId: String, name: String) {
        val fileName = sanitizeFileName(name)
        val file = File(problemDir(problemId), "$fileName.json")
        file.delete()
    }

    fun nextAutoName(problemId: String): String {
        val existing = load(problemId).keys
        var counter = 1
        while ("커스텀 $counter" in existing) {
            counter++
        }
        return "커스텀 $counter"
    }

    private fun problemDir(problemId: String): File =
        File(baseDir, "custom-cases/$problemId")

    companion object {
        fun sanitizeFileName(name: String): String =
            name.replace(Regex("[/\\\\:*?\"<>|]"), "_")

        private fun toJson(case: CustomTestCase): String {
            val escapedInput = escapeJsonString(case.input)
            val expectedPart = if (case.expectedOutput != null) {
                "\"expectedOutput\": \"${escapeJsonString(case.expectedOutput)}\""
            } else {
                "\"expectedOutput\": null"
            }
            return "{\n  \"input\": \"$escapedInput\",\n  $expectedPart\n}"
        }

        private fun parseJson(json: String): CustomTestCase {
            val input = extractJsonStringValue(json, "input") ?: ""
            val expectedOutput = extractJsonStringValue(json, "expectedOutput")
            return CustomTestCase(input = input, expectedOutput = expectedOutput)
        }

        private fun escapeJsonString(value: String): String =
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")

        private fun extractJsonStringValue(json: String, key: String): String? {
            val nullPattern = "\"${Regex.escape(key)}\"\\s*:\\s*null".toRegex()
            if (nullPattern.containsMatchIn(json)) return null

            val pattern = "\"${Regex.escape(key)}\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
            val raw = pattern.find(json)?.groupValues?.get(1) ?: return null
            return raw
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
        }
    }
}
