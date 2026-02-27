package com.boj.intellij.ui

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.Presentation

object ActionToolbarCompat {
    fun ActionToolbar.updateActionsSafe() {
        runCatching { updateActionsAsync() }
            .onFailure { updateActionsImmediately() }
    }

    /**
     * ActionUtil.SHOW_TEXT_IN_TOOLBAR은 2024.x에서 도입되었으므로
     * 이전 버전에서는 리플렉션으로 안전하게 처리한다.
     */
    fun Presentation.showTextInToolbar() {
        runCatching {
            val field = Class.forName("com.intellij.openapi.actionSystem.ex.ActionUtil")
                .getField("SHOW_TEXT_IN_TOOLBAR")
            val key = field.get(null)
            @Suppress("UNCHECKED_CAST")
            putClientProperty(key as com.intellij.openapi.util.Key<Boolean>, true)
        }
    }
}
