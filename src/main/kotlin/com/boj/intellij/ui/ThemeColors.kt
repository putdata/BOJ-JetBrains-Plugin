package com.boj.intellij.ui

import java.awt.Color
import javax.swing.UIManager

data class ThemeColors(
    val panelBg: String,
    val labelFg: String,
    val borderColor: String,
    val editorBg: String,
    val editorFg: String,
    val secondaryFg: String,
    val sampleBg: String = "#f2f2f2",
    val passBg: String = "#d4edda",
    val passFg: String = "#155724",
    val failBg: String = "#f8d7da",
    val failFg: String = "#721c24",
    val failBorder: String = "#dc3545",
    val scrollbarThumb: String = "rgba(0,0,0,0.2)",
    val scrollbarHover: String = "rgba(0,0,0,0.4)",
) {
    companion object {
        fun fromCurrentTheme(): ThemeColors {
            val bg = UIManager.getColor("Panel.background") ?: Color(0xFFFFFF)
            val isDark = isDarkTheme(bg)

            return ThemeColors(
                panelBg = bg.toCssHex(),
                labelFg = (UIManager.getColor("Label.foreground") ?: Color(0x202124)).toCssHex(),
                borderColor = (UIManager.getColor("Component.borderColor") ?: Color(0xD0D7DE)).toCssHex(),
                editorBg = (UIManager.getColor("EditorPane.background") ?: Color(0xF6F8FA)).toCssHex(),
                editorFg = (UIManager.getColor("EditorPane.foreground") ?: Color(0x1F2328)).toCssHex(),
                secondaryFg = (UIManager.getColor("Label.disabledForeground") ?: Color(0x666666)).toCssHex(),
                sampleBg = shiftBrightness(bg, if (isDark) 0.08 else -0.04).toCssHex(),
                passBg = if (isDark) "#1a3a2a" else "#d4edda",
                passFg = if (isDark) "#8DD694" else "#155724",
                failBg = if (isDark) "#3a1a1a" else "#f8d7da",
                failFg = if (isDark) "#FF8A80" else "#721c24",
                failBorder = if (isDark) "#FF8A80" else "#dc3545",
                scrollbarThumb = if (isDark) "rgba(255,255,255,0.28)" else "rgba(0,0,0,0.18)",
                scrollbarHover = if (isDark) "rgba(255,255,255,0.50)" else "rgba(0,0,0,0.38)",
            )
        }

        private fun shiftBrightness(color: Color, amount: Double): Color {
            val r = (color.red + 255 * amount).toInt().coerceIn(0, 255)
            val g = (color.green + 255 * amount).toInt().coerceIn(0, 255)
            val b = (color.blue + 255 * amount).toInt().coerceIn(0, 255)
            return Color(r, g, b)
        }

        private fun isDarkTheme(bg: Color): Boolean {
            val luminance = (0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue) / 255.0
            return luminance < 0.5
        }
    }
}

fun Color.toCssHex(): String = String.format("#%02x%02x%02x", red, green, blue)
