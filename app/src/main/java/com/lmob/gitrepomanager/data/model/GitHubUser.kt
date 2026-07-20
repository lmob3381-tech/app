package com.lmob.gitrepomanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the authenticated user, from GET /user
 */
@Serializable
data class GitHubUser(
    val login: String,
    val id: Long,
    @SerialName("avatar_url") val avatarUrl: String,
    val name: String? = null,
    val bio: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("public_repos") val publicRepos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    val company: String? = null,
    val location: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)
