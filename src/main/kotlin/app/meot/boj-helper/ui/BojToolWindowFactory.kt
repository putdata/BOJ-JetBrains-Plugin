package com.boj.intellij.ui

import com.boj.intellij.ui.general.GeneralTestPanel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener

class BojToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ActionToolbarCompat.getContentFactory()

        val bojPanel = BojToolWindowPanel(project)
        val bojContent = contentFactory.createContent(bojPanel, "백준", false)
        bojContent.setDisposer(bojPanel)
        toolWindow.contentManager.addContent(bojContent)

        val generalPanel = GeneralTestPanel(project)
        val generalContent = contentFactory.createContent(generalPanel, "일반", false)
        generalContent.setDisposer(generalPanel)
        toolWindow.contentManager.addContent(generalContent)

        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                if (event.operation == ContentManagerEvent.ContentOperation.add) {
                    when (val component = event.content.component) {
                        is BojToolWindowPanel -> component.onTabSelected()
                        is GeneralTestPanel -> component.onTabSelected()
                    }
                }
            }
        })
    }
}
