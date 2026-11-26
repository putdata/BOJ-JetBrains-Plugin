package com.boj.intellij.ui

import com.boj.intellij.submit.BojSubmitPanel
import com.boj.intellij.submit.SubmitUnavailablePanel
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

        // BojSubmitPanel은 JCEF 타입에 직접 의존하므로 JCEF를 쓸 수 없으면 생성하지 않는다.
        val submitPanel = if (JcefSupport.isAvailable()) BojSubmitPanel(project) else null
        val submitContent = contentFactory.createContent(submitPanel ?: SubmitUnavailablePanel(), "제출", false)
        submitPanel?.let(submitContent::setDisposer)
        toolWindow.contentManager.addContent(submitContent)

        val generalPanel = GeneralTestPanel(project)
        val generalContent = contentFactory.createContent(generalPanel, "일반", false)
        generalContent.setDisposer(generalPanel)
        toolWindow.contentManager.addContent(generalContent)

        val settingsPanel = SettingsPanel(project)
        val settingsContent = contentFactory.createContent(settingsPanel, "설정", false)
        settingsContent.setDisposer(settingsPanel)
        toolWindow.contentManager.addContent(settingsContent)

        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                if (event.operation == ContentManagerEvent.ContentOperation.add) {
                    when (val component = event.content.component) {
                        is BojToolWindowPanel -> component.onTabSelected()
                        is GeneralTestPanel -> component.onTabSelected()
                        is BojSubmitPanel -> component.onTabSelected()
                        is SettingsPanel -> component.onTabSelected()
                    }
                }
            }
        })
    }
}
