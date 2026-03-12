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
        var githubPathTemplate: String = "백준/{tier}/{problemId}. {title}/{title}.{ext}",
        var githubCommitTemplate: String = "[{tier} {tierNum}] Title: {title}, Time: {time} ms, Memory: {memory} KB",
        var githubReadmeEnabled: Boolean = false,
        var uploadedSubmissionIds: MutableList<String> = mutableListOf(),
        // 보일러플레이트 설정
        var boilerplatePathTemplate: String = "{problemId}/Main.{ext}",
        var boilerplateTemplates: MutableMap<String, String> = DEFAULT_BOILERPLATE_TEMPLATES.toMutableMap(),
        var lastSelectedLanguage: String = "",
        // 메모 설정
        var memoFontSize: Int = 0,
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

        val DEFAULT_BOILERPLATE_TEMPLATES: Map<String, String> = mapOf(
            "java" to """
                |import java.io.*;
                |import java.util.*;
                |
                |public class Main {
                |    public static void main(String[] args) throws IOException {
                |        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                |        StringBuilder sb = new StringBuilder();
                |
                |        br.close();
                |        System.out.print(sb);
                |    }
                |}
            """.trimMargin(),
            "py" to """
                |import sys
                |input = sys.stdin.readline
                |
            """.trimMargin(),
            "cpp" to """
                |#include <bits/stdc++.h>
                |using namespace std;
                |
                |int main() {
                |    ios::sync_with_stdio(false);
                |    cin.tie(nullptr);
                |
                |    return 0;
                |}
            """.trimMargin(),
            "kt" to """
                |import java.io.BufferedReader
                |import java.io.InputStreamReader
                |
                |fun main() {
                |    val br = BufferedReader(InputStreamReader(System.`in`))
                |
                |    br.close()
                |}
            """.trimMargin(),
        )
    }
}
