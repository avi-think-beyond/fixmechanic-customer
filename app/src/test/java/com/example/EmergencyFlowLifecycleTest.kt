package com.example

import com.example.data.datasource.ServiceCatalog
import com.example.data.model.EmergencyRequest
import com.example.data.model.JobStatus
import com.example.data.model.LocationData
import com.example.data.model.Mechanic
import com.example.data.model.VehicleType
import com.example.data.repository.EmergencyRepository
import com.example.ui.viewmodel.EmergencyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    fun completedRequestWithoutRating_surfacesInPendingReviewAndClearsOnSubmit() = runTest {
        val repo = InMemoryEmergencyRepository()
        val viewModel = EmergencyViewModel(repo)
        val pendingCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.pendingReviewRequest.collect()
        }

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
                isObtained = true
            ),
            baseInspectionFee = 399
        )

        val submitted = repo.submitEmergencyRequest(request)
        assertTrue(submitted.isSuccess)
        val createdId = submitted.getOrThrow().id
        assertNotNull(repo.activeRequest.value)

        repo.markCompleted(createdId)
        advanceUntilIdle()
        assertEquals(createdId, viewModel.pendingReviewRequest.value?.id)

        var finished = false
        viewModel.submitRating(
            rating = 4.0f,
            comment = "Fast roadside battery jump start",
            paymentMethod = "UPI"
        ) {
            finished = true
        }
        advanceUntilIdle()

        assertTrue(finished)
        assertNull(repo.activeRequest.value)
        assertNull(viewModel.pendingReviewRequest.value)

        val savedRecord = repo.requestHistory.value.firstOrNull { it.id == createdId }
        assertNotNull(savedRecord)
        assertEquals(JobStatus.COMPLETED, savedRecord?.status)
        assertEquals(4.0f, savedRecord?.ratingGiven)
        assertEquals("Fast roadside battery jump start", savedRecord?.feedbackComment)
        pendingCollector.cancel()
    }

    @Test
    fun viewModel_submitRatingFailure_reportsErrorAndResetsSubmitting() = runTest {
        val failingRepo = InMemoryEmergencyRepository(failRatings = true)
        val viewModel = EmergencyViewModel(failingRepo)

        var finishedCalled = false
        viewModel.submitRating(5f, "Great work", "Cash") { finishedCalled = true }
        advanceUntilIdle()

        assertFalse(finishedCalled)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals("Rating submission failed", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun viewModel_startNewEmergencyFlow_clearsStaleErrorsAndInputs() = runTest {
        val repo = InMemoryEmergencyRepository()
        val viewModel = EmergencyViewModel(repo)

        viewModel.saveManualLocation(null)
        assertNotNull(viewModel.uiState.value.locationErrorMessage)

        viewModel.updateVehicleDetails("Old Car", "DL 01 AA 0001", "Old breakdown note", "content://photo/1")
        viewModel.setConfirmedLocation(
            LocationData(
                latitude = 28.6139,
                longitude = 77.2090,
                address = "Old Street",
                isObtained = true
            )
        )
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
    fun viewModel_submitEmergencyRequest_succeedsWithConfirmedCoordinates() = runTest {
        val repo = InMemoryEmergencyRepository()
        val viewModel = EmergencyViewModel(repo)

        viewModel.startNewEmergencyFlow(VehicleType.CAR)
        viewModel.setConfirmedLocation(
            LocationData(
                latitude = 19.0760,
                longitude = 72.8777,
                address = "Marine Drive, Mumbai",
                city = "Mumbai",
                isObtained = true
            )
        )

        var dispatchedId: String? = null
        viewModel.submitEmergencyRequest { id -> dispatchedId = id }
        advanceUntilIdle()

        assertNotNull(dispatchedId)
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun viewModel_refreshSession_clearsErrorAndRefreshesRepository() = runTest {
        val repo = InMemoryEmergencyRepository(failRatings = true)
        val viewModel = EmergencyViewModel(repo)

        var finishedCalled = false
        viewModel.submitRating(5f, "Test", "UPI") { finishedCalled = true }
        advanceUntilIdle()
        assertFalse(finishedCalled)
        assertEquals("Rating submission failed", viewModel.uiState.value.errorMessage)

        viewModel.refreshSession()
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(1, repo.refreshCalls)
    }

    private class InMemoryEmergencyRepository(
        private val failRatings: Boolean = false
    ) : EmergencyRepository {
        var refreshCalls = 0
        private var counter = 0

        private val _activeRequest = MutableStateFlow<EmergencyRequest?>(null)
        override val activeRequest: StateFlow<EmergencyRequest?> = _activeRequest.asStateFlow()

        private val _requestHistory = MutableStateFlow<List<EmergencyRequest>>(emptyList())
        override val requestHistory: StateFlow<List<EmergencyRequest>> = _requestHistory.asStateFlow()

        private val _nearbyMechanics = MutableStateFlow<List<Mechanic>>(emptyList())
        override val nearbyMechanics: StateFlow<List<Mechanic>> = _nearbyMechanics.asStateFlow()

        override val isDemoMode: Boolean = false

        override suspend fun submitEmergencyRequest(request: EmergencyRequest): Result<EmergencyRequest> {
            counter++
            val created = request.copy(id = "req_$counter", status = JobStatus.SEARCHING)
            _activeRequest.value = created
            _requestHistory.value = listOf(created) + _requestHistory.value
            return Result.success(created)
        }

        fun markCompleted(requestId: String) {
            val updated = _requestHistory.value.map {
                if (it.id == requestId) it.copy(status = JobStatus.COMPLETED) else it
            }
            _requestHistory.value = updated
            _activeRequest.value = updated.firstOrNull { it.id == requestId }
        }

        override suspend fun cancelActiveRequest(): Result<Unit> {
            _activeRequest.value = null
            return Result.success(Unit)
        }

        override suspend fun retrySearch(): Result<Unit> = Result.success(Unit)
        override suspend fun triggerOfferExpiryCheck(): Result<Unit> = Result.success(Unit)

        override suspend fun completeJob(rating: Float, comment: String, paymentMethod: String): Result<Unit> {
            if (failRatings) {
                return Result.failure(IllegalStateException("Rating submission failed"))
            }
            val target = _activeRequest.value
                ?: return Result.failure(IllegalStateException("No active request"))
            val rated = target.copy(
                status = JobStatus.COMPLETED,
                ratingGiven = rating,
                feedbackComment = comment
            )
            _requestHistory.value = _requestHistory.value.map { if (it.id == target.id) rated else it }
            _activeRequest.value = null
            return Result.success(Unit)
        }

        override fun refreshSession() {
            refreshCalls++
        }

        override fun clearSession() {
            _activeRequest.value = null
            _requestHistory.value = emptyList()
            _nearbyMechanics.value = emptyList()
        }
    }
}
