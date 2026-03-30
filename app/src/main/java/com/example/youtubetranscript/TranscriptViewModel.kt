package com.example.youtubetranscript

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(
        val videoTitle: String?,
        val entries: List<TranscriptEntry>
    ) : UiState()
    data class Error(val message: String) : UiState()
}

class TranscriptViewModel : ViewModel() {

    private val repository = TranscriptRepository()

    private val _uiState = MutableLiveData<UiState>(UiState.Idle)
    val uiState: LiveData<UiState> = _uiState

    private var cachedTitle: String? = null
    private var cachedEntries: List<TranscriptEntry> = emptyList()

    fun fetchTranscript(input: String) {
        _uiState.value = UiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.fetchTranscript(input)

            val newState = when (result) {
                is TranscriptResult.Success -> {
                    cachedTitle = result.videoTitle
                    cachedEntries = result.entries
                    UiState.Success(result.videoTitle, result.entries)
                }
                is TranscriptResult.Error.InvalidVideoId ->
                    UiState.Error("Invalid YouTube URL or video ID.")
                is TranscriptResult.Error.NoCaptionsAvailable ->
                    UiState.Error("No captions available for this video.")
                is TranscriptResult.Error.NetworkError ->
                    UiState.Error("Network error. Please check your connection.")
                is TranscriptResult.Error.ParseError ->
                    UiState.Error("Failed to parse transcript: ${result.detail}")
            }

            _uiState.postValue(newState)
        }
    }

    fun getFormattedTranscript(): String? {
        if (cachedEntries.isEmpty()) return null
        return TranscriptParser.formatAsText(cachedTitle, cachedEntries)
    }
}
