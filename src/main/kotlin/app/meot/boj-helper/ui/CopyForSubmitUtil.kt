package com.boj.intellij.ui

object CopyForSubmitUtil {

    private val PUBLIC_CLASS_PATTERN = Regex("""(public\s+class\s+)\w+""")
    private val CLASS_PATTERN = Regex("""(class\s+)\w+""")

    // package 선언 앞에는 공백과 주석만 올 수 있다. 앞쪽 주석은 남기고, 선언 줄과 뒤이은 빈 줄만 지운다.
    private val PACKAGE_DECLARATION_PATTERN = Regex(
        """\A(\uFEFF?(?:\s|//[^\r\n]*+|/\*[\s\S]*?\*/)*+)package\s+[\p{L}_$][\p{L}\p{N}_$]*(?:\s*\.\s*[\p{L}_$][\p{L}\p{N}_$]*)*\s*;[ \t]*(?:\r?\n(?:[ \t]*\r?\n)*)?""",
    )

    fun transformForSubmit(code: String, extension: String?): String {
        if (extension?.lowercase() != "java") return code
        // 백준은 기본 패키지의 Main 클래스를 실행하므로 package 선언이 있으면 클래스를 찾지 못한다.
        val withoutPackage = PACKAGE_DECLARATION_PATTERN.replace(code) { it.groupValues[1] }
        val match = PUBLIC_CLASS_PATTERN.find(withoutPackage) ?: CLASS_PATTERN.find(withoutPackage) ?: return withoutPackage
        val replacement = "${match.groupValues[1]}Main"
        return withoutPackage.replaceRange(match.range, replacement)
    }
}
