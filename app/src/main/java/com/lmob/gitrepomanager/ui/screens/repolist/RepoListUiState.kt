package com.lmob.gitrepomanager.ui.screens.repolist

import com.lmob.gitrepomanager.data.model.GitHubRepo
import com.lmob.gitrepomanager.data.model.GitHubUser

enum class RepoSortOption {
    UPDATED, NAME, STARS
}

data class RepoListUiState(
    val user: GitHubUser? = null,
    val allRepos: List<GitHubRepo> = emptyList(),
    val searchQuery: String = "",
    val sortOption: RepoSortOption = RepoSortOption.UPDATED,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
) {
    val filteredRepos: List<GitHubRepo>
        get() {
            val filtered = if (searchQuery.isBlank()) {
                allRepos
            } else {
                allRepos.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                        it.description?.contains(searchQuery, ignoreCase = true) == true
                }
            }
            return when (sortOption) {
                RepoSortOption.UPDATED -> filtered.sortedByDescending { it.updatedAt }
                RepoSortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
                RepoSortOption.STARS -> filtered.sortedByDescending { it.stargazersCount }
            }
        }
}
