package com.boj.intellij.sample_run

interface SampleRunService {
    fun runSample(sampleCase: SampleCase): SampleRunResult

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Long = 10_000
    }
}
