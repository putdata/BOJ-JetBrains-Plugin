package com.boj.intellij.settings

import com.boj.intellij.submit.LanguageMapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "BojSettings", storages = [Storage("BojSettings.xml")])
class BojSettings : PersistentStateComponent<BojSettings.State> {

    data class State(
        var timeoutSeconds: Int = 10,
        var languageMappings: MutableMap<String, String> = mutableMapOf(),
        var defaultLanguage: String? = null,
        // GitHub 설정
        var githubRepo: String = "",
        var githubBranch: String = "main",
        var githubEnabled: Boolean = false,
        var githubPathTemplate: String = "{language}/{problemId}.{ext}",
        var githubCommitTemplate: String = "[{problemId}] {title}",
        var githubReadmeEnabled: Boolean = false,
        var uploadedSubmissionIds: MutableList<String> = mutableListOf(),
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    val languageMappings: MutableMap<String, String>
        get() {
            val mappings = myState.languageMappings
            if (mappings.isEmpty()) {
                mappings.putAll(LanguageMapper.DEFAULT_MAPPINGS)
            }
            return mappings
        }

    var defaultLanguage: String?
        get() = myState.defaultLanguage
        set(value) { myState.defaultLanguage = value }

    fun isSubmissionUploaded(submissionId: String): Boolean {
        return myState.uploadedSubmissionIds.contains(submissionId)
    }

    fun markSubmissionUploaded(submissionId: String) {
        if (submissionId.all { it.isDigit() } && !myState.uploadedSubmissionIds.contains(submissionId)) {
            myState.uploadedSubmissionIds.add(submissionId)
        }
    }

    companion object {
        fun getInstance(): BojSettings {
            return ApplicationManager.getApplication().getService(BojSettings::class.java)
        }
    }
}
