package com.example

import com.example.data.datasource.ServiceCatalog
import com.example.data.model.EmergencyRequest
import com.example.data.model.JobStatus
import com.example.data.model.LocationData
import com.example.data.model.Mechanic
import com.example.data.model.VehicleType
import com.example.data.repository.EmergencyRepository
import com.example.data.repository.PrototypeEmergencyRepository
import com.example.ui.viewmodel.EmergencyViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmergencyFlowLifecycleTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun simulateCompletionThenCompleteJob_savesRatingAndClearsPendingStates() = runTest {
        val repo = PrototypeEmergencyRepository(CoroutineScope(SupervisorJob() + testDispatcher))
        val request = EmergencyRequest(
            id = "",
            vehicleType = VehicleType.CAR,
            serviceItem = ServiceCatalog.carServices.first(),
            vehicleMakeModel = "Hyundai Creta",
            location = LocationData(
                latitude = 28.6139,
                longitude = 77.2090,
                address = "Connaught Place, New Delhi",
                city = "New Delhi",
                state = "Delhi",
                pincode = "110001",
                isObtained = true
            ),
            baseInspectionFee = 399
        )

        val submitted = repo.submitEmergencyRequest(request)
        assertTrue(submitted.isSuccess)
        val createdId = submitted.getOrThrow().id
        assertNotNull(repo.activeRequest.value)

        repo.transitionJobStatus(JobStatus.COMPLETED)
        assertNotNull(repo.pendingReviewRequest.value)

        val completeResult = repo.completeJob(
            rating = 4.0f,
            comment = "Fast roadside battery jump start",
            paymentMethod = "UPI"
        )
        assertTrue(completeResult.isSuccess)
        assertNull(repo.activeRequest.value)
        assertNull(repo.pendingReviewRequest.value)

        val savedRecord = repo.requestHistory.value.firstOrNull { it.id == createdId }
        assertNotNull(savedRecord)
        assertEquals(JobStatus.COMPLETED, savedRecord?.status)
        assertEquals(4.0f, savedRecord?.ratingGiven)
        assertEquals("Fast roadside battery jump start", savedRecord?.feedbackComment)
    }

    @Test
    fun viewModel_completeAndOpenRatingAndSubmitRating_clearsAndReportsErrors() = runTest {
        val failingRepo = FakeFailingReviewRepository()
        val viewModel = EmergencyViewModel(failingRepo)

        var readyCalled = false
        viewModel.completeAndOpenRating { readyCalled = true }
        advanceUntilIdle()
        assertTrue(readyCalled)

        var finishedCalled = false
        viewModel.submitRating(5f, "Great work", "Cash") { finishedCalled = true }
        advanceUntilIdle()

        assertFalse(finishedCalled)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals("Firestore review write failed", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun viewModel_startNewEmergencyFlow_clearsStaleErrorsAndInputs() = runTest {
        val repo = PrototypeEmergencyRepository(CoroutineScope(SupervisorJob() + testDispatcher))
        val viewModel = EmergencyViewModel(repo)

        // Trigger a validation error on manual save to populate errorMessage
        viewModel.saveManualLocation(null)
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.updateVehicleDetails("Old Car", "DL 01 AA 0001", "Old breakdown note", "content://photo/1")
        viewModel.onManualStateChanged("Delhi")
        viewModel.onManualPincodeChanged("110001")
        viewModel.onManualAddressChanged("Old Street")
        viewModel.saveManualLocation(null)
        assertTrue(viewModel.uiState.value.location.isObtained)
        assertEquals("Old breakdown note", viewModel.uiState.value.issueDescription)
        assertEquals("content://photo/1", viewModel.uiState.value.selectedPhotoUri)

        viewModel.startNewEmergencyFlow(VehicleType.BIKE)
        assertNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.locationErrorMessage)
        assertFalse(viewModel.uiState.value.location.isObtained)
        assertNull(viewModel.uiState.value.location.latitude)
        assertFalse(viewModel.uiState.value.isManualLocationMode)
        assertEquals("", viewModel.uiState.value.manualAddressInput)
        assertEquals("", viewModel.uiState.value.issueDescription)
        assertNull(viewModel.uiState.value.selectedPhotoUri)
        assertEquals(VehicleType.BIKE, viewModel.uiState.value.selectedVehicleType)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun viewModel_submitEmergencyRequest_succeedsAfterValidManualLocation() = runTest {
        val repo = PrototypeEmergencyRepository(CoroutineScope(SupervisorJob() + testDispatcher))
        val viewModel = EmergencyViewModel(repo)

        viewModel.startNewEmergencyFlow(VehicleType.CAR)
        viewModel.onManualStateChanged("Maharashtra")
        viewModel.onManualPincodeChanged("400001")
        viewModel.onManualCityChanged("Mumbai")
        viewModel.onManualAddressChanged("Marine Drive")
        viewModel.saveManualLocation(null)

        var dispatchedId: String? = null
        viewModel.submitEmergencyRequest { id -> dispatchedId = id }
        advanceUntilIdle()

        assertNotNull(dispatchedId)
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun viewModel_refreshSession_clearsErrorAndRefreshesRepository() = runTest {
        val repo = FakeFailingReviewRepository()
        val viewModel = EmergencyViewModel(repo)

        var finishedCalled = false
        viewModel.submitRating(5f, "Test", "UPI") { finishedCalled = true }
        advanceUntilIdle()
        assertFalse(finishedCalled)
        assertEquals("Firestore review write failed", viewModel.uiState.value.errorMessage)

        viewModel.refreshSession()
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(1, repo.refreshCalls)
    }

    private class FakeFailingReviewRepository : EmergencyRepository {
        var refreshCalls = 0
        private val _activeRequest = MutableStateFlow<EmergencyRequest?>(null)
        override val activeRequest: StateFlow<EmergencyRequest?> = _activeRequest.asStateFlow()

        private val _pendingReviewRequest = MutableStateFlow<EmergencyRequest?>(null)
        override val pendingReviewRequest: StateFlow<EmergencyRequest?> = _pendingReviewRequest.asStateFlow()

        private val _requestHistory = MutableStateFlow<List<EmergencyRequest>>(emptyList())
        override val requestHistory: StateFlow<List<EmergencyRequest>> = _requestHistory.asStateFlow()

        private val _nearbyMechanics = MutableStateFlow<List<Mechanic>>(emptyList())
        override val nearbyMechanics: StateFlow<List<Mechanic>> = _nearbyMechanics.asStateFlow()

        private val _isLoadingNearbyMechanics = MutableStateFlow(false)
        override val isLoadingNearbyMechanics: StateFlow<Boolean> = _isLoadingNearbyMechanics.asStateFlow()

        override val isDemoMode: Boolean = false

        override suspend fun submitEmergencyRequest(request: EmergencyRequest) =
            Result.failure<EmergencyRequest>(IllegalStateException("Unused"))

        override suspend fun cancelActiveRequest() = Result.success(Unit)
        override suspend fun retrySearch() = Result.success(Unit)
        override suspend fun transitionJobStatus(nextStatus: JobStatus) = Unit

        override suspend fun completeJob(rating: Float, comment: String, paymentMethod: String): Result<Unit> =
            Result.failure(IllegalStateException("Firestore review write failed"))

        override fun observeNearbyMechanics(latitude: Double, longitude: Double, radiusKm: Double) = Unit
        override fun stopObservingNearbyMechanics() = Unit
        override fun refreshSession() {
            refreshCalls++
        }
    }
}
