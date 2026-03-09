package com.boj.intellij.github

object TierMapper {

    private val TIER_NAMES = arrayOf("Bronze", "Silver", "Gold", "Platinum", "Diamond", "Ruby")

    fun tierName(svgLevel: Int): String? {
        if (svgLevel == 0) return "Unrated"
        if (svgLevel !in 1..30) return null
        val groupIndex = (svgLevel - 1) / 5
        return TIER_NAMES[groupIndex]
    }

    fun tierNum(svgLevel: Int): Int {
        if (svgLevel == 0) return 0
        if (svgLevel !in 1..30) return 0
        return 5 - (svgLevel - 1) % 5
    }
}
