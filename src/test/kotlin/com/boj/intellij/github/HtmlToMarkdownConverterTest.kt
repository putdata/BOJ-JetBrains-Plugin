package com.boj.intellij.github

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlToMarkdownConverterTest {

    @Test
    fun `converts plain text`() {
        val html = "Hello World"
        assertEquals("Hello World", HtmlToMarkdownConverter.convert(html))
    }

    @Test
    fun `converts paragraphs with blank line separation`() {
        val html = "<p>First paragraph</p><p>Second paragraph</p>"
        assertEquals("First paragraph\n\nSecond paragraph", HtmlToMarkdownConverter.convert(html))
    }

    @Test
    fun `converts br to newline`() {
        val html = "<p>Line one<br>Line two</p>"
        assertEquals("Line one\nLine two", HtmlToMarkdownConverter.convert(html))
    }

    @Test
    fun `converts div as block element`() {
        val html = "<div>Block one</div><div>Block two</div>"
        assertEquals("Block one\n\nBlock two", HtmlToMarkdownConverter.convert(html))
    }

    @Test
    fun `converts span as inline element`() {
        val html = "<p>Hello <span>world</span></p>"
        assertEquals("Hello world", HtmlToMarkdownConverter.convert(html))
    }

    @Test
    fun `returns empty string for empty html`() {
        assertEquals("", HtmlToMarkdownConverter.convert(""))
    }

    @Test
    fun `converts strong and b to bold`() {
        assertEquals("**bold**", HtmlToMarkdownConverter.convert("<strong>bold</strong>"))
        assertEquals("**bold**", HtmlToMarkdownConverter.convert("<b>bold</b>"))
    }

    @Test
    fun `converts em and i to italic`() {
        assertEquals("*italic*", HtmlToMarkdownConverter.convert("<em>italic</em>"))
        assertEquals("*italic*", HtmlToMarkdownConverter.convert("<i>italic</i>"))
    }

    @Test
    fun `preserves sup and sub as html`() {
        assertEquals("x<sup>2</sup>", HtmlToMarkdownConverter.convert("x<sup>2</sup>"))
        assertEquals("a<sub>i</sub>", HtmlToMarkdownConverter.convert("a<sub>i</sub>"))
    }

    @Test
    fun `converts nested inline formatting`() {
        assertEquals("**bold *and italic***", HtmlToMarkdownConverter.convert("<strong>bold <em>and italic</em></strong>"))
    }

    @Test
    fun `converts unordered list`() {
        val html = "<ul><li>Apple</li><li>Banana</li></ul>"
        assertEquals("- Apple\n- Banana", HtmlToMarkdownConverter.convert(html))
    }

    @Test
    fun `converts ordered list`() {
        val html = "<ol><li>First</li><li>Second</li><li>Third</li></ol>"
        assertEquals("1. First\n2. Second\n3. Third", HtmlToMarkdownConverter.convert(html))
    }

    @Test
    fun `converts nested list`() {
        val html = "<ul><li>Parent<ul><li>Child</li></ul></li></ul>"
        assertEquals("- Parent\n  - Child", HtmlToMarkdownConverter.convert(html))
    }

    @Test
    fun `list inside paragraph`() {
        val html = "<p>Before list</p><ul><li>Item</li></ul><p>After list</p>"
        assertEquals("Before list\n\n- Item\n\nAfter list", HtmlToMarkdownConverter.convert(html))
    }

    @Test
    fun `converts anchor to markdown link`() {
        val html = """<a href="https://example.com">Link text</a>"""
        assertEquals("[Link text](https://example.com)", HtmlToMarkdownConverter.convert(html))
    }

    @Test
    fun `converts img to markdown image`() {
        val html = """<img src="https://upload.acmicpc.net/img.png" alt="diagram">"""
        assertEquals("![diagram](https://upload.acmicpc.net/img.png)", HtmlToMarkdownConverter.convert(html))
    }

    @Test
    fun `converts img without alt`() {
        val html = """<img src="https://upload.acmicpc.net/img.png">"""
        assertEquals("![](https://upload.acmicpc.net/img.png)", HtmlToMarkdownConverter.convert(html))
    }

    @Test
    fun `converts simple table`() {
        val html = """
            <table>
              <thead><tr><th>A</th><th>B</th></tr></thead>
              <tbody><tr><td>1</td><td>2</td></tr><tr><td>3</td><td>4</td></tr></tbody>
            </table>
        """.trimIndent()
        val expected = "| A | B |\n| --- | --- |\n| 1 | 2 |\n| 3 | 4 |"
        assertEquals(expected, HtmlToMarkdownConverter.convert(html))
    }

    @Test
    fun `converts table without thead`() {
        val html = """
            <table>
              <tr><td>A</td><td>B</td></tr>
              <tr><td>1</td><td>2</td></tr>
            </table>
        """.trimIndent()
        val expected = "| A | B |\n| --- | --- |\n| 1 | 2 |"
        assertEquals(expected, HtmlToMarkdownConverter.convert(html))
    }

    @Test
    fun `converts inline code`() {
        val html = "<p>Use <code>printf</code> function</p>"
        assertEquals("Use `printf` function", HtmlToMarkdownConverter.convert(html))
    }

    @Test
    fun `converts pre block to code fence`() {
        val html = "<pre>int main() {\n  return 0;\n}</pre>"
        assertEquals("```\nint main() {\n  return 0;\n}\n```", HtmlToMarkdownConverter.convert(html))
    }

    @Test
    fun `converts pre with code child`() {
        val html = "<pre><code>x = 1\ny = 2</code></pre>"
        assertEquals("```\nx = 1\ny = 2\n```", HtmlToMarkdownConverter.convert(html))
    }
}
