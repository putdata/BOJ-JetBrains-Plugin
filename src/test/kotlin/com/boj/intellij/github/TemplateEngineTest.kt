package com.boj.intellij.github

import com.boj.intellij.submit.SubmitResult
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
    fun `buildVariables creates correct map from SubmitResult`() {
        val result = SubmitResult(
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
}
