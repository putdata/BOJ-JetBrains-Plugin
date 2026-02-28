package com.boj.intellij.general

import java.io.File

data class GeneralTestCase(
    val input: String,
    val expectedOutput: String,
)

class GeneralTestCaseRepository(
    private val baseDir: File,
) {
    fun load(fileKey: String): Map<String, GeneralTestCase> {
        val dir = caseDir(fileKey)
        if (!dir.isDirectory) return emptyMap()

        val inFiles = dir.listFiles()?.filter { it.extension == "in" } ?: return emptyMap()
        return inFiles.associate { inFile ->
            val name = inFile.nameWithoutExtension
            val input = inFile.readText()
            val outFile = File(dir, "$name.out")
            val expectedOutput = if (outFile.exists()) outFile.readText() else ""
            name to GeneralTestCase(input = input, expectedOutput = expectedOutput)
        }
    }

    fun save(fileKey: String, testName: String, case: GeneralTestCase) {
        val dir = caseDir(fileKey)
        dir.mkdirs()
        val safeName = sanitizeFileName(testName)
        File(dir, "$safeName.in").writeText(case.input)
        File(dir, "$safeName.out").writeText(case.expectedOutput)
    }

    fun delete(fileKey: String, testName: String) {
        val dir = caseDir(fileKey)
        val safeName = sanitizeFileName(testName)
        File(dir, "$safeName.in").delete()
        File(dir, "$safeName.out").delete()
    }

    fun nextAutoName(fileKey: String): String {
        val existing = load(fileKey).keys
        var counter = 1
        while ("$counter" in existing) {
            counter++
        }
        return "$counter"
    }

    private fun caseDir(fileKey: String): File =
        File(baseDir, "general-cases/$fileKey")

    companion object {
        fun sanitizeFileName(name: String): String =
            name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
    }
}
