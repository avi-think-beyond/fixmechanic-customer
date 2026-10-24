package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.JobStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FirestoreChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ChatRepository {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id)),
        FirebaseAuth.getInstance()
    )

    private val flowMap = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()
    private val listenerMap = mutableMapOf<String, ListenerRegistration>()

    override fun isJobSessionActive(jobStatus: JobStatus?): Boolean {
        if (jobStatus == null) return false
        return !jobStatus.isTerminal
    }

    override fun observeMessages(jobId: String): StateFlow<List<ChatMessage>> {
        val existingFlow = flowMap[jobId]
        if (existingFlow != null) return existingFlow.asStateFlow()

        val newFlow = MutableStateFlow<List<ChatMessage>>(emptyList())
        flowMap[jobId] = newFlow

        val listener = db.collection("serviceRequests")
            .document(jobId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "serviceRequests/$jobId/messages")
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val messages = snapshots.documents.mapNotNull { doc ->
                        val text = doc.getString("message") ?: return@mapNotNull null
                        ChatMessage(
                            id = doc.id,
                            jobId = jobId,
                            senderName = doc.getString("senderName") ?: "User",
                            message = text,
                            timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: System.currentTimeMillis(),
                            isCustomer = doc.getBoolean("isCustomer") ?: true
                        )
                    }
                    newFlow.value = messages
                }
            }
        listenerMap[jobId] = listener

        return newFlow.asStateFlow()
    }

    override suspend fun sendMessage(
        jobId: String,
        text: String,
        isCustomer: Boolean,
        jobStatus: JobStatus?
    ): Result<ChatMessage> {
        if (jobStatus != null && !isJobSessionActive(jobStatus)) {
            return Result.failure(IllegalStateException("This emergency session has closed. Messages can no longer be sent."))
        }

        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message cannot be empty."))
        }

        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not authenticated."))

        return try {
            val docRef = db.collection("serviceRequests")
                .document(jobId)
                .collection("messages")
                .document()

            val msgData = hashMapOf<String, Any?>(
                "senderId" to uid,
                "senderName" to if (isCustomer) "You" else "Mechanic",
                "message" to trimmed,
                "isCustomer" to isCustomer,
                "timestamp" to FieldValue.serverTimestamp()
            )

            docRef.set(msgData).await()

            val sentMsg = ChatMessage(
                id = docRef.id,
                jobId = jobId,
                senderName = if (isCustomer) "You" else "Mechanic",
                message = trimmed,
                isCustomer = isCustomer,
                timestamp = System.currentTimeMillis()
            )
            Result.success(sentMsg)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "serviceRequests/$jobId/messages")
            Result.failure(e)
        }
    }
}
