# GitHub Upload Customization Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** GitHub 업로드 시 티어/수정자 등 확장 변수를 지원하고, 폴더 모드에서 README.md를 단일 커밋으로 생성한다.

**Architecture:** TemplateEngine에 수정자 문법 추가, 상태 페이지 JS에서 티어/제출일자 추출, solved.ac API로 알고리즘 분류 조회, Git Data API로 다중 파일 단일 커밋 구현.

**Tech Stack:** Kotlin, IntelliJ Platform SDK, GitHub REST API (Git Data API), solved.ac API, JBCef (JavaScript 주입)

---

### Task 1: TierMapper — 티어 변환 유틸리티

**Files:**
- Create: `src/main/kotlin/app/meot/boj-helper/github/TierMapper.kt`
- Test: `src/test/kotlin/com/boj/intellij/github/TierMapperTest.kt`

**Step 1: Write the failing test**

```kotlin
// src/test/kotlin/com/boj/intellij/github/TierMapperTest.kt
package com.boj.intellij.github

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TierMapperTest {

    @Test
    fun `SVG 0 is Unrated`() {
        assertEquals("Unrated", TierMapper.tierName(0))
        assertEquals(0, TierMapper.tierNum(0))
    }

    @Test
    fun `SVG 1 is Bronze 5`() {
        assertEquals("Bronze", TierMapper.tierName(1))
        assertEquals(5, TierMapper.tierNum(1))
    }

    @Test
    fun `SVG 5 is Bronze 1`() {
        assertEquals("Bronze", TierMapper.tierName(5))
        assertEquals(1, TierMapper.tierNum(5))
    }

    @Test
    fun `SVG 6 is Silver 5`() {
        assertEquals("Silver", TierMapper.tierName(6))
        assertEquals(5, TierMapper.tierNum(6))
    }

    @Test
    fun `SVG 11 is Gold 5`() {
        assertEquals("Gold", TierMapper.tierName(11))
        assertEquals(5, TierMapper.tierNum(11))
    }

    @Test
    fun `SVG 15 is Gold 1`() {
        assertEquals("Gold", TierMapper.tierName(15))
        assertEquals(1, TierMapper.tierNum(15))
    }

    @Test
    fun `SVG 16 is Platinum 5`() {
        assertEquals("Platinum", TierMapper.tierName(16))
        assertEquals(5, TierMapper.tierNum(16))
    }

    @Test
    fun `SVG 21 is Diamond 5`() {
        assertEquals("Diamond", TierMapper.tierName(21))
        assertEquals(5, TierMapper.tierNum(21))
    }

    @Test
    fun `SVG 26 is Ruby 5`() {
        assertEquals("Ruby", TierMapper.tierName(26))
        assertEquals(5, TierMapper.tierNum(26))
    }

    @Test
    fun `SVG 30 is Ruby 1`() {
        assertEquals("Ruby", TierMapper.tierName(30))
        assertEquals(1, TierMapper.tierNum(30))
    }

    @Test
    fun `invalid SVG returns null`() {
        assertNull(TierMapper.tierName(-1))
        assertNull(TierMapper.tierName(31))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.boj.intellij.github.TierMapperTest" --info 2>&1 | tail -20`
Expected: FAIL — class not found

**Step 3: Write minimal implementation**

```kotlin
// src/main/kotlin/app/meot/boj-helper/github/TierMapper.kt
package com.boj.intellij.github

object TierMapper {

    private val TIER_NAMES = arrayOf("Bronze", "Silver", "Gold", "Platinum", "Diamond", "Ruby")

    fun tierName(svgLevel: Int): String? {
        if (svgLevel == 0) return "Unrated"
        if (svgLevel !in 1..30) return null
        val groupIndex = (svgLevel - 1) / 5
        return TIER_NAMES[groupIndex]
    }

    fun tierNum(svgLevel: Int): Int {
        if (svgLevel == 0) return 0
        if (svgLevel !in 1..30) return 0
        return 5 - (svgLevel - 1) % 5
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.boj.intellij.github.TierMapperTest" --info 2>&1 | tail -20`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/app/meot/boj-helper/github/TierMapper.kt src/test/kotlin/com/boj/intellij/github/TierMapperTest.kt
git commit -m "feat(github): TierMapper 추가 — SVG 번호를 티어명/서브티어로 변환"
```

---

### Task 2: TemplateEngine 수정자 지원 (:u, :l, :c)

**Files:**
- Modify: `src/main/kotlin/app/meot/boj-helper/github/TemplateEngine.kt`
- Test: `src/test/kotlin/com/boj/intellij/github/TemplateEngineTest.kt` (신규)

**Step 1: Write the failing test**

```kotlin
// src/test/kotlin/com/boj/intellij/github/TemplateEngineTest.kt
package com.boj.intellij.github

