package com.lmob.gitrepomanager.ui.screens.repodetail

import com.lmob.gitrepomanager.data.model.GitHubRepo

enum class RepoDetailTab {
    FILES, ACTIONS, ABOUT
}

data class RepoDetailUiState(
    val repo: GitHubRepo? = null,
    val selectedTab: RepoDetailTab = RepoDetailTab.FILES,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
