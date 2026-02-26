package com.boj.intellij.sample_run

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class ProcessSampleRunService(
    private val command: String,
    private val timeoutMillis: Long = SampleRunService.DEFAULT_TIMEOUT_MILLIS,
    private val workingDirectory: File? = null,
    private val outputComparator: OutputComparator = OutputComparator(),
) : SampleRunService {
    override fun runSample(sampleCase: SampleCase): SampleRunResult {
        return try {
            val tokens = tokenizeCommand(command)
            val processBuilder = ProcessBuilder(tokens)
            if (workingDirectory != null) {
                processBuilder.directory(workingDirectory)
            }
            val process = processBuilder.start()
            process.outputStream.use { outputStream ->
                outputStream.write(sampleCase.input.toByteArray(StandardCharsets.UTF_8))
                outputStream.flush()
            }

            val finishedInTime = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finishedInTime) {
                process.destroyForcibly()
                process.waitFor(100, TimeUnit.MILLISECONDS)

                val comparison = outputComparator.compare(sampleCase.expectedOutput, "")
                return SampleRunResult(
                    passed = false,
                    actualOutput = "",
                    expectedOutput = sampleCase.expectedOutput,
                    standardError = "",
                    exitCode = null,
                    timedOut = true,
                    comparison = comparison,
                )
            }

            val actualOutput = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val standardError = process.errorStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val exitCode = process.exitValue()
            val comparison = outputComparator.compare(sampleCase.expectedOutput, actualOutput)

            SampleRunResult(
                passed = exitCode == 0 && comparison.passed,
                actualOutput = actualOutput,
                expectedOutput = sampleCase.expectedOutput,
                standardError = standardError,
                exitCode = exitCode,
                timedOut = false,
                comparison = comparison,
            )
        } catch (exception: Exception) {
            val comparison = outputComparator.compare(sampleCase.expectedOutput, "")
            SampleRunResult(
                passed = false,
                actualOutput = "",
                expectedOutput = sampleCase.expectedOutput,
                standardError = exception.message ?: exception::class.simpleName.orEmpty(),
                exitCode = null,
                timedOut = false,
                comparison = comparison,
            )
        }
    }

    companion object {
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
