package com.lmob.repomanager.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmob.repomanager.ui.screens.CreateRepoScreen
import com.lmob.repomanager.ui.screens.LoginScreen
import com.lmob.repomanager.ui.screens.RepoDetailScreen
import com.lmob.repomanager.ui.screens.RepoListScreen
import kotlinx.coroutines.launch

private enum class Screen { LIST, DETAIL, CREATE }

@Composable
fun RepoManagerApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var screen by remember { mutableStateOf(Screen.LIST) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    viewModel.importZip(bytes)
                } else {
                    snackbarHostState.showSnackbar("Gagal membaca file ZIP yang dipilih")
                }
            }
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearSnackbar()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        when {
            state.isCheckingSession -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            !state.isLoggedIn -> {
                LoginScreen(
                    isLoading = state.isLoggingIn,
                    error = state.loginError,
                    onLogin = { token -> viewModel.login(token) },
                    onDismissError = { viewModel.clearLoginError() }
                )
            }

            screen == Screen.CREATE -> {
                CreateRepoScreen(
                    isCreating = state.isCreatingRepo,
                    error = state.createRepoError,
                    onCreate = { name, desc, priv ->
                        viewModel.createRepo(name, desc, priv) { screen = Screen.LIST }
                    },
                    onBack = { screen = Screen.LIST }
                )
            }

            screen == Screen.DETAIL && state.selectedRepo != null -> {
                RepoDetailScreen(
                    repo = state.selectedRepo!!,
                    contents = state.contents,
                    currentPath = state.currentPath,
                    isLoadingContents = state.isLoadingContents,
                    contentsError = state.contentsError,
                    workflowRuns = state.workflowRuns,
                    isLoadingRuns = state.isLoadingRuns,
                    runsError = state.runsError,
                    isCleaningRepo = state.isCleaningRepo,
                    cleanProgressLog = state.cleanProgressLog,
                    cleanDone = state.cleanDone,
                    isDeletingRepo = state.isDeletingRepo,
                    selectedRun = state.selectedRun,
                    runJobs = state.runJobs,
                    isLoadingJobs = state.isLoadingJobs,
                    jobsError = state.jobsError,
                    selectedJobLog = state.selectedJobLog,
                    isLoadingJobLog = state.isLoadingJobLog,
                    jobLogError = state.jobLogError,
                    isCreatingFileOrFolder = state.isCreatingFileOrFolder,
                    createFileError = state.createFileError,
                    isMoving = state.isMoving,
                    moveError = state.moveError,
                    isImportingZip = state.isImportingZip,
                    zipImportProgressLog = state.zipImportProgressLog,
                    zipImportDone = state.zipImportDone,
                    zipImportError = state.zipImportError,
                    onBack = {
                        viewModel.clearSelectedRepo()
                        screen = Screen.LIST
                    },
                    onNavigate = { path -> viewModel.loadContents(path) },
                    onRefreshRuns = { viewModel.loadWorkflowRuns() },
                    onCleanRepo = { viewModel.cleanSelectedRepo() },
                    onDeleteRepo = {
                        viewModel.deleteRepo(state.selectedRepo!!) { screen = Screen.LIST }
                    },
                    onOpenRun = { run -> viewModel.openRunDetail(run) },
                    onSelectJob = { jobId -> viewModel.loadJobLog(jobId) },
                    onCloseRunDetail = { viewModel.closeRunDetail() },
                    onCreateFile = { name, content -> viewModel.createFile(name, content) },
                    onCreateFolder = { name -> viewModel.createFolder(name) },
                    onClearCreateFileError = { viewModel.clearCreateFileError() },
                    onMoveItem = { item, destination -> viewModel.moveItem(item, destination) },
                    onClearMoveError = { viewModel.clearMoveError() },
                    onPickZipToImport = { zipPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                    onDismissZipImportDialog = { viewModel.dismissZipImportDialog() }
                )
            }

            else -> {
                RepoListScreen(
                    user = state.user,
                    repos = state.repos,
                    isLoading = state.isLoadingRepos,
                    error = state.reposError,
                    onRefresh = { viewModel.loadRepos() },
                    onRepoClick = { repo ->
                        viewModel.selectRepo(repo)
                        screen = Screen.DETAIL
                    },
                    onCreateClick = { screen = Screen.CREATE },
                    onLogout = { viewModel.logout() }
                )
            }
        }
    }
}
