package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.JobStatus
import com.example.ui.theme.EmergencyOrange
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.Neutral500
import com.example.ui.theme.PureWhite
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
    val nearbyMechanics by emergencyViewModel.nearbyMechanics.collectAsState()
    val status = activeRequest?.status

    // Status message cycling
    val statusSteps = listOf(
        "Broadcasting emergency job to verified roadside mechanics...",
        "Scanning nearest units within 5 km radius...",
        "Locating mechanics equipped with required parts...",
        "Confirming technician availability & dispatch queue..."
    )
    var stepIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            stepIndex = (stepIndex + 1) % statusSteps.size
        }
    }

    // Periodically trigger Spark backend expiry / radius expansion check while searching
    LaunchedEffect(status, activeRequest?.id) {
        while (status == JobStatus.SEARCHING) {
            delay(15000)
            emergencyViewModel.triggerOfferExpiryCheck()
        }
    }

    // Auto-navigate when mechanic is assigned or accepted
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
    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark)
            .padding(24.dp)
            .testTag("searching_mechanic_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = NavySurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, EmergencyOrange.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (status == JobStatus.NO_MECHANIC_FOUND || status == JobStatus.EXPIRED)
                                    EmergencyRed
                                else
                                    EmergencyOrange
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (status == JobStatus.NO_MECHANIC_FOUND)
                            "DISPATCH ALERT"
                        else if (status == JobStatus.EXPIRED)
                            "REQUEST EXPIRED"
                        else
                            "LIVE DISPATCH SEARCH",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = PureWhite,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (status == JobStatus.NO_MECHANIC_FOUND)
                    "No Mechanics in Range"
                else if (status == JobStatus.EXPIRED)
                    "Dispatch Window Expired"
                else
                    stringResource(id = R.string.searching_mechanic_title),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${activeRequest?.serviceItem?.title ?: "Emergency Help"} • ${activeRequest?.vehicleMakeModel ?: ""}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = EmergencyOrange,
                    fontWeight = FontWeight.SemiBold
                ),
                textAlign = TextAlign.Center
            )
        }

        // Central Visual
        if (status == JobStatus.NO_MECHANIC_FOUND || status == JobStatus.EXPIRED) {
            // Error / Timeout Visual
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, EmergencyRed)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = EmergencyRed,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (status == JobStatus.NO_MECHANIC_FOUND)
                            "All verified technicians within 5–7 km are currently occupied with other breakdown rescues."
                        else
                            "Mechanics did not accept within the active 3-minute dispatch window.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = PureWhite,
                            lineHeight = 20.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "You can retry to expand the search radius or contact our 24x7 toll-free helpline at 1800-349-4357.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = PureWhite.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Radar Animation Visual Center
            Box(
                modifier = Modifier.size(260.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2

                    drawCircle(
                        color = EmergencyOrange.copy(alpha = (1f - pulse1).coerceIn(0f, 0.4f)),
                        radius = radius * pulse1,
                        style = Stroke(width = 4.dp.toPx())
                    )
                    drawCircle(
                        color = EmergencyOrange.copy(alpha = (1f - pulse2).coerceIn(0f, 0.35f)),
                        radius = radius * pulse2,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    drawCircle(
                        color = PureWhite.copy(alpha = 0.08f),
                        radius = radius * 0.45f,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawCircle(
                        color = PureWhite.copy(alpha = 0.08f),
                        radius = radius * 0.75f,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(EmergencyOrange),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Search",
                        tint = PureWhite,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        // Live Status Stepper Card & Action Controls
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (status != JobStatus.NO_MECHANIC_FOUND && status != JobStatus.EXPIRED) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PureWhite.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = statusSteps[stepIndex],
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = PureWhite,
                                fontWeight = FontWeight.Medium
                            ),
                            textAlign = TextAlign.Center,
                            minLines = 2
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = EmergencyOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeRequest?.location?.address?.ifBlank { "Breakdown location recorded" } ?: "Breakdown location recorded",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = PureWhite.copy(alpha = 0.7f)
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Retry or Cancel Buttons
            if (status == JobStatus.NO_MECHANIC_FOUND || status == JobStatus.EXPIRED) {
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

            // Cancel Button
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
                border = androidx.compose.foundation.BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.7f)),
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

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${nearbyMechanics.size} online mechanic(s) visible on network • Waiting for acceptance",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = PureWhite.copy(alpha = 0.7f),
                    fontSize = 11.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
