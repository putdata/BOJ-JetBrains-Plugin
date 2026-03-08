package com.boj.intellij.github

import com.boj.intellij.settings.BojSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GitHubSettingsFieldsTest {

    @Test
    fun `State has GitHub default values`() {
        val state = BojSettings.State()
        assertEquals("", state.githubRepo)
        assertEquals("main", state.githubBranch)
        assertFalse(state.githubEnabled)
        assertEquals("{language}/{problemId}.{ext}", state.githubPathTemplate)
        assertEquals("[{problemId}] {title}", state.githubCommitTemplate)
        assertEquals(emptyList<String>(), state.uploadedSubmissionIds)
    }

    @Test
    fun `State has githubReadmeEnabled default false`() {
        val state = BojSettings.State()
        assertEquals(false, state.githubReadmeEnabled)
    }
}
