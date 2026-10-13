package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.datasource.ServiceCatalog
import com.example.data.model.VehicleType
import com.example.ui.components.FixMechanicTopBar
import com.example.ui.components.ServiceCategoryCard
import com.example.ui.components.VehicleSelectionCard
import com.example.ui.theme.EmergencyOrange
import com.example.ui.theme.NavyDark
import com.example.ui.theme.Neutral500
import com.example.ui.theme.PureWhite
import com.example.ui.viewmodel.EmergencyViewModel

@Composable
fun VehicleSelectionScreen(
    emergencyViewModel: EmergencyViewModel,
    onBackClick: () -> Unit,
    onVehicleSelected: () -> Unit
) {
    val state by emergencyViewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("vehicle_selection_screen")
    ) {
        FixMechanicTopBar(
            title = "Select Vehicle Type",
            onBackClick = onBackClick
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "What vehicle requires emergency help?",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "We dispatch certified mechanics equipped with tailored bike or car toolkit kits.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Neutral500)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Bike Option
                VehicleSelectionCard(
                    vehicleType = VehicleType.BIKE,
                    isSelected = state.selectedVehicleType == VehicleType.BIKE,
                    onClick = { emergencyViewModel.selectVehicleType(VehicleType.BIKE) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Car Option
                VehicleSelectionCard(
                    vehicleType = VehicleType.CAR,
                    isSelected = state.selectedVehicleType == VehicleType.CAR,
                    onClick = { emergencyViewModel.selectVehicleType(VehicleType.CAR) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Popular Fleet Note
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = if (state.selectedVehicleType == VehicleType.BIKE)
                                "Supported: Scooters, Commuter 100-150cc, Royal Enfield, Cruisers, EV 2-Wheelers"
                            else
                                "Supported: Hatchbacks, Sedans, Compact SUVs, Luxury Cars, EV Four-Wheelers",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Neutral500,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        )
                    }
                }
            }

            Button(
                onClick = onVehicleSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_vehicle_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyOrange)
            ) {
                Text(
                    text = "Continue to Services",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                )
            }
        }
    }
}

@Composable
fun ServiceSelectionScreen(
    emergencyViewModel: EmergencyViewModel,
    onBackClick: () -> Unit,
    onServiceConfirmed: () -> Unit,
    onOpenAiDiagnoser: () -> Unit
) {
    val state by emergencyViewModel.uiState.collectAsState()
    val availableServices = ServiceCatalog.getServices(state.selectedVehicleType)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("service_selection_screen")
    ) {
        FixMechanicTopBar(
            title = if (state.selectedVehicleType == VehicleType.BIKE) "Bike Services" else "Car Services",
            onBackClick = onBackClick
        )

        // Vehicle Switch Tabs
        TabRow(
            selectedTabIndex = if (state.selectedVehicleType == VehicleType.BIKE) 0 else 1,
            containerColor = PureWhite,
            contentColor = EmergencyOrange,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[if (state.selectedVehicleType == VehicleType.BIKE) 0 else 1]),
                    color = EmergencyOrange
                )
            }
        ) {
            Tab(
                selected = state.selectedVehicleType == VehicleType.BIKE,
                onClick = { emergencyViewModel.selectVehicleType(VehicleType.BIKE) },
                text = {
                    Text(
                        text = "🏍️ Bike Services (${ServiceCatalog.bikeServices.size})",
                        fontWeight = FontWeight.Bold,
                        color = if (state.selectedVehicleType == VehicleType.BIKE) EmergencyOrange else Neutral500
                    )
                }
            )
            Tab(
                selected = state.selectedVehicleType == VehicleType.CAR,
                onClick = { emergencyViewModel.selectVehicleType(VehicleType.CAR) },
                text = {
                    Text(
                        text = "🚗 Car Services (${ServiceCatalog.carServices.size})",
                        fontWeight = FontWeight.Bold,
                        color = if (state.selectedVehicleType == VehicleType.CAR) EmergencyOrange else Neutral500
                    )
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Choose Breakdown Category",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                        )
                        Text(
                            text = "Transparent upfront pricing • Certified tools included",
                            style = MaterialTheme.typography.bodySmall.copy(color = Neutral500)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            items(availableServices) { service ->
                ServiceCategoryCard(
                    service = service,
                    isSelected = state.selectedService?.id == service.id,
                    onClick = { emergencyViewModel.selectService(service) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                // AI Help Prompt
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7D2FE))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Can't find your exact problem?",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF3730A3)
                                )
                            )
                            Text(
                                text = "Use our AI Assistant to classify your vehicle symptom automatically.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = Color(0xFF4338CA)
                                )
                            )
                        }
                        Button(
                            onClick = onOpenAiDiagnoser,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Try AI", fontSize = 12.sp, color = PureWhite)
                        }
                    }
                }
            }
        }

        // Bottom Continue Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Selected: ${state.selectedService?.title ?: "None"}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = NavyDark
                        )
                        Text(
                            text = "Base Inspection: ₹${state.selectedService?.estimatedPriceInr ?: 0}",
                            style = MaterialTheme.typography.bodySmall.copy(color = EmergencyOrange, fontWeight = FontWeight.SemiBold)
                        )
                    }

                    Button(
                        onClick = onServiceConfirmed,
                        enabled = state.selectedService != null,
                        modifier = Modifier.testTag("continue_to_location_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyOrange)
                    ) {
                        Text("Next: Location", fontWeight = FontWeight.Bold, color = PureWhite)
                    }
                }
            }
        }
    }
}
