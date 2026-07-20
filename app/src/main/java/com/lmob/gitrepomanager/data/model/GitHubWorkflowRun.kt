package com.lmob.gitrepomanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response wrapper for GET /repos/{owner}/{repo}/actions/runs
 */
@Serializable
data class WorkflowRunsResponse(
    @SerialName("total_count") val totalCount: Int,
    @SerialName("workflow_runs") val workflowRuns: List<GitHubWorkflowRun>
)

@Serializable
data class GitHubWorkflowRun(
    val id: Long,
    val name: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    val status: String, // queued, in_progress, completed
    val conclusion: String? = null, // success, failure, cancelled, skipped, null
    @SerialName("head_branch") val headBranch: String? = null,
    @SerialName("head_sha") val headSha: String,
    @SerialName("event") val event: String,
    @SerialName("run_number") val runNumber: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("html_url") val htmlUrl: String,
    val actor: GitHubOwner? = null
)
