package com.boj.intellij.github

import com.boj.intellij.submit.SubmitResult
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class UploadConfirmDialog(
    project: Project?,
    private val result: SubmitResult,
    private val problemTitle: String,
) : DialogWrapper(project) {

    init {
        title = "GitHub 업로드 확인"
        setOKButtonText("업로드")
        setCancelButtonText("건너뛰기")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(350, 120)
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val html = """
            <html>
            <b>[${result.problemId}] $problemTitle</b><br><br>
            결과: ${result.result}<br>
            메모리: ${result.memory} KB / 시간: ${result.time} ms<br><br>
            이 풀이를 GitHub에 업로드하시겠습니까?
            </html>
        """.trimIndent()

        panel.add(JLabel(html), BorderLayout.CENTER)
        return panel
    }
}
