package com.example.data.repository

import com.example.data.datasource.ServiceCatalog
import com.example.data.model.ServiceItem
import com.example.data.model.VehicleType

data class AiDiagnosisResult(
    val suggestedService: ServiceItem,
    val confidenceScore: Float,
    val explanation: String,
    val alternativeServices: List<ServiceItem>,
    val safetyAction: String = "",
    val severityLevel: String = "Moderate"
)

interface AiDiagnosisRepository {
    suspend fun diagnoseIssue(
        description: String,
        vehicleType: VehicleType
    ): Result<AiDiagnosisResult>

    suspend fun askAssistant(
        question: String,
        vehicleType: VehicleType
    ): Result<String>
}

/**
 * Intelligent Issue Classifier service.
 * Ready for server-side Gemini backend API or on-device keyword fallback.
 * Strictly suggests service categories; never dispatches or overrides user selection.
 */
class RuleBasedAiDiagnosisRepository : AiDiagnosisRepository {

    override suspend fun askAssistant(
        question: String,
        vehicleType: VehicleType
    ): Result<String> {
        val lower = question.lowercase()
        val answer = when {
            lower.contains("puncture") || lower.contains("flat") ->
                "If driving on a flat tyre, pull over immediately to the left highway shoulder. Driving further will damage the wheel rim and sidewall. Turn on hazard blinkers and request tyre service."
            lower.contains("battery") || lower.contains("jump") ->
                "If jump-starting, connect Red (+ve) clamp first, then Black (-ve) to an unpainted chassis ground. Let donor vehicle run for 3 minutes before starting."
            lower.contains("overheat") || lower.contains("steam") ->
                "Do NOT open the radiator cap while the engine is hot—scalding coolant can erupt under pressure. Wait at least 20 minutes for the engine to cool down."
            lower.contains("clutch") || lower.contains("brake") ->
                "If brakes or clutch feel soft or unresponsive, avoid driving. Shift to lower gear gradually and coast safely to the shoulder."
            else ->
                "Safety first: Keep hazard lights flashing, position the vehicle on the roadside shoulder away from oncoming traffic, and request roadside dispatch."
        }
        return Result.success(answer)
    }

    override suspend fun diagnoseIssue(
        description: String,
        vehicleType: VehicleType
    ): Result<AiDiagnosisResult> {
        val lower = description.lowercase().trim()
        val services = ServiceCatalog.getServices(vehicleType)

        if (lower.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please describe the symptom or sound your vehicle is making."))
        }

        val (matchedId, confidence, reasoning) = when {
            lower.contains("puncture") || lower.contains("flat") || lower.contains("tyre") || lower.contains("tire") || lower.contains("air") || lower.contains("nail") || lower.contains("spare") || lower.contains("stepney") -> {
                val id = if (vehicleType == VehicleType.BIKE) "bike_puncture" else "car_tyre"
                Triple(id, 0.94f, "Detected references to flat tyre, air loss, puncture, or spare tyre replacement.")
            }

            lower.contains("battery") || lower.contains("jump") || lower.contains("drain") || lower.contains("dead") || lower.contains("clicking") || lower.contains("boost") -> {
                val id = if (vehicleType == VehicleType.BIKE) "bike_battery" else "car_battery"
                Triple(id, 0.92f, "Symptom points to depleted battery or starter solenoid failure.")
            }

            lower.contains("overheat") || lower.contains("steam") || lower.contains("coolant") || lower.contains("radiator") || lower.contains("temperature") || lower.contains("hot") -> {
                val id = if (vehicleType == VehicleType.CAR) "car_overheating" else "bike_not_starting"
                Triple(id, 0.90f, "High engine temperature or steam emission detected.")
            }

            lower.contains("key") || lower.contains("lock") || lower.contains("inside") || lower.contains("unlock") -> {
                val id = if (vehicleType == VehicleType.CAR) "car_lockout" else "bike_minor_repair"
                Triple(id, 0.96f, "Detected lockout scenario where keys or remote are trapped inside.")
            }

            lower.contains("petrol") || lower.contains("fuel") || lower.contains("diesel") || lower.contains("empty tank") || lower.contains("ran out") -> {
                val id = if (vehicleType == VehicleType.BIKE) "bike_fuel" else "car_fuel"
                Triple(id, 0.95f, "Fuel exhaustion or starvation detected.")
            }

            lower.contains("chain") || lower.contains("clutch") || lower.contains("cable") || lower.contains("gear shifting") -> {
                val id = if (vehicleType == VehicleType.BIKE) "bike_chain_clutch" else "car_minor_repair"
                Triple(id, 0.92f, "Transmission chain, clutch linkage, or cable break symptom.")
            }

            lower.contains("brake") || lower.contains("stopping") || lower.contains("pad") || lower.contains("disc") -> {
                val id = if (vehicleType == VehicleType.BIKE) "bike_brake" else "car_minor_repair"
                Triple(id, 0.91f, "Braking system resistance, pad wear, or hydraulic fluid issue.")
            }

            lower.contains("tow") || lower.contains("accident") || lower.contains("immobile") || lower.contains("flatbed") -> {
                val id = if (vehicleType == VehicleType.BIKE) "bike_towing" else "car_towing"
                Triple(id, 0.95f, "Vehicle is completely immobilized and requires hydraulic flatbed towing.")
            }

            lower.contains("light") || lower.contains("horn") || lower.contains("fuse") || lower.contains("wiring") || lower.contains("electrical") -> {
                val id = if (vehicleType == VehicleType.BIKE) "bike_electrical" else "car_electrical"
                Triple(id, 0.88f, "Electrical, headlight, horn, or blown fuse circuit failure.")
            }

            lower.contains("not starting") || lower.contains("crank") || lower.contains("self") || lower.contains("stall") -> {
                val id = if (vehicleType == VehicleType.BIKE) "bike_not_starting" else "car_not_starting"
                Triple(id, 0.85f, "Engine ignition or fuel delivery failure preventing start.")
            }

            else -> {
                val id = if (vehicleType == VehicleType.BIKE) "bike_other" else "car_other"
                Triple(id, 0.60f, "Issue requires on-site general mechanical diagnostics.")
            }
        }

        val primaryService = services.find { it.id == matchedId } ?: services.first()
        val alternatives = services.filter { it.id != primaryService.id }.take(2)

        return Result.success(
            AiDiagnosisResult(
                suggestedService = primaryService,
                confidenceScore = confidence,
                explanation = reasoning,
                alternativeServices = alternatives
            )
        )
    }
}
