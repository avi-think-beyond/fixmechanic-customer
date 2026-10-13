package com.example.data.model

enum class VehicleType(val displayName: String, val hindiName: String) {
    BIKE("Motorcycle / Scooter", "मोटरसाइकिल / स्कूटर"),
    CAR("Car / Four-Wheeler", "कार / चार पहिया वाहन")
}

data class ServiceItem(
    val id: String,
    val title: String,
    val hindiTitle: String,
    val vehicleType: VehicleType,
    val estimatedPriceInr: Int,
    val etaEstimateMinutes: Int,
    val description: String,
    val categoryIcon: String // key for rendering icon
)

/**
 * Authoritative Job Status enum aligned with backend specifications:
 * REQUESTED, SEARCHING, ASSIGNED, ACCEPTED, EN_ROUTE, ARRIVED, IN_PROGRESS, COMPLETED, CANCELLED.
 * Also includes terminal dispatch search failure states: NO_MECHANIC_FOUND and EXPIRED.
 */
enum class JobStatus(
    val stepIndex: Int,
    val label: String,
    val hindiLabel: String,
    val isTerminal: Boolean = false
) {
    REQUESTED(0, "Requested", "अनुरोध प्राप्त"),
    SEARCHING(1, "Searching", "मैकेनिक खोज जारी"),
    ASSIGNED(2, "Assigned", "नियुक्त"),
    ACCEPTED(3, "Accepted", "स्वीकृत"),
    EN_ROUTE(4, "En Route", "रास्ते में"),
    ARRIVED(5, "Arrived", "पहुंच गए"),
    IN_PROGRESS(6, "In Progress", "मरम्मत जारी"),
    COMPLETED(7, "Completed", "संपन्न", isTerminal = true),
    CANCELLED(-1, "Cancelled", "रद्द", isTerminal = true),
    NO_MECHANIC_FOUND(-2, "No Mechanic Found", "कोई मैकेनिक उपलब्ध नहीं", isTerminal = true),
    EXPIRED(-3, "Request Expired", "अनुरोध समाप्त", isTerminal = true)
}

data class LocationData(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String = "",
    val landmark: String = "",
    val city: String = "",
    val isGpsAccurate: Boolean = false,
    val isObtained: Boolean = false
)

data class Mechanic(
    val id: String,
    val name: String,
    val rating: Float,
    val rescuesCompleted: Int,
    val vehicleInfo: String,
    val phoneMasked: String = "+91 98XXX-XX321", // Masked proxy line for privacy
    val isVerified: Boolean = true,
    val photoUrl: String? = null,
    val currentDistanceKm: Float? = null,
    val estimatedArrivalMinutes: Int? = null
)

data class ChatMessage(
    val id: String,
    val jobId: String,
    val senderName: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isCustomer: Boolean = true
)

data class StatusHistoryEntry(
    val status: JobStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
)

data class EmergencyRequest(
    val id: String,
    val vehicleType: VehicleType,
    val serviceItem: ServiceItem,
    val vehicleMakeModel: String,
    val registrationNumber: String = "",
    val issueDescription: String = "",
    val photoUri: String? = null,
    val location: LocationData,
    val status: JobStatus = JobStatus.REQUESTED,
    val assignedMechanic: Mechanic? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val baseInspectionFee: Int = 249,
    val finalPartsFee: Int = 0,
    val totalPaid: Int = 0,
    val ratingGiven: Float? = null,
    val feedbackComment: String? = null,
    val statusHistory: List<StatusHistoryEntry> = listOf(StatusHistoryEntry(JobStatus.REQUESTED))
)

data class BackendIntegrationStatus(
    val firebaseAuthAvailable: Boolean = false,
    val firestoreAvailable: Boolean = false,
    val fcmAvailable: Boolean = false,
    val backendApiAvailable: Boolean = false,
    val isDemoMode: Boolean = true,
    val activeEnvironment: String = "Development Demo Mode (Isolated Backend Interfaces)",
    val statusSummary: String = "Firebase credentials and backend API are not yet configured. The app is running in safe Demo Mode with isolated repository interfaces."
)
