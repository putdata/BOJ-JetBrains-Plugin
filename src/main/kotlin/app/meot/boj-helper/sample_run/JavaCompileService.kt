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

    private val COMMENT_PATTERN = Regex("""/\*[\s\S]*?\*/|//[^\r\n]*""")
    private val PACKAGE_DECLARATION_PATTERN =
        Regex("""^\s*package\s+([\p{L}_$][\p{L}\p{N}_$]*(?:\s*\.\s*[\p{L}_$][\p{L}\p{N}_$]*)*)\s*;""")
    private val WHITESPACE_PATTERN = Regex("""\s+""")

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

        // javac -d는 패키지 경로(예: boj/p1000/Main.class)로 출력하므로 실행할 때는 FQCN이 필요하다.
        val runCommand = buildRunCommand(javaPath, outputDir, resolveMainClassName(javaInfo))
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

            // 파이프 데드락 방지: waitFor() 전에 출력 스트림을 먼저 읽는다
            val output = String(process.inputStream.readAllBytes(), StandardCharsets.UTF_8)

            val finished = process.waitFor(COMPILE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return CompileResult(false, "컴파일 시간 초과 (${COMPILE_TIMEOUT_SECONDS}초)", outputDir)
            }

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

    /**
     * 소스 맨 앞의 package 선언에서 패키지명을 읽는다. 선언이 없으면 null.
     * package 선언 앞에는 주석만 올 수 있으므로, 주석을 지운 뒤 시작 부분만 확인한다.
     */
    fun extractPackageName(source: String): String? {
        val code = source.removePrefix("\uFEFF").replace(COMMENT_PATTERN, " ")
        return PACKAGE_DECLARATION_PATTERN.find(code)?.groupValues?.get(1)?.replace(WHITESPACE_PATTERN, "")
    }

    private fun resolveMainClassName(javaInfo: JavaSourceInfo): String {
        val source = runCatching { javaInfo.sourceFile.readText(StandardCharsets.UTF_8) }.getOrNull()
        val packageName = source?.let(::extractPackageName)
        return if (packageName.isNullOrEmpty()) javaInfo.className else "$packageName.${javaInfo.className}"
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
