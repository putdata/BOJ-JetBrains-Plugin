package com.boj.intellij.ui

object CopyForSubmitUtil {

    private val JAVA_CLASS_PATTERN = Regex("""((?:public\s+)?class\s+)\w+""")

    fun transformForSubmit(code: String, extension: String?): String {
        if (extension?.lowercase() != "java") return code
        val match = JAVA_CLASS_PATTERN.find(code) ?: return code
        val replacement = "${match.groupValues[1]}Main"
        return code.replaceRange(match.range, replacement)
    }
}
