package com.boj.intellij.settings

import com.intellij.openapi.options.Configurable
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class BojSettingsConfigurable : Configurable {

    private var timeoutSpinner: JSpinner? = null

    override fun getDisplayName(): String = "BOJ"

    override fun createComponent(): JComponent {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))
        panel.add(JLabel("타임아웃 (초):"))
        timeoutSpinner = JSpinner(SpinnerNumberModel(
            BojSettings.getInstance().state.timeoutSeconds,
            1, 300, 1,
        ))
        panel.add(timeoutSpinner)
        return panel
    }

    override fun isModified(): Boolean {
        return timeoutSpinner?.value != BojSettings.getInstance().state.timeoutSeconds
    }

    override fun apply() {
        BojSettings.getInstance().state.timeoutSeconds = timeoutSpinner?.value as? Int ?: 10
    }

    override fun reset() {
        timeoutSpinner?.value = BojSettings.getInstance().state.timeoutSeconds
    }
}
