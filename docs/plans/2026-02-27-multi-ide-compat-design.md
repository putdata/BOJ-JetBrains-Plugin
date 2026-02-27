# JetBrains 멀티 IDE 호환성 디자인

## 목표

BOJ IntelliJ 플러그인을 IntelliJ IDEA뿐만 아니라 PyCharm, CLion 등 주요 JetBrains IDE에서도
설치 가능하도록 호환성을 확보한다.

## 범위

- **대상 IDE**: 전체 주요 JetBrains IDE (IntelliJ IDEA, PyCharm, CLion, GoLand, WebStorm 등)
- **버전**: 2021.3+ (sinceBuild=213, untilBuild 제거)
- **JVM**: 11
- **배포**: JetBrains Marketplace + GitHub Releases

## 현재 상태 분석

### 호환 가능한 부분 (변경 불필요)

- `plugin.xml`에서 `com.intellij.modules.platform`만 의존 — 모든 JetBrains IDE 호환
- Java/Ultimate 전용 API 미사용 (Java PSI, RunConfiguration API 등 없음)
- 프로세스 실행은 순수 `ProcessBuilder` 사용 — IDE 독립적
- JCEF는 `JBCefApp.isSupported()` 체크 + `JTextArea` 폴백 처리됨
- `ContentFactory.getInstance()` — 2021.3에서 도입, OK

### 호환성 이슈 (변경 필요)

| 이슈 | 도입 시점 | 사용 위치 | 해결 방법 |
|------|----------|----------|----------|
| `ActionUpdateThread` 클래스 | 2022.3 | RunBarPanel, BojTestResultPanel | override 제거 |
| `updateActionsAsync()` 메서드 | 2022.3 | RunBarPanel, BojTestResultPanel | 폴백 유틸로 교체 |
| JVM toolchain 21 | — | build.gradle.kts | 11로 변경 |
| 빌드 SDK: Ultimate | — | build.gradle.kts | Community로 변경 |
| sinceBuild/untilBuild 범위 | — | build.gradle.kts | 213 / 제거 |

## 변경 계획

### 1. build.gradle.kts

- `intellijIdeaUltimate("2025.3.3")` → `intellijIdeaCommunity("2024.3")`
- `sinceBuild = "253"` → `"213"`
- `untilBuild = "253.*"` → 제거
- `jvmToolchain(21)` → `jvmToolchain(11)`

### 2. ActionToolbarCompat.kt (신규)

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

### 3. RunBarPanel.kt

- `ActionUpdateThread` import 및 `getActionUpdateThread()` 오버라이드 제거 (2곳)
- `toolbar?.updateActionsAsync()` → `toolbar?.updateActionsSafe()` (2곳)

### 4. BojTestResultPanel.kt

- `ActionUpdateThread` 참조 및 `getActionUpdateThread()` 오버라이드 제거 (5곳)
- `headerToolbar?.updateActionsAsync()` → `headerToolbar?.updateActionsSafe()` (1곳)
