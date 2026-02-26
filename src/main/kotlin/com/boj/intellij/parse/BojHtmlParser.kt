package com.boj.intellij.parse

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class BojHtmlParser : BojParser {
    override fun parse(rawHtml: String): ParsedProblem {
        val document = Jsoup.parse(rawHtml)
        val info = parseProblemInfo(document)
        val samplePairs = parseSamplePairs(document)
        val problemDescription = parseBodySection(document, "#problem_description")
        val inputDescription = parseBodySection(document, "#problem_input")
        val outputDescription = parseBodySection(document, "#problem_output")

        return ParsedProblem(
            title = textOf(document, "#problem_title"),
            timeLimit = info["시간 제한"].orEmpty(),
            memoryLimit = info["메모리 제한"].orEmpty(),
            submitCount = info["제출"].orEmpty(),
            answerCount = info["정답"].orEmpty(),
            solvedCount = info["맞힌 사람"].orEmpty(),
            correctRate = info["정답 비율"].orEmpty(),
            problemDescription = problemDescription.text,
            inputDescription = inputDescription.text,
            outputDescription = outputDescription.text,
            problemDescriptionHtml = problemDescription.html,
            inputDescriptionHtml = inputDescription.html,
            outputDescriptionHtml = outputDescription.html,
            samplePairs = samplePairs,
        )
    }

    private fun parseProblemInfo(document: Document): Map<String, String> {
        val table = document.selectFirst("#problem-info") ?: return emptyMap()
        val parsed = LinkedHashMap<String, String>()

        val headers = table.select("thead th").map { normalizeWhitespace(it.text()) }
        val values = table.select("tbody td").map { normalizeWhitespace(it.text()) }

        headers.zip(values).forEach { (header, value) ->
            if (header.isNotEmpty()) {
                parsed[header] = value
            }
        }

        table.select("tr")
            .flatMap { row ->
                val cells = row.children().toList()
                cells.chunked(2).mapNotNull { pair ->
                    if (pair.size < 2 || pair[0].tagName() != "th" || pair[1].tagName() != "td") {
                        null
                    } else {
                        normalizeWhitespace(pair[0].text()) to normalizeWhitespace(pair[1].text())
                    }
                }
            }
            .forEach { (label, value) ->
                if (label.isNotEmpty()) {
                    parsed.putIfAbsent(label, value)
                }
            }

        return parsed
    }

    private fun parseBodySection(document: Document, selector: String): SectionContent {
        val section = document.selectFirst(selector) ?: return SectionContent(text = "", html = "")
        val bodyElements = sectionBodyElements(section)

        val text =
            normalizeWhitespace(
                if (bodyElements.isNotEmpty()) {
                    bodyElements.joinToString(separator = " ") { it.text() }
                } else {
                    section.text()
                },
            )

        val rawHtml =
            if (bodyElements.isNotEmpty()) {
                bodyElements.joinToString(separator = "\n") { it.outerHtml() }
            } else {
                section.html()
            }

        return SectionContent(
            text = text,
            html = sanitizeSectionHtml(rawHtml),
        )
    }

    private fun sectionBodyElements(section: Element): List<Element> {
        val elements = section.children().toList()
        if (elements.isEmpty()) {
            return emptyList()
        }

        return if (elements.first().tagName().equals("h2", ignoreCase = true)) {
            elements.drop(1)
        } else {
            elements
        }
    }

    private fun parseSamplePairs(document: Document): List<ParsedSamplePair> {
        val inputMap = parseSampleByPrefix(document, "sampleinput")
        val outputMap = parseSampleByPrefix(document, "sampleoutput")
        val indexes = (inputMap.keys + outputMap.keys).distinct().sorted()

        return indexes.map { index ->
            ParsedSamplePair(
                input = inputMap[index].orEmpty(),
                output = outputMap[index].orEmpty(),
            )
        }
    }

    private fun parseSampleByPrefix(document: Document, prefix: String): Map<Int, String> {
        return document.select("section[id^=$prefix]")
            .associateNotNull { section ->
                val id = section.id()
                val index = id.removePrefix(prefix).toIntOrNull() ?: return@associateNotNull null
                val value = section.selectFirst("pre")?.wholeText()?.trimEnd().orEmpty()
                index to value
            }
    }

    private inline fun <T, K, V> Iterable<T>.associateNotNull(transform: (T) -> Pair<K, V>?): Map<K, V> {
        val destination = LinkedHashMap<K, V>()
        for (element in this) {
            val entry = transform(element) ?: continue
            destination[entry.first] = entry.second
        }
        return destination
    }

    private fun textOf(document: Document, selector: String): String =
        document.selectFirst(selector)?.text()?.let(::normalizeWhitespace).orEmpty()

    private fun sanitizeSectionHtml(rawHtml: String): String =
        rawHtml
            .replace(SCRIPT_TAG_REGEX, "")
            .replace(STYLE_TAG_REGEX, "")
            .trim()

    private fun normalizeWhitespace(raw: String): String = raw.replace(WHITESPACE, " ").trim()

    private companion object {
        private val WHITESPACE = Regex("\\s+")
        private val SCRIPT_TAG_REGEX = Regex("(?is)<script[^>]*>.*?</script>")
        private val STYLE_TAG_REGEX = Regex("(?is)<style[^>]*>.*?</style>")
    }

    private data class SectionContent(
        val text: String,
        val html: String,
    )
}
