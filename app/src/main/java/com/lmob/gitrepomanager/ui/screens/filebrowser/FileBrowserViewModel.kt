package com.lmob.gitrepomanager.ui.screens.filebrowser

import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmob.gitrepomanager.data.model.GitHubContent
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
 * Handles browsing a repository's file tree one directory at a time
 * (GitHub's contents API is not recursive), and previewing text files
 * inline by base64-decoding the `content` field.
 *
 * Binary/large files show a friendly "cannot preview" message instead
 * of attempting to decode/display them.
 */
@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val repository: GitHubRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val owner: String = checkNotNull(savedStateHandle[Routes.ARG_OWNER])
    val repoName: String = checkNotNull(savedStateHandle[Routes.ARG_REPO])
    private val initialPath: String = Routes.decodeFileBrowserPath(
        savedStateHandle.get<String>(Routes.ARG_PATH).orEmpty()
    )

    private val _uiState = MutableStateFlow(FileBrowserUiState(currentPath = initialPath))
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    init {
        loadDirectory(initialPath)
    }

    fun loadDirectory(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, currentPath = path) }
            when (val result = repository.listContents(owner, repoName, path)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, entries = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onEntryClick(entry: GitHubContent) {
        if (entry.isDirectory) {
            loadDirectory(entry.path)
        } else {
            openFilePreview(entry)
        }
    }

    private fun openFilePreview(entry: GitHubContent) {
        _uiState.update {
            it.copy(
                previewFile = entry,
                isPreviewLoading = true,
                previewText = null,
                previewError = null
            )
        }

        // Guard against opening very large files (>1MB) that GitHub's
        // contents endpoint may reject or that would be unusable on-device.
        if (entry.size > MAX_PREVIEWABLE_BYTES) {
            _uiState.update {
                it.copy(
                    isPreviewLoading = false,
                    previewError = "Berkas terlalu besar untuk dilihat (${entry.size / 1024} KB)"
                )
            }
            return
        }

        viewModelScope.launch {
            when (val result = repository.getFileContent(owner, repoName, entry.path)) {
                is Resource.Success -> {
                    val decoded = decodeBase64Content(result.data)
                    if (decoded != null) {
                        _uiState.update { it.copy(isPreviewLoading = false, previewText = decoded) }
                    } else {
                        _uiState.update {
                            it.copy(
                                isPreviewLoading = false,
                                previewError = "Berkas ini bukan teks (kemungkinan biner) dan tidak dapat ditampilkan."
                            )
                        }
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isPreviewLoading = false, previewError = result.message)
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun decodeBase64Content(content: GitHubContent): String? {
        val raw = content.content ?: return null
        return try {
            val cleaned = raw.replace("\n", "")
            val bytes = Base64.decode(cleaned, Base64.DEFAULT)
            val text = bytes.toString(Charsets.UTF_8)
            // Heuristic: if decoding produced a lot of replacement/control
            // characters, treat it as binary rather than showing garbage.
            val suspiciousCharCount = text.count { it.code == 0xFFFD || (it.code < 32 && it != '\n' && it != '\r' && it != '\t') }
            if (text.isNotEmpty() && suspiciousCharCount.toDouble() / text.length > 0.05) {
                null
            } else {
                text
            }
        } catch (e: Exception) {
            null
        }
    }

    fun closePreview() {
        _uiState.update {
            it.copy(previewFile = null, previewText = null, previewError = null, isPreviewLoading = false)
        }
    }

    /** Returns the parent path for breadcrumb "up" navigation, or null if at root. */
    fun parentPath(): String? {
        val path = _uiState.value.currentPath
        if (path.isBlank()) return null
        val lastSlash = path.lastIndexOf('/')
        return if (lastSlash <= 0) "" else path.substring(0, lastSlash)
    }

    companion object {
        private const val MAX_PREVIEWABLE_BYTES = 1_000_000
    }
}
