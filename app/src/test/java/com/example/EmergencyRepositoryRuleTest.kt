package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.datasource.ServiceCatalog
import com.example.data.model.EmergencyRequest
import com.example.data.model.JobStatus
import com.example.data.model.LocationData
import com.example.data.model.VehicleType
import com.example.data.repository.FirestoreEmergencyRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun submitEmergencyRequest_authenticated_succeedsAndReturnsId() = runBlocking {
    signInTestUser("alice_customer@example.com")
    val repo = FirestoreEmergencyRepository(firestore, auth)

    val service = ServiceCatalog.carServices.first()
    val req = EmergencyRequest(
      id = "",
      vehicleType = VehicleType.CAR,
      serviceItem = service,
      vehicleMakeModel = "Hyundai i20",
      registrationNumber = "DL 01 AB 1234",
      issueDescription = "Battery dead, car won't crank",
      location = LocationData(
        latitude = 28.5355,
        longitude = 77.3910,
        address = "Sector 62, Noida",
        isObtained = true
      ),
      baseInspectionFee = service.estimatedPriceInr
    )

    val result = withTimeout(10000L) {
      repo.submitEmergencyRequest(req)
    }

    assertTrue(result.isSuccess)
    val created = result.getOrThrow()
    assertTrue(created.id.isNotEmpty())
    assertEquals(JobStatus.REQUESTED, created.status)
  }

  @Test
  fun submitEmergencyRequest_unauthenticated_fails() = runBlocking {
    auth.signOut()
    val repo = FirestoreEmergencyRepository(firestore, auth)

    val service = ServiceCatalog.bikeServices.first()
    val req = EmergencyRequest(
      id = "",
      vehicleType = VehicleType.BIKE,
      serviceItem = service,
      vehicleMakeModel = "Bajaj Pulsar",
      registrationNumber = "UP 16 CD 5678",
      issueDescription = "Flat rear tyre",
      location = LocationData(address = "Noida Expressway", isObtained = true)
    )

    val result = repo.submitEmergencyRequest(req)
    assertTrue(result.isFailure)
  }

  @Test
  fun cancelActiveRequest_authenticated_cancelsRequest() = runBlocking {
    signInTestUser("bob_customer@example.com")
    val repo = FirestoreEmergencyRepository(firestore, auth)

    val service = ServiceCatalog.carServices.first()
    val req = EmergencyRequest(
      id = "",
      vehicleType = VehicleType.CAR,
      serviceItem = service,
      vehicleMakeModel = "Honda City",
      registrationNumber = "HR 26 EF 9012",
      issueDescription = "Locked keys inside",
      location = LocationData(address = "Cyber Hub, Gurgaon", isObtained = true)
    )

    val createResult = repo.submitEmergencyRequest(req)
    assertTrue(createResult.isSuccess)

    val cancelResult = repo.cancelActiveRequest()
    assertTrue(cancelResult.isSuccess)
  }
}
