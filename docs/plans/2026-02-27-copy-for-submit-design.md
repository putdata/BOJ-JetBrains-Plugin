# RunBar 복사 버튼 및 레이아웃 변경 디자인

## 목표

1. RunBar의 상태 라벨("0/0 통과")을 중지 버튼 오른쪽으로 이동
2. 맨 오른쪽에 백준 제출용 코드 복사 버튼 추가 (Java 클래스명 → Main 자동 변환)

## 레이아웃

### 변경 전

```
[ComboBox] [▶ 실행] [■ 중지]                    [0/0 통과]
 ← WEST (leftPanel)                        EAST (statusLabel) →
```

### 변경 후

```
[ComboBox] [▶ 실행] [■ 중지] 0/0 통과              [📋]
 ← WEST (leftPanel + statusLabel)     EAST (copyToolbar) →
```

## 컴포넌트 구조

```
RunBarPanel (BorderLayout)
├─ WEST (leftPanel, FlowLayout.LEFT)
│  ├─ commandComboBox (JComboBox)
│  ├─ actionToolbar [RunAllAction] [StopAction]
│  └─ statusLabel (JLabel, "0/0 통과")
│
└─ EAST (copyToolbar, ActionToolbar)
   └─ CopyForSubmitAction (AllIcons.Actions.Copy)
```

## CopyForSubmitAction 동작

1. 현재 활성 에디터에서 파일 내용과 확장자를 읽음
2. `.java` 파일인 경우: `public class 클래스명` → `public class Main` 치환
3. 다른 언어: 코드를 그대로 사용
4. 시스템 클립보드에 복사
5. 피드백: statusLabel에 "복사됨" 표시 → 2초 후 이전 텍스트로 복원

## 클래스명 변환 로직

- 대상: `.java` 파일만
- 정규식: `(public\s+class\s+)\w+` → `$1Main`
- Kotlin: top-level `fun main()` 사용하므로 변환 불필요

## 피드백 UX

- 아이콘: `AllIcons.Actions.Copy`
- 툴팁: "백준 제출용 코드 복사"
- 복사 성공: statusLabel → "복사됨" (2초 후 이전 텍스트 복원)

## 파일 변경 범위

- `RunBarPanel.kt`: 레이아웃 변경, CopyForSubmitAction 추가
- `BojToolWindowPanel.kt`: 필요시 콜백 연결
