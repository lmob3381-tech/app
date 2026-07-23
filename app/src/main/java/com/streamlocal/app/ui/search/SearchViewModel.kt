package com.streamlocal.app.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.streamlocal.app.data.ApiResult
import com.streamlocal.app.data.SearchItem
import com.streamlocal.app.data.StreamLocalRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(val items: List<SearchItem>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StreamLocalRepository(application)
    val uiState = MutableLiveData<SearchUiState>(SearchUiState.Idle)

    private var searchJob: Job? = null
    var currentType: String = "video"
        private set
    var currentQuery: String = ""
        private set

    fun setType(type: String) {
        currentType = type
        if (currentQuery.isNotBlank()) search(currentQuery)
    }

    fun search(query: String) {
        currentQuery = query
        searchJob?.cancel()
        if (query.isBlank()) {
            uiState.value = SearchUiState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            uiState.value = SearchUiState.Loading
            when (val result = repository.search(query, currentType, 20)) {
                is ApiResult.Success -> uiState.value = SearchUiState.Success(result.data.results)
                is ApiResult.Failure -> uiState.value = SearchUiState.Error(result.message)
            }
        }
    }
}
