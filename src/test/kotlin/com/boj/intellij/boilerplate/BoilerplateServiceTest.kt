package com.boj.intellij.boilerplate

import kotlin.test.Test
import kotlin.test.assertEquals
import java.io.File

class BoilerplateServiceTest {

    @Test
    fun `resolves path from template with problemId and ext`() {
        val result = BoilerplateService.resolvePath(
            template = "{problemId}/Main.{ext}",
            problemId = "1000",
            extension = "java",
        )
        assertEquals("1000/Main.java", result)
    }

    @Test
    fun `resolves path with title variable`() {
        val result = BoilerplateService.resolvePath(
            template = "{problemId}. {title}/Main.{ext}",
            problemId = "1000",
            extension = "java",
            title = "A+B",
        )
        assertEquals("1000. A+B/Main.java", result)
    }

    @Test
    fun `resolves path without title defaults to empty`() {
        val result = BoilerplateService.resolvePath(
            template = "{problemId}/{title}.{ext}",
            problemId = "1000",
            extension = "java",
        )
        assertEquals("1000/.java", result)
    }

    @Test
    fun `creates file with template content`() {
        val tempDir = createTempDir("boilerplate-test")
        try {
            val template = "public class Main {}"
            val result = BoilerplateService.createFile(
                baseDir = tempDir,
                relativePath = "1000/Main.java",
                content = template,
                overwrite = false,
            )
            assertEquals(true, result.success)
            assertEquals(template, File(tempDir, "1000/Main.java").readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `does not overwrite existing file when overwrite is false`() {
        val tempDir = createTempDir("boilerplate-test")
        try {
            val file = File(tempDir, "1000/Main.java")
            file.parentFile.mkdirs()
            file.writeText("existing content")

            val result = BoilerplateService.createFile(
                baseDir = tempDir,
                relativePath = "1000/Main.java",
                content = "new content",
                overwrite = false,
            )
            assertEquals(false, result.success)
            assertEquals("existing content", file.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `overwrites existing file when overwrite is true`() {
        val tempDir = createTempDir("boilerplate-test")
        try {
            val file = File(tempDir, "1000/Main.java")
            file.parentFile.mkdirs()
            file.writeText("existing content")

            val result = BoilerplateService.createFile(
                baseDir = tempDir,
                relativePath = "1000/Main.java",
                content = "new content",
                overwrite = true,
            )
            assertEquals(true, result.success)
            assertEquals("new content", file.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `resolves content template with variables`() {
        val result = BoilerplateService.resolveContent(
            template = "// Problem: {problemId} - {title}\npublic class Main {}",
            problemId = "1000",
            extension = "java",
            title = "A+B",
        )
        assertEquals("// Problem: 1000 - A+B\npublic class Main {}", result)
    }

    @Test
    fun `resolves content without variables returns as-is`() {
        val template = "public class Main {}"
        val result = BoilerplateService.resolveContent(
            template = template,
            problemId = "1000",
            extension = "java",
        )
        assertEquals(template, result)
    }

    @Test
    fun `resolves content with modifier`() {
        val result = BoilerplateService.resolveContent(
            template = "// {title:u}",
            problemId = "1000",
            extension = "java",
            title = "hello",
        )
        assertEquals("// HELLO", result)
    }

    @Test
    fun `returns available extensions from templates`() {
        val templates = mapOf("java" to "...", "py" to "...", "cpp" to "...")
        val extensions = BoilerplateService.getAvailableExtensions(templates)
        assertEquals(listOf("cpp", "java", "py"), extensions.sorted())
    }
}
