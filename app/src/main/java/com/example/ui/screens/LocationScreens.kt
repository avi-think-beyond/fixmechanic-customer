package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.location.LocationHelper
import com.example.ui.components.FixMechanicTopBar
import com.example.ui.theme.EmergencyOrange
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.NavyDark
import com.example.ui.theme.Neutral300
import com.example.ui.theme.Neutral500
import com.example.ui.theme.Neutral900
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.EmergencyViewModel

@Composable
fun LocationScreen(
    emergencyViewModel: EmergencyViewModel,
    onBackClick: () -> Unit,
    onLocationConfirmed: () -> Unit
) {
    val context = LocalContext.current
    val state by emergencyViewModel.uiState.collectAsState()
    val locationHelper = LocationHelper(context)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val isGranted = fineGranted || coarseGranted

        emergencyViewModel.onLocationPermissionResult(isGranted, context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .testTag("location_screen")
    ) {
        FixMechanicTopBar(
            title = stringResource(id = R.string.location_screen_title),
            onBackClick = onBackClick
        )

        Column(modifier = Modifier.padding(20.dp)) {

            Text(
                text = "Where is your vehicle stranded?",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavyDark
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "The dispatched mechanic will navigate directly to this spot. Ensure landmark is clearly noted.",
                style = MaterialTheme.typography.bodyMedium.copy(color = Neutral500)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Location Error or GPS Disabled Alert Banner
            AnimatedVisibility(visible = state.locationErrorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFEDD5))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Location Status Notice",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF9A3412)
                                )
                            )
                            Text(
                                text = state.locationErrorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF7C2D12),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            // GPS Fetch Button & Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (state.location.isObtained && state.location.isGpsAccurate) SafetyGreen else EmergencyOrange
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.location.isObtained && state.location.isGpsAccurate)
                                        SafetyGreen.copy(alpha = 0.15f)
                                    else
                                        EmergencyOrange.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (state.location.isObtained && state.location.isGpsAccurate)
                                    Icons.Default.CheckCircle
                                else
                                    Icons.Default.GpsFixed,
                                contentDescription = "GPS",
                                tint = if (state.location.isObtained && state.location.isGpsAccurate) SafetyGreen else EmergencyOrange,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.location.isObtained && state.location.isGpsAccurate)
                                    "GPS Location Acquired"
                                else
                                    "Detect GPS Breakdown Location",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = NavyDark
                            )
                            Text(
                                text = if (state.location.isObtained && state.location.isGpsAccurate)
                                    "Accuracy: High (Satellites Locked)"
                                else
                                    "Uses device GPS for on-road rescue navigation",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Neutral500)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "We never estimate or fake locations. If GPS is unavailable, please enter your breakdown address manually.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = Neutral500)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (locationHelper.hasLocationPermission()) {
                                emergencyViewModel.fetchGpsLocation(context)
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        enabled = !state.isLocationLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("request_gps_permission_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (state.isLocationLoading) {
                            CircularProgressIndicator(
                                color = PureWhite,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Detecting Satellites...", fontWeight = FontWeight.Bold, color = PureWhite)
                        } else {
                            Icon(
                                imageVector = if (state.location.isObtained) Icons.Default.Refresh else Icons.Default.NearMe,
                                contentDescription = null,
                                tint = PureWhite
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.location.isObtained) "Re-detect GPS Location" else stringResource(id = R.string.btn_allow_location),
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { emergencyViewModel.enableManualLocation(true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("enter_manual_address_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditLocation,
                            contentDescription = null,
                            tint = NavyDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.isManualLocationMode) "Edit Manual Address" else stringResource(id = R.string.btn_manual_address),
                            color = NavyDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Manual Address Entry Form
            if (state.isManualLocationMode) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EditLocation,
                                contentDescription = null,
                                tint = NavyDark
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Enter Address Manually",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = NavyDark
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Road, Sector, Highway or Area *",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Neutral900
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = state.manualAddressInput,
                            onValueChange = { emergencyViewModel.onManualAddressChanged(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_address_input"),
                            placeholder = { Text("e.g. NH-48 Highway, 2km before Kherki Daula Toll") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NavyDark,
                                unfocusedBorderColor = Neutral300
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = stringResource(id = R.string.address_landmark_label),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Neutral900
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = state.manualLandmarkInput,
                            onValueChange = { emergencyViewModel.onManualLandmarkChanged(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_landmark_input"),
                            placeholder = { Text("e.g. Opposite Indian Oil Petrol Pump / Dhaba") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NavyDark,
                                unfocusedBorderColor = Neutral300
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { emergencyViewModel.saveManualLocation() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_manual_location_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NavyDark),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save Breakdown Location", fontWeight = FontWeight.Bold, color = PureWhite)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Confirmed Pin Display Card
            if (state.location.isObtained || state.location.address.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column {
                        // Map Pin Canvas Header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val step = 36.dp.toPx()
                                for (x in 0..(size.width / step).toInt()) {
                                    drawLine(
                                        color = Color(0xFFCBD5E1),
                                        start = Offset(x * step, 0f),
                                        end = Offset(x * step, size.height),
                                        strokeWidth = 1f
                                    )
                                }
                                for (y in 0..(size.height / step).toInt()) {
                                    drawLine(
                                        color = Color(0xFFCBD5E1),
                                        start = Offset(0f, y * step),
                                        end = Offset(size.width, y * step),
                                        strokeWidth = 1f
                                    )
                                }
                                drawLine(
                                    color = Color(0xFF94A3B8),
                                    start = Offset(0f, size.height * 0.6f),
                                    end = Offset(size.width, size.height * 0.4f),
                                    strokeWidth = 7f
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = NavyDark,
                                    shadowElevation = 4.dp
                                ) {
                                    Text(
                                        text = if (state.location.isGpsAccurate) "GPS Satellite Pin" else "Manual Pin",
                                        color = PureWhite,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Pin",
                                    tint = EmergencyRed,
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                        }

                        // Address Details Block
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Confirmed Dispatch Location",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Neutral500,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = state.location.address,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = NavyDark
                                    )
                                    if (state.location.landmark.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Landmark: ${state.location.landmark}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = Neutral500)
                                        )
                                    }
                                    if (state.location.latitude != null && state.location.longitude != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "GPS: ${"%.4f".format(state.location.latitude)}, ${"%.4f".format(state.location.longitude)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Neutral500)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { emergencyViewModel.enableManualLocation(true) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditLocation,
                                        contentDescription = "Edit Address",
                                        tint = EmergencyOrange
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Confirm Location and proceed to vehicle details
            val isLocationReady = state.location.isObtained || state.location.address.isNotBlank()

            Button(
                onClick = onLocationConfirmed,
                enabled = isLocationReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_location_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyOrange)
            ) {
                Text(
                    text = if (isLocationReady) "Confirm Location & Proceed" else "Provide Location to Proceed",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                )
            }
        }
    }
}
