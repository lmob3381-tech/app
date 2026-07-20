package com.lmob.gitrepomanager.ui.screens.filebrowser

import com.lmob.gitrepomanager.data.model.GitHubContent

data class FileBrowserUiState(
    val currentPath: String = "",
    val entries: List<GitHubContent> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // File preview state (when a file, not a dir, is tapped)
    val previewFile: GitHubContent? = null,
    val previewText: String? = null,
    val isPreviewLoading: Boolean = false,
    val previewError: String? = null
) {
    val isPreviewOpen: Boolean get() = previewFile != null
}
