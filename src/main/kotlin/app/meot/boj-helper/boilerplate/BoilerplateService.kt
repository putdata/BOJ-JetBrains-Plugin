package com.boj.intellij.boilerplate

import com.boj.intellij.github.TemplateEngine
import java.io.File

data class CreateFileResult(
    val success: Boolean,
    val filePath: File? = null,
    val error: String? = null,
)

object BoilerplateService {

    private fun buildVariables(
        problemId: String,
        extension: String,
        title: String = "",
    ): Map<String, String> = mapOf(
        "problemId" to problemId,
        "ext" to extension,
        "title" to title,
    )

    fun resolvePath(
        template: String,
        problemId: String,
        extension: String,
        title: String = "",
    ): String {
        return TemplateEngine.render(template, buildVariables(problemId, extension, title))
    }

    fun resolveContent(
        template: String,
        problemId: String,
        extension: String,
        title: String = "",
    ): String {
        return TemplateEngine.render(template, buildVariables(problemId, extension, title))
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
