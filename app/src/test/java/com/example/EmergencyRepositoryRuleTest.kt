package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.BackendApiClient
import com.example.data.FirestoreProvider.awaitResult
import com.example.data.datasource.ServiceCatalog
import com.example.data.model.EmergencyRequest
import com.example.data.model.JobStatus
import com.example.data.model.LocationData
import com.example.data.model.VehicleType
import com.example.data.repository.FirestoreEmergencyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.util.UUID

class EmergencyRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun seedCustomerUser(uid: String, email: String) {
        seedFirestoreDocViaRest(
            "users",
            uid,
            """{"uid":{"stringValue":"$uid"},"role":{"stringValue":"CUSTOMER"},"email":{"stringValue":"$email"},"disabled":{"booleanValue":false}}"""
        )
    }

    private fun seedApprovedOnlineMechanic(uid: String, name: String) {
        val now = System.currentTimeMillis()
        seedFirestoreDocViaRest(
            "users",
            uid,
            """{"uid":{"stringValue":"$uid"},"role":{"stringValue":"MECHANIC"},"email":{"stringValue":"$uid@test.com"},"disabled":{"booleanValue":false}}"""
        )
        seedFirestoreDocViaRest(
            "mechanicProfiles",
            uid,
            """{"mechanicId":{"stringValue":"$uid"},"name":{"stringValue":"$name"},"phone":{"stringValue":"9876543210"},"approvalStatus":{"stringValue":"APPROVED"},"isOnline":{"booleanValue":true},"isBusy":{"booleanValue":false},"vehicleTypes":{"arrayValue":{"values":[{"stringValue":"TWO_WHEELER"},{"stringValue":"FOUR_WHEELER_CAR"}]}},"serviceCategories":{"arrayValue":{"values":[{"stringValue":"FLAT_TYRE"},{"stringValue":"BATTERY_JUMPSTART"}]}},"serviceRadiusKm":{"integerValue":"15"},"currentLocation":{"mapValue":{"fields":{"lat":{"doubleValue":28.4595},"lng":{"doubleValue":77.0266},"address":{"stringValue":"Sector 29 Gurugram"},"updatedAt":{"integerValue":"$now"}}}}}"""
        )
    }

    @Test
    fun submitEmergencyRequest_bikeFlatTyre_dispatchesOfferToNearbyOnlineMechanic() = runBlocking(Dispatchers.IO) {
        Assume.assumeTrue("Emulators & Spark backend must run on 8085/9099/5055", areEmulatorsRunning())
        withTimeout(10000L) {
            val mechEmail = "mech_${UUID.randomUUID().toString().take(6)}@test.com"
            val mechUid = signInTestUser(mechEmail)
            seedApprovedOnlineMechanic(mechUid, "Ravi Bike Expert")

            val custEmail = "alice_${UUID.randomUUID().toString().take(6)}@test.com"
            val custUid = signInTestUser(custEmail)
            seedCustomerUser(custUid, custEmail)

            val repo = FirestoreEmergencyRepository(
                db = firestore,
                auth = auth,
                backendApiClient = BackendApiClient(auth, "http://127.0.0.1:$SPARK_PORT")
            )

            // Wait for online mechanic visibility snapshot
            var attempts = 0
            while (repo.nearbyMechanics.value.isEmpty() && attempts++ < 30) {
                delay(100)
            }
            assertTrue("Customer must see nearby online approved mechanic", repo.nearbyMechanics.value.any { it.id == mechUid })

            val bikeTyreService = ServiceCatalog.bikeServices.first { it.id == "bike_puncture" }
            val req = EmergencyRequest(
                id = "",
                vehicleType = VehicleType.BIKE,
                serviceItem = bikeTyreService,
                vehicleMakeModel = "Royal Enfield Classic 350",
                registrationNumber = "HR 26 AB 1234",
                issueDescription = "Rear tubeless tyre punctured near market",
                location = LocationData(
                    latitude = 28.4600,
                    longitude = 77.0270,
                    address = "Sector 29 Market, Gurugram",
                    isObtained = true
                ),
                baseInspectionFee = bikeTyreService.estimatedPriceInr
            )

            val result = repo.submitEmergencyRequest(req)
            assertTrue("submitEmergencyRequest failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
            val created = result.getOrThrow()
            assertTrue(created.id.isNotEmpty())
            assertEquals(JobStatus.SEARCHING, created.status)

            // Switch to mechanic account to verify zero-trust targeted jobOffer receipt
            auth.signInWithEmailAndPassword(mechEmail, DEFAULT_PASSWORD).awaitResult()
            val offerSnap = firestore.collection("jobOffers")
                .whereEqualTo("requestId", created.id)
                .whereEqualTo("mechanicId", mechUid)
                .get()
                .awaitResult()
            assertTrue("Expected PENDING jobOffer for online mechanic", !offerSnap.isEmpty)
            assertEquals("PENDING", offerSnap.documents.first().getString("status"))
            assertEquals("FLAT_TYRE", offerSnap.documents.first().getString("serviceCategory"))
        }
    }

    @Test
    fun submitEmergencyRequest_unauthenticated_fails() = runBlocking(Dispatchers.IO) {
        Assume.assumeTrue("Emulators & Spark backend must run on 8085/9099/5055", areEmulatorsRunning())
        withTimeout(10000L) {
            auth.signOut()
            val repo = FirestoreEmergencyRepository(
                db = firestore,
                auth = auth,
                backendApiClient = BackendApiClient(auth, "http://127.0.0.1:$SPARK_PORT")
            )
            val service = ServiceCatalog.bikeServices.first()
            val req = EmergencyRequest(
                id = "",
                vehicleType = VehicleType.BIKE,
                serviceItem = service,
                vehicleMakeModel = "Bajaj Pulsar",
                registrationNumber = "UP 16 CD 5678",
                issueDescription = "Flat rear tyre",
                location = LocationData(latitude = 28.46, longitude = 77.02, address = "Gurugram", isObtained = true)
            )
            val result = repo.submitEmergencyRequest(req)
            assertTrue(result.isFailure)
        }
    }

    @Test
    fun cancelActiveRequest_authenticated_cancelsRequest() = runBlocking(Dispatchers.IO) {
        Assume.assumeTrue("Emulators & Spark backend must run on 8085/9099/5055", areEmulatorsRunning())
        withTimeout(10000L) {
            val email = "bob_${UUID.randomUUID().toString().take(6)}@test.com"
            val uid = signInTestUser(email)
            seedCustomerUser(uid, email)

            val repo = FirestoreEmergencyRepository(
                db = firestore,
                auth = auth,
                backendApiClient = BackendApiClient(auth, "http://127.0.0.1:$SPARK_PORT")
            )
            val service = ServiceCatalog.carServices.first()
            val req = EmergencyRequest(
                id = "",
                vehicleType = VehicleType.CAR,
                serviceItem = service,
                vehicleMakeModel = "Honda City",
                registrationNumber = "HR 26 EF 9012",
                issueDescription = "Locked keys inside",
                location = LocationData(
                    latitude = 28.4950,
                    longitude = 77.0890,
                    address = "Cyber Hub, Gurgaon",
                    isObtained = true
                )
            )
            val createResult = repo.submitEmergencyRequest(req)
            assertTrue("Create failed: ${createResult.exceptionOrNull()?.message}", createResult.isSuccess)
            val cancelResult = repo.cancelActiveRequest()
            assertTrue("Cancel failed: ${cancelResult.exceptionOrNull()?.message}", cancelResult.isSuccess)
        }
    }
}
