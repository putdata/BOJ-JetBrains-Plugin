package com.boj.intellij.ui

import com.intellij.openapi.actionSystem.ActionToolbar

object ActionToolbarCompat {
    fun ActionToolbar.updateActionsSafe() {
        runCatching { updateActionsAsync() }
            .onFailure { updateActionsImmediately() }
    }
}
