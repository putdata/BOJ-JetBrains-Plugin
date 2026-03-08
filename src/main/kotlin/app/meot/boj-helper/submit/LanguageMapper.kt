package com.boj.intellij.submit

import com.boj.intellij.settings.BojSettings
import com.intellij.openapi.application.ApplicationManager

object LanguageMapper {

    val DEFAULT_MAPPINGS = mapOf(
        "java" to "Java 11",
        "kt" to "Kotlin (JVM)",
        "py" to "Python 3",
        "cpp" to "C++17",
        "cc" to "C++17",
        "cxx" to "C++17",
        "c" to "C99",
        "js" to "node.js",
        "go" to "Go",
    )

    val BOJ_LANGUAGES = listOf(
        "C99", "C11", "C90", "C2x",
        "C99 (Clang)", "C11 (Clang)", "C90 (Clang)", "C2x (Clang)",
        "C++98", "C++11", "C++14", "C++17", "C++20", "C++23", "C++26",
        "C++98 (Clang)", "C++11 (Clang)", "C++14 (Clang)", "C++17 (Clang)", "C++20 (Clang)",
        "Java 8", "Java 8 (OpenJDK)", "Java 11", "Java 15",
        "Python 2", "Python 3", "PyPy2", "PyPy3",
        "Kotlin (JVM)", "Kotlin (Native)",
        "C#",
        "node.js", "TypeScript", "Rhino",
        "Go", "Go (gccgo)",
        "D", "D (LDC)",
        "Ruby",
        "Rust 2015", "Rust 2018", "Rust 2021", "Rust 2024",
        "Swift",
        "Fortran",
        "Pascal",
        "Lua",
        "Perl",
        "F#",
        "Visual Basic",
        "Objective-C", "Objective-C++",
        "PHP",
        "Bash",
        "Scheme",
        "Ada",
        "awk",
        "OCaml",
        "Brainf**k",
        "Whitespace",
        "Tcl",
        "Pike",
        "sed",
        "INTERCAL",
        "bc",
        "Algol 68",
        "Befunge",
        "FreeBASIC",
        "Haxe",
        "LOLCODE",
        "아희",
        "SystemVerilog",
        "Golfscript",
        "Assembly (32bit)", "Assembly (64bit)",
        "Text",
    )

    val BOJ_LANGUAGE_IDS = mapOf(
        "C99" to 0,
        "C++98" to 1,
        "Pascal" to 2,
        "Java 8" to 3,
        "Bash" to 5,
        "PHP" to 7,
        "Perl" to 8,
        "Objective-C" to 10,
        "Go" to 12,
        "Fortran" to 13,
        "Scheme" to 14,
        "Lua" to 16,
        "node.js" to 17,
        "Ada" to 19,
        "awk" to 21,
        "OCaml" to 22,
        "Brainf**k" to 23,
        "Whitespace" to 24,
        "Tcl" to 26,
        "Assembly (32bit)" to 27,
        "Python 3" to 28,
        "D" to 29,
        "Rhino" to 34,
        "Pike" to 41,
        "sed" to 43,
        "Rust 2015" to 44,
        "INTERCAL" to 47,
        "bc" to 48,
        "C++11" to 49,
        "Text" to 58,
        "C99 (Clang)" to 59,
        "C++98 (Clang)" to 60,
        "Objective-C++" to 64,
        "C++11 (Clang)" to 66,
        "C++14 (Clang)" to 67,
        "Ruby" to 68,
        "Kotlin (JVM)" to 69,
        "Algol 68" to 70,
        "Befunge" to 71,
        "PyPy3" to 73,
        "Swift" to 74,
        "C11" to 75,
        "C11 (Clang)" to 77,
        "FreeBASIC" to 78,
        "Golfscript" to 79,
        "Haxe" to 81,
        "LOLCODE" to 82,
        "아희" to 83,
        "C++17" to 84,
        "C++17 (Clang)" to 85,
        "C#" to 86,
        "Assembly (64bit)" to 87,
        "C++14" to 88,
        "Go (gccgo)" to 90,
        "Java 8 (OpenJDK)" to 91,
        "Java 11" to 93,
        "Rust 2018" to 94,
        "C++20" to 95,
        "C++20 (Clang)" to 96,
        "D (LDC)" to 100,
        "C90" to 101,
        "C2x" to 102,
        "C90 (Clang)" to 103,
        "C2x (Clang)" to 104,
        "SystemVerilog" to 105,
        "TypeScript" to 106,
        "Java 15" to 107,
        "F#" to 108,
        "Visual Basic" to 109,
        "Rust 2021" to 113,
        "C++23" to 114,
        "C++26" to 115,
        "Rust 2024" to 116,
    )

    private val LANGUAGE_TO_EXTENSION = mapOf(
        "java" to listOf("Java 8", "Java 8 (OpenJDK)", "Java 11", "Java 15"),
        "kt" to listOf("Kotlin (JVM)", "Kotlin (Native)"),
        "py" to listOf("Python 2", "Python 3", "PyPy2", "PyPy3"),
        "cpp" to listOf(
            "C++98", "C++11", "C++14", "C++17", "C++20", "C++23", "C++26",
            "C++98 (Clang)", "C++11 (Clang)", "C++14 (Clang)", "C++17 (Clang)", "C++20 (Clang)",
        ),
        "c" to listOf(
            "C99", "C11", "C90", "C2x",
            "C99 (Clang)", "C11 (Clang)", "C90 (Clang)", "C2x (Clang)",
        ),
        "js" to listOf("node.js", "Rhino"),
        "ts" to listOf("TypeScript"),
        "go" to listOf("Go", "Go (gccgo)"),
        "rs" to listOf("Rust 2015", "Rust 2018", "Rust 2021", "Rust 2024"),
        "rb" to listOf("Ruby"),
        "swift" to listOf("Swift"),
        "cs" to listOf("C#"),
        "fs" to listOf("F#"),
        "vb" to listOf("Visual Basic"),
        "sh" to listOf("Bash"),
        "php" to listOf("PHP"),
        "d" to listOf("D", "D (LDC)"),
        "txt" to listOf("Text"),
    )

    private val reverseLookup: Map<String, String> by lazy {
        LANGUAGE_TO_EXTENSION.flatMap { (ext, langs) ->
            langs.map { lang -> lang to ext }
        }.toMap()
    }

    fun toExtension(bojLanguageName: String): String? {
        return reverseLookup[bojLanguageName]
    }

    fun toBojLanguageName(extension: String): String? {
        val ext = extension.lowercase()
        val app = ApplicationManager.getApplication() ?: return DEFAULT_MAPPINGS[ext]
        if (app.isUnitTestMode) return DEFAULT_MAPPINGS[ext]
        val settings = BojSettings.getInstance()
        return settings.languageMappings[ext] ?: settings.defaultLanguage
    }

    fun toBojLanguageId(extension: String): Int? {
        val name = toBojLanguageName(extension) ?: return null
        return BOJ_LANGUAGE_IDS[name]
    }
}
