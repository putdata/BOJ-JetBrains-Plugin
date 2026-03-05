package com.boj.intellij.submit

import com.boj.intellij.settings.BojSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.table.AbstractTableModel

class LanguageSettingsDialog(
    project: Project?,
) : DialogWrapper(project) {

    private val mappingData: MutableList<Pair<String, String>> = mutableListOf()
    private val tableModel = MappingTableModel()
    private val table = JBTable(tableModel)
    private val defaultLanguageCombo = JComboBox<String>()

    init {
        title = "언어 설정"
        val settings = BojSettings.getInstance()
        settings.languageMappings.entries
            .sortedBy { it.key }
            .forEach { (ext, lang) -> mappingData.add(ext to lang) }
        defaultLanguageCombo.addItem("없음")
        LanguageMapper.BOJ_LANGUAGES.forEach { defaultLanguageCombo.addItem(it) }
        val currentDefault = settings.defaultLanguage
        if (currentDefault != null) {
            defaultLanguageCombo.selectedItem = currentDefault
        } else {
            defaultLanguageCombo.selectedIndex = 0
        }
        setupTable()
        init()
    }

    private fun setupTable() {
        val languageCombo = JComboBox(LanguageMapper.BOJ_LANGUAGES.toTypedArray())
        table.columnModel.getColumn(1).cellEditor = DefaultCellEditor(languageCombo)
        table.rowHeight = 28
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        panel.preferredSize = Dimension(450, 350)

        val scrollPane = JScrollPane(table)
        panel.add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        val addButton = JButton("추가")
        val removeButton = JButton("삭제")
        val resetButton = JButton("초기화")

        addButton.addActionListener {
            mappingData.add("" to LanguageMapper.BOJ_LANGUAGES.first())
            tableModel.fireTableRowsInserted(mappingData.size - 1, mappingData.size - 1)
            table.editCellAt(mappingData.size - 1, 0)
        }
        removeButton.addActionListener {
            val row = table.selectedRow
            if (row >= 0) {
                mappingData.removeAt(row)
                tableModel.fireTableRowsDeleted(row, row)
            }
        }
        resetButton.addActionListener {
            mappingData.clear()
            LanguageMapper.DEFAULT_MAPPINGS.entries
                .sortedBy { it.key }
                .forEach { (ext, lang) -> mappingData.add(ext to lang) }
            tableModel.fireTableDataChanged()
            defaultLanguageCombo.selectedIndex = 0
        }

        buttonPanel.add(addButton)
        buttonPanel.add(removeButton)
        buttonPanel.add(resetButton)

        val bottomPanel = JPanel(BorderLayout(0, 8))
        bottomPanel.add(buttonPanel, BorderLayout.NORTH)

        val defaultPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        defaultPanel.add(JLabel("기본 언어:"))
        defaultPanel.add(defaultLanguageCombo)
        bottomPanel.add(defaultPanel, BorderLayout.SOUTH)

        panel.add(bottomPanel, BorderLayout.SOUTH)
        return panel
    }

    override fun doOKAction() {
        if (table.isEditing) {
            table.cellEditor?.stopCellEditing()
        }
        val settings = BojSettings.getInstance()
        settings.state.languageMappings.clear()
        mappingData
            .filter { it.first.isNotBlank() }
            .forEach { (ext, lang) ->
                settings.state.languageMappings[ext.lowercase().trimStart('.')] = lang
            }
        settings.defaultLanguage = when (defaultLanguageCombo.selectedIndex) {
            0 -> null
            else -> defaultLanguageCombo.selectedItem as? String
        }
        super.doOKAction()
    }

    private inner class MappingTableModel : AbstractTableModel() {
        override fun getRowCount() = mappingData.size
        override fun getColumnCount() = 2
        override fun getColumnName(column: Int) = when (column) {
            0 -> "확장자"
            1 -> "백준 언어"
            else -> ""
        }
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val (ext, lang) = mappingData[rowIndex]
            return when (columnIndex) {
                0 -> ext
                1 -> lang
                else -> ""
            }
        }
        override fun isCellEditable(rowIndex: Int, columnIndex: Int) = true
        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            val (ext, lang) = mappingData[rowIndex]
            mappingData[rowIndex] = when (columnIndex) {
                0 -> (aValue as? String ?: ext) to lang
                1 -> ext to (aValue as? String ?: lang)
                else -> ext to lang
            }
            fireTableCellUpdated(rowIndex, columnIndex)
        }
    }
}
