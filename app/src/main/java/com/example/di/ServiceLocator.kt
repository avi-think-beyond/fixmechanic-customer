package com.example.di

import android.content.Context
import com.example.R
import com.example.data.repository.AiDiagnosisRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.BackendStatusRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.EmergencyRepository
import com.example.data.repository.FirebaseAuthRepository
import com.example.data.repository.FirestoreChatRepository
import com.example.data.repository.FirestoreEmergencyRepository
import com.example.data.repository.FirestoreProfileRepository
import com.example.data.repository.LiveBackendStatusRepository
import com.example.data.repository.ProfileRepository
import com.example.data.repository.RuleBasedAiDiagnosisRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * ServiceLocator providing live Firebase-backed repositories configured with
 * the named custom database ID and Firebase Authentication.
 */
object ServiceLocator {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun getAppContext(): Context? = appContext

    val firestoreDb: FirebaseFirestore by lazy {
        val ctx = checkNotNull(appContext) {
            "ServiceLocator must be initialized with Context by calling ServiceLocator.init(context)"
        }
        val databaseId = ctx.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(databaseId)
    }

    val firebaseAuth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(firestoreDb, firebaseAuth)
    }

    val emergencyRepository: EmergencyRepository by lazy {
        FirestoreEmergencyRepository(firestoreDb, firebaseAuth)
    }

    val chatRepository: ChatRepository by lazy {
        FirestoreChatRepository(firestoreDb, firebaseAuth)
    }

    val profileRepository: ProfileRepository by lazy {
        FirestoreProfileRepository(firestoreDb, firebaseAuth)
    }

    val backendStatusRepository: BackendStatusRepository by lazy {
        LiveBackendStatusRepository(firestoreDb, firebaseAuth)
    }

    val aiDiagnosisRepository: AiDiagnosisRepository by lazy {
        com.example.data.repository.GeminiAiAssistantRepository(RuleBasedAiDiagnosisRepository())
    }
}
