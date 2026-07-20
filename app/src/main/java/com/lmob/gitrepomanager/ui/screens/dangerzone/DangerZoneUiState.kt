package com.lmob.gitrepomanager.ui.screens.dangerzone

enum class EmptyRepoStep {
    IDLE, CONFIRMING, IN_PROGRESS, DONE, FAILED
}

data class DangerZoneUiState(
    val repoName: String = "",
    val step: EmptyRepoStep = EmptyRepoStep.IDLE,
    val confirmTextInput: String = "",
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val progressCurrentFileName: String = "",
    val deletedCount: Int = 0,
    val errorMessage: String? = null
) {
    val isConfirmTextMatching: Boolean get() = confirmTextInput == repoName
}
