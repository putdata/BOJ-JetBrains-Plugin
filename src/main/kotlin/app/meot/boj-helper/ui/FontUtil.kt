package com.boj.intellij.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import java.awt.Font

object FontUtil {
    private const val CJK_TEST_CHAR = '한'

    fun monoFont(size: Float): Font {
        val editorFont = runCatching {
            EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN).deriveFont(size)
        }.getOrNull()
        if (editorFont != null && editorFont.canDisplay(CJK_TEST_CHAR)) return editorFont

        val mono = Font(Font.MONOSPACED, Font.PLAIN, size.toInt())
        if (mono.canDisplay(CJK_TEST_CHAR)) return mono

        return Font(Font.DIALOG, Font.PLAIN, size.toInt())
    }
}
