package com.boj.intellij.service

import com.boj.intellij.sample_run.OutputComparisonResult
import com.boj.intellij.sample_run.SampleRunResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class TestResultServiceTest {
    @Test
    fun `adds sample result and notifies listener`() {
        val service = TestResultService()
        var notifiedIndex: Int? = null
        service.addListener(object : TestResultService.Listener {
            override fun onSampleResult(index: Int, result: SampleRunResult) {
                notifiedIndex = index
            }
            override fun onAllResultsCleared() {}
            override fun onRunAllComplete(passedCount: Int, totalCount: Int) {}
        })

        val result = createPassResult()
        service.addSampleResult(0, result)

        assertEquals(0, notifiedIndex)
        assertEquals(result, service.getResult(0))
    }

    @Test
    fun `clears results and notifies listener`() {
        val service = TestResultService()
        service.addSampleResult(0, createPassResult())

        var cleared = false
        service.addListener(object : TestResultService.Listener {
            override fun onSampleResult(index: Int, result: SampleRunResult) {}
            override fun onAllResultsCleared() { cleared = true }
            override fun onRunAllComplete(passedCount: Int, totalCount: Int) {}
        })

        service.clearResults()
        assertTrue(cleared)
        assertNull(service.getResult(0))
    }

    @Test
    fun `notifies run all complete`() {
        val service = TestResultService()
        var passedCount = -1
        var totalCount = -1
        service.addListener(object : TestResultService.Listener {
            override fun onSampleResult(index: Int, result: SampleRunResult) {}
            override fun onAllResultsCleared() {}
            override fun onRunAllComplete(passed: Int, total: Int) {
                passedCount = passed
                totalCount = total
            }
        })

        service.notifyRunAllComplete(3, 4)
        assertEquals(3, passedCount)
        assertEquals(4, totalCount)
    }

    @Test
    fun `sets sample info`() {
        val service = TestResultService()
        service.setSampleInfo(0, "1 2", "3")
        service.setSampleInfo(1, "10 20", "30")

        assertEquals("1 2", service.getSampleInput(0))
        assertEquals("3", service.getSampleExpectedOutput(0))
        assertEquals(2, service.getSampleCount())
    }

    @Test
    fun `adds result with TestCaseKey and notifies listener`() {
        val service = TestResultService()
        var notifiedKey: TestCaseKey? = null
        service.addListener(object : TestResultService.Listener {
            override fun onSampleResult(index: Int, result: SampleRunResult) {}
            override fun onAllResultsCleared() {}
            override fun onRunAllComplete(passedCount: Int, totalCount: Int) {}
            override fun onResult(key: TestCaseKey, result: SampleRunResult) {
                notifiedKey = key
            }
        })

        val key = TestCaseKey.Custom("엣지케이스")
        service.addResult(key, createPassResult())

        assertEquals(key, notifiedKey)
        assertEquals(createPassResult(), service.getResult(key))
    }

    @Test
    fun `sets and gets custom case info`() {
        val service = TestResultService()
        val key = TestCaseKey.Custom("테스트1")
        service.setCaseInfo(key, "입력값", "출력값")

        assertEquals("입력값", service.getCaseInput(key))
        assertEquals("출력값", service.getCaseExpectedOutput(key))
    }

    private fun createPassResult() = SampleRunResult(
        passed = true,
        actualOutput = "3",
        expectedOutput = "3",
        standardError = "",
        exitCode = 0,
        timedOut = false,
        comparison = OutputComparisonResult(
            passed = true,
            normalizedExpected = "3",
            normalizedActual = "3",
        ),
    )
}
