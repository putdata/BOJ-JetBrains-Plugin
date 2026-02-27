# 실행 시 활성 파일 자동 저장

## 요약
실행 버튼 클릭 시 현재 에디터에서 활성화된 파일을 자동 저장한 후 실행을 진행한다.

## 변경 대상
- `BojToolWindowPanel.kt`의 `handleRunAll()`과 `handleRunSingle()` 메서드

## 구현
두 메서드의 시작부에서 EDT를 통해 활성 에디터 문서를 저장한다.

```kotlin
runOnEdt {
    val editor = FileEditorManager.getInstance(project).selectedTextEditor
    editor?.document?.let { FileDocumentManager.getInstance().saveDocument(it) }
}
```

- `FileEditorManager.getInstance(project).selectedTextEditor` — 현재 활성 에디터
- `FileDocumentManager.getInstance().saveDocument(document)` — 해당 문서 저장
- `runOnEdt` — 기존 코드에서 사용하는 EDT 전환 패턴

## 고려 사항
- 저장할 문서가 없거나 변경 사항이 없으면 `saveDocument`는 no-op
- 저장은 실행 로직 이전에 동기적으로 수행됨
- 변경 파일 1개, 추가 코드 약 5줄
