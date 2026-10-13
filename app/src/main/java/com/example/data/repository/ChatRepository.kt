package com.example.data.repository

import com.example.data.model.ChatMessage
import com.example.data.model.JobStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ChatRepository {
    fun observeMessages(jobId: String): StateFlow<List<ChatMessage>>
    suspend fun sendMessage(jobId: String, text: String, isCustomer: Boolean, jobStatus: JobStatus? = null): Result<ChatMessage>
    fun isJobSessionActive(jobStatus: JobStatus?): Boolean
}

class PrototypeChatRepository : ChatRepository {

    private val _messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())

    override fun isJobSessionActive(jobStatus: JobStatus?): Boolean {
        if (jobStatus == null) return false
        // Closed if Completed, Cancelled, No Mechanic Found, or Expired
        return !jobStatus.isTerminal
    }

    override fun observeMessages(jobId: String): StateFlow<List<ChatMessage>> {
        val currentMap = _messages.value
        if (!currentMap.containsKey(jobId)) {
            val initialList = listOf(
                ChatMessage(
                    id = "msg_init_1",
                    jobId = jobId,
                    senderName = "FixMechanic Dispatch",
                    message = "Your private emergency chat session is active. Customer and technician phone numbers are masked for security.",
                    isCustomer = false,
                    timestamp = System.currentTimeMillis() - 120000
                ),
                ChatMessage(
                    id = "msg_init_2",
                    jobId = jobId,
                    senderName = "Rajesh (Mechanic)",
                    message = "Namaste! I have received your breakdown details and am en route with emergency equipment. Please stay behind the safety barrier.",
                    isCustomer = false,
                    timestamp = System.currentTimeMillis() - 60000
                )
            )
            _messages.value = currentMap + (jobId to initialList)
        }

        return MutableStateFlow(_messages.value[jobId] ?: emptyList()).asStateFlow()
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

        val newMessage = ChatMessage(
            id = "msg_${System.currentTimeMillis()}",
            jobId = jobId,
            senderName = if (isCustomer) "You" else "Mechanic",
            message = trimmed,
            isCustomer = isCustomer,
            timestamp = System.currentTimeMillis()
        )
        val currentList = _messages.value[jobId] ?: emptyList()
        val updatedList = currentList + newMessage
        _messages.value = _messages.value + (jobId to updatedList)
        return Result.success(newMessage)
    }
}
