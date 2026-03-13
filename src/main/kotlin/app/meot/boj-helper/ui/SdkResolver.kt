package com.boj.intellij.ui

data class SdkEntry(
    val name: String,
    val homePath: String,
    val versionString: String?,
    val typeName: String,
)

object SdkResolver {

    private const val TYPE_JAVA = "JavaSDK"
    private const val TYPE_PYTHON = "Python SDK"

    fun getAllSdks(): List<SdkEntry> {
        return runCatching {
            val tableClass = Class.forName("com.intellij.openapi.projectRoots.ProjectJdkTable")
            val sdkClass = Class.forName("com.intellij.openapi.projectRoots.Sdk")
            val sdkTypeIdClass = Class.forName("com.intellij.openapi.projectRoots.SdkTypeId")

            val table = tableClass.getMethod("getInstance").invoke(null)
            val allSdks = tableClass.getMethod("getAllJdks").invoke(table) as? Array<*> ?: return emptyList()

            allSdks.mapNotNull { sdk ->
                if (sdk == null) return@mapNotNull null

                val sdkType = sdkClass.getMethod("getSdkType").invoke(sdk)
                val typeName = sdkTypeIdClass.getMethod("getName").invoke(sdkType) as? String
                    ?: return@mapNotNull null

                val name = sdkClass.getMethod("getName").invoke(sdk) as? String ?: return@mapNotNull null
                val homePath = sdkClass.getMethod("getHomePath").invoke(sdk) as? String ?: return@mapNotNull null
                val versionString = sdkClass.getMethod("getVersionString").invoke(sdk) as? String

                SdkEntry(name = name, homePath = homePath, versionString = versionString, typeName = typeName)
            }
        }.getOrDefault(emptyList())
    }

    fun filterByExtension(sdks: List<SdkEntry>, extension: String): List<SdkEntry> {
        val targetType = extensionToSdkType(extension) ?: return emptyList()
        return sdks.filter { it.typeName == targetType }
    }

    fun isSdkSelectableExtension(extension: String): Boolean {
        return extensionToSdkType(extension) != null
    }

    fun extensionToSdkTypeKey(extension: String): String? = when (extension.lowercase()) {
        "java" -> "java"
        "py" -> "python"
        else -> null
    }

    fun resolveJavaBinary(jdkHomePath: String): String {
        val suffix = if (isWindows()) ".exe" else ""
        return "$jdkHomePath/bin/java$suffix"
    }

    fun resolveJavacBinary(jdkHomePath: String): String {
        val suffix = if (isWindows()) ".exe" else ""
        return "$jdkHomePath/bin/javac$suffix"
    }

    fun resolvePythonBinary(sdkHomePath: String): String = sdkHomePath

    private fun extensionToSdkType(extension: String): String? = when (extension.lowercase()) {
        "java" -> TYPE_JAVA
        "py" -> TYPE_PYTHON
        else -> null
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("win")
}
