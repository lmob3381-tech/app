package com.lmob.gitrepomanager.ui.screens.repolist

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
class RepoListViewModel @Inject constructor(
    private val repository: GitHubRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepoListUiState())
    val uiState: StateFlow<RepoListUiState> = _uiState.asStateFlow()

    init {
        loadAll(isInitial = true)
    }

    fun loadAll(isInitial: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                if (isInitial) it.copy(isLoading = true, errorMessage = null)
                else it.copy(isRefreshing = true, errorMessage = null)
            }

            val userResult = repository.validateCurrentSessionUser()
            val reposResult = repository.listRepos()

            _uiState.update { current ->
                var next = current.copy(isLoading = false, isRefreshing = false)
                if (userResult is Resource.Success) {
                    next = next.copy(user = userResult.data)
                }
                next = when (reposResult) {
                    is Resource.Success -> next.copy(allRepos = reposResult.data, errorMessage = null)
                    is Resource.Error -> next.copy(errorMessage = reposResult.message)
                    Resource.Loading -> next
                }
                next
            }
        }
    }

    fun onRefresh() = loadAll(isInitial = false)

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSortOptionChanged(option: RepoSortOption) {
        _uiState.update { it.copy(sortOption = option) }
    }

    fun logout() {
        repository.logout()
    }
}
