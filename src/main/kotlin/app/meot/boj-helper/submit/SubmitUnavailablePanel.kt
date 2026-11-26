package com.boj.intellij.submit

import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/** JCEF를 쓸 수 없을 때 제출 탭에 표시하는 안내 패널. JCEF 타입을 참조하지 않는다. */
class SubmitUnavailablePanel : JPanel(BorderLayout()) {
    init {
        add(JLabel("이 IDE에서는 제출 기능을 지원하지 않습니다. (JCEF 미지원)", SwingConstants.CENTER), BorderLayout.CENTER)
    }
}
