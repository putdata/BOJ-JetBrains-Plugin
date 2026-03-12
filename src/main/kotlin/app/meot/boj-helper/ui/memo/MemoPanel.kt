package com.boj.intellij.ui.memo

import com.boj.intellij.common.MemoRepository
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import java.awt.BorderLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class MemoPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val memoRepository: MemoRepository by lazy {
        val basePath = project.basePath ?: throw IllegalStateException("Project basePath is null")
        MemoRepository(File(basePath, ".boj"))
    }

    private val tabbedPane = JBTabbedPane()
    private val textArea = JTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        font = com.intellij.util.ui.JBUI.Fonts.create(Font.MONOSPACED, 12)
        border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
    }
    private val saveButton = JButton("저장")

    private var currentProblemId: String? = null

    // problemId -> 탭 순서대로의 메모 이름 목록
    private val tabOrder = mutableMapOf<String, MutableList<String>>()
    // problemId -> (memoName -> content) 메모리 캐시
    private val cache = mutableMapOf<String, MutableMap<String, String>>()
    // problemId -> dirty 메모 이름 집합
    private val dirtyFlags = mutableMapOf<String, MutableSet<String>>()

    private var updatingText = false

    init {
        val topPanel = JPanel(BorderLayout())
        val addButton = JButton("+")
        addButton.toolTipText = "새 메모 추가"
        addButton.addActionListener { addNewMemo() }

        topPanel.add(tabbedPane, BorderLayout.CENTER)
        topPanel.add(addButton, BorderLayout.EAST)

        val bottomPanel = JPanel(BorderLayout())
        bottomPanel.border = BorderFactory.createEmptyBorder(4, 0, 0, 0)
        bottomPanel.add(saveButton, BorderLayout.EAST)

        add(topPanel, BorderLayout.NORTH)
        add(JBScrollPane(textArea), BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)

        tabbedPane.addChangeListener {
            if (!updatingText) {
                onTabSelected()
            }
        }

        textArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onTextChanged()
            override fun removeUpdate(e: DocumentEvent) = onTextChanged()
            override fun changedUpdate(e: DocumentEvent) = onTextChanged()
        })

        saveButton.addActionListener { saveCurrentMemo() }

        wireTabMouseListener()

        updateEmptyState()
    }

    fun setProblemId(problemId: String?) {
        // 이전 문제의 현재 탭 내용을 캐시에 저장
        cacheCurrentTabContent()

        currentProblemId = problemId
        loadMemos()
    }

    private fun loadMemos() {
        updatingText = true
        tabbedPane.removeAll()

        val problemId = currentProblemId
        if (problemId == null) {
            textArea.text = ""
            textArea.isEnabled = false
            saveButton.isEnabled = false
            updatingText = false
            updateEmptyState()
            return
        }

        val existingOrder = tabOrder[problemId]
        if (existingOrder != null) {
            // 캐시에서 복원 (탭 순서 유지)
            for (memoName in existingOrder) {
                val dirty = dirtyFlags[problemId]?.contains(memoName) == true
                val tabTitle = if (dirty) "$memoName *" else memoName
                tabbedPane.addTab(tabTitle, null)
            }
        } else {
            // 파일에서 로드
            val memoNames = memoRepository.listMemos(problemId)
            if (memoNames.isEmpty()) {
                // 메모 없으면 자동 생성
                val autoName = memoRepository.nextAutoName(problemId)
                tabOrder[problemId] = mutableListOf(autoName)
                cache[problemId] = mutableMapOf(autoName to "")
                dirtyFlags[problemId] = mutableSetOf()
                tabbedPane.addTab(autoName, null)
            } else {
                val order = mutableListOf<String>()
                val memoCache = mutableMapOf<String, String>()
                for (name in memoNames) {
                    order.add(name)
                    memoCache[name] = memoRepository.load(problemId, name)
                    tabbedPane.addTab(name, null)
                }
                tabOrder[problemId] = order
                cache[problemId] = memoCache
                dirtyFlags[problemId] = mutableSetOf()
            }
        }

        textArea.isEnabled = true
        saveButton.isEnabled = true

        if (tabbedPane.tabCount > 0) {
            tabbedPane.selectedIndex = 0
            onTabSelected()
        }

        updatingText = false
        updateEmptyState()
    }

    private fun onTabSelected() {
        val problemId = currentProblemId ?: return
        val selectedIndex = tabbedPane.selectedIndex
        if (selectedIndex < 0) return

        val memoName = getMemoNameAt(problemId, selectedIndex) ?: return

        val cachedContent = cache[problemId]?.get(memoName)
        updatingText = true
        textArea.text = cachedContent ?: ""
        textArea.caretPosition = 0
        updatingText = false
    }

    private fun onTextChanged() {
        if (updatingText) return
        val problemId = currentProblemId ?: return
        val selectedIndex = tabbedPane.selectedIndex
        if (selectedIndex < 0) return
        val memoName = getMemoNameAt(problemId, selectedIndex) ?: return

        // 캐시 업데이트
        cache.getOrPut(problemId) { mutableMapOf() }[memoName] = textArea.text

        // dirty 마킹
        val dirty = dirtyFlags.getOrPut(problemId) { mutableSetOf() }
        if (memoName !in dirty) {
            dirty.add(memoName)
            tabbedPane.setTitleAt(selectedIndex, "$memoName *")
        }
    }

    private fun cacheCurrentTabContent() {
        val problemId = currentProblemId ?: return
        val selectedIndex = tabbedPane.selectedIndex
        if (selectedIndex < 0) return
        val memoName = getMemoNameAt(problemId, selectedIndex) ?: return

        cache.getOrPut(problemId) { mutableMapOf() }[memoName] = textArea.text
    }

    private fun saveCurrentMemo() {
        val problemId = currentProblemId ?: return
        val selectedIndex = tabbedPane.selectedIndex
        if (selectedIndex < 0) return
        val memoName = getMemoNameAt(problemId, selectedIndex) ?: return

        val content = textArea.text
        memoRepository.save(problemId, memoName, content)
        cache.getOrPut(problemId) { mutableMapOf() }[memoName] = content
        dirtyFlags[problemId]?.remove(memoName)
        tabbedPane.setTitleAt(selectedIndex, memoName)
    }

    private fun addNewMemo() {
        val problemId = currentProblemId ?: return
        val existing = tabOrder[problemId]?.toSet() ?: emptySet()
        var counter = 1
        while ("메모 $counter" in existing) counter++
        val newName = "메모 $counter"

        tabOrder.getOrPut(problemId) { mutableListOf() }.add(newName)
        cache.getOrPut(problemId) { mutableMapOf() }[newName] = ""
        tabbedPane.addTab(newName, null)
        tabbedPane.selectedIndex = tabbedPane.tabCount - 1
    }

    private fun wireTabMouseListener() {
        tabbedPane.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val tabIndex = tabbedPane.indexAtLocation(e.x, e.y)
                if (tabIndex < 0) return

                if (e.clickCount == 2) {
                    renameTab(tabIndex)
                }
            }

            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) showTabContextMenu(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) showTabContextMenu(e)
            }
        })
    }

    private fun showTabContextMenu(e: MouseEvent) {
        val tabIndex = tabbedPane.indexAtLocation(e.x, e.y)
        if (tabIndex < 0) return

        val menu = JPopupMenu()

        val renameItem = JMenuItem("이름 변경")
        renameItem.addActionListener { renameTab(tabIndex) }
        menu.add(renameItem)

        val deleteItem = JMenuItem("삭제")
        deleteItem.addActionListener { deleteTab(tabIndex) }
        menu.add(deleteItem)

        menu.show(tabbedPane, e.x, e.y)
    }

    private fun renameTab(tabIndex: Int) {
        val problemId = currentProblemId ?: return
        val oldName = getMemoNameAt(problemId, tabIndex) ?: return

        val newName = JOptionPane.showInputDialog(
            this,
            "새 이름을 입력하세요:",
            "메모 이름 변경",
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            oldName,
        ) as? String ?: return

        if (newName.isBlank() || newName == oldName) return

        // tabOrder 업데이트 (같은 위치에서 이름만 교체)
        val order = tabOrder[problemId] ?: return
        val orderIndex = order.indexOf(oldName)
        if (orderIndex >= 0) order[orderIndex] = newName

        // 캐시 업데이트
        val memoCache = cache[problemId] ?: return
        val content = memoCache.remove(oldName) ?: ""
        memoCache[newName] = content

        // dirty 플래그 이전
        val dirty = dirtyFlags[problemId]
        val wasDirty = dirty?.remove(oldName) == true

        // 파일 이름 변경 (저장된 파일이 있을 때만)
        memoRepository.rename(problemId, oldName, newName)

        // 탭 제목 업데이트
        val tabTitle = if (wasDirty) {
            dirty?.add(newName)
            "$newName *"
        } else newName
        tabbedPane.setTitleAt(tabIndex, tabTitle)
    }

    private fun deleteTab(tabIndex: Int) {
        val problemId = currentProblemId ?: return
        val memoName = getMemoNameAt(problemId, tabIndex) ?: return

        val confirm = JOptionPane.showConfirmDialog(
            this,
            "'$memoName' 메모를 삭제하시겠습니까?",
            "메모 삭제",
            JOptionPane.YES_NO_OPTION,
        )
        if (confirm != JOptionPane.YES_OPTION) return

        // tabOrder, 캐시, dirty에서 제거
        tabOrder[problemId]?.removeAt(tabIndex)
        cache[problemId]?.remove(memoName)
        dirtyFlags[problemId]?.remove(memoName)

        // 파일 삭제
        memoRepository.delete(problemId, memoName)

        updatingText = true
        tabbedPane.removeTabAt(tabIndex)
        updatingText = false

        // 탭이 없으면 새 메모 자동 생성
        if (tabbedPane.tabCount == 0) {
            addNewMemo()
        } else {
            val newIndex = minOf(tabIndex, tabbedPane.tabCount - 1)
            tabbedPane.selectedIndex = newIndex
            onTabSelected()
        }
    }

    private fun getMemoNameAt(problemId: String, tabIndex: Int): String? {
        val order = tabOrder[problemId] ?: return null
        return order.getOrNull(tabIndex)
    }

    private fun updateEmptyState() {
        val hasProblem = currentProblemId != null
        textArea.isEnabled = hasProblem
        saveButton.isEnabled = hasProblem
        if (!hasProblem) {
            textArea.text = ""
        }
    }
}
