package com.boj.intellij.ui.memo

import com.boj.intellij.common.MemoRepository
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.ui.tabs.impl.JBTabsImpl
import java.awt.BorderLayout
import java.awt.Font
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.BorderFactory
import javax.swing.KeyStroke
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class MemoPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val memoRepository: MemoRepository by lazy {
        val basePath = project.basePath ?: throw IllegalStateException("Project basePath is null")
        MemoRepository(File(basePath, ".boj"))
    }

    private val tabs = JBTabsImpl(project).apply {
        try {
            val ctor = com.intellij.ui.tabs.UiDecorator.UiDecoration::class.java
                .getConstructor(java.awt.Font::class.java, java.awt.Insets::class.java)
            val insets = com.intellij.util.ui.JBUI.insets(8, 8)
            val decoration = ctor.newInstance(null, insets)
            setUiDecorator(object : com.intellij.ui.tabs.UiDecorator {
                override fun getDecoration() = decoration as com.intellij.ui.tabs.UiDecorator.UiDecoration
            })
        } catch (_: Exception) {
        }
    }
    private val textArea = JTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        font = com.intellij.util.ui.JBUI.Fonts.create(Font.MONOSPACED, 12)
        border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
    }

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

        val actionGroup = DefaultActionGroup().apply {
            add(AddMemoAction())
            add(SaveMemoAction())
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("MemoToolbar", actionGroup, true)
        toolbar.targetComponent = this
        toolbar.setMinimumButtonSize(com.intellij.util.ui.JBUI.size(22, 22))

        val isNewUi = try {
            Class.forName("com.intellij.ui.ExperimentalUI")
                .getMethod("isNewUI")
                .invoke(null) as Boolean
        } catch (_: Exception) {
            false
        }

        val tabsWrapper = object : JPanel(BorderLayout()) {
            init { add(tabs.component, BorderLayout.CENTER) }
            override fun getPreferredSize(): java.awt.Dimension {
                val w = super.getPreferredSize().width
                val toolbarHeight = toolbar.component.preferredSize.height
                val headerHeight = try {
                    val m = JBTabsImpl::class.java.getDeclaredMethod("computeHeaderPreferredSize", Int::class.java)
                    m.isAccessible = true
                    (m.invoke(tabs, 0) as java.awt.Dimension).height
                } catch (_: Exception) {
                    toolbarHeight + tabs.borderThickness
                }
                val h = if (isNewUi) toolbarHeight else headerHeight
                return java.awt.Dimension(w, h)
            }
        }

        topPanel.add(tabsWrapper, BorderLayout.CENTER)
        topPanel.add(toolbar.component, BorderLayout.EAST)

        add(topPanel, BorderLayout.NORTH)
        add(JBScrollPane(textArea), BorderLayout.CENTER)

        tabs.addListener(object : TabsListener {
            override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
                if (!updatingText && newSelection != null) {
                    onTabSelected()
                }
            }
        })

        textArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onTextChanged()
            override fun removeUpdate(e: DocumentEvent) = onTextChanged()
            override fun changedUpdate(e: DocumentEvent) = onTextChanged()
        })

        object : DumbAwareAction() {
            override fun actionPerformed(e: AnActionEvent) = saveCurrentMemo()
        }.registerCustomShortcutSet(
            com.intellij.openapi.actionSystem.CustomShortcutSet(
                KeyStroke.getKeyStroke(KeyEvent.VK_S, java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx),
            ),
            textArea,
            this,
        )

        tabs.setPopupGroup(
            DefaultActionGroup().apply {
                add(object : DumbAwareAction("이름 변경") {
                    override fun actionPerformed(e: AnActionEvent) {
                        val tabInfo = tabs.selectedInfo ?: return
                        renameTab(tabInfo)
                    }
                })
                add(object : DumbAwareAction("삭제") {
                    override fun actionPerformed(e: AnActionEvent) {
                        val tabInfo = tabs.selectedInfo ?: return
                        deleteTab(tabInfo)
                    }
                })
            },
            ActionPlaces.EDITOR_TAB,
            false,
        )

        updateEmptyState()
    }

    fun setProblemId(problemId: String?) {
        cacheCurrentTabContent()
        currentProblemId = problemId
        loadMemos()
    }

    private fun loadMemos() {
        updatingText = true
        removeAllTabs()

        val problemId = currentProblemId
        if (problemId == null) {
            textArea.text = ""
            textArea.isEnabled = false
            updatingText = false
            updateEmptyState()
            return
        }

        val existingOrder = tabOrder[problemId]
        if (existingOrder != null) {
            for (memoName in existingOrder) {
                val dirty = dirtyFlags[problemId]?.contains(memoName) == true
                addTab(memoName, dirty)
            }
        } else {
            val memoNames = memoRepository.listMemos(problemId)
            if (memoNames.isEmpty()) {
                val autoName = memoRepository.nextAutoName(problemId)
                tabOrder[problemId] = mutableListOf(autoName)
                cache[problemId] = mutableMapOf(autoName to "")
                dirtyFlags[problemId] = mutableSetOf()
                addTab(autoName, false)
            } else {
                val order = mutableListOf<String>()
                val memoCache = mutableMapOf<String, String>()
                for (name in memoNames) {
                    order.add(name)
                    memoCache[name] = memoRepository.load(problemId, name)
                    addTab(name, false)
                }
                tabOrder[problemId] = order
                cache[problemId] = memoCache
                dirtyFlags[problemId] = mutableSetOf()
            }
        }

        textArea.isEnabled = true

        val allTabs = tabs.tabs
        if (allTabs.isNotEmpty()) {
            tabs.select(allTabs.first(), false)
            onTabSelected()
        }

        updatingText = false
        updateEmptyState()
    }

    private fun addTab(memoName: String, dirty: Boolean): TabInfo {
        val title = if (dirty) "$memoName *" else memoName
        val tabInfo = TabInfo(JPanel()).setText(title)
        tabInfo.setObject(memoName)
        val closeAction = DefaultActionGroup().apply {
            add(object : DumbAwareAction(
                "Close",
                "메모 삭제",
                AllIcons.Actions.CloseDarkGrey,
            ) {
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabledAndVisible = true
                }

                override fun actionPerformed(e: AnActionEvent) {
                    deleteTab(tabInfo)
                }
            })
        }
        tabInfo.setTabLabelActions(closeAction, ActionPlaces.EDITOR_TAB)
        tabs.addTab(tabInfo)
        return tabInfo
    }

    private fun removeAllTabs() {
        tabs.tabs.toList().forEach { tabs.removeTab(it) }
    }

    private fun onTabSelected() {
        val problemId = currentProblemId ?: return
        val selected = tabs.selectedInfo ?: return
        val memoName = selected.`object` as? String ?: return

        val cachedContent = cache[problemId]?.get(memoName)
        updatingText = true
        textArea.text = cachedContent ?: ""
        textArea.caretPosition = 0
        updatingText = false
    }

    private fun onTextChanged() {
        if (updatingText) return
        val problemId = currentProblemId ?: return
        val selected = tabs.selectedInfo ?: return
        val memoName = selected.`object` as? String ?: return

        cache.getOrPut(problemId) { mutableMapOf() }[memoName] = textArea.text

        val dirty = dirtyFlags.getOrPut(problemId) { mutableSetOf() }
        if (memoName !in dirty) {
            dirty.add(memoName)
            selected.setText("$memoName *")
        }
    }

    private fun cacheCurrentTabContent() {
        val problemId = currentProblemId ?: return
        val selected = tabs.selectedInfo ?: return
        val memoName = selected.`object` as? String ?: return

        cache.getOrPut(problemId) { mutableMapOf() }[memoName] = textArea.text
    }

    private fun saveCurrentMemo() {
        val problemId = currentProblemId ?: return
        val selected = tabs.selectedInfo ?: return
        val memoName = selected.`object` as? String ?: return

        val content = textArea.text
        memoRepository.save(problemId, memoName, content)
        cache.getOrPut(problemId) { mutableMapOf() }[memoName] = content
        dirtyFlags[problemId]?.remove(memoName)
        selected.setText(memoName)
    }

    private fun addNewMemo() {
        val problemId = currentProblemId ?: return
        val existing = tabOrder[problemId]?.toSet() ?: emptySet()
        var counter = 1
        while ("메모 $counter" in existing) counter++
        val newName = "메모 $counter"

        tabOrder.getOrPut(problemId) { mutableListOf() }.add(newName)
        cache.getOrPut(problemId) { mutableMapOf() }[newName] = ""
        val tabInfo = addTab(newName, false)
        tabs.select(tabInfo, false)
    }

    private fun renameTab(tabInfo: TabInfo) {
        val problemId = currentProblemId ?: return
        val oldName = tabInfo.`object` as? String ?: return

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

        val order = tabOrder[problemId] ?: return
        val orderIndex = order.indexOf(oldName)
        if (orderIndex >= 0) order[orderIndex] = newName

        val memoCache = cache[problemId] ?: return
        val content = memoCache.remove(oldName) ?: ""
        memoCache[newName] = content

        val dirty = dirtyFlags.getOrPut(problemId) { mutableSetOf() }
        val wasDirty = dirty.remove(oldName)

        memoRepository.rename(problemId, oldName, newName)

        tabInfo.setObject(newName)
        if (wasDirty) {
            dirty.add(newName)
            tabInfo.setText("$newName *")
        } else {
            tabInfo.setText(newName)
        }
    }

    private fun deleteTab(tabInfo: TabInfo) {
        val problemId = currentProblemId ?: return
        val memoName = tabInfo.`object` as? String ?: return

        val content = cache[problemId]?.get(memoName) ?: ""
        if (content.isNotEmpty()) {
            val confirm = JOptionPane.showConfirmDialog(
                this,
                "'$memoName' 메모를 삭제하시겠습니까?",
                "메모 삭제",
                JOptionPane.YES_NO_OPTION,
            )
            if (confirm != JOptionPane.YES_OPTION) return
        }

        val tabIndex = tabs.tabs.indexOf(tabInfo)
        tabOrder[problemId]?.removeAt(tabIndex)
        cache[problemId]?.remove(memoName)
        dirtyFlags[problemId]?.remove(memoName)

        memoRepository.delete(problemId, memoName)

        updatingText = true
        tabs.removeTab(tabInfo)
        updatingText = false

        if (tabs.tabCount == 0) {
            addNewMemo()
        } else {
            val allTabs = tabs.tabs
            val newIndex = minOf(tabIndex, allTabs.size - 1)
            tabs.select(allTabs[newIndex], false)
            onTabSelected()
        }
    }

    private fun updateEmptyState() {
        val hasProblem = currentProblemId != null
        textArea.isEnabled = hasProblem
        if (!hasProblem) {
            textArea.text = ""
        }
    }

    private inner class AddMemoAction : DumbAwareAction(
        "추가",
        "새 메모 추가",
        AllIcons.General.Add,
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            addNewMemo()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = currentProblemId != null
        }
    }

    private inner class SaveMemoAction : DumbAwareAction(
        "저장",
        "현재 메모 저장",
        AllIcons.Actions.MenuSaveall,
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            saveCurrentMemo()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = currentProblemId != null
        }
    }

    override fun dispose() {
    }
}
