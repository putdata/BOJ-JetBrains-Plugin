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

    private fun convertChildren(parent: Node, sb: StringBuilder, listDepth: Int = 0) {
        for (child in parent.childNodes()) {
            convertNode(child, sb, listDepth)
        }
    }

    private fun convertNode(node: Node, sb: StringBuilder, listDepth: Int = 0) {
        when (node) {
            is TextNode -> {
                val text = node.wholeText
                if (text.isNotBlank() || (text.contains(' ') && sb.isNotEmpty() && !sb.endsWith('\n'))) {
                    sb.append(text.replace(Regex("\\s+"), " "))
                }
            }
            is Element -> convertElement(node, sb, listDepth)
        }
    }

    private fun convertElement(element: Element, sb: StringBuilder, listDepth: Int = 0) {
        when (element.tagName().lowercase()) {
            "p", "div" -> {
                ensureBlankLine(sb)
                convertChildren(element, sb, listDepth)
                ensureBlankLine(sb)
            }
            "br" -> sb.append("\n")
            "strong", "b" -> {
                sb.append("**")
                convertChildren(element, sb, listDepth)
                sb.append("**")
            }
            "em", "i" -> {
                sb.append("*")
                convertChildren(element, sb, listDepth)
                sb.append("*")
            }
            "sup" -> {
                sb.append("<sup>")
                convertChildren(element, sb, listDepth)
                sb.append("</sup>")
            }
            "sub" -> {
                sb.append("<sub>")
                convertChildren(element, sb, listDepth)
                sb.append("</sub>")
            }
            "ul" -> {
                if (listDepth == 0) ensureBlankLine(sb)
                else if (sb.isNotEmpty() && !sb.endsWith('\n')) sb.append("\n")
                val items = element.children().filter { it.tagName().lowercase() == "li" }
                items.forEachIndexed { index, li ->
                    val indent = "  ".repeat(listDepth)
                    sb.append("${indent}- ")
                    convertChildren(li, sb, listDepth + 1)
                    if (index < items.size - 1) sb.append("\n")
                }
                if (listDepth == 0) ensureBlankLine(sb)
            }
            "ol" -> {
                if (listDepth == 0) ensureBlankLine(sb)
                else if (sb.isNotEmpty() && !sb.endsWith('\n')) sb.append("\n")
                val items = element.children().filter { it.tagName().lowercase() == "li" }
                items.forEachIndexed { index, li ->
                    val indent = "  ".repeat(listDepth)
                    sb.append("${indent}${index + 1}. ")
                    convertChildren(li, sb, listDepth + 1)
                    if (index < items.size - 1) sb.append("\n")
                }
                if (listDepth == 0) ensureBlankLine(sb)
            }
            "a" -> {
                val href = element.attr("href")
                sb.append("[")
                convertChildren(element, sb, listDepth)
                sb.append("](${href})")
            }
            "img" -> {
                val src = element.attr("src")
                val alt = element.attr("alt")
                sb.append("![${alt}](${src})")
            }
            "span" -> convertChildren(element, sb, listDepth)
            else -> convertChildren(element, sb, listDepth)
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
