package com.lmob.repomanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lmob.repomanager.data.*
import com.lmob.repomanager.network.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppUiState(
    val isLoggedIn: Boolean = false,
    val isCheckingSession: Boolean = true,
    val loginError: String? = null,
    val isLoggingIn: Boolean = false,

    val user: GhUser? = null,
    val repos: List<GhRepo> = emptyList(),
    val isLoadingRepos: Boolean = false,
    val reposError: String? = null,

    val selectedRepo: GhRepo? = null,
    val contents: List<GhContentItem> = emptyList(),
    val currentPath: String = "",
    val isLoadingContents: Boolean = false,
    val contentsError: String? = null,

    val workflowRuns: List<GhWorkflowRun> = emptyList(),
    val isLoadingRuns: Boolean = false,
    val runsError: String? = null,

    val isCleaningRepo: Boolean = false,
    val cleanProgressLog: List<String> = emptyList(),
    val cleanDone: Boolean = false,

    val isCreatingRepo: Boolean = false,
    val createRepoError: String? = null,

    val isDeletingRepo: Boolean = false,
    val deleteRepoError: String? = null,

    val snackbarMessage: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = TokenStore(application)
    private var repository: GitHubRepository = buildRepository()

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        checkExistingSession()
    }

    private fun buildRepository(): GitHubRepository {
        val api = ApiClient.create { tokenStore.getToken() }
        return GitHubRepository(api)
    }

    private fun checkExistingSession() {
        val token = tokenStore.getToken()
        if (token.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(isCheckingSession = false)
            return
        }
        viewModelScope.launch {
            when (val result = repository.verifyTokenAndGetUser()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoggedIn = true,
                        isCheckingSession = false,
                        user = result.data
                    )
                    loadRepos()
                }
                is Result.Error -> {
                    tokenStore.clear()
                    _uiState.value = _uiState.value.copy(isCheckingSession = false)
                }
            }
        }
    }

    fun login(token: String) {
        if (token.isBlank()) {
            _uiState.value = _uiState.value.copy(loginError = "Token tidak boleh kosong.")
            return
        }
        _uiState.value = _uiState.value.copy(isLoggingIn = true, loginError = null)
        viewModelScope.launch {
            // temporarily use a repository bound to the entered token before saving
            val tempApi = ApiClient.create { token }
            val tempRepo = GitHubRepository(tempApi)
            when (val result = tempRepo.verifyTokenAndGetUser()) {
                is Result.Success -> {
                    tokenStore.saveSession(token, result.data.login)
                    repository = buildRepository()
                    _uiState.value = _uiState.value.copy(
                        isLoggedIn = true,
                        isLoggingIn = false,
                        user = result.data,
                        loginError = null
                    )
                    loadRepos()
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoggingIn = false,
                        loginError = result.message
                    )
                }
            }
        }
    }

    fun logout() {
        tokenStore.clear()
        _uiState.value = AppUiState(isCheckingSession = false)
    }

    fun loadRepos() {
        _uiState.value = _uiState.value.copy(isLoadingRepos = true, reposError = null)
        viewModelScope.launch {
            when (val result = repository.listRepos()) {
                is Result.Success -> _uiState.value = _uiState.value.copy(
                    isLoadingRepos = false,
                    repos = result.data
                )
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isLoadingRepos = false,
                    reposError = result.message
                )
            }
        }
    }

    fun selectRepo(repo: GhRepo) {
        _uiState.value = _uiState.value.copy(
            selectedRepo = repo,
            contents = emptyList(),
            currentPath = "",
            workflowRuns = emptyList(),
            cleanProgressLog = emptyList(),
            cleanDone = false
        )
        loadContents("")
        loadWorkflowRuns()
    }

    fun clearSelectedRepo() {
        _uiState.value = _uiState.value.copy(selectedRepo = null)
    }

    fun loadContents(path: String) {
        val repo = _uiState.value.selectedRepo ?: return
        val owner = repo.owner?.login ?: return
        _uiState.value = _uiState.value.copy(isLoadingContents = true, contentsError = null, currentPath = path)
        viewModelScope.launch {
            when (val result = repository.getContents(owner, repo.name, path)) {
                is Result.Success -> _uiState.value = _uiState.value.copy(
                    isLoadingContents = false,
                    contents = result.data
                )
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isLoadingContents = false,
                    contentsError = result.message,
                    contents = emptyList()
                )
            }
        }
    }

    fun loadWorkflowRuns() {
        val repo = _uiState.value.selectedRepo ?: return
        val owner = repo.owner?.login ?: return
        _uiState.value = _uiState.value.copy(isLoadingRuns = true, runsError = null)
        viewModelScope.launch {
            when (val result = repository.getWorkflowRuns(owner, repo.name)) {
                is Result.Success -> _uiState.value = _uiState.value.copy(
                    isLoadingRuns = false,
                    workflowRuns = result.data
                )
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isLoadingRuns = false,
                    runsError = result.message
                )
            }
        }
    }

    fun cleanSelectedRepo() {
        val repo = _uiState.value.selectedRepo ?: return
        val owner = repo.owner?.login ?: return
        val branch = repo.defaultBranch ?: "main"
        _uiState.value = _uiState.value.copy(isCleaningRepo = true, cleanProgressLog = emptyList(), cleanDone = false)
        viewModelScope.launch {
            when (val result = repository.cleanRepo(owner, repo.name, branch) { line ->
                _uiState.value = _uiState.value.copy(cleanProgressLog = _uiState.value.cleanProgressLog + line)
            }) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isCleaningRepo = false,
                        cleanDone = true,
                        cleanProgressLog = _uiState.value.cleanProgressLog + "✅ Selesai. ${result.data} file dihapus.",
                        snackbarMessage = "Repo '${repo.name}' berhasil dikosongkan"
                    )
                    loadContents("")
                }
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isCleaningRepo = false,
                    cleanProgressLog = _uiState.value.cleanProgressLog + "❌ ${result.message}"
                )
            }
        }
    }

    fun createRepo(name: String, description: String, isPrivate: Boolean, onSuccess: () -> Unit) {
        _uiState.value = _uiState.value.copy(isCreatingRepo = true, createRepoError = null)
        viewModelScope.launch {
            when (val result = repository.createRepo(name, description.ifBlank { null }, isPrivate)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isCreatingRepo = false,
                        snackbarMessage = "Repo '${result.data.name}' berhasil dibuat"
                    )
                    loadRepos()
                    onSuccess()
                }
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isCreatingRepo = false,
                    createRepoError = result.message
                )
            }
        }
    }

    fun deleteRepo(repo: GhRepo, onSuccess: () -> Unit) {
        val owner = repo.owner?.login ?: return
        _uiState.value = _uiState.value.copy(isDeletingRepo = true, deleteRepoError = null)
        viewModelScope.launch {
            when (val result = repository.deleteRepo(owner, repo.name)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isDeletingRepo = false,
                        selectedRepo = null,
                        snackbarMessage = "Repo '${repo.name}' berhasil dihapus"
                    )
                    loadRepos()
                    onSuccess()
                }
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isDeletingRepo = false,
                    deleteRepoError = result.message
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    fun clearLoginError() {
        _uiState.value = _uiState.value.copy(loginError = null)
    }
}
