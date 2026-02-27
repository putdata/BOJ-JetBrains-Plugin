# JetBrains 멀티 IDE 호환성 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** BOJ IntelliJ 플러그인을 2021.3+ 전체 주요 JetBrains IDE에서 설치 가능하도록 호환성 확보

**Architecture:** build.gradle.kts의 빌드 SDK를 Community로 변경하고, 버전 범위를 213+로 확장한다. 2022.3에서 도입된 ActionUpdateThread와 updateActionsAsync()는 폴백 유틸로 대체한다.

**Tech Stack:** IntelliJ Platform SDK, Kotlin, Gradle (intellij-platform-gradle-plugin 2.x)

---

### Task 1: ActionToolbarCompat 유틸 생성

**Files:**
- Create: `src/main/kotlin/com/boj/intellij/ui/ActionToolbarCompat.kt`

**Step 1: 유틸 파일 작성**

```kotlin
package com.boj.intellij.ui

import com.intellij.openapi.actionSystem.ActionToolbar

object ActionToolbarCompat {
    fun ActionToolbar.updateActionsSafe() {
        runCatching { updateActionsAsync() }
            .onFailure { updateActionsImmediately() }
    }
}
```

**Step 2: 커밋**

```bash
git add src/main/kotlin/com/boj/intellij/ui/ActionToolbarCompat.kt
git commit -m "feat: ActionToolbar 하위 호환 유틸 추가"
```

---

### Task 2: RunBarPanel에서 ActionUpdateThread 제거 및 폴백 적용

**Files:**
- Modify: `src/main/kotlin/com/boj/intellij/ui/RunBarPanel.kt`

**Step 1: import 변경**

- 제거: `import com.intellij.openapi.actionSystem.ActionUpdateThread` (6행)
- 추가: `import com.boj.intellij.ui.ActionToolbarCompat.updateActionsSafe`

**Step 2: updateActionsAsync → updateActionsSafe 교체 (2곳)**

- 85행: `toolbar?.updateActionsAsync()` → `toolbar?.updateActionsSafe()`
- 115행: `toolbar?.updateActionsAsync()` → `toolbar?.updateActionsSafe()`

**Step 3: getActionUpdateThread() 오버라이드 제거 (2곳)**

- 141행: `override fun getActionUpdateThread() = ActionUpdateThread.EDT` 제거
- 157행: `override fun getActionUpdateThread() = ActionUpdateThread.EDT` 제거

**Step 4: 커밋**

```bash
git add src/main/kotlin/com/boj/intellij/ui/RunBarPanel.kt
git commit -m "refactor: RunBarPanel에서 ActionUpdateThread 제거 및 폴백 적용"
```

---

### Task 3: BojTestResultPanel에서 ActionUpdateThread 제거 및 폴백 적용

**Files:**
- Modify: `src/main/kotlin/com/boj/intellij/ui/testresult/BojTestResultPanel.kt`

**Step 1: import 추가**

- 추가: `import com.boj.intellij.ui.ActionToolbarCompat.updateActionsSafe`

**Step 2: updateActionsAsync → updateActionsSafe 교체 (1곳)**

- 344행: `headerToolbar?.updateActionsAsync()` → `headerToolbar?.updateActionsSafe()`

**Step 3: getActionUpdateThread() 오버라이드 제거 (5곳)**

- 374행: `override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT` 제거
- 394행: 동일 제거
- 414행: 동일 제거
- 426행: 동일 제거
- 438행: 동일 제거

**Step 4: 커밋**

```bash
git add src/main/kotlin/com/boj/intellij/ui/testresult/BojTestResultPanel.kt
git commit -m "refactor: BojTestResultPanel에서 ActionUpdateThread 제거 및 폴백 적용"
```

---

### Task 4: build.gradle.kts 호환성 설정 변경

**Files:**
- Modify: `build.gradle.kts`

**Step 1: 빌드 SDK 변경**

- 23행: `intellijIdeaUltimate("2025.3.3")` → `intellijIdeaCommunity("2024.3")`

**Step 2: 버전 범위 변경**

- 39행: `sinceBuild = "253"` → `sinceBuild = "213"`
- 40행: `untilBuild = "253.*"` → 이 줄 전체 삭제

**Step 3: JVM toolchain 변경**

- 32행: `jvmToolchain(21)` → `jvmToolchain(11)`

**Step 4: 커밋**

```bash
git add build.gradle.kts
git commit -m "build: JetBrains 멀티 IDE 호환성 설정 (2021.3+, Community SDK)"
```

---

### Task 5: 빌드 검증

**Step 1: Gradle 빌드 실행**

```bash
./gradlew buildPlugin
```

Expected: BUILD SUCCESSFUL, build/distributions에 ZIP 파일 생성

**Step 2: Plugin Verifier로 호환성 검증 (선택)**

```bash
./gradlew verifyPlugin
```

Expected: 치명적 오류 없음
