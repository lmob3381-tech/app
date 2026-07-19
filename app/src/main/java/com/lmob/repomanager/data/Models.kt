package com.lmob.repomanager.data

import com.google.gson.annotations.SerializedName

data class GhUser(
    val login: String,
    val name: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("public_repos") val publicRepos: Int?
)

data class GhRepo(
    val id: Long,
    val name: String,
    @SerializedName("full_name") val fullName: String,
    val private: Boolean,
    @SerializedName("html_url") val htmlUrl: String,
    val description: String?,
    val fork: Boolean,
    @SerializedName("default_branch") val defaultBranch: String?,
    @SerializedName("stargazers_count") val stars: Int?,
    @SerializedName("updated_at") val updatedAt: String?,
    val owner: GhUser?
)

data class GhContentItem(
    val name: String,
    val path: String,
    val type: String, // "file" or "dir"
    val size: Long?,
    val sha: String?
)

data class GhWorkflowRunsResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("workflow_runs") val workflowRuns: List<GhWorkflowRun>
)

data class GhWorkflowRun(
    val id: Long,
    val name: String?,
    @SerializedName("display_title") val displayTitle: String?,
    val status: String?, // queued, in_progress, completed
    val conclusion: String?, // success, failure, cancelled, null
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("head_branch") val headBranch: String?
)

data class GhTreeResponse(
    val sha: String,
    val tree: List<GhTreeItem>,
    val truncated: Boolean
)

data class GhTreeItem(
    val path: String,
    val mode: String,
    val type: String, // "blob" or "tree"
    val sha: String,
    val size: Long?
)

data class GhRefResponse(
    val ref: String,
    val `object`: GhRefObject
)

data class GhRefObject(
    val sha: String,
    val type: String
)

data class CreateRepoRequest(
    val name: String,
    val description: String? = null,
    val private: Boolean = false,
    @SerializedName("auto_init") val autoInit: Boolean = true
)

data class DeleteFileRequest(
    val message: String,
    val sha: String,
    val branch: String? = null
)

data class GhApiError(
    val message: String?
)
