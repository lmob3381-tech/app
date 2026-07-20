package com.lmob.gitrepomanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single repository returned by GitHub's REST API
 * GET /user/repos or GET /users/{username}/repos
 */
@Serializable
data class GitHubRepo(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val private: Boolean,
    val owner: GitHubOwner,
    @SerialName("html_url") val htmlUrl: String,
    val description: String? = null,
    val fork: Boolean = false,
    val url: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("pushed_at") val pushedAt: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int = 0,
    @SerialName("watchers_count") val watchersCount: Int = 0,
    val language: String? = null,
    @SerialName("forks_count") val forksCount: Int = 0,
    @SerialName("open_issues_count") val openIssuesCount: Int = 0,
    @SerialName("default_branch") val defaultBranch: String = "main",
    val archived: Boolean = false,
    val disabled: Boolean = false,
    val visibility: String = "public",
    val size: Long = 0
)

@Serializable
data class GitHubOwner(
    val login: String,
    val id: Long,
    @SerialName("avatar_url") val avatarUrl: String,
    @SerialName("html_url") val htmlUrl: String,
    val type: String = "User"
)
