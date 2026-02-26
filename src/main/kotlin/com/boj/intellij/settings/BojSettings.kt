package com.boj.intellij.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "BojSettings", storages = [Storage("BojSettings.xml")])
class BojSettings : PersistentStateComponent<BojSettings.State> {

    data class State(
        var timeoutSeconds: Int = 10,
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): BojSettings {
            return ApplicationManager.getApplication().getService(BojSettings::class.java)
        }
    }
}
