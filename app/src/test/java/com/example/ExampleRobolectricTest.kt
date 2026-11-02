package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.datasource.ServiceCatalog
import com.example.data.model.VehicleType
import com.example.data.repository.RuleBasedAiDiagnosisRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("FixMechanic Customer", appName)
    }

    @Test
    fun `verify bike services count`() {
        val bikeServices = ServiceCatalog.getServices(VehicleType.BIKE)
        assertEquals(10, bikeServices.size)
    }

    @Test
    fun `verify car services count`() {
        val carServices = ServiceCatalog.getServices(VehicleType.CAR)
        assertEquals(10, carServices.size)
    }

    @Test
    fun `verify ai problem diagnosis classification`() = runBlocking {
        val repo = RuleBasedAiDiagnosisRepository()
        val result = repo.diagnoseIssue("My car tyre has a nail and is completely flat", VehicleType.CAR)
        assertTrue(result.isSuccess)
        val diag = result.getOrNull()
        assertNotNull(diag)
        assertEquals("car_tyre", diag?.suggestedService?.id)
    }
}
