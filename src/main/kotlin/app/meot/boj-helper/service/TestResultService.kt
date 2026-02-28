package com.boj.intellij.service

import com.boj.intellij.sample_run.SampleRunResult

class TestResultService {
    interface Listener {
        fun onSampleResult(index: Int, result: SampleRunResult)
        fun onAllResultsCleared()
        fun onRunAllComplete(passedCount: Int, totalCount: Int)
        fun onResult(key: TestCaseKey, result: SampleRunResult) {}
    }

    private val results = mutableMapOf<Int, SampleRunResult>()
    private val listeners = mutableListOf<Listener>()
    private val sampleInputs = mutableMapOf<Int, String>()
    private val sampleExpectedOutputs = mutableMapOf<Int, String>()
    private var sampleCount = 0
    private val keyedResults = mutableMapOf<TestCaseKey, SampleRunResult>()
    private val caseInputs = mutableMapOf<TestCaseKey, String>()
    private val caseExpectedOutputs = mutableMapOf<TestCaseKey, String?>()

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
        keyedResults.clear()
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
        caseInputs.clear()
        caseExpectedOutputs.clear()
    }

    fun addResult(key: TestCaseKey, result: SampleRunResult) {
        keyedResults[key] = result
        listeners.forEach { it.onResult(key, result) }
        if (key is TestCaseKey.Sample) {
            addSampleResult(key.index, result)
        }
    }

    fun getResult(key: TestCaseKey): SampleRunResult? = keyedResults[key]

    fun setCaseInfo(key: TestCaseKey, input: String, expectedOutput: String?) {
        caseInputs[key] = input
        if (expectedOutput != null) caseExpectedOutputs[key] = expectedOutput
        if (key is TestCaseKey.Sample && expectedOutput != null) {
            setSampleInfo(key.index, input, expectedOutput)
        }
    }

    fun getCaseInput(key: TestCaseKey): String? = caseInputs[key]

    fun getCaseExpectedOutput(key: TestCaseKey): String? = caseExpectedOutputs[key]

    fun getCustomKeys(): List<TestCaseKey.Custom> =
        caseInputs.keys.filterIsInstance<TestCaseKey.Custom>()

    fun getGeneralKeys(): List<TestCaseKey.General> =
        caseInputs.keys.filterIsInstance<TestCaseKey.General>()

    fun clearCustomCaseInfo() {
        val customKeys = caseInputs.keys.filterIsInstance<TestCaseKey.Custom>()
        for (key in customKeys) {
            caseInputs.remove(key)
            caseExpectedOutputs.remove(key)
            keyedResults.remove(key)
        }
    }

    fun clearGeneralCaseInfo() {
        val generalKeys = caseInputs.keys.filterIsInstance<TestCaseKey.General>()
        for (key in generalKeys) {
            caseInputs.remove(key)
            caseExpectedOutputs.remove(key)
            keyedResults.remove(key)
        }
    }
}
