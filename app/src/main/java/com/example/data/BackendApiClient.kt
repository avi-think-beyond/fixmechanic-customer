package com.example.data

import android.os.Build
import com.example.BuildConfig
import com.example.data.FirestoreProvider.awaitResult
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class BackendApiClient(
    private val auth: FirebaseAuth?,
    private val baseUrl: String = BuildConfig.BACKEND_BASE_URL,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    fun isConfigured(): Boolean {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) return false
        val isLocalOnly = trimmed.contains("10.0.2.2") ||
            trimmed.contains("127.0.0.1") ||
            trimmed.contains("localhost")
        if (isLocalOnly && !isRunningOnEmulator()) return false
        return true
    }

    private fun isRunningOnEmulator(): Boolean = runCatching {
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk_gphone", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true)
    }.getOrDefault(false)

    suspend fun callFunction(functionName: String, data: JSONObject): JSONObject {
        if (!isConfigured()) {
            throw IOException("Direct Firestore mode active (no remote dispatch server configured).")
        }
        val user = auth?.currentUser
            ?: throw IllegalStateException("Customer must be authenticated before calling /$functionName.")
        val tokenResult = user.getIdToken(false).awaitResult()
        val idToken = tokenResult.token
            ?: throw IllegalStateException("Unable to obtain Firebase Auth token.")

        return withContext(Dispatchers.IO) {
            val cleanBase = baseUrl.trimEnd('/')
            val envelope = JSONObject().put("data", data)
            val body = envelope.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$cleanBase/$functionName")
                .addHeader("Authorization", "Bearer $idToken")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val parsed = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
                if (!response.isSuccessful) {
                    val errCode = parsed.optJSONObject("error")?.optString("code").orEmpty()
                    val errMsg = parsed.optJSONObject("error")?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                        ?: "Backend /$functionName failed (HTTP ${response.code})"
                    throw BackendCallException(response.code, errCode, errMsg)
                }
                parsed.optJSONObject("result") ?: parsed
            }
        }
    }

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext false
        runCatching {
            val cleanBase = baseUrl.trimEnd('/')
            val request = Request.Builder()
                .url("$cleanBase/health")
                .get()
                .build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    companion object {
        fun isNetworkFailure(throwable: Throwable): Boolean {
            var current: Throwable? = throwable
            while (current != null) {
                if (current is IOException) return true
                current = current.cause
            }
            return false
        }
    }
}

class BackendCallException(
    val httpStatus: Int,
    val errorCode: String,
    override val message: String
) : IllegalStateException(message)
