# RunBar 복사 버튼 및 레이아웃 변경 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** RunBar에 백준 제출용 코드 복사 버튼을 추가하고, 상태 라벨 위치를 중지 버튼 오른쪽으로 이동

**Architecture:** RunBarPanel의 레이아웃을 변경하여 statusLabel을 leftPanel(WEST)에 포함시키고, EAST에 별도 ActionToolbar로 CopyForSubmitAction을 배치한다. 복사 시 Java 파일은 클래스명을 Main으로 자동 변환한다.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (AnAction, ActionToolbar, AllIcons), Java AWT/Swing

---

### Task 1: 클래스명 변환 유틸리티 함수 테스트 작성

**Files:**
- Create: `src/test/kotlin/com/boj/intellij/ui/CopyForSubmitUtilTest.kt`

**Step 1: 클래스명 변환 유틸리티 테스트 작성**

```kotlin
package com.boj.intellij.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class CopyForSubmitUtilTest {

    @Test
    fun `replaces public class name with Main for java`() {
        val code = "public class Boj1000 {\n    public static void main(String[] args) {\n    }\n}"
        val result = CopyForSubmitUtil.transformForSubmit(code, "java")
        assertEquals("public class Main {\n    public static void main(String[] args) {\n    }\n}", result)
    }

    @Test
    fun `does not change non-java code`() {
        val code = "fun main() {\n    println(\"hello\")\n}"
        val result = CopyForSubmitUtil.transformForSubmit(code, "kt")
        assertEquals(code, result)
    }

    @Test
    fun `does not change python code`() {
        val code = "print('hello')"
        val result = CopyForSubmitUtil.transformForSubmit(code, "py")
        assertEquals(code, result)
    }

    @Test
    fun `handles java class without public modifier`() {
        val code = "class Solution {\n}"
        val result = CopyForSubmitUtil.transformForSubmit(code, "java")
        assertEquals("class Main {\n}", result)
    }

    @Test
    fun `handles java class with extra spaces`() {
        val code = "public  class  MyClass {"
        val result = CopyForSubmitUtil.transformForSubmit(code, "java")
        assertEquals("public  class  Main {", result)
    }

    @Test
    fun `returns original code when extension is null`() {
        val code = "some code"
        val result = CopyForSubmitUtil.transformForSubmit(code, null)
        assertEquals(code, result)
    }
}
```

**Step 2: 테스트 실행하여 실패 확인**

Run: `./gradlew test --tests "com.boj.intellij.ui.CopyForSubmitUtilTest" --no-daemon`
Expected: FAIL - CopyForSubmitUtil 클래스가 존재하지 않아 컴파일 오류

---

### Task 2: 클래스명 변환 유틸리티 구현

**Files:**
- Create: `src/main/kotlin/com/boj/intellij/ui/CopyForSubmitUtil.kt`

**Step 1: CopyForSubmitUtil 구현**

```kotlin
package com.boj.intellij.ui

object CopyForSubmitUtil {

    private val JAVA_CLASS_PATTERN = Regex("""((?:public\s+)?class\s+)\w+""")

    fun transformForSubmit(code: String, extension: String?): String {
        if (extension?.lowercase() != "java") return code
        return JAVA_CLASS_PATTERN.replaceFirst(code) { match ->
            "${match.groupValues[1]}Main"
        }
    }
}
```

**Step 2: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.boj.intellij.ui.CopyForSubmitUtilTest" --no-daemon`
Expected: PASS - 모든 테스트 통과

**Step 3: 커밋**

```bash
git add src/main/kotlin/com/boj/intellij/ui/CopyForSubmitUtil.kt src/test/kotlin/com/boj/intellij/ui/CopyForSubmitUtilTest.kt
git commit -m "feat: 백준 제출용 클래스명 변환 유틸리티 추가"
```

---

### Task 3: RunBarPanel 레이아웃 변경 및 복사 버튼 추가

**Files:**
- Modify: `src/main/kotlin/com/boj/intellij/ui/RunBarPanel.kt`

**Step 1: RunBarPanel 생성자에 onCopyForSubmit 콜백 추가, statusLabel을 leftPanel로 이동, EAST에 복사 ActionToolbar 배치**

`RunBarPanel.kt` 변경 내용:

