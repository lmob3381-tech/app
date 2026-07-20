package com.lmob.gitrepomanager.ui.screens.settings

import com.lmob.gitrepomanager.data.model.GitHubUser

data class SettingsUiState(
    val user: GitHubUser? = null,
    val isLoading: Boolean = false,
    val didLogout: Boolean = false
)
