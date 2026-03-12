package com.boj.intellij.ui.memo

import com.boj.intellij.common.MemoRepository
import com.boj.intellij.settings.BojSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import java.awt.BorderLayout
import javax.swing.BorderFactory
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.KeyStroke
import javax.swing.JOptionPane
import javax.swing.JPanel

class MemoPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val memoRepository: MemoRepository by lazy {
        val basePath = project.basePath ?: throw IllegalStateException("Project basePath is null")
        MemoRepository(File(basePath, ".boj"))
    }

    private val tabs: JBTabs = run {
        val instance = Class.forName("com.intellij.ui.tabs.impl.JBTabsImpl")
            .getConstructor(Project::class.java)
            .newInstance(project) as JBTabs
        try {
            val decorationCtor = com.intellij.ui.tabs.UiDecorator.UiDecoration::class.java
                .getConstructor(java.awt.Font::class.java, java.awt.Insets::class.java)
            val insets = com.intellij.util.ui.JBUI.insets(8, 8)
            val decoration = decorationCtor.newInstance(null, insets)
            instance.javaClass.getMethod("setUiDecorator", com.intellij.ui.tabs.UiDecorator::class.java)
                .invoke(instance, object : com.intellij.ui.tabs.UiDecorator {
                    override fun getDecoration() = decoration as com.intellij.ui.tabs.UiDecorator.UiDecoration
                })
        } catch (_: Exception) {
        }
        instance
    }
    private var currentEditor: Editor? = null
    private val editorPanel = JPanel(BorderLayout())
    // problemId -> (memoName -> Document)
    private val documents = mutableMapOf<String, MutableMap<String, Document>>()

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
                    val m = tabs.javaClass.getDeclaredMethod("computeHeaderPreferredSize", Int::class.java)
                    m.isAccessible = true
                    (m.invoke(tabs, 0) as java.awt.Dimension).height
                } catch (_: Exception) {
                    val bt = try {
                        tabs.javaClass.getMethod("getBorderThickness").invoke(tabs) as Int
                    } catch (_: Exception) { 0 }
                    toolbarHeight + bt
                }
                val h = if (isNewUi) toolbarHeight else headerHeight
                return java.awt.Dimension(w, h)
            }
        }

        topPanel.add(tabsWrapper, BorderLayout.CENTER)
        topPanel.add(toolbar.component, BorderLayout.EAST)

        add(topPanel, BorderLayout.NORTH)
        add(editorPanel, BorderLayout.CENTER)

        tabs.addListener(object : TabsListener {
            override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
                if (!updatingText && newSelection != null) {
                    onTabSelected()
                }
            }
        })

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
            updatingText = false
            updateEmptyState()
            return
        }

        val existingOrder = tabOrder[problemId]
        if (existingOrder != null) {
            for (memoName in existingOrder) {
                val dirty = dirtyFlags[problemId]?.contains(memoName) == true
                val content = cache[problemId]?.get(memoName) ?: ""
                getOrCreateDocument(problemId, memoName, content)
                addTab(memoName, dirty)
            }
        } else {
            val memoNames = memoRepository.listMemos(problemId)
            if (memoNames.isEmpty()) {
                val autoName = memoRepository.nextAutoName(problemId)
                tabOrder[problemId] = mutableListOf(autoName)
                cache[problemId] = mutableMapOf(autoName to "")
                dirtyFlags[problemId] = mutableSetOf()
                getOrCreateDocument(problemId, autoName, "")
                addTab(autoName, false)
            } else {
                val order = mutableListOf<String>()
                val memoCache = mutableMapOf<String, String>()
                for (name in memoNames) {
                    order.add(name)
                    val content = memoRepository.load(problemId, name)
                    memoCache[name] = content
                    getOrCreateDocument(problemId, name, content)
                    addTab(name, false)
                }
                tabOrder[problemId] = order
                cache[problemId] = memoCache
                dirtyFlags[problemId] = mutableSetOf()
            }
        }

        val allTabs = tabs.tabs
        updatingText = false
        if (allTabs.isNotEmpty()) {
            tabs.select(allTabs.first(), false)
            onTabSelected()
        }

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

        val content = cache[problemId]?.get(memoName) ?: ""
        val document = getOrCreateDocument(problemId, memoName, content)
        swapEditor(document)
    }

    private fun onTextChanged() {
        if (updatingText) return
        val problemId = currentProblemId ?: return
        val selected = tabs.selectedInfo ?: return
        val memoName = selected.`object` as? String ?: return

        val text = currentEditor?.document?.text ?: return
        cache.getOrPut(problemId) { mutableMapOf() }[memoName] = text

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

        val text = currentEditor?.document?.text ?: return
        cache.getOrPut(problemId) { mutableMapOf() }[memoName] = text
    }

    private fun saveCurrentMemo() {
        val problemId = currentProblemId ?: return
        val selected = tabs.selectedInfo ?: return
        val memoName = selected.`object` as? String ?: return

        val content = currentEditor?.document?.text ?: return
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
        getOrCreateDocument(problemId, newName, "")
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

        val problemDocs = documents[problemId]
        if (problemDocs != null) {
            val doc = problemDocs.remove(oldName)
            if (doc != null) problemDocs[newName] = doc
        }

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
        val diskContent = memoRepository.load(problemId, memoName)
        if (content.isNotEmpty() || diskContent.isNotEmpty()) {
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
        documents[problemId]?.remove(memoName)
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

    private fun createEditor(document: Document): Editor {
        val editor = EditorFactory.getInstance().createEditor(document, project)
        editor.settings.apply {
            isLineNumbersShown = false
            isFoldingOutlineShown = false
            isLineMarkerAreaShown = false
            additionalLinesCount = 0
            additionalColumnsCount = 0
            isRightMarginShown = false
            isCaretRowShown = false
            isUseSoftWraps = false
        }
        val ideFontSize = editor.colorsScheme.editorFontSize
        val fontOffset = BojSettings.getInstance().state.memoFontSize
        if (fontOffset != 0) {
            editor.colorsScheme.editorFontSize = (ideFontSize + fontOffset).coerceIn(8, 40)
        }
        editor.contentComponent.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        editor.contentComponent.addMouseWheelListener { e ->
            if (e.isControlDown) {
                e.consume()
                val settings = BojSettings.getInstance().state
                val delta = if (e.wheelRotation < 0) 4 else -4
                val newOffset = settings.memoFontSize + delta
                val newSize = (ideFontSize + newOffset).coerceIn(8, 40)
                val clampedOffset = (newSize - ideFontSize).coerceAtLeast(0)
                if (clampedOffset != settings.memoFontSize) {
                    editor.colorsScheme.editorFontSize = newSize
                    settings.memoFontSize = clampedOffset
                }
            }
        }
        return editor
    }

    private fun getOrCreateDocument(problemId: String, memoName: String, content: String): Document {
        val problemDocs = documents.getOrPut(problemId) { mutableMapOf() }
        return problemDocs.getOrPut(memoName) {
            val doc = EditorFactory.getInstance().createDocument(content)
            doc.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    if (!updatingText) onTextChanged()
                }
            })
            doc
        }
    }

    private fun swapEditor(document: Document) {
        currentEditor?.let { EditorFactory.getInstance().releaseEditor(it) }
        val editor = createEditor(document)
        currentEditor = editor
        editorPanel.removeAll()
        editorPanel.add(editor.component, BorderLayout.CENTER)
        editorPanel.revalidate()
        editorPanel.repaint()

        // Ctrl+S 단축키를 새 에디터에 등록
        object : DumbAwareAction() {
            override fun actionPerformed(e: AnActionEvent) = saveCurrentMemo()
        }.registerCustomShortcutSet(
            com.intellij.openapi.actionSystem.CustomShortcutSet(
                KeyStroke.getKeyStroke(KeyEvent.VK_S, java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx),
            ),
            editor.component,
            this,
        )
    }

    private fun releaseCurrentEditor() {
        currentEditor?.let { EditorFactory.getInstance().releaseEditor(it) }
        currentEditor = null
        editorPanel.removeAll()
        editorPanel.revalidate()
        editorPanel.repaint()
    }

    private fun updateEmptyState() {
        if (currentProblemId == null) {
            releaseCurrentEditor()
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
        currentEditor?.let { EditorFactory.getInstance().releaseEditor(it) }
        currentEditor = null
        documents.clear()
    }
}
