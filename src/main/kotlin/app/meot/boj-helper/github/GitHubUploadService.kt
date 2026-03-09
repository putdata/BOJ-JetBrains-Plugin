package com.boj.intellij.github

import com.boj.intellij.parse.ParsedProblem
import com.boj.intellij.settings.BojSettings
import com.boj.intellij.submit.SubmitResult
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

object GitHubUploadService {

    fun upload(
        project: Project,
        submitResult: SubmitResult,
        sourceCode: String,
        title: String,
        extension: String,
        tierLevel: Int = 0,
        submittedAt: String = "",
        problemData: ParsedProblem? = null,
        onSuccess: (() -> Unit)? = null,
        onFailure: (() -> Unit)? = null,
    ) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                doUpload(project, submitResult, sourceCode, title, extension, tierLevel, submittedAt, problemData)
                onSuccess?.invoke()
            } catch (e: Exception) {
                notifyError(project, e.message ?: "알 수 없는 오류가 발생했습니다")
                onFailure?.invoke()
            }
        }
    }

    private fun doUpload(
        project: Project,
        submitResult: SubmitResult,
        sourceCode: String,
        title: String,
        extension: String,
        tierLevel: Int = 0,
        submittedAt: String = "",
        problemData: ParsedProblem? = null,
    ) {
        val token = GitHubCredentialStore.getToken()
        if (token.isNullOrBlank()) {
            throw RuntimeException("GitHub 토큰이 설정되지 않았습니다. GitHub 설정을 확인해주세요.")
        }

        val settings = BojSettings.getInstance()
        val repo = settings.state.githubRepo
        if (repo.isBlank()) {
            throw RuntimeException("GitHub 리포지토리가 설정되지 않았습니다. GitHub 설정을 확인해주세요.")
        }

        val branch = settings.state.githubBranch
        val variables = TemplateEngine.buildVariables(submitResult, title, extension, tierLevel)
        val path = TemplateEngine.render(settings.state.githubPathTemplate, variables)
        val commitMessage = TemplateEngine.render(settings.state.githubCommitTemplate, variables)

        val client = GitHubApiClient(token)

        if (settings.state.githubReadmeEnabled && problemData != null) {
            // README 포함 — Git Data API로 단일 커밋
            val tags = SolvedAcApiClient.fetchTags(submitResult.problemId)
            val readmeContent = ReadmeGenerator.generate(
                problemId = submitResult.problemId,
                title = title,
                tierLevel = tierLevel,
                problemData = problemData,
                submitResult = submitResult,
                submittedAt = submittedAt,
                tags = tags,
            )
            val dir = path.substringBeforeLast('/', "")
            val readmePath = if (dir.isNotEmpty()) "$dir/README.md" else "README.md"

            val result = client.uploadMultipleFiles(
                repo = repo,
                branch = branch,
                commitMessage = commitMessage,
                files = mapOf(
                    path to sourceCode,
                    readmePath to readmeContent,
                ),
            )
            if (result.success) {
                notifySuccess(project, submitResult.problemId, title, result.htmlUrl)
            } else {
                throw RuntimeException("다중 파일 업로드에 실패했습니다")
            }
        } else {
            // 기존 단일 파일 업로드
            val existingSha = try {
                client.getFileSha(repo, path, branch)
            } catch (e: GitHubApiClient.GitHubApiException) {
                null
            }
            val result = client.uploadFile(
                repo = repo, path = path, content = sourceCode,
                commitMessage = commitMessage, branch = branch, existingSha = existingSha,
            )
            if (result.success) {
                notifySuccess(project, submitResult.problemId, title, result.htmlUrl)
            } else {
                throw RuntimeException("파일 업로드에 실패했습니다")
            }
        }
    }

    fun resolveUploadPath(
        template: String,
        submitResult: SubmitResult,
        title: String,
        extension: String,
    ): String {
        val variables = TemplateEngine.buildVariables(submitResult, title, extension)
        return TemplateEngine.render(template, variables)
    }

    fun resolveCommitMessage(
        template: String,
        submitResult: SubmitResult,
        title: String,
        extension: String,
    ): String {
        val variables = TemplateEngine.buildVariables(submitResult, title, extension)
        return TemplateEngine.render(template, variables)
    }

    private fun notifySuccess(project: Project, problemId: String, title: String, htmlUrl: String?) {
        val message = "[$problemId] $title 업로드 완료"
        notify(project, message, NotificationType.INFORMATION, htmlUrl)
    }

    private fun notifyError(project: Project, message: String) {
        notify(project, "GitHub 업로드 실패: $message", NotificationType.WARNING)
    }

    private fun notify(project: Project, content: String, type: NotificationType, browseUrl: String? = null) {
        ApplicationManager.getApplication().invokeLater {
            val notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("BOJ Helper")
                .createNotification(content, type)
            if (browseUrl != null) {
                notification.addAction(com.intellij.notification.BrowseNotificationAction("GitHub에서 보기", browseUrl))
            }
            notification.notify(project)
        }
    }
}
