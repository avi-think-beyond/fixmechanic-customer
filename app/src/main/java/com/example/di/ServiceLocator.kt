package com.example.di

import com.example.data.repository.AiDiagnosisRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.BackendStatusRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.DefaultBackendStatusRepository
import com.example.data.repository.EmergencyRepository
import com.example.data.repository.ProfileRepository
import com.example.data.repository.PrototypeAuthRepository
import com.example.data.repository.PrototypeChatRepository
import com.example.data.repository.PrototypeEmergencyRepository
import com.example.data.repository.PrototypeProfileRepository
import com.example.data.repository.RuleBasedAiDiagnosisRepository

/**
 * Clean ServiceLocator pattern for single-source-of-truth state sharing
 * between navigation screens and ViewModels.
 */
object ServiceLocator {
    val authRepository: AuthRepository by lazy { PrototypeAuthRepository() }
    val emergencyRepository: EmergencyRepository by lazy { PrototypeEmergencyRepository() }
    val chatRepository: ChatRepository by lazy { PrototypeChatRepository() }
    val aiDiagnosisRepository: AiDiagnosisRepository by lazy { RuleBasedAiDiagnosisRepository() }
    val profileRepository: ProfileRepository by lazy { PrototypeProfileRepository() }
    val backendStatusRepository: BackendStatusRepository by lazy { DefaultBackendStatusRepository() }
}
