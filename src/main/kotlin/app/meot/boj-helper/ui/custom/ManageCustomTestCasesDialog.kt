package com.boj.intellij.ui.custom

import com.boj.intellij.custom.CustomTestCase
import com.boj.intellij.custom.CustomTestCaseRepository
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.FlowLayout

class ManageCustomTestCasesDialog(
    private val project: Project?,
    private val repository: CustomTestCaseRepository,
    private val problemId: String,
    private val onChanged: () -> Unit,
) : DialogWrapper(project) {

    private val listModel = DefaultListModel<String>()
    private val caseList = JBList(listModel)
    private var cases: MutableMap<String, CustomTestCase> = mutableMapOf()

    init {
        title = "커스텀 테스트 케이스 관리 — 문제 $problemId"
        setOKButtonText("닫기")
        // Cancel button is already excluded via createActions() returning only okAction
        refreshList()
        caseList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2 && caseList.selectedValue != null) {
                    onEdit()
                }
            }
        })
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(500, 350)

        panel.add(JBScrollPane(caseList), BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        val addButton = JButton("추가")
        val editButton = JButton("편집")
        val deleteButton = JButton("삭제")

        addButton.addActionListener { onAdd() }
        editButton.addActionListener { onEdit() }
        deleteButton.addActionListener { onDelete() }

        buttonPanel.add(addButton)
        buttonPanel.add(editButton)
        buttonPanel.add(deleteButton)
        buttonPanel.border = BorderFactory.createEmptyBorder(4, 0, 0, 0)

        panel.add(buttonPanel, BorderLayout.SOUTH)
        return panel
    }

    override fun createActions() = arrayOf(okAction)

    private fun onAdd() {
        val defaultName = repository.nextAutoName(problemId)
        val dialog = AddCustomTestCaseDialog(project, defaultName)
        if (dialog.showAndGet()) {
            val name = dialog.getCaseName().ifBlank { defaultName }
            repository.save(problemId, name, dialog.getCustomTestCase())
            refreshList()
            onChanged()
        }
    }

    private fun onEdit() {
        val selectedName = caseList.selectedValue ?: return
        val existingCase = cases[selectedName] ?: return
        val dialog = AddCustomTestCaseDialog(project, selectedName, existingCase)
        if (dialog.showAndGet()) {
            val newName = dialog.getCaseName().ifBlank { selectedName }
            if (newName != selectedName) {
                repository.delete(problemId, selectedName)
            }
            repository.save(problemId, newName, dialog.getCustomTestCase())
            refreshList()
            onChanged()
        }
    }

    private fun onDelete() {
        val selectedName = caseList.selectedValue ?: return
        val confirmed = Messages.showYesNoDialog(
            project,
            "'$selectedName' 커스텀 케이스를 삭제하시겠습니까?",
            "삭제 확인",
            Messages.getQuestionIcon(),
        )
        if (confirmed == Messages.YES) {
            repository.delete(problemId, selectedName)
            refreshList()
            onChanged()
        }
    }

    private fun refreshList() {
        cases = repository.load(problemId).toMutableMap()
        listModel.clear()
        cases.keys.sorted().forEach { listModel.addElement(it) }
    }
}
