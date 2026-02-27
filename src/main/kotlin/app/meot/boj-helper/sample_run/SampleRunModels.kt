package com.boj.intellij.sample_run

data class SampleCase(
    val input: String,
    val expectedOutput: String,
)

data class OutputComparisonResult(
    val passed: Boolean,
    val normalizedExpected: String,
    val normalizedActual: String,
)

data class SampleRunResult(
    val passed: Boolean,
    val actualOutput: String,
    val expectedOutput: String,
    val standardError: String,
    val exitCode: Int?,
    val timedOut: Boolean,
    val comparison: OutputComparisonResult,
    val elapsedMs: Long = 0,
)
