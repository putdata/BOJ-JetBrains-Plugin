package com.boj.intellij.github

import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateEngineTest {

    @Test
    fun `replaces single variable`() {
        val result = TemplateEngine.render("{problemId}", mapOf("problemId" to "1000"))
        assertEquals("1000", result)
    }

    @Test
    fun `replaces multiple variables`() {
        val result = TemplateEngine.render(
            "[{problemId}] {title}",
            mapOf("problemId" to "1000", "title" to "A+B"),
        )
        assertEquals("[1000] A+B", result)
    }

    @Test
    fun `leaves unknown variables as-is`() {
        val result = TemplateEngine.render("{unknown}", mapOf("problemId" to "1000"))
        assertEquals("{unknown}", result)
    }

    @Test
    fun `handles path template`() {
        val result = TemplateEngine.render(
            "{language}/{problemId}.{ext}",
            mapOf("language" to "Java 11", "problemId" to "1000", "ext" to "java"),
        )
        assertEquals("Java 11/1000.java", result)
    }

    @Test
    fun `handles empty template`() {
        assertEquals("", TemplateEngine.render("", mapOf("a" to "b")))
    }

    @Test
    fun `handles template with no variables`() {
        assertEquals("hello", TemplateEngine.render("hello", mapOf("a" to "b")))
    }

    @Test
    fun `buildVariables creates correct map from SubmitResult`() {
        val result = com.boj.intellij.submit.SubmitResult(
            submissionId = "12345678",
            problemId = "1000",
            result = "맞았습니다!!",
            memory = "14512",
            time = "132",
            language = "Java 11",
            codeLength = "512",
        )
        val vars = TemplateEngine.buildVariables(
            submitResult = result,
            title = "A+B",
            extension = "java",
        )
        assertEquals("1000", vars["problemId"])
        assertEquals("A+B", vars["title"])
        assertEquals("Java 11", vars["language"])
        assertEquals("java", vars["ext"])
        assertEquals("14512", vars["memory"])
        assertEquals("132", vars["time"])
    }
}
