package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.datasource.ServiceCatalog
import com.example.data.model.ServiceItem
import com.example.data.model.VehicleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiAiAssistantRepository(
    private val fallbackClassifier: AiDiagnosisRepository = RuleBasedAiDiagnosisRepository()
) : AiDiagnosisRepository {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Supported modern models evaluated per guidelines
    private val primaryModel = "gemini-3.8-flash"
    private val fallbackModel = "gemini-3.5-flash"

    override suspend fun diagnoseIssue(
        description: String,
        vehicleType: VehicleType
    ): Result<AiDiagnosisResult> = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d("GeminiAiRepo", "No custom Gemini API key configured, using intelligent on-device diagnosis")
            return@withContext fallbackClassifier.diagnoseIssue(description, vehicleType)
        }

        val services = ServiceCatalog.getServices(vehicleType)
        val validTitles = services.joinToString(", ") { "\"${it.title}\"" }

        val systemPrompt = """
            You are FixMechanic AI, an expert 24/7 automotive emergency roadside technician in India.
            A user reports a breakdown on a $vehicleType.
            Issue description: "$description"
            Available FixMechanic roadside services: [$validTitles].
            
            Return a JSON object with:
            - "matchedServiceTitle": exact title from the available services list above
            - "confidence": confidence score between 0.5 and 0.99
            - "severity": "Low", "Moderate", or "Critical"
            - "explanation": 2 sentences explaining root cause and what needs repair
            - "safetyAdvice": 1-2 practical roadside safety steps customer should take now
            
            Output raw valid JSON only. Do not enclose in markdown backticks.
        """.trimIndent()

        try {
            val responseText = callGeminiApi(primaryModel, systemPrompt, apiKey)
                ?: callGeminiApi(fallbackModel, systemPrompt, apiKey)

            if (responseText != null) {
                val cleanedJson = responseText
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                val json = JSONObject(cleanedJson)
                val matchedTitle = json.optString("matchedServiceTitle", "")
                val confidence = json.optDouble("confidence", 0.92).toFloat()
                val explanation = json.optString("explanation", "Mechanical failure identified by FixMechanic AI.")
                val safetyAdvice = json.optString("safetyAdvice", "Turn on hazard indicators and move to roadside shoulder.")
                val severity = json.optString("severity", "Moderate")

                val matchedService = services.firstOrNull {
                    it.title.equals(matchedTitle, ignoreCase = true) ||
                    matchedTitle.contains(it.title, ignoreCase = true) ||
                    it.title.contains(matchedTitle, ignoreCase = true)
                } ?: services.first()

                val alternatives = services.filter { it.id != matchedService.id }.take(2)

                val fullExplanation = buildString {
                    append(explanation)
                    if (safetyAdvice.isNotBlank()) {
                        append("\n\n⚠️ Roadside Safety Step: ")
                        append(safetyAdvice)
                    }
                }

                return@withContext Result.success(
                    AiDiagnosisResult(
                        suggestedService = matchedService,
                        confidenceScore = confidence,
                        explanation = fullExplanation,
                        alternativeServices = alternatives,
                        safetyAction = safetyAdvice,
                        severityLevel = severity
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("GeminiAiRepo", "Gemini API call failed, falling back to heuristic diagnosis: ${e.message}")
        }

        fallbackClassifier.diagnoseIssue(description, vehicleType)
    }

    override suspend fun askAssistant(
        question: String,
        vehicleType: VehicleType
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.success(
                "FixMechanic AI Assistant is ready! For instant breakdown help, choose a service above or enter your vehicle symptoms. (Configure your Gemini API key in AI Studio to enable general open-ended chat)."
            )
        }

        val prompt = """
            You are FixMechanic AI Copilot, a friendly roadside automotive assistant for Indian highway and city drivers.
            Vehicle type: $vehicleType
            Question: "$question"
            
            Give a concise, practical, safety-first response in 2-3 short paragraphs.
            If dangerous to drive, warn the driver clearly. Suggest whether on-site mechanic or flatbed towing is needed.
        """.trimIndent()

        try {
            val responseText = callGeminiApi(primaryModel, prompt, apiKey)
                ?: callGeminiApi(fallbackModel, prompt, apiKey)

            if (!responseText.isNullOrBlank()) {
                return@withContext Result.success(responseText.trim())
            }
        } catch (e: Exception) {
            Log.e("GeminiAiRepo", "Assistant question call failed", e)
        }

        Result.success("Please keep vehicle hazard blinkers active. Our dispatch network is scanning for available mechanics nearby.")
    }

    private fun callGeminiApi(model: String, promptText: String, apiKey: String): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().put("text", promptText))
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            val genConfig = JSONObject().apply {
                put("temperature", 0.2)
                put("topP", 0.95)
            }
            put("generationConfig", genConfig)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w("GeminiAiRepo", "API error: HTTP ${response.code} - ${response.message}")
            return null
        }

        val responseBodyString = response.body?.string() ?: return null
        val root = JSONObject(responseBodyString)
        val candidates = root.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        if (parts.length() == 0) return null

        return parts.getJSONObject(0).optString("text")
    }
}
