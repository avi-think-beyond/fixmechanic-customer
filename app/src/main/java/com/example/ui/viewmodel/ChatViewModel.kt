package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.JobStatus
import com.example.data.repository.ChatRepository
import com.example.data.repository.EmergencyRepository
import com.example.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputMessage: String = "",
    val isSending: Boolean = false,
    val isSessionClosed: Boolean = false,
    val errorMessage: String? = null,
    val quickReplies: List<String> = listOf(
        "I'm near the emergency hazard cones.",
        "Car hazard lights are turned on.",
        "Please bring battery jump cables.",
        "I have a spare wheel ready in the boot.",
        "Call me via masked line if you can't spot me."
    )
)

class ChatViewModel(
    private val chatRepository: ChatRepository = ServiceLocator.chatRepository,
    private val emergencyRepository: EmergencyRepository = ServiceLocator.emergencyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentJobId: String = "active_job"

    init {
        // Observe active request to track if job status becomes terminal
        viewModelScope.launch {
            emergencyRepository.activeRequest.collect { req ->
                val status = req?.status
                val isClosed = status?.isTerminal ?: false
                _uiState.update { it.copy(isSessionClosed = isClosed) }
            }
        }
    }

    fun loadChat(jobId: String, jobStatus: JobStatus? = null) {
        currentJobId = jobId
        val isClosed = jobStatus?.isTerminal ?: false
        _uiState.update { it.copy(isSessionClosed = isClosed) }

        viewModelScope.launch {
            chatRepository.observeMessages(jobId).collect { msgList ->
                _uiState.update { it.copy(messages = msgList) }
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputMessage = text, errorMessage = null) }
    }

    fun sendMessage(text: String? = null) {
        val messageToSend = (text ?: _uiState.value.inputMessage).trim()
        if (messageToSend.isEmpty()) return

        if (_uiState.value.isSessionClosed) {
            _uiState.update { it.copy(errorMessage = "Communication session has closed. New messages cannot be sent.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, errorMessage = null) }
            val currentStatus = emergencyRepository.activeRequest.value?.status
            val result = chatRepository.sendMessage(
                jobId = currentJobId,
                text = messageToSend,
                isCustomer = true,
                jobStatus = currentStatus
            )
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        inputMessage = "",
                        isSending = false,
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = err.message ?: "Failed to send message"
                    )
                }
            }
        }
    }
}
