package com.boj.intellij.ui

object CopyForSubmitUtil {

    private val PUBLIC_CLASS_PATTERN = Regex("""(public\s+class\s+)\w+""")
    private val CLASS_PATTERN = Regex("""(class\s+)\w+""")

    fun transformForSubmit(code: String, extension: String?): String {
        if (extension?.lowercase() != "java") return code
        val match = PUBLIC_CLASS_PATTERN.find(code) ?: CLASS_PATTERN.find(code) ?: return code
        val replacement = "${match.groupValues[1]}Main"
        return code.replaceRange(match.range, replacement)
    }
}
