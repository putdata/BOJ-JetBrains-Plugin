package com.boj.intellij.common

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MemoRepositoryTest {

    @TempDir
    lateinit var baseDir: File

    private fun createRepo() = MemoRepository(baseDir)

    @Test
    fun `listMemos returns empty list for nonexistent problem`() {
        val repo = createRepo()
        assertTrue(repo.listMemos("9999").isEmpty())
    }

    @Test
    fun `save and load single memo`() {
        val repo = createRepo()
        repo.save("1000", "메모 1", "DP로 풀기")

        val memos = repo.listMemos("1000")
        assertEquals(listOf("메모 1"), memos)
        assertEquals("DP로 풀기", repo.load("1000", "메모 1"))
    }

    @Test
    fun `save and load multiple memos`() {
        val repo = createRepo()
        repo.save("1000", "메모 1", "첫 번째")
        repo.save("1000", "메모 2", "두 번째")

        val memos = repo.listMemos("1000")
        assertEquals(2, memos.size)
        assertTrue(memos.contains("메모 1"))
        assertTrue(memos.contains("메모 2"))
    }

    @Test
    fun `load returns empty string for nonexistent memo`() {
        val repo = createRepo()
        assertEquals("", repo.load("1000", "없는메모"))
    }

    @Test
    fun `delete removes memo file`() {
        val repo = createRepo()
        repo.save("1000", "삭제할메모", "content")
        repo.delete("1000", "삭제할메모")

        assertFalse(repo.listMemos("1000").contains("삭제할메모"))
    }

    @Test
    fun `rename changes memo file name`() {
        val repo = createRepo()
        repo.save("1000", "원래이름", "content")
        repo.rename("1000", "원래이름", "새이름")

        assertFalse(repo.listMemos("1000").contains("원래이름"))
        assertTrue(repo.listMemos("1000").contains("새이름"))
        assertEquals("content", repo.load("1000", "새이름"))
    }

    @Test
    fun `nextAutoName generates incrementing names`() {
        val repo = createRepo()
        assertEquals("메모 1", repo.nextAutoName("1000"))
        repo.save("1000", "메모 1", "a")
        assertEquals("메모 2", repo.nextAutoName("1000"))
        repo.save("1000", "메모 2", "b")
        assertEquals("메모 3", repo.nextAutoName("1000"))
    }

    @Test
    fun `nextAutoName skips existing names`() {
        val repo = createRepo()
        repo.save("1000", "메모 1", "a")
        repo.save("1000", "메모 3", "c")
        assertEquals("메모 2", repo.nextAutoName("1000"))
    }

    @Test
    fun `save overwrites existing memo`() {
        val repo = createRepo()
        repo.save("1000", "메모 1", "old")
        repo.save("1000", "메모 1", "new")
        assertEquals("new", repo.load("1000", "메모 1"))
    }

    @Test
    fun `memos are isolated per problem`() {
        val repo = createRepo()
        repo.save("1000", "메모 1", "problem 1000")
        repo.save("2000", "메모 1", "problem 2000")

        assertEquals("problem 1000", repo.load("1000", "메모 1"))
        assertEquals("problem 2000", repo.load("2000", "메모 1"))
    }

    @Test
    fun `sanitizes file name with invalid characters`() {
        val repo = createRepo()
        repo.save("1000", "test/case", "content")

        val memos = repo.listMemos("1000")
        assertEquals(1, memos.size)
        assertEquals("content", repo.load("1000", "test/case"))
    }
}
