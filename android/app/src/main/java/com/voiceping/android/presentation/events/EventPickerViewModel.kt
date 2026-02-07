package com.voiceping.android.presentation.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceping.android.data.storage.PreferencesManager
import com.voiceping.android.domain.model.Event
import com.voiceping.android.domain.usecase.GetEventsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class EventPickerUiState {
    object Loading : EventPickerUiState()
    data class Success(val events: List<Event>) : EventPickerUiState()
    data class Error(val message: String) : EventPickerUiState()
    object Empty : EventPickerUiState()
}

@HiltViewModel
class EventPickerViewModel @Inject constructor(
    private val getEventsUseCase: GetEventsUseCase,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<EventPickerUiState>(EventPickerUiState.Loading)
    val uiState: StateFlow<EventPickerUiState> = _uiState.asStateFlow()

    private val _selectedEvent = MutableStateFlow<Event?>(null)
    val selectedEvent: StateFlow<Event?> = _selectedEvent.asStateFlow()

    init {
        loadEvents()
    }

    fun loadEvents() {
        viewModelScope.launch {
            _uiState.value = EventPickerUiState.Loading

            val result = getEventsUseCase()

            _uiState.value = when {
                result.isSuccess -> {
                    val events = result.getOrNull()!!
                    if (events.isEmpty()) {
                        EventPickerUiState.Empty
                    } else {
                        EventPickerUiState.Success(events)
                    }
                }
                result.isFailure -> {
                    val error = result.exceptionOrNull()?.message ?: "Failed to load events"
                    EventPickerUiState.Error(error)
                }
                else -> EventPickerUiState.Error("Unknown error")
            }
        }
    }

    fun selectEvent(event: Event) {
        _selectedEvent.value = event
        preferencesManager.saveLastEventId(event.id)
    }
}
