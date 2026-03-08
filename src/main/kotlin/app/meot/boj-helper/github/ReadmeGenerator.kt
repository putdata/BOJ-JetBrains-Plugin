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
