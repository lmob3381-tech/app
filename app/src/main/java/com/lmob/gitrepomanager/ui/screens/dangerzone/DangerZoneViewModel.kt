package com.lmob.gitrepomanager.ui.screens.dangerzone

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

/**
 * Mirrors bersihkan-repo.sh: deletes every file in the repo's default
 * branch, one commit at a time via the GitHub Contents API, after the
 * user explicitly types the repo name to confirm (safer than the
 * original script's zero-friction execution).
 */
@HiltViewModel
class DangerZoneViewModel @Inject constructor(
    private val repository: GitHubRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val owner: String = checkNotNull(savedStateHandle[Routes.ARG_OWNER])
    val repoName: String = checkNotNull(savedStateHandle[Routes.ARG_REPO])
    val branch: String = checkNotNull(savedStateHandle[Routes.ARG_BRANCH])

    private val _uiState = MutableStateFlow(DangerZoneUiState(repoName = repoName))
    val uiState: StateFlow<DangerZoneUiState> = _uiState.asStateFlow()

    fun onStartConfirming() {
        _uiState.update { it.copy(step = EmptyRepoStep.CONFIRMING, confirmTextInput = "") }
    }

    fun onCancelConfirming() {
        _uiState.update { it.copy(step = EmptyRepoStep.IDLE, confirmTextInput = "") }
    }

    fun onConfirmTextChanged(value: String) {
        _uiState.update { it.copy(confirmTextInput = value) }
    }

    fun onConfirmExecute() {
        if (!_uiState.value.isConfirmTextMatching) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    step = EmptyRepoStep.IN_PROGRESS,
                    progressCurrent = 0,
                    progressTotal = 0,
                    errorMessage = null
                )
            }

            val result = repository.emptyRepository(
                owner = owner,
                repo = repoName,
                branch = branch,
                onProgress = { current, total, fileName ->
                    _uiState.update {
                        it.copy(
                            progressCurrent = current,
                            progressTotal = total,
                            progressCurrentFileName = fileName
                        )
                    }
                }
            )

            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(step = EmptyRepoStep.DONE, deletedCount = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(step = EmptyRepoStep.FAILED, errorMessage = result.message)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onReset() {
        _uiState.update {
            DangerZoneUiState(repoName = repoName)
        }
    }
}
