package com.boj.intellij.ui

import com.intellij.openapi.project.Project

/**
 * 프로젝트에 설정된 Python 인터프리터 경로를 리플렉션으로 가져온다.
 * PyCharm 등 Python 플러그인이 있는 IDE에서는 프로젝트 SDK의 인터프리터를 반환하고,
 * 없는 IDE에서는 null을 반환한다.
 */
object PythonInterpreterResolver {

    fun resolve(project: Project): String? {
        return runCatching {
            val prmClass = Class.forName("com.intellij.openapi.roots.ProjectRootManager")
            val manager = prmClass.getMethod("getInstance", Project::class.java).invoke(null, project)
            val sdk = prmClass.getMethod("getProjectSdk").invoke(manager) ?: return null

            val sdkType = sdk.javaClass.getMethod("getSdkType").invoke(sdk)
            val typeName = sdkType.javaClass.getMethod("getName").invoke(sdkType) as? String
            if (typeName != "Python SDK") return null

            sdk.javaClass.getMethod("getHomePath").invoke(sdk) as? String
        }.getOrNull()
    }

    fun defaultCommand(): String {
        return if (System.getProperty("os.name").lowercase().contains("win")) "python" else "python3"
    }
}
