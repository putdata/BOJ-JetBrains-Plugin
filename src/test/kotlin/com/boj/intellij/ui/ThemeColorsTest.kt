package com.boj.intellij.ui

import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeColorsTest {
    @Test
    fun `toCssHex converts color to hex string`() {
        assertEquals("#ff0000", Color.RED.toCssHex())
        assertEquals("#00ff00", Color(0, 255, 0).toCssHex())
        assertEquals("#ffffff", Color.WHITE.toCssHex())
        assertEquals("#000000", Color.BLACK.toCssHex())
    }

    @Test
    fun `fromCurrentTheme returns theme colors with valid hex values`() {
        val colors = ThemeColors.fromCurrentTheme()
        assertTrue(colors.panelBg.startsWith("#"))
        assertTrue(colors.labelFg.startsWith("#"))
        assertTrue(colors.borderColor.startsWith("#"))
        assertTrue(colors.editorBg.startsWith("#"))
        assertTrue(colors.editorFg.startsWith("#"))
        assertTrue(colors.secondaryFg.startsWith("#"))
        assertEquals(7, colors.panelBg.length)
        assertEquals(7, colors.labelFg.length)
    }
}
