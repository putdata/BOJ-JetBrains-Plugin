package com.boj.intellij.common

import java.io.File

class MemoRepository(private val baseDir: File) {

    fun listMemos(problemId: String): List<String> {
        val dir = memoDir(problemId)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension == "txt" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    fun load(problemId: String, memoName: String): String {
        val file = memoFile(problemId, memoName)
        return if (file.exists()) file.readText() else ""
    }

    fun save(problemId: String, memoName: String, content: String) {
        val file = memoFile(problemId, memoName)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    fun delete(problemId: String, memoName: String) {
        memoFile(problemId, memoName).delete()
    }

    fun rename(problemId: String, oldName: String, newName: String) {
        val oldFile = memoFile(problemId, oldName)
        val newFile = memoFile(problemId, newName)
        if (oldFile.exists()) {
            oldFile.renameTo(newFile)
        }
    }

    fun nextAutoName(problemId: String): String {
        val existing = listMemos(problemId).toSet()
        var counter = 1
        while ("메모 $counter" in existing) {
            counter++
        }
        return "메모 $counter"
    }

    private fun memoDir(problemId: String): File =
        File(baseDir, "memo/$problemId")

    private fun memoFile(problemId: String, memoName: String): File {
        val safeName = sanitizeFileName(memoName)
        return File(memoDir(problemId), "$safeName.txt")
    }

    companion object {
        fun sanitizeFileName(name: String): String =
            name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
    }
}
