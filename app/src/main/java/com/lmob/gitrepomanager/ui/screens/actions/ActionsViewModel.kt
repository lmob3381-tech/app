package com.lmob.gitrepomanager.ui.screens.actions

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
class ActionsViewModel @Inject constructor(
    private val repository: GitHubRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val owner: String = checkNotNull(savedStateHandle[Routes.ARG_OWNER])
    val repoName: String = checkNotNull(savedStateHandle[Routes.ARG_REPO])

    private val _uiState = MutableStateFlow(ActionsUiState())
    val uiState: StateFlow<ActionsUiState> = _uiState.asStateFlow()

    init {
        loadRuns(isInitial = true)
    }

    fun loadRuns(isInitial: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                if (isInitial) it.copy(isLoading = true, errorMessage = null)
                else it.copy(isRefreshing = true, errorMessage = null)
            }
            when (val result = repository.listWorkflowRuns(owner, repoName)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, runs = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = result.message)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onRefresh() = loadRuns(isInitial = false)
}
