package com.boj.intellij.ui.testresult

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.boj.intellij.ui.ActionToolbarCompat

class BojTestResultToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ActionToolbarCompat.getContentFactory()
        val panel = BojTestResultPanel(project)
        val content = contentFactory.createContent(panel, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}
