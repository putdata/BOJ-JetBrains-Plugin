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

    @Test
    fun `removes java package declaration`() {
        val code = "package boj.p1000;\n\nimport java.io.*;\n\npublic class Boj1000 {\n}"
        val result = CopyForSubmitUtil.transformForSubmit(code, "java")
        assertEquals("import java.io.*;\n\npublic class Main {\n}", result)
    }

    @Test
    fun `removes java package declaration but keeps leading comments`() {
        val code = "// 백준 1000번\n/* A+B */\npackage boj . p1000 ;\n\npublic class Boj1000 {\n}"
        val result = CopyForSubmitUtil.transformForSubmit(code, "java")
        assertEquals("// 백준 1000번\n/* A+B */\npublic class Main {\n}", result)
    }

    @Test
    fun `removes java package declaration with CRLF line endings`() {
        val code = "package boj.p1000;\r\n\r\npublic class Main {\r\n}"
        val result = CopyForSubmitUtil.transformForSubmit(code, "java")
        assertEquals("public class Main {\r\n}", result)
    }

    @Test
    fun `does not touch package text outside declaration`() {
        val code = "public class Main {\n    String s = \"package x;\";\n}"
        val result = CopyForSubmitUtil.transformForSubmit(code, "java")
        assertEquals(code, result)
    }

    @Test
    fun `does not remove package from non-java code`() {
        val code = "package boj\n\nfun main() {\n}"
        val result = CopyForSubmitUtil.transformForSubmit(code, "kt")
        assertEquals(code, result)
    }

    @Test
    fun `renames public class with main method not first class`() {
        val code = """
            |import java.io.*;
            |import java.util.*;
            |
            |class Example {
            |    // some code
            |}
            |
            |public class BOJ11400 {
            |    public static void main(String[] args) throws Exception {
            |        // some code
            |    }
            |}
        """.trimMargin()
        val expected = """
            |import java.io.*;
            |import java.util.*;
            |
            |class Example {
            |    // some code
            |}
            |
            |public class Main {
            |    public static void main(String[] args) throws Exception {
            |        // some code
            |    }
            |}
        """.trimMargin()
        val result = CopyForSubmitUtil.transformForSubmit(code, "java")
        assertEquals(expected, result)
    }
}
