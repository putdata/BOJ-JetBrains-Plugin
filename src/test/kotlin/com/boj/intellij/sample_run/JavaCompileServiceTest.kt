package com.boj.intellij.sample_run

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class JavaCompileServiceTest {

    @Test
    fun `extractJavaSourceInfo parses java source command`() {
        val info = JavaCompileService.extractJavaSourceInfo("java \"/tmp/boj/Main.java\"")
        assertNotNull(info)
        assertEquals("Main", info.className)
        assertTrue(info.sourceFile.path.endsWith("Main.java"))
    }

    @Test
    fun `extractJavaSourceInfo returns null for non-java commands`() {
        assertNull(JavaCompileService.extractJavaSourceInfo("python3 main.py"))
        assertNull(JavaCompileService.extractJavaSourceInfo("kotlin com.boj.MainKt"))
    }

    @Test
    fun `extractJavaSourceInfo returns null for java class execution`() {
        assertNull(JavaCompileService.extractJavaSourceInfo("java -cp /dir Main"))
    }

    @Test
    fun `compile succeeds with valid java source`() {
        if (!isJavacAvailable()) return
        val tempDir = createTempDirectory("javac-test").toFile()
        try {
            val sourceFile = File(tempDir, "Main.java")
            sourceFile.writeText("""
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Hello");
                    }
                }
            """.trimIndent())

            val outputDir = File(tempDir, ".boj-out")
            val result = JavaCompileService.compile("javac", sourceFile, outputDir)
            assertTrue(result.success, "Compile should succeed: ${result.errorOutput}")
            assertTrue(File(outputDir, "Main.class").exists())
            assertFalse(File(tempDir, "Main.class").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `compile fails with syntax error`() {
        if (!isJavacAvailable()) return
        val tempDir = createTempDirectory("javac-test").toFile()
        try {
            val sourceFile = File(tempDir, "Bad.java")
            sourceFile.writeText("public class Bad { invalid syntax }")
            val result = JavaCompileService.compile("javac", sourceFile, File(tempDir, ".boj-out"))
            assertFalse(result.success)
            assertTrue(result.errorOutput.isNotBlank())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `buildRunCommand generates correct command`() {
        val cmd = JavaCompileService.buildRunCommand("java", File("/tmp/boj"), "Main")
        assertEquals("\"java\" -cp \"/tmp/boj\" Main", cmd)
    }

    @Test
    fun `compileAndBuildCommand returns original command for non-java`() {
        val result = JavaCompileService.compileAndBuildCommand("python3 main.py", null)
        assertTrue(result.success)
        assertEquals("python3 main.py", result.effectiveCommand)
    }

    @Test
    fun `cleanupOutputDir only deletes boj-out directory`() {
        val tempDir = createTempDirectory("cleanup-test").toFile()
        try {
            val bojOut = File(tempDir, ".boj-out").apply { mkdirs() }
            File(bojOut, "Main.class").createNewFile()
            JavaCompileService.cleanupOutputDir(bojOut)
            assertFalse(bojOut.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `cleanupOutputDir ignores non-boj-out directory`() {
        val tempDir = createTempDirectory("cleanup-test").toFile()
        try {
            JavaCompileService.cleanupOutputDir(tempDir)
            assertTrue(tempDir.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun isJavacAvailable(): Boolean =
        runCatching { ProcessBuilder("javac", "-version").start().waitFor() == 0 }.getOrDefault(false)
}
