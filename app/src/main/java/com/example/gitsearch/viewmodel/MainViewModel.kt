package com.example.gitsearch.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gitsearch.data.GitHubRepository
import com.example.gitsearch.data.Repository
import com.example.gitsearch.data.UserDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: GitHubRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> get() = _state

    fun fetchRepositories(username: String) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            val result = repository.getUserRepositories(username)
            _state.value = when {
                result.isSuccess -> UiState.Success(result.getOrNull()!!)
                result.isFailure -> UiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
                else -> UiState.Error("An unexpected error occurred.")
            }
        }
    }
    private val _searchQuery = MutableStateFlow("")
    val searchResults = MutableStateFlow<List<UserDto>>(emptyList())

    // New: Error message state exposed to UI
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        _errorMessage.value = null // clear errors on blank query
                        flowOf(emptyList())
                    } else {
                        flow {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    repository.searchUsers(query)
                                }

                                result.fold(
                                    onSuccess = { users ->
                                        _errorMessage.value = null // clear error on success
                                        emit(users)
                                    },
                                    onFailure = { throwable ->
                                        // Set error message to the error text (handle rate limit, etc)
                                        _errorMessage.value = throwable.message ?: "Unknown error"
                                        emit(emptyList()) // emit empty list on failure
                                    }
                                )
                            } catch (e: CancellationException) {

                                throw e
                            } catch (e: Exception) {
                                _errorMessage.value = e.message ?: "Unexpected error"
                                emit(emptyList())
                            }
                        }
                    }
                }
                .collect { results ->
                    searchResults.value = results
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }


    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val repos: List<Repository>) : UiState()
        data class Error(val message: String) : UiState()
    }

}

