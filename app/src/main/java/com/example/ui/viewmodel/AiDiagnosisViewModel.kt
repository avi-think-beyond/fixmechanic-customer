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

data class AiDiagnosisUiState(
    val queryInput: String = "",
    val isAnalyzing: Boolean = false,
    val diagnosisResult: AiDiagnosisResult? = null,
    val errorMessage: String? = null
)

class AiDiagnosisViewModel(
    private val aiDiagnosisRepository: AiDiagnosisRepository = ServiceLocator.aiDiagnosisRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiDiagnosisUiState())
    val uiState: StateFlow<AiDiagnosisUiState> = _uiState.asStateFlow()

    fun onQueryChanged(text: String) {
        _uiState.update { it.copy(queryInput = text, errorMessage = null) }
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

    fun clear() {
        _uiState.value = AiDiagnosisUiState()
    }
}
