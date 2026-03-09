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
    fun `returns empty string for empty html`() {
        assertEquals("", HtmlToMarkdownConverter.convert(""))
    }
}
