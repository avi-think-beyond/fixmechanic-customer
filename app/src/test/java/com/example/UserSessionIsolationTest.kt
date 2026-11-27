package com.example

import com.example.data.datasource.ServiceCatalog
import com.example.data.model.BackendIntegrationStatus
import com.example.data.model.ChatMessage
import com.example.data.model.EmergencyRequest
import com.example.data.model.JobStatus
import com.example.data.model.LocationData
import com.example.data.model.Mechanic
import com.example.data.model.VehicleType
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthState
import com.example.data.repository.BackendStatusRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.EmergencyRepository
import com.example.data.repository.ProfileRepository
import com.example.data.repository.PrototypeProfileRepository
import com.example.data.repository.UserProfileData
import com.example.ui.viewmodel.AiDiagnosisViewModel
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.EmergencyViewModel
import com.example.ui.viewmodel.ProfileViewModel
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
class UserSessionIsolationTest {

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
    fun multiAccountSwitch_userALogoutThenUserBLogin_neverLeaksRequestsChatProfileOrUiInputs() = runTest {
        val sessionStore = MultiAccountFakeBackend()
        val authViewModel = AuthViewModel(sessionStore.authRepo)
        val emergencyViewModel = EmergencyViewModel(sessionStore.emergencyRepo)
        val chatViewModel = ChatViewModel(sessionStore.chatRepo, sessionStore.emergencyRepo)
        val profileViewModel = ProfileViewModel(sessionStore.profileRepo, sessionStore.statusRepo)
        val aiViewModel = AiDiagnosisViewModel()

        val activeCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            emergencyViewModel.activeRequest.collect()
        }
        val historyCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            emergencyViewModel.requestHistory.collect()
        }
        val profileCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            profileViewModel.profileData.collect()
        }

        // 1. Log in as User A
        sessionStore.signInAs("user_a", "Alice", "alice@example.com")
        advanceUntilIdle()
        assertEquals("Alice", profileViewModel.profileData.value.fullName)

        // User A fills breakdown details and creates an emergency request + chat message
        emergencyViewModel.startNewEmergencyFlow(VehicleType.CAR)
        emergencyViewModel.onManualStateChanged("Maharashtra")
        emergencyViewModel.onManualPincodeChanged("400001")
        emergencyViewModel.onManualCityChanged("Mumbai")
        emergencyViewModel.onManualAddressChanged("Alice Private Address, Marine Drive")
        emergencyViewModel.saveManualLocation(null)
        emergencyViewModel.updateVehicleDetails(
            makeModel = "Alice's Honda City",
            regNo = "MH 01 AL 0001",
            description = "Alice breakdown note",
            photoUri = "content://alice/photo.jpg"
        )

        var aliceReqId: String? = null
        emergencyViewModel.submitEmergencyRequest { aliceReqId = it }
        advanceUntilIdle()
        assertNotNull(aliceReqId)
        assertEquals(aliceReqId, emergencyViewModel.activeRequest.value?.id)
        assertEquals(1, emergencyViewModel.requestHistory.value.size)

        chatViewModel.loadChat(aliceReqId!!, JobStatus.REQUESTED)
        chatViewModel.sendMessage("Alice private location clue")
        advanceUntilIdle()
        assertEquals(1, chatViewModel.uiState.value.messages.size)
        assertEquals("Alice private location clue", chatViewModel.uiState.value.messages.first().message)

        aiViewModel.onQueryChanged("Alice engine smoke")

        // 2. Log out User A
        emergencyViewModel.resetForLogout()
        chatViewModel.resetForLogout()
        aiViewModel.clear()
        profileViewModel.resetForLogout()
        authViewModel.logout(null)
        advanceUntilIdle()

        // Verify ALL User A state is completely cleared immediately
        assertFalse(authViewModel.uiState.value.isVerified)
        assertNull(emergencyViewModel.activeRequest.value)
        assertNull(emergencyViewModel.pendingReviewRequest.value)
        assertTrue(emergencyViewModel.requestHistory.value.isEmpty())
        assertEquals("", emergencyViewModel.uiState.value.manualAddressInput)
        assertEquals("", emergencyViewModel.uiState.value.registrationNumber)
        assertEquals("", emergencyViewModel.uiState.value.issueDescription)
        assertNull(emergencyViewModel.uiState.value.selectedPhotoUri)
        assertTrue(chatViewModel.uiState.value.messages.isEmpty())
        assertEquals("Customer", profileViewModel.profileData.value.fullName)
        assertEquals("", profileViewModel.profileData.value.email)
        assertEquals("", aiViewModel.uiState.value.queryInput)

        // 3. Log in as User B (without restarting app)
        sessionStore.signInAs("user_b", "Bob", "bob@example.com")
        advanceUntilIdle()

        // User B must NOT see User A's service request or history or profile
        assertNull(emergencyViewModel.activeRequest.value)
        assertTrue(emergencyViewModel.requestHistory.value.isEmpty())
        assertEquals("Bob", profileViewModel.profileData.value.fullName)
        assertEquals("bob@example.com", profileViewModel.profileData.value.email)

        // Even if chatViewModel.loadChat were called for User A's jobId while logged in as User B,
        // authorization check in backend repository must refuse User A's messages
        chatViewModel.loadChat(aliceReqId!!, JobStatus.REQUESTED)
        advanceUntilIdle()
        assertTrue(chatViewModel.uiState.value.messages.isEmpty())

        // 4. User B creates their own service request and sees ONLY User B's record
        emergencyViewModel.startNewEmergencyFlow(VehicleType.BIKE)
        emergencyViewModel.onManualStateChanged("Delhi")
        emergencyViewModel.onManualPincodeChanged("110001")
        emergencyViewModel.onManualCityChanged("New Delhi")
        emergencyViewModel.onManualAddressChanged("Bob Connaught Place")
        emergencyViewModel.saveManualLocation(null)
        var bobReqId: String? = null
        emergencyViewModel.submitEmergencyRequest { bobReqId = it }
        advanceUntilIdle()

        assertNotNull(bobReqId)
        assertEquals(bobReqId, emergencyViewModel.activeRequest.value?.id)
        assertEquals(1, emergencyViewModel.requestHistory.value.size)

        chatViewModel.loadChat(bobReqId!!, JobStatus.REQUESTED)
        chatViewModel.sendMessage("Bob private message")
        advanceUntilIdle()
        assertEquals(1, chatViewModel.uiState.value.messages.size)
        assertEquals("Bob private message", chatViewModel.uiState.value.messages.first().message)

        // 5. Log out User B and switch back to User A -> User A's data is restored cleanly
        emergencyViewModel.resetForLogout()
        chatViewModel.resetForLogout()
        profileViewModel.resetForLogout()
        authViewModel.logout(null)
        advanceUntilIdle()

        sessionStore.signInAs("user_a", "Alice", "alice@example.com")
        advanceUntilIdle()
        assertEquals(aliceReqId, emergencyViewModel.activeRequest.value?.id)
        assertEquals(1, emergencyViewModel.requestHistory.value.size)
        assertEquals("Alice", profileViewModel.profileData.value.fullName)

        chatViewModel.loadChat(aliceReqId!!, JobStatus.REQUESTED)
        advanceUntilIdle()
        assertEquals(1, chatViewModel.uiState.value.messages.size)
        assertEquals("Alice private location clue", chatViewModel.uiState.value.messages.first().message)

        activeCollector.cancel()
        historyCollector.cancel()
        profileCollector.cancel()
    }

    @Test
    fun authViewModel_logoutSynchronouslyClearsIsVerifiedFlag() = runTest {
        val sessionStore = MultiAccountFakeBackend()
        val authViewModel = AuthViewModel(sessionStore.authRepo)

        authViewModel.onPhoneChanged("9876543210")
        authViewModel.sendOtp()
        advanceUntilIdle()
        val otp = authViewModel.uiState.value.generatedOtp!!
        authViewModel.onOtpChanged(otp)
        authViewModel.verifyOtp()
        advanceUntilIdle()

        assertTrue(authViewModel.uiState.value.isVerified)

        // Immediately after calling logout(), before any coroutine completes, isVerified must already be false
        authViewModel.logout(null)
        assertFalse(authViewModel.uiState.value.isVerified)
        advanceUntilIdle()
        assertFalse(authViewModel.uiState.value.isVerified)
    }

    /**
     * Simulates an authenticated multi-tenant backend + client repository layer enforcing uid ownership.
     */
    private class MultiAccountFakeBackend {
        var currentUid: String? = null
            private set

        private val serverRequestsByUid = mutableMapOf<String, MutableList<EmergencyRequest>>()
        private val serverChatsByJobId = mutableMapOf<String, Pair<String, MutableList<ChatMessage>>>()
        private val serverProfilesByUid = mutableMapOf<String, UserProfileData>()

        private val _activeRequest = MutableStateFlow<EmergencyRequest?>(null)
        private val _pendingReview = MutableStateFlow<EmergencyRequest?>(null)
        private val _history = MutableStateFlow<List<EmergencyRequest>>(emptyList())
        private val _nearby = MutableStateFlow<List<Mechanic>>(emptyList())
        private val _loadingNearby = MutableStateFlow(false)

        private val chatFlows = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()
        private val _profile = MutableStateFlow(UserProfileData())
        private val _status = MutableStateFlow(BackendIntegrationStatus())

        val authRepo = object : AuthRepository {
            private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
            override val authState: StateFlow<AuthState> = _authState.asStateFlow()
            private val _loggedIn = MutableStateFlow(false)
            override val isLoggedInFlow: StateFlow<Boolean> = _loggedIn.asStateFlow()
            private val _phone = MutableStateFlow<String?>(null)
            override val currentUserPhone: StateFlow<String?> = _phone.asStateFlow()
            override val isDemoMode: Boolean = false
            private var otp: String? = null

            override suspend fun signInWithGoogle(idToken: String): Result<Unit> = Result.success(Unit)
            override suspend fun requestOtp(phoneNumber: String): Result<String> {
                otp = "123456"
                _authState.value = AuthState.OtpSent(phoneNumber, System.currentTimeMillis() + 60_000L)
                return Result.success("123456")
            }

            override suspend fun verifyOtp(phoneNumber: String, otpCode: String): Result<Boolean> {
                _loggedIn.value = true
                _authState.value = AuthState.Authenticated(phoneNumber, false)
                return Result.success(true)
            }

            override suspend fun logout() {
                currentUid = null
                _loggedIn.value = false
                _phone.value = null
                _authState.value = AuthState.Unauthenticated
                emergencyRepo.clearSession()
                chatRepo.clearSession()
                profileRepo.clearSession()
            }
        }

        val emergencyRepo = object : EmergencyRepository {
            override val activeRequest: StateFlow<EmergencyRequest?> = _activeRequest.asStateFlow()
            override val pendingReviewRequest: StateFlow<EmergencyRequest?> = _pendingReview.asStateFlow()
            override val requestHistory: StateFlow<List<EmergencyRequest>> = _history.asStateFlow()
            override val nearbyMechanics: StateFlow<List<Mechanic>> = _nearby.asStateFlow()
            override val isLoadingNearbyMechanics: StateFlow<Boolean> = _loadingNearby.asStateFlow()
            override val isDemoMode: Boolean = false
            private var counter = 0

            override suspend fun submitEmergencyRequest(request: EmergencyRequest): Result<EmergencyRequest> {
                val uid = currentUid ?: return Result.failure(IllegalStateException("Unauthenticated"))
                counter++
                val created = request.copy(id = "REQ_${uid}_$counter", status = JobStatus.REQUESTED)
                serverRequestsByUid.getOrPut(uid) { mutableListOf() }.add(0, created)
                serverChatsByJobId[created.id] = uid to mutableListOf()
                syncForUid(uid)
                return Result.success(created)
            }

            override suspend fun cancelActiveRequest(): Result<Unit> = Result.success(Unit)
            override suspend fun retrySearch(): Result<Unit> = Result.success(Unit)
            override suspend fun transitionJobStatus(nextStatus: JobStatus) = Unit
            override suspend fun completeJob(rating: Float, comment: String, paymentMethod: String): Result<Unit> = Result.success(Unit)
            override fun observeNearbyMechanics(latitude: Double, longitude: Double, radiusKm: Double) = Unit
            override fun stopObservingNearbyMechanics() = Unit

            override fun clearSession() {
                _activeRequest.value = null
                _pendingReview.value = null
                _history.value = emptyList()
                _nearby.value = emptyList()
            }
        }

        private fun syncForUid(uid: String) {
            val owned = serverRequestsByUid[uid].orEmpty().toList()
            _history.value = owned
            _activeRequest.value = owned.firstOrNull { !it.status.isTerminal }
        }

        val chatRepo = object : ChatRepository {
            override fun observeMessages(jobId: String): StateFlow<List<ChatMessage>> {
                val uid = currentUid ?: return MutableStateFlow(emptyList())
                val record = serverChatsByJobId[jobId]
                val authorizedMsgs = if (record != null && record.first == uid) record.second.toList() else emptyList()
                val flow = chatFlows.getOrPut(jobId) { MutableStateFlow(authorizedMsgs) }
                flow.value = authorizedMsgs
                return flow.asStateFlow()
            }

            override suspend fun sendMessage(
                jobId: String,
                text: String,
                isCustomer: Boolean,
                jobStatus: JobStatus?
            ): Result<ChatMessage> {
                val uid = currentUid ?: return Result.failure(IllegalStateException("Unauthenticated"))
                val record = serverChatsByJobId[jobId]
                    ?: return Result.failure(IllegalStateException("Not found"))
                if (record.first != uid) {
                    return Result.failure(SecurityException("Forbidden"))
                }
                val msg = ChatMessage(
                    id = "m_${record.second.size + 1}",
                    jobId = jobId,
                    senderName = "You",
                    message = text,
                    isCustomer = isCustomer
                )
                record.second.add(msg)
                chatFlows[jobId]?.value = record.second.toList()
                return Result.success(msg)
            }

            override fun isJobSessionActive(jobStatus: JobStatus?): Boolean = jobStatus?.isTerminal == false

            override fun clearSession() {
                chatFlows.values.forEach { it.value = emptyList() }
                chatFlows.clear()
            }
        }

        val profileRepo = object : ProfileRepository {
            override val profile: StateFlow<UserProfileData> = _profile.asStateFlow()
            override fun updateProfile(profile: UserProfileData) {
                val uid = currentUid ?: return
                serverProfilesByUid[uid] = profile
                _profile.value = profile
            }

            override fun toggleNumberMasking(enabled: Boolean) = Unit
            override fun toggleGpsSharing(enabled: Boolean) = Unit
            override fun setLanguage(langCode: String) = Unit
            override fun clearSession() {
                _profile.value = UserProfileData()
            }
        }

        val statusRepo = object : BackendStatusRepository {
            override val status: StateFlow<BackendIntegrationStatus> = _status.asStateFlow()
            override fun refreshStatus() = Unit
            override fun clearSession() = Unit
        }

        fun signInAs(uid: String, name: String, email: String) {
            emergencyRepo.clearSession()
            chatRepo.clearSession()
            profileRepo.clearSession()
            currentUid = uid
            val profile = serverProfilesByUid.getOrPut(uid) {
                UserProfileData(fullName = name, email = email)
            }
            _profile.value = profile
            syncForUid(uid)
        }
    }
}
