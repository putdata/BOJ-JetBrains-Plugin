# GitHub Upload Customization Design

## Overview
GitHub 업로드 시 경로/커밋 메시지에 사용할 수 있는 변수를 확장하고, 폴더 모드에서 README.md를 생성할 수 있도록 한다.

## 1. Template Engine 확장

### 수정자 문법
정규식을 `\{(\w+)(?::([ulc]))?\}`로 변경하여 변수 뒤에 수정자를 지원한다.

| 수정자 | 동작 | 예시 |
|--------|------|------|
| 없음 | 원본 그대로 | `{tier}` → Gold |
| `:u` | 전체 대문자 | `{tier:u}` → GOLD |
| `:l` | 전체 소문자 | `{tier:l}` → gold |
| `:c` | 첫 글자만 대문자 | `{tier:c}` → Gold |

모든 변수에 적용 가능: `{title:u}`, `{language:l}` 등

### 새 변수

| 변수 | 소스 | 예시 |
|------|------|------|
| `{tier}` | SVG 번호 → 티어명 | Gold |
| `{tierNum}` | SVG 번호 → 서브티어 (1-5) | 5 |

### 티어 변환 로직
```
SVG 0     → Unrated, 0
SVG 1-5   → Bronze 5,4,3,2,1
SVG 6-10  → Silver 5,4,3,2,1
SVG 11-15 → Gold 5,4,3,2,1
SVG 16-20 → Platinum 5,4,3,2,1
SVG 21-25 → Diamond 5,4,3,2,1
SVG 26-30 → Ruby 5,4,3,2,1
```

tierNum = `5 - ((svgNumber - 1) % 5)` (svgNumber > 0일 때)

## 2. 티어 정보 추출

상태 페이지(`/status`) 테이블 행에서 추출한다.

각 행의 문제 번호 셀에 있는 `<img src="...tier/{번호}.svg">` 이미지의 src에서 숫자를 파싱한다.

### 업로드 요청 JSON 확장
```json
{
  "submissionId": "12345",
  "problemId": "1000",
  "sourceCode": "...",
  "language": "Java 11",
  "tierLevel": 11,
  "submittedAt": "2026-03-09 12:34:56"
}
```

`injectGitHubUploadButtons()`에서 버튼 주입 시 같은 행에서 `tierLevel`과 `submittedAt`을 함께 추출하여 Kotlin 측에 전달한다.

## 3. README 생성

### 활성화
GitHub 설정 다이얼로그에 "README.md 생성" 체크박스 추가.

### 생성 위치
경로 템플릿의 파일과 같은 디렉토리.
- 예: `{tier:u} {tierNum}/{problemId}/solution.{ext}` → `GOLD 5/1000/README.md`

### README 내용

```markdown
# {문제번호} - {문제 제목}

| 난이도 | 시간 제한 | 메모리 제한 |
|--------|----------|------------|
| Gold 5 | 1초      | 128 MB     |

## 알고리즘 분류
- 다이나믹 프로그래밍
- 그래프 이론

## 제출 결과
| 메모리 | 시간 | 언어 | 코드 길이 | 제출 일자 |
|--------|------|------|----------|----------|
| 14512 KB | 132 ms | Java 11 | 512 B | 2026-03-09 12:34:56 |

## 문제 설명
{문제 설명}

## 입력
{입력 설명}

## 출력
{출력 설명}
```

### 데이터 소스
- 문제 정보(제목, 제한, 설명): BojToolWindowPanel에서 현재 로드된 문제 데이터
- 알고리즘 분류: solved.ac API (`/api/v3/problem/show?problemId={id}`)
- 제출 결과: SubmitResult + submittedAt (상태 페이지에서 추출)

## 4. Git Data API를 통한 단일 커밋 다중 파일 업로드

### 분기 로직
- README OFF → 기존 Contents API (파일 1개, 커밋 1개)
- README ON → Git Data API (파일 2개, 커밋 1개)

### Git Data API 호출 흐름
1. **GET** `/git/ref/heads/{branch}` → 최신 커밋 SHA
2. **POST** `/git/trees` → solution + README.md 포함 새 Tree 생성
3. **POST** `/git/commits` → 새 커밋 생성 (parent: 1의 커밋)
4. **PATCH** `/git/ref/heads/{branch}` → 브랜치를 새 커밋으로 이동

## 5. 설정 저장

BojSettings에 추가:
- `githubReadmeEnabled: Boolean = false`

## 6. 경로 예시

| 템플릿 | 결과 |
|--------|------|
| `{language}/{problemId}.{ext}` | `Java 11/1000.java` |
| `{tier:u} {tierNum}/{problemId}/solution.{ext}` | `GOLD 5/1000/solution.java` |
| `{tier:l}/{problemId} - {title}.{ext}` | `gold/1000 - A+B.java` |
