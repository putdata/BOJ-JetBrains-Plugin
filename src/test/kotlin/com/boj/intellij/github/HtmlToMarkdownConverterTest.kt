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
}
