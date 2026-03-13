package com.boj.intellij.sample_run

import com.boj.intellij.ui.SdkResolver
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object JavaCompileService {

    data class JavaSourceInfo(
        val sourceFile: File,
        val className: String,
        val sourceDir: File,
    )

    data class CompileResult(
        val success: Boolean,
        val errorOutput: String,
        val outputDir: File? = null,
    )

    data class PrepareResult(
        val success: Boolean,
        val errorOutput: String,
        val effectiveCommand: String?,
        val outputDir: File? = null,
    )

    private const val COMPILE_TIMEOUT_SECONDS = 30L
    private const val OUTPUT_DIR_NAME = ".boj-out"

    fun extractJavaSourceInfo(command: String): JavaSourceInfo? {
        val tokens = ProcessSampleRunService.tokenizeCommand(command)
        if (tokens.size != 2) return null
        if (tokens[0] != "java") return null
        val filePath = tokens[1]
        if (!filePath.endsWith(".java", ignoreCase = true)) return null
        val sourceFile = File(filePath)
        return JavaSourceInfo(
            sourceFile = sourceFile,
            className = sourceFile.nameWithoutExtension,
            sourceDir = sourceFile.parentFile ?: File("."),
        )
    }

    fun compileAndBuildCommand(command: String, sdkHomePath: String?): PrepareResult {
        val javaInfo = extractJavaSourceInfo(command) ?: return PrepareResult(
            success = true, errorOutput = "", effectiveCommand = command,
        )

        val javacPath = if (sdkHomePath != null) SdkResolver.resolveJavacBinary(sdkHomePath) else "javac"
        val javaPath = if (sdkHomePath != null) SdkResolver.resolveJavaBinary(sdkHomePath) else "java"

        val outputDir = File(javaInfo.sourceDir, OUTPUT_DIR_NAME).apply { mkdirs() }
        val compileResult = compile(javacPath, javaInfo.sourceFile, outputDir)

        if (!compileResult.success) {
            return PrepareResult(
                success = false,
                errorOutput = compileResult.errorOutput,
                effectiveCommand = null,
                outputDir = outputDir,
            )
        }

        val runCommand = buildRunCommand(javaPath, outputDir, javaInfo.className)
        return PrepareResult(
            success = true, errorOutput = "", effectiveCommand = runCommand, outputDir = outputDir,
        )
    }

    fun compile(javacPath: String, sourceFile: File, outputDir: File): CompileResult {
        return try {
            outputDir.mkdirs()
            val processBuilder = ProcessBuilder(
                javacPath, "-encoding", "UTF-8", "-d", outputDir.absolutePath, sourceFile.absolutePath,
            )
            processBuilder.directory(sourceFile.parentFile)
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()

            val finished = process.waitFor(COMPILE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return CompileResult(false, "컴파일 시간 초과 (${COMPILE_TIMEOUT_SECONDS}초)", outputDir)
            }

            val output = String(process.inputStream.readAllBytes(), StandardCharsets.UTF_8)
            CompileResult(process.exitValue() == 0, output.trim(), outputDir)
        } catch (e: Exception) {
            CompileResult(false, e.message ?: e::class.simpleName.orEmpty())
        }
    }

    fun buildRunCommand(javaPath: String, classDir: File, className: String): String {
        val escapedJava = quoteForShell(javaPath)
        val escapedDir = quoteForShell(classDir.absolutePath)
        return "$escapedJava -cp $escapedDir $className"
    }

    fun cleanupOutputDir(outputDir: File?) {
        if (outputDir == null) return
        if (outputDir.name != OUTPUT_DIR_NAME) return
        outputDir.deleteRecursively()
    }

    private fun quoteForShell(path: String): String {
        val escaped = path.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }
}
