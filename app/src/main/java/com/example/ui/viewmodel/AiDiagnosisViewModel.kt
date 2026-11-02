package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.VehicleType
import com.example.data.repository.AiDiagnosisRepository
import com.example.data.repository.AiDiagnosisResult
import com.example.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiChatMessage(
    val sender: String, // "user" or "ai"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiDiagnosisUiState(
    val queryInput: String = "",
    val assistantQuestionInput: String = "",
    val isAnalyzing: Boolean = false,
    val isAssistantThinking: Boolean = false,
    val diagnosisResult: AiDiagnosisResult? = null,
    val chatMessages: List<AiChatMessage> = emptyList(),
    val errorMessage: String? = null
)

class AiDiagnosisViewModel(
    private val aiDiagnosisRepository: AiDiagnosisRepository = ServiceLocator.aiDiagnosisRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiDiagnosisUiState(
            chatMessages = listOf(
                AiChatMessage(
                    sender = "ai",
                    text = "Hello! I am your FixMechanic AI Assistant. Ask me any question about vehicle breakdown safety, jumpstarting, tyre puncture fixes, or mechanical trouble."
                )
            )
        )
    )
    val uiState: StateFlow<AiDiagnosisUiState> = _uiState.asStateFlow()

    fun onQueryChanged(text: String) {
        _uiState.update { it.copy(queryInput = text, errorMessage = null) }
    }

    fun onAssistantQuestionChanged(text: String) {
        _uiState.update { it.copy(assistantQuestionInput = text, errorMessage = null) }
    }

    fun diagnoseProblem(vehicleType: VehicleType) {
        val query = _uiState.value.queryInput.trim()
        if (query.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter what happened or what noise you heard.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, errorMessage = null) }
            val result = aiDiagnosisRepository.diagnoseIssue(query, vehicleType)
            result.onSuccess { diag ->
                _uiState.update { it.copy(isAnalyzing = false, diagnosisResult = diag) }
            }.onFailure { err ->
                _uiState.update { it.copy(isAnalyzing = false, errorMessage = err.message ?: "Could not classify symptom") }
            }
        }
    }

    fun askAssistant(vehicleType: VehicleType) {
        val question = _uiState.value.assistantQuestionInput.trim()
        if (question.isEmpty()) return

        val userMsg = AiChatMessage(sender = "user", text = question)
        val currentList = _uiState.value.chatMessages + userMsg

        _uiState.update {
            it.copy(
                assistantQuestionInput = "",
                chatMessages = currentList,
                isAssistantThinking = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val result = aiDiagnosisRepository.askAssistant(question, vehicleType)
            result.onSuccess { answer ->
                val aiMsg = AiChatMessage(sender = "ai", text = answer)
                _uiState.update {
                    it.copy(
                        isAssistantThinking = false,
                        chatMessages = it.chatMessages + aiMsg
                    )
                }
            }.onFailure { err ->
                val errorMsg = AiChatMessage(
                    sender = "ai",
                    text = "⚠️ Roadside advice: Please keep vehicle parked safely behind highway barrier. Dispatched mechanics can assess your vehicle."
                )
                _uiState.update {
                    it.copy(
                        isAssistantThinking = false,
                        chatMessages = it.chatMessages + errorMsg
                    )
                }
            }
        }
    }

    fun clear() {
        _uiState.value = AiDiagnosisUiState(
            chatMessages = listOf(
                AiChatMessage(
                    sender = "ai",
                    text = "Hello! I am your FixMechanic AI Assistant. Ask me any question about vehicle breakdown safety, jumpstarting, tyre puncture fixes, or mechanical trouble."
                )
            )
        )
    }
}
