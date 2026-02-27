package com.boj.intellij.ui.testresult

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class BojTestResultToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val panel = BojTestResultPanel(project)
        val content = contentFactory.createContent(panel, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)

        toolWindow.setTitleActions(panel.titleActions)
    }
}
