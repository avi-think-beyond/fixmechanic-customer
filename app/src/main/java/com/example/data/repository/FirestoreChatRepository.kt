package com.example.data.repository

import android.content.Context
import com.example.data.BackendApiClient
import com.example.data.FirestoreProvider
import com.example.data.model.ChatMessage
import com.example.data.model.JobStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class FirestoreChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val backendApiClient: BackendApiClient = BackendApiClient(auth)
) : ChatRepository {

    companion object {
        private fun createFirestore(context: Context): FirebaseFirestore =
            FirestoreProvider.createFirestore(context.applicationContext)

        private fun createAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    }

    constructor(context: Context) : this(
        db = createFirestore(context),
        auth = createAuth(),
        backendApiClient = BackendApiClient(createAuth())
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

        val listener = db.collection("jobChats")
            .document(jobId)
            .collection("jobMessages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "jobChats/$jobId/jobMessages")
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    val messages = snapshots.documents.mapNotNull { doc ->
                        val text = doc.getString("text")
                            ?: doc.getString("message")
                            ?: return@mapNotNull null
                        val senderRole = (doc.getString("senderRole") ?: "CUSTOMER").uppercase()
                        val isCustomer = if (doc.contains("isCustomer")) {
                            doc.getBoolean("isCustomer") ?: (senderRole == "CUSTOMER")
                        } else {
                            senderRole == "CUSTOMER"
                        }
                        val senderName = doc.getString("senderName") ?: when (senderRole) {
                            "CUSTOMER" -> "You"
                            "MECHANIC" -> "Mechanic"
                            else -> "Dispatch"
                        }
                        val ts = (doc.get("timestamp") as? Number)?.toLong()
                            ?: doc.getTimestamp("timestamp")?.toDate()?.time
                            ?: System.currentTimeMillis()
                        ChatMessage(
                            id = doc.id,
                            jobId = jobId,
                            senderName = senderName,
                            message = text,
                            timestamp = ts,
                            isCustomer = isCustomer
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
            return Result.failure(
                IllegalStateException("This emergency session has closed. Messages can no longer be sent.")
            )
        }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message cannot be empty."))
        }
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not authenticated."))

        return try {
            val res = backendApiClient.callFunction(
                "sendJobChatMessage",
                JSONObject()
                    .put("requestId", jobId)
                    .put("text", trimmed)
            )
            val sentMsg = ChatMessage(
                id = res.optString("messageId", "msg_${System.currentTimeMillis()}"),
                jobId = jobId,
                senderName = "You",
                message = trimmed,
                isCustomer = true,
                timestamp = res.optLong("timestamp", System.currentTimeMillis())
            )
            Result.success(sentMsg)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "sendJobChatMessage/$jobId")
            Result.failure(e)
        }
    }
}
