package com.boj.intellij.sample_run

import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class ProcessSampleRunService(
    private val command: String,
    private val timeoutMillis: Long = SampleRunService.DEFAULT_TIMEOUT_MILLIS,
    private val workingDirectory: File? = null,
    private val outputComparator: OutputComparator = OutputComparator(),
) : SampleRunService {
    override fun runSample(sampleCase: SampleCase): SampleRunResult {
        val startNanos = System.nanoTime()
        return try {
            val tokens = tokenizeCommand(command)
            val processBuilder = ProcessBuilder(tokens)
            if (workingDirectory != null) {
                processBuilder.directory(workingDirectory)
            }
            configureUtf8Environment(processBuilder)
            val process = processBuilder.start()
            process.outputStream.use { outputStream ->
                outputStream.write(sampleCase.input.toByteArray(StandardCharsets.UTF_8))
                outputStream.flush()
            }

            val finishedInTime = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finishedInTime) {
                process.destroyForcibly()
                process.waitFor(100, TimeUnit.MILLISECONDS)

                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                val comparison = outputComparator.compare(sampleCase.expectedOutput, "")
                return SampleRunResult(
                    passed = false,
                    actualOutput = "",
                    expectedOutput = sampleCase.expectedOutput,
                    standardError = "",
                    exitCode = null,
                    timedOut = true,
                    comparison = comparison,
                    elapsedMs = elapsedMs,
                )
            }

            val rawOutput = process.inputStream.readAllBytes()
            val rawError = process.errorStream.readAllBytes()
            val actualOutput = decodeOutput(rawOutput)
            val standardError = stripJvmNoise(decodeOutput(rawError))
            val exitCode = process.exitValue()
            val comparison = outputComparator.compare(sampleCase.expectedOutput, actualOutput)
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000

            SampleRunResult(
                passed = exitCode == 0 && comparison.passed,
                actualOutput = actualOutput,
                expectedOutput = sampleCase.expectedOutput,
                standardError = standardError,
                exitCode = exitCode,
                timedOut = false,
                comparison = comparison,
                elapsedMs = elapsedMs,
            )
        } catch (exception: Exception) {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            val comparison = outputComparator.compare(sampleCase.expectedOutput, "")
            SampleRunResult(
                passed = false,
                actualOutput = "",
                expectedOutput = sampleCase.expectedOutput,
                standardError = exception.message ?: exception::class.simpleName.orEmpty(),
                exitCode = null,
                timedOut = false,
                comparison = comparison,
                elapsedMs = elapsedMs,
            )
        }
    }

    private fun configureUtf8Environment(processBuilder: ProcessBuilder) {
        processBuilder.environment().apply {
            putIfAbsent("PYTHONIOENCODING", "utf-8")
            putIfAbsent("PYTHONUTF8", "1")
            val javaOpts = get("JAVA_TOOL_OPTIONS") ?: ""
            if ("file.encoding" !in javaOpts) {
                put("JAVA_TOOL_OPTIONS", "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 $javaOpts".trim())
            }
        }
    }

    private fun decodeOutput(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val utf8 = String(bytes, StandardCharsets.UTF_8)
        if (REPLACEMENT_CHAR !in utf8) return utf8
        val nativeCharset = nativeCharset()
        if (nativeCharset == StandardCharsets.UTF_8) return utf8
        val native = String(bytes, nativeCharset)
        return if (REPLACEMENT_CHAR !in native) native else utf8
    }

    private fun stripJvmNoise(stderr: String): String =
        stderr.lineSequence()
            .filterNot { it.startsWith("Picked up JAVA_TOOL_OPTIONS:") || it.startsWith("Picked up _JAVA_OPTIONS:") }
            .joinToString("\n")

    private fun nativeCharset(): Charset =
        runCatching {
            val name = System.getProperty("native.encoding")
                ?: System.getProperty("sun.jnu.encoding")
                ?: return@runCatching StandardCharsets.UTF_8
            Charset.forName(name)
        }.getOrDefault(StandardCharsets.UTF_8)

    companion object {
        private const val REPLACEMENT_CHAR = '\uFFFD'

        fun tokenizeCommand(command: String): List<String> {
            val tokens = mutableListOf<String>()
            val current = StringBuilder()
            var quote: Char? = null
            var escaping = false

            for (character in command) {
                if (escaping) {
                    current.append(character)
                    escaping = false
                    continue
                }

                if (character == '\\' && quote != '\'') {
                    escaping = true
                    continue
                }

                if (quote != null) {
                    if (character == quote) {
                        quote = null
                    } else {
                        current.append(character)
                    }
                    continue
                }

                when {
                    character == '"' || character == '\'' -> quote = character
                    character.isWhitespace() -> {
                        if (current.isNotEmpty()) {
                            tokens.add(current.toString())
                            current.clear()
                        }
                    }

                    else -> current.append(character)
                }
            }

            if (escaping) {
                current.append('\\')
            }
            if (quote != null) {
                throw IllegalArgumentException("Unterminated quote in command: $command")
            }
            if (current.isNotEmpty()) {
                tokens.add(current.toString())
            }
            if (tokens.isEmpty()) {
                throw IllegalArgumentException("Command must not be blank")
            }
            return tokens
        }
    }
}
