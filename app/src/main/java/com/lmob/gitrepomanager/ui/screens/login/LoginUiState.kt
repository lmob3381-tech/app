package com.lmob.gitrepomanager.ui.screens.login

import com.lmob.gitrepomanager.data.model.GitHubUser

data class LoginUiState(
    val tokenInput: String = "",
    val isTokenVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loginSuccessUser: GitHubUser? = null
) {
    val isLoginSuccessful: Boolean get() = loginSuccessUser != null
}