1. 생성자에 `onCopyForSubmit` 콜백 파라미터 추가:
```kotlin
class RunBarPanel(
    private val onRunAll: (command: String) -> Unit,
    private val onStop: () -> Unit = {},
    private val onCopyForSubmit: () -> Unit = {},
) : JPanel(BorderLayout()) {
```

2. import 추가:
```kotlin
import java.util.Timer
import kotlin.concurrent.schedule
```

3. init 블록에서 statusLabel을 leftPanel에 추가하고, EAST에 복사 전용 ActionToolbar 배치:
```kotlin
init {
    border = BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, resolveTopBorderColor()),
        BorderFactory.createEmptyBorder(4, 8, 4, 8),
    )

    val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
    leftPanel.isOpaque = false
    leftPanel.add(commandComboBox)

    runCatching {
        val actionGroup = DefaultActionGroup().apply {
            add(RunAllAction())
            add(StopAction())
        }
        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar("BojRunBar", actionGroup, true)
        actionToolbar.targetComponent = this
        toolbar = actionToolbar
        leftPanel.add(actionToolbar.component)
    }

    leftPanel.add(statusLabel)

    add(leftPanel, BorderLayout.WEST)

    runCatching {
        val copyGroup = DefaultActionGroup().apply {
            add(CopyForSubmitAction())
        }
        val copyToolbar = ActionManager.getInstance()
            .createActionToolbar("BojRunBarCopy", copyGroup, true)
        copyToolbar.targetComponent = this
        add(copyToolbar.component, BorderLayout.EAST)
    }
}
```

4. CopyForSubmitAction inner class 추가 (StopAction 뒤에):
```kotlin
private inner class CopyForSubmitAction : AnAction(
    "제출용 복사",
    "백준 제출용 코드 복사 (Java: 클래스명→Main)",
    AllIcons.Actions.Copy,
) {
    override fun actionPerformed(e: AnActionEvent) {
        onCopyForSubmit()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}
```

5. 복사 성공 피드백을 위한 메서드 추가:
```kotlin
fun showCopyFeedback() {
    val previousText = statusLabel.text
    statusLabel.text = "복사됨"
    Timer().schedule(2000) {
        javax.swing.SwingUtilities.invokeLater {
            if (statusLabel.text == "복사됨") {
                statusLabel.text = previousText
            }
        }
    }
}
```

**Step 2: 기존 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "com.boj.intellij.ui.RunBarPanelTest" --no-daemon`
Expected: PASS - 기존 테스트 모두 통과 (onCopyForSubmit에 기본값이 있으므로)

**Step 3: 커밋**

```bash
git add src/main/kotlin/com/boj/intellij/ui/RunBarPanel.kt
git commit -m "feat: RunBar 레이아웃 변경 및 복사 버튼 추가"
```

---

### Task 4: BojToolWindowPanel에서 복사 콜백 연결

**Files:**
- Modify: `src/main/kotlin/com/boj/intellij/ui/BojToolWindowPanel.kt:87-90`

**Step 1: RunBarPanel 생성 부분에 onCopyForSubmit 콜백 추가**

기존 코드 (87-90줄):
```kotlin
private val runBarPanel = RunBarPanel(
    onRunAll = { command -> runInBackground { handleRunAll(command) } },
    onStop = { handleStop() },
)
```

변경 후:
```kotlin
private val runBarPanel = RunBarPanel(
    onRunAll = { command -> runInBackground { handleRunAll(command) } },
    onStop = { handleStop() },
    onCopyForSubmit = { handleCopyForSubmit() },
)
```

**Step 2: handleCopyForSubmit 메서드 추가**

`BojToolWindowPanel` 클래스에 `handleCopyForSubmit()` 메서드 추가 (saveActiveEditorDocument 근처에):

```kotlin
private fun handleCopyForSubmit() {
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
    val document = editor.document
    val code = document.text
    val virtualFile = FileDocumentManager.getInstance().getFile(document)
    val extension = virtualFile?.extension

    val transformed = CopyForSubmitUtil.transformForSubmit(code, extension)

    val selection = java.awt.datatransfer.StringSelection(transformed)
    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)

    runBarPanel.showCopyFeedback()
}
```

**Step 3: 빌드 확인**

Run: `./gradlew build --no-daemon`
Expected: BUILD SUCCESSFUL

**Step 4: 커밋**

```bash
git add src/main/kotlin/com/boj/intellij/ui/BojToolWindowPanel.kt
git commit -m "feat: 백준 제출용 코드 복사 기능 연결"
```
