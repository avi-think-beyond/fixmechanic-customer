package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.JobStatus
import com.example.data.model.VehicleType
import com.example.ui.components.BackendNoticeBanner
import com.example.ui.components.EmergencyActionButton
import com.example.ui.theme.EmergencyOrange
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.Neutral100
import com.example.ui.theme.Neutral500
import com.example.ui.theme.Neutral800
import com.example.ui.theme.Neutral900
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.EmergencyViewModel

@Composable
fun HomeScreen(
    emergencyViewModel: EmergencyViewModel,
    onStartEmergency: (VehicleType) -> Unit,
    onOpenActiveJob: () -> Unit,
    onOpenAiDiagnoser: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBackendStatus: () -> Unit
) {
    val emergencyState by emergencyViewModel.uiState.collectAsState()
    val activeRequest by emergencyViewModel.activeRequest.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .testTag("home_screen")
    ) {
        // Top Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavyDark)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "FixMechanic Logo",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "FixMechanic",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PureWhite
                                )
                            )
                            Text(
                                text = "Emergency Assistance Network",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = EmergencyOrange,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    // 24/7 SOS Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = NavySurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmergencyOrange.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(EmergencyOrange)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "24/7 ACTIVE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Location Pill
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = NavySurface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = EmergencyOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Current Dispatch Location",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = PureWhite.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            )
                            Text(
                                text = "${emergencyState.location.address}, ${emergencyState.location.city}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = PureWhite
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(20.dp)) {

            // Prototype Notice
            BackendNoticeBanner(onClickInspect = onOpenBackendStatus)

            Spacer(modifier = Modifier.height(16.dp))

            // Active Request Alert Banner (if there's a live request)
            if (activeRequest != null && activeRequest?.status != JobStatus.COMPLETED && activeRequest?.status != JobStatus.CANCELLED) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenActiveJob() }
                        .testTag("active_request_banner"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, EmergencyOrange)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(EmergencyOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "Active Job",
                                tint = PureWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active Emergency Request",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = NavyDark
                            )
                            Text(
                                text = "${activeRequest?.serviceItem?.title} • Status: ${activeRequest?.status?.label}",
                                style = MaterialTheme.typography.bodySmall.copy(color = EmergencyOrange, fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "View",
                            tint = NavyDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // PRIMARY EMERGENCY ACTION BUTTON
            EmergencyActionButton(
                text = stringResource(id = R.string.emergency_help_title),
                subtitle = stringResource(id = R.string.emergency_help_subtitle),
                onClick = { onStartEmergency(emergencyState.selectedVehicleType) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // VEHICLE SELECTION SHORTCUT
            Text(
                text = stringResource(id = R.string.quick_select_vehicle),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavyDark
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bike Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            emergencyViewModel.selectVehicleType(VehicleType.BIKE)
                            onStartEmergency(VehicleType.BIKE)
                        }
                        .testTag("home_select_bike"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFECE5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TwoWheeler,
                                contentDescription = "Bike",
                                tint = EmergencyOrange,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Bike / Scooter",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = NavyDark
                        )
                        Text(
                            text = "10 Services • From ₹199",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Neutral500
                        )
                    }
                }

                // Car Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            emergencyViewModel.selectVehicleType(VehicleType.CAR)
                            onStartEmergency(VehicleType.CAR)
                        }
                        .testTag("home_select_car"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE0F2FE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = "Car",
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Car / SUV",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = NavyDark
                        )
                        Text(
                            text = "10 Services • From ₹299",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Neutral500
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // AI ASSISTANT DIAGNOSIS BANNER
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAiDiagnoser() }
                    .testTag("home_ai_diagnoser_banner"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF818CF8)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEEF2FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Diagnosis",
                            tint = Color(0xFF4F46E5),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Smart Problem Diagnoser",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = NavyDark
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFEEF2FF)
                            ) {
                                Text(
                                    text = "AI HELPER",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4F46E5)
                                    ),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Unsure what happened? Describe your noise or issue for instant service advice.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = Neutral500
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open",
                        tint = Color(0xFF4F46E5)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // POPULAR EMERGENCY SERVICES STRIP
            Text(
                text = "Rapid Response Emergencies",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavyDark
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RapidServiceRow(
                    title = "Dead Battery Jump-Start",
                    subtitle = "High-amp cables & booster pack",
                    price = "₹249",
                    onClick = {
                        emergencyViewModel.selectVehicleType(VehicleType.CAR)
                        onStartEmergency(VehicleType.CAR)
                    }
                )
                RapidServiceRow(
                    title = "Tyre Puncture & Air Refill",
                    subtitle = "Tubeless plug or spare tyre change",
                    price = "₹199",
                    onClick = {
                        emergencyViewModel.selectVehicleType(VehicleType.BIKE)
                        onStartEmergency(VehicleType.BIKE)
                    }
                )
                RapidServiceRow(
                    title = "Emergency Fuel On-Call",
                    subtitle = "Certified canister delivery to spot",
                    price = "₹199",
                    onClick = {
                        emergencyViewModel.selectVehicleType(VehicleType.CAR)
                        onStartEmergency(VehicleType.CAR)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 24x7 HELPLINE BANNER
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NavySurface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(EmergencyOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "Helpline",
                            tint = PureWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Roadside Emergency Helpline",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        )
                        Text(
                            text = "1800-349-4357 (1800-FIX-HELP) • Toll Free",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = PureWhite.copy(alpha = 0.8f)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RapidServiceRow(
    title: String,
    subtitle: String,
    price: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF8FAFC)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = NavyDark,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = NavyDark
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Neutral500
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFECFDF5)
            ) {
                Text(
                    text = price,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF047857)
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
