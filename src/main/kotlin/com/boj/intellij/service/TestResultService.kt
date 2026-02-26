package com.boj.intellij.service

import com.boj.intellij.sample_run.SampleRunResult

class TestResultService {
    interface Listener {
        fun onSampleResult(index: Int, result: SampleRunResult)
        fun onAllResultsCleared()
        fun onRunAllComplete(passedCount: Int, totalCount: Int)
    }

    private val results = mutableMapOf<Int, SampleRunResult>()
    private val listeners = mutableListOf<Listener>()
    private val sampleInputs = mutableMapOf<Int, String>()
    private val sampleExpectedOutputs = mutableMapOf<Int, String>()
    private var sampleCount = 0

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun addSampleResult(index: Int, result: SampleRunResult) {
        results[index] = result
        listeners.forEach { it.onSampleResult(index, result) }
    }

    fun getResult(index: Int): SampleRunResult? = results[index]

    fun getAllResults(): Map<Int, SampleRunResult> = results.toMap()

    fun clearResults() {
        results.clear()
        listeners.forEach { it.onAllResultsCleared() }
    }

    fun notifyRunAllComplete(passedCount: Int, totalCount: Int) {
        listeners.forEach { it.onRunAllComplete(passedCount, totalCount) }
    }

    fun setSampleInfo(index: Int, input: String, expectedOutput: String) {
        sampleInputs[index] = input
        sampleExpectedOutputs[index] = expectedOutput
        sampleCount = maxOf(sampleCount, index + 1)
    }

    fun getSampleInput(index: Int): String? = sampleInputs[index]

    fun getSampleExpectedOutput(index: Int): String? = sampleExpectedOutputs[index]

    fun getSampleCount(): Int = sampleCount

    fun clearSampleInfo() {
        sampleInputs.clear()
        sampleExpectedOutputs.clear()
        sampleCount = 0
    }
}
