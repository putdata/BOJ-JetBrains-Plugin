package com.boj.intellij.ui

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.ui.content.ContentFactory

object ActionToolbarCompat {
    fun ActionToolbar.updateActionsSafe() {
        try {
            ActionToolbar::class.java.getMethod("updateActionsAsync").invoke(this)
        } catch (_: Exception) {
            updateActionsImmediately()
        }
    }

    fun getContentFactory(): ContentFactory {
        return try {
            ContentFactory::class.java.getMethod("getInstance").invoke(null) as ContentFactory
        } catch (_: Exception) {
            val service = ContentFactory::class.java.getField("SERVICE").get(null)
            service.javaClass.getMethod("getInstance").invoke(service) as ContentFactory
        }
    }
}
