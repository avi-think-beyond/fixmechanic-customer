package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EmergencyRequest
import com.example.data.model.JobStatus
import com.example.data.model.VehicleType
import com.example.ui.components.FixMechanicTopBar
import com.example.ui.theme.EmergencyOrange
import com.example.ui.theme.NavyDark
import com.example.ui.theme.Neutral100
import com.example.ui.theme.Neutral300
import com.example.ui.theme.Neutral500
import com.example.ui.theme.Neutral800
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.EmergencyViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ServiceHistoryScreen(
    emergencyViewModel: EmergencyViewModel,
    onBookServiceAgain: (VehicleType) -> Unit
) {
    val history by emergencyViewModel.requestHistory.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("service_history_screen")
    ) {
        FixMechanicTopBar(
            title = "Rescue Activity & History"
        )

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Neutral500,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Past Emergency Requests",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = NavyDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "When you request emergency mechanic help, records and invoices will appear here.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Neutral500),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Past Roadside Rescues (${history.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = NavyDark
                    )
                }

                items(history) { item ->
                    HistoryItemCard(
                        request = item,
                        onRebook = { onBookServiceAgain(item.vehicleType) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    request: EmergencyRequest,
    onRebook: () -> Unit
) {
    val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(request.createdAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_card_${request.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (request.vehicleType == VehicleType.BIKE) Color(0xFFFFECE5) else Color(0xFFE0F2FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (request.vehicleType == VehicleType.BIKE) Icons.Default.TwoWheeler else Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = if (request.vehicleType == VehicleType.BIKE) EmergencyOrange else Color(0xFF0284C7),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = request.serviceItem.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = NavyDark
                        )
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.labelSmall.copy(color = Neutral500, fontSize = 10.sp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (request.status == JobStatus.COMPLETED) Color(0xFFECFDF5) else Color(0xFFFFFBEB)
                ) {
                    Text(
                        text = if (request.status == JobStatus.COMPLETED) "Completed" else request.status.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (request.status == JobStatus.COMPLETED) SafetyGreen else WarningAmber,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Neutral100)
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "${request.vehicleMakeModel} (${request.registrationNumber.ifBlank { "Unregistered" }})",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = Neutral800)
            )

            Text(
                text = "Location: ${request.location.address}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Neutral500)
            )

            if (request.assignedMechanic != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Mechanic: ${request.assignedMechanic.name}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Neutral500)
                    )
                    if (request.ratingGiven != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${request.ratingGiven}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Neutral800)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Paid: ₹${request.totalPaid.takeIf { it > 0 } ?: request.baseInspectionFee}",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = NavyDark
                    )
                )

                OutlinedButton(
                    onClick = onRebook,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Request Again", fontSize = 11.sp, color = NavyDark, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
