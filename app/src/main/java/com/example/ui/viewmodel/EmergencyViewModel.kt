package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.datasource.ServiceCatalog
import com.example.data.location.LocationFetchResult
import com.example.data.location.LocationHelper
import com.example.data.model.EmergencyRequest
import com.example.data.model.JobStatus
import com.example.data.model.LocationData
import com.example.data.model.ServiceItem
import com.example.data.model.VehicleType
import com.example.data.repository.EmergencyRepository
import com.example.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EmergencyUiState(
    val selectedVehicleType: VehicleType = VehicleType.CAR,
    val selectedService: ServiceItem? = ServiceCatalog.carServices.first(),
    val location: LocationData = LocationData(),
    val isLocationLoading: Boolean = false,
    val isLocationPermissionGranted: Boolean = false,
    val isGpsDisabled: Boolean = false,
    val locationErrorMessage: String? = null,
    val isManualLocationMode: Boolean = false,
    val manualAddressInput: String = "",
    val manualLandmarkInput: String = "",
    val vehicleMakeModel: String = "Maruti Suzuki Swift Dzire",
    val registrationNumber: String = "",
    val issueDescription: String = "",
    val selectedPhotoUri: String? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class EmergencyViewModel(
    private val emergencyRepository: EmergencyRepository = ServiceLocator.emergencyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmergencyUiState())
    val uiState: StateFlow<EmergencyUiState> = _uiState.asStateFlow()

    val activeRequest: StateFlow<EmergencyRequest?> = emergencyRepository.activeRequest
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val requestHistory: StateFlow<List<EmergencyRequest>> = emergencyRepository.requestHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectVehicleType(type: VehicleType) {
        val newDefaultService = ServiceCatalog.getServices(type).firstOrNull()
        _uiState.update {
            it.copy(
                selectedVehicleType = type,
                selectedService = newDefaultService,
                vehicleMakeModel = if (type == VehicleType.BIKE) "Royal Enfield Classic 350" else "Maruti Suzuki Swift Dzire"
            )
        }
    }

    fun selectService(service: ServiceItem) {
        _uiState.update { it.copy(selectedService = service) }
    }

    fun onLocationPermissionResult(granted: Boolean, context: Context? = null) {
        _uiState.update { it.copy(isLocationPermissionGranted = granted) }
        if (granted && context != null) {
            fetchGpsLocation(context)
        } else if (!granted) {
            _uiState.update {
                it.copy(
                    isManualLocationMode = true,
                    locationErrorMessage = "Location permission denied. Please enter your breakdown location manually."
                )
            }
        }
    }

    fun fetchGpsLocation(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocationLoading = true, locationErrorMessage = null, isGpsDisabled = false) }
            val helper = LocationHelper(context.applicationContext)
            val result = helper.getCurrentLocation()

            when (result) {
                is LocationFetchResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLocationLoading = false,
                            location = result.locationData,
                            isManualLocationMode = false,
                            locationErrorMessage = null,
                            isGpsDisabled = false
                        )
                    }
                }
                is LocationFetchResult.PermissionRequired -> {
                    _uiState.update {
                        it.copy(
                            isLocationLoading = false,
                            isLocationPermissionGranted = false,
                            locationErrorMessage = "Location permission is required to detect GPS coordinates."
                        )
                    }
                }
                is LocationFetchResult.GpsDisabled -> {
                    _uiState.update {
                        it.copy(
                            isLocationLoading = false,
                            isGpsDisabled = true,
                            isManualLocationMode = true,
                            locationErrorMessage = "Device GPS is turned off. Please enable GPS in device settings or enter breakdown address manually."
                        )
                    }
                }
                is LocationFetchResult.Unavailable -> {
                    _uiState.update {
                        it.copy(
                            isLocationLoading = false,
                            isManualLocationMode = true,
                            locationErrorMessage = result.reason
                        )
                    }
                }
            }
        }
    }

    fun enableManualLocation(isManual: Boolean) {
        _uiState.update { it.copy(isManualLocationMode = isManual) }
    }

    fun onManualAddressChanged(address: String) {
        _uiState.update { it.copy(manualAddressInput = address, errorMessage = null) }
    }

    fun onManualLandmarkChanged(landmark: String) {
        _uiState.update { it.copy(manualLandmarkInput = landmark) }
    }

    fun saveManualLocation() {
        val address = _uiState.value.manualAddressInput.trim()
        val landmark = _uiState.value.manualLandmarkInput.trim()
        if (address.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter breakdown street, highway, or area address.") }
            return
        }
        _uiState.update {
            it.copy(
                location = LocationData(
                    latitude = null, // Do not pretend coordinates if not obtained via GPS!
                    longitude = null,
                    address = address,
                    landmark = landmark,
                    city = "India",
                    isGpsAccurate = false,
                    isObtained = true
                ),
                isManualLocationMode = false,
                locationErrorMessage = null,
                errorMessage = null
            )
        }
    }

    fun updateVehicleDetails(makeModel: String, regNo: String, description: String, photoUri: String? = null) {
        _uiState.update {
            it.copy(
                vehicleMakeModel = makeModel,
                registrationNumber = regNo,
                issueDescription = description,
                selectedPhotoUri = photoUri ?: it.selectedPhotoUri
            )
        }
    }

    fun onPhotoSelected(uri: String?) {
        _uiState.update { it.copy(selectedPhotoUri = uri) }
    }

    fun submitEmergencyRequest(onSuccess: (String) -> Unit) {
        val state = _uiState.value
        val service = state.selectedService ?: run {
            _uiState.update { it.copy(errorMessage = "Please choose a breakdown service.") }
            return
        }

        if (!state.location.isObtained && state.location.address.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please confirm your breakdown location or enter address manually.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val newRequest = EmergencyRequest(
                id = "", // Will be assigned authoritative ID in repository
                vehicleType = state.selectedVehicleType,
                serviceItem = service,
                vehicleMakeModel = state.vehicleMakeModel.ifBlank { "Vehicle" },
                registrationNumber = state.registrationNumber,
                issueDescription = state.issueDescription,
                photoUri = state.selectedPhotoUri,
                location = state.location,
                baseInspectionFee = service.estimatedPriceInr
            )

            val result = emergencyRepository.submitEmergencyRequest(newRequest)
            _uiState.update { it.copy(isSubmitting = false) }
            result.onSuccess {
                onSuccess(it.id)
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Failed to initiate emergency request") }
            }
        }
    }

    fun retrySearch() {
        viewModelScope.launch {
            emergencyRepository.retrySearch()
        }
    }

    fun cancelActiveRequest(onCancelled: () -> Unit) {
        viewModelScope.launch {
            emergencyRepository.cancelActiveRequest().onSuccess {
                onCancelled()
            }
        }
    }

    fun devAdvanceStatus(nextStatus: JobStatus) {
        viewModelScope.launch {
            emergencyRepository.transitionJobStatus(nextStatus)
        }
    }

    fun submitRating(rating: Float, comment: String, onFinished: () -> Unit) {
        viewModelScope.launch {
            emergencyRepository.completeJob(rating, comment).onSuccess {
                onFinished()
            }
        }
    }
}
