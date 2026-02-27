package com.boj.intellij.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class CopyForSubmitUtilTest {

    @Test
    fun `replaces public class name with Main for java`() {
        val code = "public class Boj1000 {\n    public static void main(String[] args) {\n    }\n}"
        val result = CopyForSubmitUtil.transformForSubmit(code, "java")
        assertEquals("public class Main {\n    public static void main(String[] args) {\n    }\n}", result)
    }

    @Test
    fun `does not change non-java code`() {
        val code = "fun main() {\n    println(\"hello\")\n}"
        val result = CopyForSubmitUtil.transformForSubmit(code, "kt")
        assertEquals(code, result)
    }

    @Test
    fun `does not change python code`() {
        val code = "print('hello')"
        val result = CopyForSubmitUtil.transformForSubmit(code, "py")
        assertEquals(code, result)
    }

    @Test
    fun `handles java class without public modifier`() {
        val code = "class Solution {\n}"
        val result = CopyForSubmitUtil.transformForSubmit(code, "java")
        assertEquals("class Main {\n}", result)
    }

    @Test
    fun `handles java class with extra spaces`() {
        val code = "public  class  MyClass {"
        val result = CopyForSubmitUtil.transformForSubmit(code, "java")
        assertEquals("public  class  Main {", result)
    }

    @Test
    fun `returns original code when extension is null`() {
        val code = "some code"
        val result = CopyForSubmitUtil.transformForSubmit(code, null)
        assertEquals(code, result)
    }
}
