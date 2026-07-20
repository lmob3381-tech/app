package com.lmob.gitrepomanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a file or directory entry from
 * GET /repos/{owner}/{repo}/contents/{path}
 */
@Serializable
data class GitHubContent(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long = 0,
    val url: String,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    val type: String, // "file" or "dir"
    val content: String? = null, // base64 encoded, only present when fetching a single file
    val encoding: String? = null
) {
    val isDirectory: Boolean get() = type == "dir"
    val isFile: Boolean get() = type == "file"
}

/**
 * Payload for creating/updating/deleting a file via
 * PUT/DELETE /repos/{owner}/{repo}/contents/{path}
 */
@Serializable
data class FileOperationRequest(
    val message: String,
    val content: String? = null, // base64, required for create/update, omitted for delete
    val sha: String? = null,     // required for update/delete
    val branch: String? = null
)

@Serializable
data class FileOperationResponse(
    val content: GitHubContent? = null,
    val commit: GitHubCommitRef
)

@Serializable
data class GitHubCommitRef(
    val sha: String,
    val url: String,
    val message: String? = null
)
