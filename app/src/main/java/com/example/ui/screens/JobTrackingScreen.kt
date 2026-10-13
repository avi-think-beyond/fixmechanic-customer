package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.JobStatus
import com.example.data.model.VehicleType
import com.example.ui.components.FixMechanicTopBar
import com.example.ui.components.JobTimelineView
import com.example.ui.theme.EmergencyOrange
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.Neutral100
import com.example.ui.theme.Neutral300
import com.example.ui.theme.Neutral500
import com.example.ui.theme.Neutral800
import com.example.ui.theme.Neutral900
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.EmergencyViewModel

@Composable
fun JobTrackingScreen(
    emergencyViewModel: EmergencyViewModel,
    onBackClick: () -> Unit,
    onOpenChat: (String) -> Unit,
    onJobCompleted: () -> Unit
) {
    val context = LocalContext.current
    val activeRequest by emergencyViewModel.activeRequest.collectAsState()
    val request = activeRequest
    val mechanic = request?.assignedMechanic

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .testTag("job_tracking_screen")
    ) {
        FixMechanicTopBar(
            title = "Rescue In Progress",
            onBackClick = onBackClick
        )

        Column(modifier = Modifier.padding(20.dp)) {

            // Header banner with request ID and live status
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyDark)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = request?.id ?: "FM-REQ-ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = PureWhite.copy(alpha = 0.7f),
                                letterSpacing = 1.sp
                            )
                        )

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = EmergencyOrange
                        ) {
                            Text(
                                text = request?.status?.label ?: "ASSIGNED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = request?.serviceItem?.title ?: "Emergency Repair",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${request?.vehicleMakeModel} • Base Fee ₹${request?.baseInspectionFee}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = PureWhite.copy(alpha = 0.85f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ASSIGNED MECHANIC PROFILE CARD
            if (mechanic != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(NavySurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mechanic.name.take(2).uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = PureWhite
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = mechanic.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = NavyDark
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Verified Partner",
                                        tint = Color(0xFF0284C7),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Rating",
                                        tint = WarningAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${mechanic.rating} (${mechanic.rescuesCompleted} rescues)",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = Neutral800
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = mechanic.vehicleInfo,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = Neutral500
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Neutral100)
                        Spacer(modifier = Modifier.height(14.dp))

                        // ETA & Distance
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Estimated Arrival",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Neutral500)
                                )
                                Text(
                                    text = if (request?.status == JobStatus.ARRIVED || request?.status == JobStatus.IN_PROGRESS)
                                        "Arrived on Spot"
                                    else
                                        "~${mechanic.estimatedArrivalMinutes} minutes (${mechanic.currentDistanceKm} km)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (request?.status == JobStatus.ARRIVED) SafetyGreen else EmergencyOrange
                                    )
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF1F5F9)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = SafetyGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Phone Number Masked",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Neutral800
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Communication Action Buttons: Masked Call & In-App Chat
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:18003494357"))
                                    context.startActivity(dialIntent)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("masked_call_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call",
                                    tint = NavyDark,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Private Call", fontWeight = FontWeight.Bold, color = NavyDark)
                            }

                            Button(
                                onClick = {
                                    onOpenChat(request?.id ?: "active_job")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("open_chat_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NavyDark)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = "Chat",
                                    tint = PureWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("In-App Chat", fontWeight = FontWeight.Bold, color = PureWhite)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5-STAGE TIMELINE VIEW
            JobTimelineView(currentStatus = request?.status ?: JobStatus.ASSIGNED)

            Spacer(modifier = Modifier.height(24.dp))

            // DEV MODE STATE CONTROLLER
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛠️ Prototype Lifecycle Controller (Dev Mode)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Advance job status to test all 5 workflow states without backend dispatch delay:",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Neutral500)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { emergencyViewModel.devAdvanceStatus(JobStatus.ACCEPTED) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Accept", fontSize = 10.sp, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { emergencyViewModel.devAdvanceStatus(JobStatus.EN_ROUTE) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("En Route", fontSize = 10.sp, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { emergencyViewModel.devAdvanceStatus(JobStatus.ARRIVED) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Arrived", fontSize = 10.sp, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { emergencyViewModel.devAdvanceStatus(JobStatus.IN_PROGRESS) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Repairing", fontSize = 10.sp, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            emergencyViewModel.devAdvanceStatus(JobStatus.COMPLETED)
                            onJobCompleted()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("simulate_complete_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyGreen)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simulate Job Completion & Open Rating Screen", fontWeight = FontWeight.Bold, color = PureWhite, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
