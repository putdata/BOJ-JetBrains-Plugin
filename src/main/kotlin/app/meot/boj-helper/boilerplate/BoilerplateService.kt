package com.boj.intellij.boilerplate

import com.boj.intellij.github.TemplateEngine
import java.io.File

data class CreateFileResult(
    val success: Boolean,
    val filePath: File? = null,
    val error: String? = null,
)

object BoilerplateService {

    fun resolvePath(
        template: String,
        problemId: String,
        extension: String,
        title: String = "",
    ): String {
        val variables = mapOf(
            "problemId" to problemId,
            "ext" to extension,
            "title" to title,
        )
        return TemplateEngine.render(template, variables)
    }

    fun createFile(
        baseDir: File,
        relativePath: String,
        content: String,
        overwrite: Boolean,
    ): CreateFileResult {
        val file = File(baseDir, relativePath)
        if (file.exists() && !overwrite) {
            return CreateFileResult(success = false, filePath = file, error = "파일이 이미 존재합니다: ${file.path}")
        }
        file.parentFile?.mkdirs()
        file.writeText(content)
        return CreateFileResult(success = true, filePath = file)
    }

    fun getAvailableExtensions(templates: Map<String, String>): List<String> {
        return templates.keys.toList()
    }
}
