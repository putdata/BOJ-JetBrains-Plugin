package com.boj.intellij.custom

import java.io.File

class CustomTestCaseRepository(
    private val baseDir: File,
) {
    fun load(problemId: String): Map<String, CustomTestCase> {
        val dir = problemDir(problemId)
        if (!dir.isDirectory) return emptyMap()

        val inFiles = dir.listFiles()?.filter { it.extension == "in" } ?: return emptyMap()
        return inFiles.associate { inFile ->
            val name = inFile.nameWithoutExtension
            val input = inFile.readText()
            val outFile = File(dir, "$name.out")
            val expectedOutput = if (outFile.exists()) outFile.readText() else null
            name to CustomTestCase(input = input, expectedOutput = expectedOutput)
        }
    }

    fun save(problemId: String, name: String, case: CustomTestCase) {
        val dir = problemDir(problemId)
        dir.mkdirs()
        val safeName = sanitizeFileName(name)
        File(dir, "$safeName.in").writeText(case.input)
        if (case.expectedOutput != null) {
            File(dir, "$safeName.out").writeText(case.expectedOutput)
        } else {
            File(dir, "$safeName.out").delete()
        }
    }

    fun delete(problemId: String, name: String) {
        val dir = problemDir(problemId)
        val safeName = sanitizeFileName(name)
        File(dir, "$safeName.in").delete()
        File(dir, "$safeName.out").delete()
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
    }
}
