package com.boj.intellij.parse

data class ParsedProblem(
    val title: String,
    val timeLimit: String,
    val memoryLimit: String,
    val submitCount: String,
    val answerCount: String,
    val solvedCount: String,
    val correctRate: String,
    val problemDescription: String,
    val inputDescription: String,
    val outputDescription: String,
    val problemDescriptionHtml: String,
    val inputDescriptionHtml: String,
    val outputDescriptionHtml: String,
    val samplePairs: List<ParsedSamplePair>,
    val limitHtml: String = "",
    val subtaskHtml: String = "",
    val hintHtml: String = "",
    val sampleExplains: Map<Int, String> = emptyMap(),
) {
    val mainBody: String
        get() =
            listOf(problemDescription, inputDescription, outputDescription)
                .filter(String::isNotBlank)
                .joinToString(separator = "\n\n")
}

data class ParsedSamplePair(
    val input: String,
    val output: String,
)
