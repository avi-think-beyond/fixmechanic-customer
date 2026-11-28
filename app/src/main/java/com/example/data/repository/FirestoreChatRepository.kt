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
    private val sessionLock = Any()
    @Volatile
    private var activeUserId: String? = auth.currentUser?.uid
    private var sessionGeneration: Long = 0L

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val nextUid = firebaseAuth.currentUser?.uid
            if (nextUid != activeUserId) {
                clearSession()
                activeUserId = nextUid
            }
        }
    }

    override fun clearSession() {
        synchronized(sessionLock) {
            sessionGeneration++
            listenerMap.values.forEach { it.remove() }
            listenerMap.clear()
            flowMap.values.forEach { it.value = emptyList() }
            flowMap.clear()
            activeUserId = null
        }
    }

    override fun isJobSessionActive(jobStatus: JobStatus?): Boolean {
        if (jobStatus == null) return false
        return !jobStatus.isTerminal
    }

    override fun observeMessages(jobId: String): StateFlow<List<ChatMessage>> {
        val currentUid = auth.currentUser?.uid
            ?: return MutableStateFlow<List<ChatMessage>>(emptyList()).asStateFlow()

        synchronized(sessionLock) {
            if (activeUserId != null && activeUserId != currentUid) {
                clearSession()
            }
            activeUserId = currentUid

            val existingFlow = flowMap[jobId]
            if (existingFlow != null) return existingFlow.asStateFlow()

            val newFlow = MutableStateFlow<List<ChatMessage>>(emptyList())
            flowMap[jobId] = newFlow
            val generation = sessionGeneration

            val listener = db.collection("jobChats")
                .document(jobId)
                .collection("jobMessages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshots, error ->
                    if (generation != sessionGeneration || auth.currentUser?.uid != currentUid) {
                        return@addSnapshotListener
                    }
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
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Not authenticated."))
        val uid = user.uid

        if (backendApiClient.isConfigured()) {
            try {
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
                return Result.success(sentMsg)
            } catch (e: Exception) {
                if (!BackendApiClient.isNetworkFailure(e)) {
                    handleFirestoreError(e, OperationType.CREATE, "sendJobChatMessage/$jobId")
                    return Result.failure(e)
                }
            }
        }

        return try {
            val now = System.currentTimeMillis()
            val msgRef = db.collection("jobChats")
                .document(jobId)
                .collection("jobMessages")
                .document()
            val senderName = user.displayName?.takeIf { it.isNotBlank() } ?: "You"
            com.example.data.FirestoreProvider.run {
                msgRef.set(
                    mapOf(
                        "senderId" to uid,
                        "senderRole" to "CUSTOMER",
                        "senderName" to senderName,
                        "isCustomer" to true,
                        "text" to trimmed,
                        "timestamp" to now
                    )
                ).awaitResult()
            }
            Result.success(
                ChatMessage(
                    id = msgRef.id,
                    jobId = jobId,
                    senderName = "You",
                    message = trimmed,
                    isCustomer = true,
                    timestamp = now
                )
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "jobChats/$jobId/jobMessages")
            Result.failure(e)
        }
    }
}
