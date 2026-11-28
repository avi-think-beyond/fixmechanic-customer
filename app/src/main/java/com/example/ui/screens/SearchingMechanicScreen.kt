package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.location.NearbyMechanicMatcher
import com.example.data.model.JobStatus
import com.example.data.model.Mechanic
import com.example.ui.components.LiveDispatchMap
import com.example.ui.theme.EmergencyOrange
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.Neutral500
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.EmergencyViewModel
import kotlinx.coroutines.delay

@Composable
fun SearchingMechanicScreen(
    emergencyViewModel: EmergencyViewModel,
    onMechanicAssigned: () -> Unit,
    onCancelled: () -> Unit
) {
    val activeRequest by emergencyViewModel.activeRequest.collectAsState()
    val emergencyUiState by emergencyViewModel.uiState.collectAsState()
    val rawNearbyMechanics by emergencyViewModel.nearbyMechanics.collectAsState()
    val status = activeRequest?.status

    val customerLat = activeRequest?.location?.latitude ?: emergencyUiState.location.latitude
    val customerLng = activeRequest?.location?.longitude ?: emergencyUiState.location.longitude
    val customerAddress = activeRequest?.location?.address?.takeIf { it.isNotBlank() }
        ?: emergencyUiState.location.address.ifBlank { "Breakdown location recorded" }

    // Enrich all online mechanics with real-time distance & ETA relative to the breakdown spot,
    // sorted closest-first so the live map and carousel always mirror Ola/Uber nearby unit views.
    val mapMechanics: List<Mechanic> = remember(rawNearbyMechanics, customerLat, customerLng) {
        val validOrigin = NearbyMechanicMatcher.isValidCoordinate(customerLat, customerLng)
        if (validOrigin) {
            val within50Km = NearbyMechanicMatcher.filterAndSortNearby(
                mechanics = rawNearbyMechanics,
                userLat = customerLat!!,
                userLng = customerLng!!,
                searchRadiusKm = 50.0
            )
            if (within50Km.isNotEmpty()) {
                within50Km
            } else {
                // Still display online mechanics with valid coordinates and computed distance even if outside 50 km
                rawNearbyMechanics
                    .filter { NearbyMechanicMatcher.isValidCoordinate(it.latitude, it.longitude) }
                    .map { mech ->
                        val distKm = NearbyMechanicMatcher.haversineDistanceKm(
                            customerLat,
                            customerLng,
                            mech.latitude!!,
                            mech.longitude!!
                        )
                        val etaMins = ((distKm / 25.0) * 60.0).toInt().coerceAtLeast(3)
                        mech.copy(
                            currentDistanceKm = ((distKm * 10.0).toInt() / 10f),
                            estimatedArrivalMinutes = etaMins
                        )
                    }
                    .sortedBy { it.currentDistanceKm ?: Float.MAX_VALUE }
            }
        } else {
            rawNearbyMechanics
        }
    }

    var focusedMechanicId by remember { mutableStateOf<String?>(null) }
    var recenterTrigger by remember { mutableIntStateOf(0) }

    val statusSteps = listOf(
        "Broadcasting emergency job to verified roadside mechanics...",
        "Showing live online mechanics around your location...",
        "Matching technicians equipped for your breakdown...",
        "Waiting for nearest available mechanic to accept..."
    )
    var stepIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2200)
            stepIndex = (stepIndex + 1) % statusSteps.size
        }
    }

    LaunchedEffect(status, activeRequest?.id) {
        while (status == JobStatus.SEARCHING) {
            delay(15000)
            emergencyViewModel.triggerOfferExpiryCheck()
        }
    }

    LaunchedEffect(status) {
        if (status == JobStatus.ASSIGNED ||
            status == JobStatus.ACCEPTED ||
            status == JobStatus.EN_ROUTE ||
            status == JobStatus.ARRIVED ||
            status == JobStatus.IN_PROGRESS
        ) {
            onMechanicAssigned()
        } else if (status == JobStatus.CANCELLED) {
            onCancelled()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    val isFailureState = status == JobStatus.NO_MECHANIC_FOUND || status == JobStatus.EXPIRED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark)
            .testTag("searching_mechanic_screen")
    ) {
        // UPPER LIVE DISPATCH MAP (Ola/Uber style)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LiveDispatchMap(
                customerLat = customerLat,
                customerLng = customerLng,
                customerLabel = customerAddress,
                mechanics = mapMechanics,
                showSearchRadius = !isFailureState,
                searchRadiusMeters = 3500.0,
                focusedMechanicId = focusedMechanicId,
                recenterTrigger = recenterTrigger,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("searching_live_map")
            )

            // Subtle pulsing radar ring centered over the map while searching
            if (!isFailureState) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(180.dp)) {
                        val radius = size.minDimension / 2f
                        drawCircle(
                            color = EmergencyOrange.copy(alpha = (1f - pulse).coerceIn(0f, 0.35f)),
                            radius = radius * pulse,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }
            }

            // Floating Top Status Pill
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = NavyDark.copy(alpha = 0.94f),
                    shadowElevation = 6.dp,
                    border = BorderStroke(
                        1.dp,
                        if (isFailureState) EmergencyRed.copy(alpha = 0.7f) else EmergencyOrange.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isFailureState) EmergencyRed else EmergencyOrange)
                            )
                            Text(
                                text = when (status) {
                                    JobStatus.NO_MECHANIC_FOUND -> "NO MECHANICS IN RANGE"
                                    JobStatus.EXPIRED -> "DISPATCH WINDOW EXPIRED"
                                    else -> stringResource(id = R.string.searching_mechanic_title)
                                },
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${activeRequest?.serviceItem?.title ?: "Emergency Help"} • ${activeRequest?.vehicleMakeModel ?: ""}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = EmergencyOrange,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Live online mechanic count pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (mapMechanics.isNotEmpty()) {
                                SafetyGreen.copy(alpha = 0.2f)
                            } else {
                                PureWhite.copy(alpha = 0.1f)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (mapMechanics.isNotEmpty()) SafetyGreen else WarningAmber)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (mapMechanics.isNotEmpty()) {
                                        "${mapMechanics.size} Online Mechanic${if (mapMechanics.size == 1) "" else "s"} Live on Map"
                                    } else {
                                        "Scanning live mechanic network..."
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = PureWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Recenter Map Floating Action Button (48.dp touch target)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(48.dp),
                shape = CircleShape,
                color = PureWhite,
                shadowElevation = 6.dp
            ) {
                IconButton(
                    onClick = {
                        focusedMechanicId = null
                        recenterTrigger++
                    },
                    modifier = Modifier.testTag("recenter_search_map_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Recenter map on your location and nearby mechanics",
                        tint = NavyDark
                    )
                }
            }
        }

        // BOTTOM OLA-STYLE DISPATCH SHEET
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = NavyDark,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sheet Handle
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PureWhite.copy(alpha = 0.25f))
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (isFailureState) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NavySurface),
                        border = BorderStroke(1.dp, EmergencyRed)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = EmergencyRed,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (status == JobStatus.NO_MECHANIC_FOUND) {
                                    "All nearby technicians are currently busy. Tap Retry to re-ping online mechanics."
                                } else {
                                    "Dispatch window expired. Tap Retry to broadcast again to nearby online mechanics."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = PureWhite,
                                    lineHeight = 18.sp
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    // Live Online Mechanics Strip (tap any card to focus on map)
                    if (mapMechanics.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nearby Online Mechanics",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = PureWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Tap to locate on map",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = PureWhite.copy(alpha = 0.6f),
                                    fontSize = 11.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("online_mechanics_strip"),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(end = 4.dp)
                        ) {
                            items(mapMechanics, key = { it.id }) { mechanic ->
                                val isSelected = focusedMechanicId == mechanic.id
                                OnlineMechanicMapChip(
                                    mechanic = mechanic,
                                    isSelected = isSelected,
                                    onClick = {
                                        focusedMechanicId = mechanic.id
                                        recenterTrigger++
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Live Status & Breakdown Address Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = NavySurface),
                        border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    tint = EmergencyOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = statusSteps[stepIndex],
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = PureWhite,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = SafetyGreen,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = customerAddress,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = PureWhite.copy(alpha = 0.72f)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (isFailureState) {
                    Button(
                        onClick = { emergencyViewModel.retrySearch() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("retry_search_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyOrange)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = PureWhite
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry Dispatch Search", fontWeight = FontWeight.Bold, color = PureWhite)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedButton(
                    onClick = {
                        emergencyViewModel.cancelActiveRequest {
                            onCancelled()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("cancel_request_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.75f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.btn_cancel_request),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineMechanicMapChip(
    mechanic: Mechanic,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(196.dp)
            .clickable { onClick() }
            .testTag("online_mechanic_chip_${mechanic.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) EmergencyOrange.copy(alpha = 0.18f) else NavySurface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) EmergencyOrange else PureWhite.copy(alpha = 0.14f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SafetyGreen)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = mechanic.name,
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = PureWhite,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (mechanic.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val distLabel = mechanic.currentDistanceKm?.let { "${"%.1f".format(it)} km" } ?: "Online"
                val etaLabel = mechanic.estimatedArrivalMinutes?.let { "~$it min" } ?: "Ready"
                Text(
                    text = "$distLabel • $etaLabel",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = EmergencyOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${"%.1f".format(mechanic.rating)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = PureWhite.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    )
                }
            }

            if (mechanic.locality.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = mechanic.locality,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Neutral500,
                        fontSize = 10.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
