package com.boj.intellij.ui

object RunConfigurationCommandResolver {

    /**
     * 파일 경로에서 실행 명령어를 추론한다.
     * Run Configuration이 없을 때 폴백으로 사용한다.
     */
    fun resolveCommandFromFilePath(filePath: String): String? {
        return BojToolWindowPanel.inferCommandFromFilePath(filePath)
    }

    /**
     * 표시용 이름을 반환한다 (파일명만).
     */
    fun getDisplayName(pathOrName: String): String {
        val normalized = pathOrName.replace('\\', '/')
        return normalized.substringAfterLast('/')
    }
}
