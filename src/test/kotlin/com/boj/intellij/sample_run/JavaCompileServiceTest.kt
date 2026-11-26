package com.boj.intellij.sample_run

import com.boj.intellij.ui.SdkResolver
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
    fun `extractPackageName returns package from declaration`() {
        assertEquals("boj.p1000", JavaCompileService.extractPackageName("package boj.p1000;\n\npublic class Main {}"))
    }

    @Test
    fun `extractPackageName returns null without package declaration`() {
        assertNull(JavaCompileService.extractPackageName("import java.io.*;\npublic class Main {}"))
        assertNull(JavaCompileService.extractPackageName("public class Main { String s = \"package x;\"; }"))
    }

    @Test
    fun `extractPackageName skips leading comments and BOM`() {
        val source = "\uFEFF// 백준 1000번\n/*\n * package wrong;\n */\npackage boj . p1000 ;\npublic class Main {}"
        assertEquals("boj.p1000", JavaCompileService.extractPackageName(source))
    }

    @Test
    fun `compileAndBuildCommand runs packaged class by fully qualified name`() {
        val jdkHome = System.getProperty("java.home")
        if (!File(SdkResolver.resolveJavacBinary(jdkHome)).exists()) return
        val tempDir = createTempDirectory("javac-pkg-test").toFile()
        try {
            val sourceFile = File(tempDir, "boj/p1000/Main.java").apply { parentFile.mkdirs() }
            sourceFile.writeText("""
                package boj.p1000;

                public class Main {
                    public static void main(String[] args) {
                        System.out.println("ok");
                    }
                }
            """.trimIndent())

            val command = "java \"${sourceFile.path.replace("\\", "\\\\")}\""
            val result = JavaCompileService.compileAndBuildCommand(command, jdkHome)
            assertTrue(result.success, "Compile should succeed: ${result.errorOutput}")
            val effectiveCommand = assertNotNull(result.effectiveCommand)
            assertTrue(effectiveCommand.endsWith(" boj.p1000.Main"), effectiveCommand)

            val run = ProcessSampleRunService(effectiveCommand).runSample(SampleCase(input = "", expectedOutput = "ok"))
            assertTrue(run.passed, "Run should pass: ${run.standardError}")
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
