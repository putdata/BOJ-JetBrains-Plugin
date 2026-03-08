package com.boj.intellij.submit

data class SubmitResult(
    val submissionId: String,
    val problemId: String,
    val result: String,
    val memory: String,
    val time: String,
    val language: String,
    val codeLength: String,
) {
    fun isAccepted(): Boolean = result == "맞았습니다!!"

    fun isFinalResult(): Boolean = result in FINAL_RESULTS

    companion object {
        private val FINAL_RESULTS = setOf(
            "맞았습니다!!",
            "틀렸습니다",
            "시간 초과",
            "메모리 초과",
            "출력 초과",
            "런타임 에러",
            "컴파일 에러",
        )
    }
}
