package com.boj.intellij.common

import java.io.File

data class TestCaseRepositoryConfig(
    val baseDirName: String,
    val autoNamePrefix: String,
    val nullableOutput: Boolean,
) {
    companion object {
        val CUSTOM = TestCaseRepositoryConfig(
            baseDirName = "custom-cases",
            autoNamePrefix = "커스텀 ",
            nullableOutput = true,
        )
        val GENERAL = TestCaseRepositoryConfig(
            baseDirName = "general-cases",
            autoNamePrefix = "",
            nullableOutput = false,
        )
    }
}

class TestCaseRepository(
    private val baseDir: File,
    private val config: TestCaseRepositoryConfig,
) {
    fun load(key: String): Map<String, TestCase> {
        val dir = caseDir(key)
        if (!dir.isDirectory) return emptyMap()

        val inFiles = dir.listFiles()?.filter { it.extension == "in" } ?: return emptyMap()
        return inFiles.associate { inFile ->
            val name = inFile.nameWithoutExtension
            val input = inFile.readText()
            val outFile = File(dir, "$name.out")
            val expectedOutput = when {
                outFile.exists() -> outFile.readText()
                config.nullableOutput -> null
                else -> ""
            }
            name to TestCase(input = input, expectedOutput = expectedOutput)
        }
    }

    fun save(key: String, name: String, case: TestCase) {
        val dir = caseDir(key)
        dir.mkdirs()
        val safeName = sanitizeFileName(name)
        File(dir, "$safeName.in").writeText(case.input)
        if (case.expectedOutput != null) {
            File(dir, "$safeName.out").writeText(case.expectedOutput)
        } else {
            File(dir, "$safeName.out").delete()
        }
    }

    fun delete(key: String, name: String) {
        val dir = caseDir(key)
        val safeName = sanitizeFileName(name)
        File(dir, "$safeName.in").delete()
        File(dir, "$safeName.out").delete()
    }

    fun nextAutoName(key: String): String {
        val existing = load(key).keys
        var counter = 1
        while ("${config.autoNamePrefix}$counter" in existing) {
            counter++
        }
        return "${config.autoNamePrefix}$counter"
    }

    private fun caseDir(key: String): File =
        File(baseDir, "${config.baseDirName}/$key")

    companion object {
        fun sanitizeFileName(name: String): String =
            name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
    }
}
