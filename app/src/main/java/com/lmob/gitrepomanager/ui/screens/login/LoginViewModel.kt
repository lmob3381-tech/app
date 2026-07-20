package com.lmob.gitrepomanager.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmob.gitrepomanager.data.repository.GitHubRepository
import com.lmob.gitrepomanager.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: GitHubRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onTokenInputChanged(value: String) {
        _uiState.update { it.copy(tokenInput = value.trim(), errorMessage = null) }
    }

    fun onToggleTokenVisibility() {
        _uiState.update { it.copy(isTokenVisible = !it.isTokenVisible) }
    }

    fun onSubmit() {
        val token = _uiState.value.tokenInput
        if (token.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Token tidak boleh kosong") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.validateTokenAndFetchUser(token)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, loginSuccessUser = result.data)
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
