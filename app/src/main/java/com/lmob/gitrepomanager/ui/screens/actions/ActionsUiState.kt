package com.lmob.gitrepomanager.ui.screens.actions

import com.lmob.gitrepomanager.data.model.GitHubWorkflowRun

data class ActionsUiState(
    val runs: List<GitHubWorkflowRun> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)
