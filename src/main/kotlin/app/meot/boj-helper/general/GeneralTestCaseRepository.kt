package com.boj.intellij.general

import java.io.File

data class GeneralTestCase(
    val input: String,
    val expectedOutput: String,
)

class GeneralTestCaseRepository(
    private val baseDir: File,
) {
    fun load(fileName: String): Map<String, GeneralTestCase> {
        val dir = caseDir(fileName)
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

    fun save(fileName: String, testName: String, case: GeneralTestCase) {
        val dir = caseDir(fileName)
        dir.mkdirs()
        val safeName = sanitizeFileName(testName)
        File(dir, "$safeName.in").writeText(case.input)
        File(dir, "$safeName.out").writeText(case.expectedOutput)
    }

    fun delete(fileName: String, testName: String) {
        val dir = caseDir(fileName)
        val safeName = sanitizeFileName(testName)
        File(dir, "$safeName.in").delete()
        File(dir, "$safeName.out").delete()
    }

    fun nextAutoName(fileName: String): String {
        val existing = load(fileName).keys
        var counter = 1
        while ("테스트 $counter" in existing) {
            counter++
        }
        return "테스트 $counter"
    }

    private fun caseDir(fileName: String): File =
        File(baseDir, "general-cases/${sanitizeFileName(fileName)}")

    companion object {
        fun sanitizeFileName(name: String): String =
            name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
    }
}
