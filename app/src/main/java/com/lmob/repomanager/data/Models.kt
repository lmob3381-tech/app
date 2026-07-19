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

// ---- Job logs (Actions detail) ----

data class GhJobsResponse(
    @SerializedName("total_count") val totalCount: Int,
    val jobs: List<GhJob>
)

data class GhJob(
    val id: Long,
    val name: String,
    val status: String?,
    val conclusion: String?,
    @SerializedName("started_at") val startedAt: String?,
    @SerializedName("completed_at") val completedAt: String?,
    val steps: List<GhJobStep>?
)

data class GhJobStep(
    val name: String,
    val status: String?,
    val conclusion: String?,
    val number: Int
)

// ---- Create / update file content ----

data class PutFileRequest(
    val message: String,
    val content: String,
    val branch: String? = null,
    val sha: String? = null
)

data class PutFileResponse(
    val content: GhContentItem?
)

// ---- Blob/tree/commit (used for move + zip import in one commit) ----

data class GhBlobRequest(
    val content: String,
    val encoding: String = "base64"
)

data class GhBlobResponse(val sha: String)

data class GhNewTreeItem(
    val path: String,
    val mode: String = "100644",
    val type: String = "blob",
    val sha: String? = null,
    val content: String? = null
)

/**
 * Versi khusus untuk menghapus file lewat Git Trees API. Tidak punya field `content`
 * sama sekali (bukan cuma null) supaya tidak pernah bentrok dengan validasi GitHub
 * "Must supply either tree.sha or tree.content" jika suatu saat null tetap ikut ter-serialize.
 */
data class GhDeleteTreeItem(
    val path: String,
    val mode: String,
    val type: String = "blob",
    val sha: String? = null
)

data class GhNewTreeRequest(
    @SerializedName("base_tree") val baseTree: String?,
    val tree: List<GhNewTreeItem>
)

data class GhDeleteTreeRequest(
    @SerializedName("base_tree") val baseTree: String?,
    val tree: List<GhDeleteTreeItem>
)

data class GhNewCommitRequest(
    val message: String,
    val tree: String,
    val parents: List<String>
)

data class GhCommitResponse(val sha: String)

data class GhUpdateRefRequest(
    val sha: String,
    val force: Boolean = false
)
