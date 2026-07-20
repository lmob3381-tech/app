package com.lmob.gitrepomanager.ui.screens.repodetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmob.gitrepomanager.data.repository.GitHubRepository
import com.lmob.gitrepomanager.ui.navigation.Routes
import com.lmob.gitrepomanager.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RepoDetailViewModel @Inject constructor(
    private val repository: GitHubRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val owner: String = checkNotNull(savedStateHandle[Routes.ARG_OWNER])
    val repoName: String = checkNotNull(savedStateHandle[Routes.ARG_REPO])

    private val _uiState = MutableStateFlow(RepoDetailUiState())
    val uiState: StateFlow<RepoDetailUiState> = _uiState.asStateFlow()

    init {
        loadRepo()
    }

    fun loadRepo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.getRepo(owner, repoName)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, repo = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onTabSelected(tab: RepoDetailTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }
}
