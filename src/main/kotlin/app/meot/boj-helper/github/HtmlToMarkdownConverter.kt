package com.boj.intellij.github

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

object HtmlToMarkdownConverter {

    fun convert(html: String): String {
        if (html.isBlank()) return ""
        val doc = Jsoup.parseBodyFragment(html)
        val sb = StringBuilder()
        convertChildren(doc.body(), sb)
        return postProcess(sb.toString())
    }

    private fun convertChildren(parent: Node, sb: StringBuilder) {
        for (child in parent.childNodes()) {
            convertNode(child, sb)
        }
    }

    private fun convertNode(node: Node, sb: StringBuilder) {
        when (node) {
            is TextNode -> {
                val text = node.wholeText
                if (text.isNotBlank() || (text.contains(' ') && sb.isNotEmpty() && !sb.endsWith('\n'))) {
                    sb.append(text.replace(Regex("\\s+"), " "))
                }
            }
            is Element -> convertElement(node, sb)
        }
    }

    private fun convertElement(element: Element, sb: StringBuilder) {
        when (element.tagName().lowercase()) {
            "p", "div" -> {
                ensureBlankLine(sb)
                convertChildren(element, sb)
                ensureBlankLine(sb)
            }
            "br" -> sb.append("\n")
            "span" -> convertChildren(element, sb)
            else -> convertChildren(element, sb)
        }
    }

    private fun ensureBlankLine(sb: StringBuilder) {
        if (sb.isEmpty()) return
        val trailing = sb.takeLastWhile { it == '\n' || it == ' ' }
        if (!trailing.contains("\n\n")) {
            val newlines = trailing.count { it == '\n' }
            repeat(2 - newlines) { sb.append("\n") }
        }
    }

    private fun postProcess(raw: String): String {
        return raw
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
