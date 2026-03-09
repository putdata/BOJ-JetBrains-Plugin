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
        assertEquals("백준/{tier}/{problemId}. {title}/{title}.{ext}", state.githubPathTemplate)
        assertEquals("[{tier} {tierNum}] Title: {title}, Time: {time} ms, Memory: {memory} KB", state.githubCommitTemplate)
        assertEquals(emptyList<String>(), state.uploadedSubmissionIds)
    }

    @Test
    fun `State has githubReadmeEnabled default false`() {
        val state = BojSettings.State()
        assertEquals(false, state.githubReadmeEnabled)
    }
}
