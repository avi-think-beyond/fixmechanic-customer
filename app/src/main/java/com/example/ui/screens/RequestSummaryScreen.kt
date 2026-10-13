package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.VehicleType
import com.example.ui.components.FixMechanicTopBar
import com.example.ui.components.SafetyGuidelineCard
import com.example.ui.theme.EmergencyOrange
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.Neutral100
import com.example.ui.theme.Neutral300
import com.example.ui.theme.Neutral500
import com.example.ui.theme.Neutral900
import com.example.ui.theme.PureWhite
import com.example.ui.viewmodel.EmergencyViewModel

@Composable
fun RequestSummaryScreen(
    emergencyViewModel: EmergencyViewModel,
    onBackClick: () -> Unit,
    onConfirmedDispatched: (String) -> Unit
) {
    val state by emergencyViewModel.uiState.collectAsState()
    val service = state.selectedService

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .testTag("request_summary_screen")
    ) {
        FixMechanicTopBar(
            title = stringResource(id = R.string.summary_title),
            onBackClick = onBackClick
        )

        Column(modifier = Modifier.padding(20.dp)) {

            Text(
                text = "Review Emergency Request",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavyDark
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "A certified mechanic will be mobilized immediately upon confirmation.",
                style = MaterialTheme.typography.bodyMedium.copy(color = Neutral500)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Service & Vehicle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(EmergencyOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (state.selectedVehicleType == VehicleType.BIKE)
                                    Icons.Default.TwoWheeler
                                else
                                    Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = PureWhite,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = service?.title ?: "Emergency Service",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = NavyDark
                            )
                            Text(
                                text = "${state.selectedVehicleType.displayName} • ${state.vehicleMakeModel}",
                                style = MaterialTheme.typography.bodySmall.copy(color = Neutral500)
                            )
                            if (state.registrationNumber.isNotBlank()) {
                                Text(
                                    text = "Reg: ${state.registrationNumber}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Neutral500, fontSize = 11.sp)
                                )
                            }
                        }
                    }

                    if (state.issueDescription.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Neutral100)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Customer Note: \"${state.issueDescription}\"",
                            style = MaterialTheme.typography.bodySmall.copy(color = Neutral500, fontSize = 12.sp)
                        )
                    }

                    if (state.selectedPhotoUri != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            coil.compose.AsyncImage(
                                model = state.selectedPhotoUri,
                                contentDescription = "Attached damage photo",
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Photo attached for arriving mechanic",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Neutral900
                                )
                            )
                        }
                    }
                }
            }

            // Submission Error Banner
            if (state.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Text(
                        text = state.errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(color = EmergencyRed),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Location Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE0F2FE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Dispatch Location",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Neutral500
                                )
                            )
                            Text(
                                text = state.location.address,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = NavyDark
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Landmark: ${state.location.landmark}",
                        style = MaterialTheme.typography.bodySmall.copy(color = Neutral500, fontSize = 12.sp),
                        modifier = Modifier.padding(start = 48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transparent Pricing Estimate Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                tint = NavyDark,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pricing Breakdown",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = NavyDark
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE0F2FE)
                        ) {
                            Text(
                                text = "No Hidden Surcharges",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0369A1),
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Base Inspection & Roadside Dispatch",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Neutral500
                        )
                        Text(
                            text = "₹${service?.estimatedPriceInr ?: 249}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = NavyDark
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Standard Toolkit & Diagnostics Fee",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Neutral500
                        )
                        Text(
                            text = "₹0 (Included)",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF059669)),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Neutral100)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Estimated Upfront Total",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = NavyDark
                        )
                        Text(
                            text = "₹${service?.estimatedPriceInr ?: 249}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = EmergencyOrange
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "*Any spare parts (tubes, fuses, petrol litres) will be billed at actual retail cost with customer pre-approval.",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Neutral500)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Safety Guidelines
            SafetyGuidelineCard()

            Spacer(modifier = Modifier.height(28.dp))

            // Dispatch Button
            Button(
                onClick = {
                    emergencyViewModel.submitEmergencyRequest { requestId ->
                        onConfirmedDispatched(requestId)
                    }
                },
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("confirm_and_dispatch_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyOrange)
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        color = PureWhite,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = PureWhite
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.btn_confirm_dispatch),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        )
                    }
                }
            }
        }
    }
}