import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateEngineTest {

    @Test
    fun `render without modifier returns value as-is`() {
        val vars = mapOf("tier" to "Gold")
        assertEquals("Gold", TemplateEngine.render("{tier}", vars))
    }

    @Test
    fun `render with u modifier returns uppercase`() {
        val vars = mapOf("tier" to "Gold")
        assertEquals("GOLD", TemplateEngine.render("{tier:u}", vars))
    }

    @Test
    fun `render with l modifier returns lowercase`() {
        val vars = mapOf("tier" to "Gold")
        assertEquals("gold", TemplateEngine.render("{tier:l}", vars))
    }

    @Test
    fun `render with c modifier returns capitalized`() {
        val vars = mapOf("tier" to "GOLD")
        assertEquals("Gold", TemplateEngine.render("{tier:c}", vars))
    }

    @Test
    fun `render with unknown variable keeps placeholder`() {
        val vars = mapOf("tier" to "Gold")
        assertEquals("{unknown}", TemplateEngine.render("{unknown}", vars))
    }

    @Test
    fun `render with unknown modifier keeps placeholder`() {
        val vars = mapOf("tier" to "Gold")
        assertEquals("{tier:x}", TemplateEngine.render("{tier:x}", vars))
    }

    @Test
    fun `render complex template with mixed modifiers`() {
        val vars = mapOf(
            "tier" to "Gold",
            "tierNum" to "5",
            "problemId" to "1000",
            "ext" to "java",
        )
        assertEquals(
            "GOLD 5/1000.java",
            TemplateEngine.render("{tier:u} {tierNum}/{problemId}.{ext}", vars),
        )
    }

    @Test
    fun `render backward compatible without modifiers`() {
        val vars = mapOf(
            "language" to "Java 11",
            "problemId" to "1000",
            "ext" to "java",
        )
        assertEquals(
            "Java 11/1000.java",
            TemplateEngine.render("{language}/{problemId}.{ext}", vars),
        )
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.boj.intellij.github.TemplateEngineTest" --info 2>&1 | tail -20`
Expected: FAIL — modifier tests fail because `:u` etc. are not recognized

**Step 3: Modify TemplateEngine**

기존 TemplateEngine.kt (줄 7-14)을 수정한다:

```kotlin
// src/main/kotlin/app/meot/boj-helper/github/TemplateEngine.kt
package com.boj.intellij.github

import com.boj.intellij.submit.SubmitResult

object TemplateEngine {

    private val VARIABLE_PATTERN = Regex("""\{(\w+)(?::([ulc]))?\}""")

    fun render(template: String, variables: Map<String, String>): String {
        return VARIABLE_PATTERN.replace(template) { match ->
            val key = match.groupValues[1]
            val modifier = match.groupValues[2]
            val value = variables[key] ?: return@replace match.value
            applyModifier(value, modifier)
        }
    }

    private fun applyModifier(value: String, modifier: String): String {
        return when (modifier) {
            "u" -> value.uppercase()
            "l" -> value.lowercase()
            "c" -> value.lowercase().replaceFirstChar { it.uppercase() }
            "" -> value
            else -> value  // 알 수 없는 수정자는 원본 반환 — 하지만 정규식에서 [ulc]만 매칭하므로 도달하지 않음
        }
    }

    fun buildVariables(
        submitResult: SubmitResult,
        title: String,
        extension: String,
    ): Map<String, String> {
        return mapOf(
            "problemId" to submitResult.problemId,
            "title" to title,
            "language" to submitResult.language,
            "ext" to extension,
            "memory" to submitResult.memory,
            "time" to submitResult.time,
        )
    }
}
```

**주의:** 정규식이 `[ulc]`만 매칭하므로 `{tier:x}` 같은 알 수 없는 수정자는 매칭 자체가 안 되어 원본이 유지된다. 이를 반영하여 테스트의 `render with unknown modifier keeps placeholder` 케이스가 통과하는지 확인한다.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.boj.intellij.github.TemplateEngineTest" --info 2>&1 | tail -20`
Expected: PASS

또한 기존 테스트도 깨지지 않는지 확인:

Run: `./gradlew test --tests "com.boj.intellij.github.GitHubUploadServiceTest" --info 2>&1 | tail -20`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/app/meot/boj-helper/github/TemplateEngine.kt src/test/kotlin/com/boj/intellij/github/TemplateEngineTest.kt
git commit -m "feat(github): TemplateEngine에 수정자 지원 추가 (:u, :l, :c)"
```

---

### Task 3: TemplateEngine.buildVariables에 tier/tierNum 추가

**Files:**
- Modify: `src/main/kotlin/app/meot/boj-helper/github/TemplateEngine.kt` (buildVariables)
- Modify: `src/test/kotlin/com/boj/intellij/github/TemplateEngineTest.kt`
- Modify: `src/test/kotlin/com/boj/intellij/github/GitHubUploadServiceTest.kt` (호환성)

**Step 1: Write the failing test**

`TemplateEngineTest.kt`에 추가:

```kotlin
@Test
fun `buildVariables includes tier and tierNum from svgLevel`() {
    val result = SubmitResult(
        submissionId = "1", problemId = "1000", result = "맞았습니다!!",
        memory = "14512", time = "132", language = "Java 11", codeLength = "512",
    )
    val vars = TemplateEngine.buildVariables(result, "A+B", "java", tierLevel = 11)
    assertEquals("Gold", vars["tier"])
    assertEquals("5", vars["tierNum"])
}

@Test
fun `buildVariables without tierLevel omits tier variables`() {
    val result = SubmitResult(
        submissionId = "1", problemId = "1000", result = "맞았습니다!!",
        memory = "14512", time = "132", language = "Java 11", codeLength = "512",
    )
    val vars = TemplateEngine.buildVariables(result, "A+B", "java")
    assertEquals("", vars["tier"])
    assertEquals("", vars["tierNum"])
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.boj.intellij.github.TemplateEngineTest" --info 2>&1 | tail -20`
Expected: FAIL — buildVariables doesn't accept tierLevel parameter

**Step 3: Modify buildVariables**

`TemplateEngine.kt`의 `buildVariables` 메서드를 수정:

```kotlin
fun buildVariables(
    submitResult: SubmitResult,
    title: String,
    extension: String,
    tierLevel: Int = 0,
): Map<String, String> {
    val tierName = TierMapper.tierName(tierLevel) ?: ""
    val tierNum = if (tierLevel > 0) TierMapper.tierNum(tierLevel).toString() else ""
    return mapOf(
        "problemId" to submitResult.problemId,
        "title" to title,
        "language" to submitResult.language,
        "ext" to extension,
        "memory" to submitResult.memory,
        "time" to submitResult.time,
        "tier" to tierName,
        "tierNum" to tierNum,
    )
}
```

**Step 4: Run all related tests**

Run: `./gradlew test --tests "com.boj.intellij.github.*" --info 2>&1 | tail -20`
Expected: PASS — 기존 테스트는 기본값 `tierLevel = 0`으로 호환됨

**Step 5: Commit**

```bash
git add src/main/kotlin/app/meot/boj-helper/github/TemplateEngine.kt src/test/kotlin/com/boj/intellij/github/TemplateEngineTest.kt
git commit -m "feat(github): buildVariables에 tier/tierNum 변수 추가"
```

---

### Task 4: BojSubmitPanel — 상태 페이지에서 tierLevel, submittedAt 추출

**Files:**
- Modify: `src/main/kotlin/app/meot/boj-helper/submit/BojSubmitPanel.kt`

**Step 1: injectGitHubUploadButtons() JS 수정**

`BojSubmitPanel.kt`의 `injectGitHubUploadButtons()` 메서드 (줄 512-627)에서 JS의 `__bojUpload` 함수를 수정한다.

기존 `__bojUpload` 함수 (줄 560-588)에서 tierLevel과 submittedAt을 추출하도록 변경:

```javascript
window.__bojUpload = function(submissionId, cells) {
    var btn = document.querySelector('.boj-gh-btn[data-sid="' + submissionId + '"]');
    if (btn) { btn.textContent = '업로드 중...'; btn.disabled = true; }

    var problemLink = cells[2].querySelector('a');
    var problemId = problemLink ? problemLink.textContent.trim() : '';
    var language = cells[6].textContent.trim();

    // 티어 SVG 추출
    var tierImg = cells[2].querySelector('img[src*="tier/"]');
    var tierLevel = 0;
    if (tierImg) {
        var tierMatch = tierImg.src.match(/tier\/(\d+)\.svg/);
        if (tierMatch) tierLevel = parseInt(tierMatch[1], 10);
    }

    // 제출 일자 추출
    var submittedAt = '';
    var timeCell = cells[8];
    if (timeCell) {
        var timeLink = timeCell.querySelector('a[title]');
        if (timeLink) submittedAt = timeLink.getAttribute('title');
        else submittedAt = timeCell.textContent.trim();
    }

    fetch('/source/' + submissionId)
        .then(function(r) { return r.text(); })
        .then(function(html) {
            var parser = new DOMParser();
            var doc = parser.parseFromString(html, 'text/html');
            var textarea = doc.querySelector('textarea.codemirror-textarea')
                || doc.querySelector('textarea.no-mathjax')
                || doc.querySelector('textarea');
            var code = textarea ? (textarea.value || textarea.textContent) : '';

            var json = JSON.stringify({
                submissionId: submissionId,
                problemId: problemId,
                sourceCode: code,
                language: language,
                tierLevel: tierLevel,
                submittedAt: submittedAt
            });
            $callbackJs
        })
        .catch(function(e) {
            __bojMarkFailed(submissionId);
        });
};
```

**주의:** BOJ 상태 테이블 셀 인덱스 확인 필요. cells[8]이 제출 시각 셀인지 실제 BOJ 페이지에서 확인. 확인 후 인덱스 조정 가능. 시각 셀에 `<a title="2026-03-09 12:34:56">` 형태가 있을 수 있음.

**Step 2: handleUploadRequest() 수정**

`BojSubmitPanel.kt`의 `handleUploadRequest()` (줄 455-500)를 수정하여 새 필드를 파싱하고 전달:

```kotlin
private fun handleUploadRequest(jsonString: String) {
    val submissionId = extractJsonValue(jsonString, "submissionId") ?: return
    val problemId = extractJsonValue(jsonString, "problemId") ?: return
    val sourceCode = extractJsonValue(jsonString, "sourceCode") ?: return
    val language = extractJsonValue(jsonString, "language") ?: return
    val tierLevel = extractJsonInt(jsonString, "tierLevel") ?: 0
    val submittedAt = extractJsonValue(jsonString, "submittedAt") ?: ""

    val settings = com.boj.intellij.settings.BojSettings.getInstance()
    if (!settings.state.githubEnabled) return
    if (settings.isSubmissionUploaded(submissionId)) return

    val extension = LanguageMapper.toExtension(language) ?: "txt"
    val title = findProblemTitle() ?: "Problem $problemId"

    com.boj.intellij.github.GitHubUploadService.upload(
        project = project,
        submitResult = SubmitResult(
            submissionId = submissionId,
            problemId = problemId,
            result = "맞았습니다!!",
            memory = "",
            time = "",
            language = language,
            codeLength = "",
        ),
        sourceCode = sourceCode,
        title = title,
        extension = extension,
        tierLevel = tierLevel,
        submittedAt = submittedAt,
        problemData = findCurrentParsedProblem(),
        onSuccess = {
            settings.markSubmissionUploaded(submissionId)
            ApplicationManager.getApplication().invokeLater {
                browser?.cefBrowser?.executeJavaScript(
                    "if(window.__bojMarkUploaded) __bojMarkUploaded('$submissionId');",
                    browser.cefBrowser.url, 0
                )
            }
        },
        onFailure = {
            ApplicationManager.getApplication().invokeLater {
                browser?.cefBrowser?.executeJavaScript(
                    "if(window.__bojMarkFailed) __bojMarkFailed('$submissionId');",
                    browser.cefBrowser.url, 0
                )
            }
        },
    )
}
```

**주의:** `extractJsonInt`는 BojToolWindowPanel.kt에 이미 companion object에 정의되어 있지만, BojSubmitPanel에는 없다. BojSubmitPanel에도 추가하거나, 공통 유틸로 추출한다:

```kotlin
private fun extractJsonInt(json: String, key: String): Int? {
    val pattern = """"${Regex.escape(key)}"\s*:\s*(\d+)""".toRegex()
    return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
}
```

**Step 3: findCurrentParsedProblem() 헬퍼 추가**

BojSubmitPanel에서 BojToolWindowPanel의 현재 ParsedProblem에 접근하는 메서드. BojToolWindowPanel에 `getCurrentParsedProblem()` 메서드가 없으므로 추가 필요:

```kotlin
private fun findCurrentParsedProblem(): ParsedProblem? {
    return runCatching {
        val toolWindow = com.intellij.openapi.wm.ToolWindowManager
            .getInstance(project)
            .getToolWindow("BOJ Helper") ?: return null
        val content = toolWindow.contentManager.getContent(0) ?: return null
        val panel = content.component as? com.boj.intellij.ui.BojToolWindowPanel ?: return null
        panel.getCurrentParsedProblem()
    }.getOrNull()
}
```

`BojToolWindowPanel.kt`에 추가 (줄 80 근처, getCurrentProblemTitle 옆):

```kotlin
fun getCurrentParsedProblem(): ParsedProblem? = currentParsedProblem
```

**Step 4: Commit**

```bash
git add src/main/kotlin/app/meot/boj-helper/submit/BojSubmitPanel.kt src/main/kotlin/app/meot/boj-helper/ui/BojToolWindowPanel.kt
git commit -m "feat(github): 상태 페이지에서 tierLevel, submittedAt 추출 및 전달"
```

---

### Task 5: GitHubUploadService 확장 — tierLevel, submittedAt, problemData 전달

**Files:**
- Modify: `src/main/kotlin/app/meot/boj-helper/github/GitHubUploadService.kt`

**Step 1: upload() 및 doUpload() 시그니처 확장**

```kotlin
fun upload(
    project: Project,
    submitResult: SubmitResult,
    sourceCode: String,
    title: String,
    extension: String,
    tierLevel: Int = 0,
    submittedAt: String = "",
    problemData: ParsedProblem? = null,
    onSuccess: (() -> Unit)? = null,
    onFailure: (() -> Unit)? = null,
) {
    ApplicationManager.getApplication().executeOnPooledThread {
        try {
            doUpload(project, submitResult, sourceCode, title, extension, tierLevel, submittedAt, problemData)
            onSuccess?.invoke()
        } catch (e: Exception) {
            notifyError(project, e.message ?: "알 수 없는 오류가 발생했습니다")
            onFailure?.invoke()
        }
    }
}

private fun doUpload(
    project: Project,
    submitResult: SubmitResult,
    sourceCode: String,
    title: String,
    extension: String,
    tierLevel: Int,
    submittedAt: String,
    problemData: ParsedProblem?,
) {
    // ... 기존 토큰/리포지토리 검증 동일 ...

    val settings = BojSettings.getInstance()
    val branch = settings.state.githubBranch
    val variables = TemplateEngine.buildVariables(submitResult, title, extension, tierLevel)
    val path = TemplateEngine.render(settings.state.githubPathTemplate, variables)
    val commitMessage = TemplateEngine.render(settings.state.githubCommitTemplate, variables)

    val client = GitHubApiClient(token)

    if (settings.state.githubReadmeEnabled && problemData != null) {
        // README 포함 — Git Data API로 단일 커밋
        val readmeContent = ReadmeGenerator.generate(
            problemId = submitResult.problemId,
            title = title,
            tierLevel = tierLevel,
            problemData = problemData,
            submitResult = submitResult,
            submittedAt = submittedAt,
        )
        val dir = path.substringBeforeLast('/', "")
        val readmePath = if (dir.isNotEmpty()) "$dir/README.md" else "README.md"

        val result = client.uploadMultipleFiles(
            repo = repo,
            branch = branch,
            commitMessage = commitMessage,
            files = mapOf(
                path to sourceCode,
                readmePath to readmeContent,
            ),
        )
        if (result.success) {
            notifySuccess(project, submitResult.problemId, title, null)
        }
    } else {
        // 기존 단일 파일 업로드
        val existingSha = try {
            client.getFileSha(repo, path, branch)
        } catch (e: GitHubApiClient.GitHubApiException) {
            null
        }
        val result = client.uploadFile(
            repo = repo, path = path, content = sourceCode,
            commitMessage = commitMessage, branch = branch, existingSha = existingSha,
        )
        if (result.success) {
            notifySuccess(project, submitResult.problemId, title, result.htmlUrl)
        }
    }
}
```

**Step 2: import 추가**

```kotlin
import com.boj.intellij.parse.ParsedProblem
```

**Step 3: Commit**

```bash
git add src/main/kotlin/app/meot/boj-helper/github/GitHubUploadService.kt
git commit -m "feat(github): GitHubUploadService에 tier/submittedAt/problemData 전달 지원"
```

---

### Task 6: BojSettings — githubReadmeEnabled 추가

**Files:**
- Modify: `src/main/kotlin/app/meot/boj-helper/settings/BojSettings.kt`
- Modify: `src/test/kotlin/com/boj/intellij/github/GitHubSettingsFieldsTest.kt`

**Step 1: Write the failing test**

`GitHubSettingsFieldsTest.kt`에 추가:

```kotlin
@Test
fun `State has githubReadmeEnabled default false`() {
    val state = BojSettings.State()
    assertEquals(false, state.githubReadmeEnabled)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.boj.intellij.github.GitHubSettingsFieldsTest" --info 2>&1 | tail -20`
Expected: FAIL — property doesn't exist

**Step 3: Add field**

`BojSettings.kt`의 `State` data class (줄 22 뒤)에 추가:

```kotlin
var githubReadmeEnabled: Boolean = false,
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.boj.intellij.github.GitHubSettingsFieldsTest" --info 2>&1 | tail -20`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/app/meot/boj-helper/settings/BojSettings.kt src/test/kotlin/com/boj/intellij/github/GitHubSettingsFieldsTest.kt
git commit -m "feat(settings): githubReadmeEnabled 설정 필드 추가"
```

---

### Task 7: GitHubSettingsDialog — README 체크박스 + 변수 안내 업데이트

**Files:**
- Modify: `src/main/kotlin/app/meot/boj-helper/github/GitHubSettingsDialog.kt`

**Step 1: UI 컴포넌트 추가**

멤버 변수 추가 (줄 29 뒤):

```kotlin
private val readmeCheckbox = JCheckBox("README.md 생성 (폴더 모드)")
```

**Step 2: loadCurrentSettings()에서 로드**

줄 46 뒤에 추가:

```kotlin
readmeCheckbox.isSelected = settings.state.githubReadmeEnabled
```

**Step 3: createCenterPanel()에서 체크박스 배치**

커밋 메시지 미리보기 (줄 132) 이후, 변수 안내 (줄 136) 이전에 추가:

```kotlin
// README 체크박스
gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
panel.add(readmeCheckbox, gbc)
row++
```

**Step 4: 변수 안내 업데이트**

줄 137의 helpLabel 텍스트를 변경:

```kotlin
val helpLabel = JLabel("<html>사용 가능한 변수: {problemId}, {title}, {language}, {ext}, {memory}, {time}, {tier}, {tierNum}<br>수정자: {변수:u} 대문자, {변수:l} 소문자, {변수:c} 첫글자 대문자</html>")
```

**Step 5: updatePreviews()에 새 변수 추가**

줄 160-170의 sampleVars에 tier 관련 변수 추가:

```kotlin
private fun updatePreviews() {
    val sampleVars = mapOf(
        "problemId" to "1000",
        "title" to "A+B",
        "language" to "Java 11",
        "ext" to "java",
        "memory" to "14512",
        "time" to "132",
        "tier" to "Gold",
        "tierNum" to "5",
    )
    pathPreviewLabel.text = "미리보기: ${TemplateEngine.render(pathTemplateField.text, sampleVars)}"
    commitPreviewLabel.text = "미리보기: ${TemplateEngine.render(commitTemplateField.text, sampleVars)}"
}
```

**Step 6: doOKAction()에서 저장**

줄 218 뒤에 추가:

```kotlin
settings.state.githubReadmeEnabled = readmeCheckbox.isSelected
```

**Step 7: Commit**

```bash
git add src/main/kotlin/app/meot/boj-helper/github/GitHubSettingsDialog.kt
git commit -m "feat(github): GitHubSettingsDialog에 README 체크박스 및 tier 변수 안내 추가"
```

---

### Task 8: SolvedAcApiClient — 알고리즘 분류 조회

**Files:**
- Create: `src/main/kotlin/app/meot/boj-helper/github/SolvedAcApiClient.kt`
- Test: `src/test/kotlin/com/boj/intellij/github/SolvedAcApiClientTest.kt`

**Step 1: Write the failing test**

```kotlin
// src/test/kotlin/com/boj/intellij/github/SolvedAcApiClientTest.kt
package com.boj.intellij.github

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SolvedAcApiClientTest {

    @Test
    fun `parseTags extracts tag names from API response`() {
        val json = """{"tags":[{"key":"dp","displayNames":[{"language":"ko","name":"다이나믹 프로그래밍","short":"DP"}]},{"key":"graph","displayNames":[{"language":"ko","name":"그래프 이론","short":"그래프"}]}]}"""
        val tags = SolvedAcApiClient.parseTags(json)
        assertEquals(listOf("다이나믹 프로그래밍", "그래프 이론"), tags)
    }

    @Test
    fun `parseTags returns empty list for no tags`() {
        val json = """{"tags":[]}"""
        val tags = SolvedAcApiClient.parseTags(json)
        assertTrue(tags.isEmpty())
    }

    @Test
    fun `parseTags falls back to en if ko not found`() {
        val json = """{"tags":[{"key":"dp","displayNames":[{"language":"en","name":"Dynamic Programming","short":"DP"}]}]}"""
        val tags = SolvedAcApiClient.parseTags(json)
        assertEquals(listOf("Dynamic Programming"), tags)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.boj.intellij.github.SolvedAcApiClientTest" --info 2>&1 | tail -20`
Expected: FAIL — class not found

**Step 3: Write implementation**

```kotlin
// src/main/kotlin/app/meot/boj-helper/github/SolvedAcApiClient.kt
package com.boj.intellij.github

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object SolvedAcApiClient {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)

    fun fetchTags(problemId: String): List<String> {
        return try {
            val url = "https://solved.ac/api/v3/problem/show?problemId=$problemId"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                parseTags(response.body())
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseTags(json: String): List<String> {
        val tagPattern = """"displayNames"\s*:\s*\[(.*?)\]""".toRegex()
        val namePattern = """"language"\s*:\s*"(\w+)"\s*,\s*"name"\s*:\s*"([^"]+)"""".toRegex()

        val tags = mutableListOf<String>()
        for (tagMatch in tagPattern.findAll(json)) {
            val displayNamesBlock = tagMatch.groupValues[1]
            val names = namePattern.findAll(displayNamesBlock)
                .map { it.groupValues[1] to it.groupValues[2] }
                .toList()

            val koName = names.firstOrNull { it.first == "ko" }?.second
            val enName = names.firstOrNull { it.first == "en" }?.second
            val name = koName ?: enName
            if (name != null) {
                tags.add(name)
            }
        }
        return tags
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.boj.intellij.github.SolvedAcApiClientTest" --info 2>&1 | tail -20`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/app/meot/boj-helper/github/SolvedAcApiClient.kt src/test/kotlin/com/boj/intellij/github/SolvedAcApiClientTest.kt
git commit -m "feat(github): SolvedAcApiClient 추가 — 알고리즘 분류 조회"
```

---

### Task 9: ReadmeGenerator — README.md 내용 생성

**Files:**
- Create: `src/main/kotlin/app/meot/boj-helper/github/ReadmeGenerator.kt`
- Test: `src/test/kotlin/com/boj/intellij/github/ReadmeGeneratorTest.kt`

**Step 1: Write the failing test**

```kotlin
// src/test/kotlin/com/boj/intellij/github/ReadmeGeneratorTest.kt
package com.boj.intellij.github

import com.boj.intellij.parse.ParsedProblem
import com.boj.intellij.submit.SubmitResult
import kotlin.test.Test
import kotlin.test.assertContains

class ReadmeGeneratorTest {

    private val sampleProblem = ParsedProblem(
        title = "A+B",
        timeLimit = "2초",
        memoryLimit = "128 MB",
        submitCount = "1000",
        answerCount = "500",
        solvedCount = "400",
        correctRate = "50%",
        problemDescription = "두 정수 A와 B를 입력받은 다음, A+B를 출력하는 프로그램을 작성하시오.",
        inputDescription = "첫째 줄에 A와 B가 주어진다.",
        outputDescription = "첫째 줄에 A+B를 출력한다.",
        problemDescriptionHtml = "",
        inputDescriptionHtml = "",
        outputDescriptionHtml = "",
        samplePairs = emptyList(),
    )

    private val sampleResult = SubmitResult(
        submissionId = "1", problemId = "1000", result = "맞았습니다!!",
        memory = "14512", time = "132", language = "Java 11", codeLength = "512",
    )

    @Test
    fun `generate includes title with problem link`() {
        val readme = ReadmeGenerator.generate(
            problemId = "1000", title = "A+B", tierLevel = 11,
            problemData = sampleProblem, submitResult = sampleResult,
            submittedAt = "2026-03-09 12:34:56",
        )
        assertContains(readme, "# 1000 - A+B")
        assertContains(readme, "https://www.acmicpc.net/problem/1000")
    }

    @Test
    fun `generate includes tier info`() {
        val readme = ReadmeGenerator.generate(
            problemId = "1000", title = "A+B", tierLevel = 11,
            problemData = sampleProblem, submitResult = sampleResult,
            submittedAt = "2026-03-09 12:34:56",
        )
        assertContains(readme, "Gold 5")
    }

    @Test
    fun `generate includes submission result`() {
        val readme = ReadmeGenerator.generate(
            problemId = "1000", title = "A+B", tierLevel = 11,
            problemData = sampleProblem, submitResult = sampleResult,
            submittedAt = "2026-03-09 12:34:56",
        )
        assertContains(readme, "14512")
        assertContains(readme, "132")
        assertContains(readme, "Java 11")
        assertContains(readme, "2026-03-09 12:34:56")
    }

    @Test
    fun `generate includes problem description`() {
        val readme = ReadmeGenerator.generate(
            problemId = "1000", title = "A+B", tierLevel = 11,
            problemData = sampleProblem, submitResult = sampleResult,
            submittedAt = "2026-03-09 12:34:56",
        )
        assertContains(readme, "두 정수 A와 B를 입력받은 다음")
    }

    @Test
    fun `generate includes algorithm tags`() {
        val readme = ReadmeGenerator.generate(
            problemId = "1000", title = "A+B", tierLevel = 11,
            problemData = sampleProblem, submitResult = sampleResult,
            submittedAt = "2026-03-09 12:34:56",
            tags = listOf("수학", "구현"),
        )
        assertContains(readme, "- 수학")
        assertContains(readme, "- 구현")
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.boj.intellij.github.ReadmeGeneratorTest" --info 2>&1 | tail -20`
Expected: FAIL — class not found

**Step 3: Write implementation**

```kotlin
// src/main/kotlin/app/meot/boj-helper/github/ReadmeGenerator.kt
package com.boj.intellij.github

import com.boj.intellij.parse.ParsedProblem
import com.boj.intellij.submit.SubmitResult

object ReadmeGenerator {

    fun generate(
        problemId: String,
        title: String,
        tierLevel: Int,
        problemData: ParsedProblem,
        submitResult: SubmitResult,
        submittedAt: String,
        tags: List<String> = emptyList(),
    ): String {
        val tierName = TierMapper.tierName(tierLevel) ?: "Unrated"
        val tierNum = TierMapper.tierNum(tierLevel)
        val tierDisplay = if (tierLevel > 0) "$tierName $tierNum" else tierName

        return buildString {
            // 제목 + 링크
            appendLine("# $problemId - $title")
            appendLine()
            appendLine("[문제 링크](https://www.acmicpc.net/problem/$problemId)")
            appendLine()

            // 기본 정보 테이블
            appendLine("| 난이도 | 시간 제한 | 메모리 제한 |")
            appendLine("|--------|----------|------------|")
            appendLine("| $tierDisplay | ${problemData.timeLimit} | ${problemData.memoryLimit} |")
            appendLine()

            // 알고리즘 분류
            if (tags.isNotEmpty()) {
                appendLine("## 알고리즘 분류")
                for (tag in tags) {
                    appendLine("- $tag")
                }
                appendLine()
            }

            // 제출 결과
            appendLine("## 제출 결과")
            appendLine("| 메모리 | 시간 | 언어 | 코드 길이 | 제출 일자 |")
            appendLine("|--------|------|------|----------|----------|")
            appendLine("| ${submitResult.memory} KB | ${submitResult.time} ms | ${submitResult.language} | ${submitResult.codeLength} B | $submittedAt |")
            appendLine()

            // 문제 설명
            if (problemData.problemDescription.isNotBlank()) {
                appendLine("## 문제 설명")
                appendLine(problemData.problemDescription)
                appendLine()
            }

            // 입력
            if (problemData.inputDescription.isNotBlank()) {
                appendLine("## 입력")
                appendLine(problemData.inputDescription)
                appendLine()
            }

            // 출력
            if (problemData.outputDescription.isNotBlank()) {
                appendLine("## 출력")
                appendLine(problemData.outputDescription)
            }
        }.trimEnd() + "\n"
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.boj.intellij.github.ReadmeGeneratorTest" --info 2>&1 | tail -20`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/app/meot/boj-helper/github/ReadmeGenerator.kt src/test/kotlin/com/boj/intellij/github/ReadmeGeneratorTest.kt
git commit -m "feat(github): ReadmeGenerator 추가 — README.md 내용 생성"
```

---

### Task 10: GitHubApiClient — Git Data API로 다중 파일 단일 커밋

**Files:**
- Modify: `src/main/kotlin/app/meot/boj-helper/github/GitHubApiClient.kt`
- Test: `src/test/kotlin/com/boj/intellij/github/GitHubApiClientTest.kt`

**Step 1: Write the failing test**

`GitHubApiClientTest.kt`에 추가:

```kotlin
@Test
fun `buildCreateTreeRequestBody creates correct JSON`() {
    val body = GitHubApiClient.buildCreateTreeRequestBody(
        baseTreeSha = "abc123",
        files = mapOf(
            "GOLD 5/1000/solution.java" to "public class Main {}",
            "GOLD 5/1000/README.md" to "# 1000 - A+B",
        ),
    )
    assertTrue(body.contains("\"base_tree\":\"abc123\""))
    assertTrue(body.contains("\"path\":\"GOLD 5/1000/solution.java\""))
    assertTrue(body.contains("\"path\":\"GOLD 5/1000/README.md\""))
    assertTrue(body.contains("\"mode\":\"100644\""))
    assertTrue(body.contains("\"type\":\"blob\""))
}

@Test
fun `buildCreateCommitRequestBody creates correct JSON`() {
    val body = GitHubApiClient.buildCreateCommitRequestBody(
        treeSha = "tree123",
        parentSha = "parent456",
        message = "[1000] A+B",
    )
    assertTrue(body.contains("\"tree\":\"tree123\""))
    assertTrue(body.contains("\"parents\":[\"parent456\"]"))
    assertTrue(body.contains("\"message\":\"[1000] A+B\""))
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.boj.intellij.github.GitHubApiClientTest" --info 2>&1 | tail -20`
Expected: FAIL — methods don't exist

**Step 3: Add Git Data API methods to GitHubApiClient**

companion object에 추가:

```kotlin
fun buildCreateTreeRequestBody(
    baseTreeSha: String,
    files: Map<String, String>,
): String {
    val treeEntries = files.entries.joinToString(",") { (path, content) ->
        val encoded = Base64.getEncoder().encodeToString(content.toByteArray())
        """{"path":"${escapeJson(path)}","mode":"100644","type":"blob","content":"${escapeJson(content)}"}"""
    }
    return """{"base_tree":"${escapeJson(baseTreeSha)}","tree":[$treeEntries]}"""
}

fun buildCreateCommitRequestBody(
    treeSha: String,
    parentSha: String,
    message: String,
): String {
    return """{"message":"${escapeJson(message)}","tree":"${escapeJson(treeSha)}","parents":["${escapeJson(parentSha)}"]}"""
}
```

인스턴스 메서드로 `uploadMultipleFiles` 추가:

```kotlin
/**
 * Git Data API를 사용하여 여러 파일을 단일 커밋으로 업로드한다.
 *
 * 흐름:
 * 1. GET /git/ref/heads/{branch} → 최신 커밋 SHA
 * 2. POST /git/trees → 새 Tree 생성
 * 3. POST /git/commits → 새 커밋 생성
 * 4. PATCH /git/ref/heads/{branch} → 브랜치 포인터 이동
 */
fun uploadMultipleFiles(
    repo: String,
    branch: String,
    commitMessage: String,
    files: Map<String, String>,
): UploadResult {
    // 1. 브랜치 최신 커밋 SHA 조회
    val refUrl = "https://api.github.com/repos/$repo/git/ref/heads/$branch"
    val refResponse = sendGet(refUrl)
    val commitSha = parseJsonValue(refResponse, "sha")
        ?: throw GitHubApiException(0, "브랜치 커밋 SHA를 가져올 수 없습니다")

    // 커밋에서 tree SHA 조회
    val commitUrl = "https://api.github.com/repos/$repo/git/commits/$commitSha"
    val commitResponse = sendGet(commitUrl)
    val treeSha = parseTreeSha(commitResponse)
        ?: throw GitHubApiException(0, "트리 SHA를 가져올 수 없습니다")

    // 2. 새 Tree 생성
    val treeUrl = "https://api.github.com/repos/$repo/git/trees"
    val treeBody = buildCreateTreeRequestBody(treeSha, files)
    val treeResponse = sendPost(treeUrl, treeBody)
    val newTreeSha = parseJsonValue(treeResponse, "sha")
        ?: throw GitHubApiException(0, "새 트리를 생성할 수 없습니다")

    // 3. 새 커밋 생성
    val newCommitUrl = "https://api.github.com/repos/$repo/git/commits"
    val commitBody = buildCreateCommitRequestBody(newTreeSha, commitSha, commitMessage)
    val newCommitResponse = sendPost(newCommitUrl, commitBody)
    val newCommitSha = parseJsonValue(newCommitResponse, "sha")
        ?: throw GitHubApiException(0, "새 커밋을 생성할 수 없습니다")

    // 4. 브랜치 포인터 이동
    val updateRefBody = """{"sha":"$newCommitSha"}"""
    sendPatch(refUrl, updateRefBody)

    return UploadResult(success = true)
}
```

HTTP 헬퍼 메서드들 추가 (인스턴스 메서드):

```kotlin
private fun sendGet(url: String): String {
    val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github.v3+json")
        .timeout(REQUEST_TIMEOUT)
        .GET()
        .build()
    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() !in 200..299) {
        throw GitHubApiException(response.statusCode(), parseErrorMessage(response.body()))
    }
    return response.body()
}

private fun sendPost(url: String, body: String): String {
    val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github.v3+json")
        .timeout(REQUEST_TIMEOUT)
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() !in 200..299) {
        throw GitHubApiException(response.statusCode(), parseErrorMessage(response.body()))
    }
    return response.body()
}

private fun sendPatch(url: String, body: String): String {
    val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github.v3+json")
        .timeout(REQUEST_TIMEOUT)
        .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
        .build()
    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() !in 200..299) {
        throw GitHubApiException(response.statusCode(), parseErrorMessage(response.body()))
    }
    return response.body()
}
```

companion object에 파싱 유틸 추가:

```kotlin
private fun parseTreeSha(commitJson: String): String? {
    val treePattern = """"tree"\s*:\s*\{[^}]*"sha"\s*:\s*"([^"]+)"""".toRegex()
    return treePattern.find(commitJson)?.groupValues?.get(1)
}

private fun parseJsonValue(json: String, key: String): String? {
    val pattern = """"${Regex.escape(key)}"\s*:\s*"([^"]+)"""".toRegex()
    return pattern.find(json)?.groupValues?.get(1)
}
```

**주의:** `escapeJson`은 현재 private이다. `buildCreateTreeRequestBody`와 `buildCreateCommitRequestBody`가 companion object에 있고 `escapeJson`도 companion object에 있으므로 접근 가능.

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.boj.intellij.github.GitHubApiClientTest" --info 2>&1 | tail -20`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/app/meot/boj-helper/github/GitHubApiClient.kt src/test/kotlin/com/boj/intellij/github/GitHubApiClientTest.kt
git commit -m "feat(github): Git Data API로 다중 파일 단일 커밋 지원"
```

---

### Task 11: GitHubUploadService에 solved.ac 태그 조회 통합

**Files:**
- Modify: `src/main/kotlin/app/meot/boj-helper/github/GitHubUploadService.kt`

**Step 1: doUpload()의 README 생성 부분에 태그 조회 추가**

README 생성 분기 내에서 solved.ac API를 호출:

```kotlin
if (settings.state.githubReadmeEnabled && problemData != null) {
    val tags = SolvedAcApiClient.fetchTags(submitResult.problemId)
    val readmeContent = ReadmeGenerator.generate(
        problemId = submitResult.problemId,
        title = title,
        tierLevel = tierLevel,
        problemData = problemData,
        submitResult = submitResult,
        submittedAt = submittedAt,
        tags = tags,
    )
    // ... 나머지 동일
}
```

**Step 2: Commit**

```bash
git add src/main/kotlin/app/meot/boj-helper/github/GitHubUploadService.kt
git commit -m "feat(github): README 생성 시 solved.ac에서 알고리즘 분류 조회"
```

---

### Task 12: 통합 테스트 및 최종 검증

**Step 1: 전체 테스트 실행**

Run: `./gradlew test --info 2>&1 | tail -30`
Expected: ALL PASS

**Step 2: 빌드 확인**

Run: `./gradlew buildPlugin --info 2>&1 | tail -20`
Expected: BUILD SUCCESS

**Step 3: 최종 커밋**

변경된 파일이 있으면 커밋. 없으면 스킵.

---

## 파일 변경 요약

| 파일 | 작업 |
|------|------|
| `github/TierMapper.kt` | 신규 — SVG → 티어명/서브티어 변환 |
| `github/TemplateEngine.kt` | 수정 — 수정자 (:u, :l, :c) + tier 변수 |
| `github/SolvedAcApiClient.kt` | 신규 — solved.ac 알고리즘 분류 조회 |
| `github/ReadmeGenerator.kt` | 신규 — README.md 내용 생성 |
| `github/GitHubApiClient.kt` | 수정 — Git Data API 다중 파일 커밋 |
| `github/GitHubUploadService.kt` | 수정 — tier/date/readme 통합 |
| `github/GitHubSettingsDialog.kt` | 수정 — README 체크박스 + 변수 안내 |
| `settings/BojSettings.kt` | 수정 — githubReadmeEnabled 필드 |
| `submit/BojSubmitPanel.kt` | 수정 — JS에서 tier/date 추출 |
| `ui/BojToolWindowPanel.kt` | 수정 — getCurrentParsedProblem() 노출 |
