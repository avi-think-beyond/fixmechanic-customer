package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.ui.components.FixMechanicTopBar
import com.example.ui.theme.EmergencyOrange
import com.example.ui.theme.EmergencyRed
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
import com.example.ui.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    profileViewModel: ProfileViewModel,
    onLogout: () -> Unit
) {
    val state by profileViewModel.uiState.collectAsState()
    val profile by profileViewModel.profileData.collectAsState()
    val backendStatus by profileViewModel.backendStatus.collectAsState()
    var showBackendDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .testTag("profile_screen")
    ) {
        FixMechanicTopBar(
            title = "Customer Profile & Settings"
        )

        Column(modifier = Modifier.padding(20.dp)) {

            // Profile Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(NavyDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile.fullName.take(2).uppercase(),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = profile.fullName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                        )
                        Text(
                            text = profile.phoneNumber,
                            style = MaterialTheme.typography.bodySmall.copy(color = Neutral500)
                        )
                        Text(
                            text = profile.email,
                            style = MaterialTheme.typography.labelSmall.copy(color = Neutral500, fontSize = 11.sp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // PRIVACY & SECURITY CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PrivacyTip,
                            contentDescription = null,
                            tint = NavyDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Privacy & Safety Controls",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = NavyDark
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Masking Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Number Masking Proxy",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Neutral900
                            )
                            Text(
                                text = "Keep customer phone number concealed from dispatched mechanics.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Neutral500)
                            )
                        }

                        Switch(
                            checked = profile.isNumberMaskingEnabled,
                            onCheckedChange = { profileViewModel.toggleNumberMasking(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = SafetyGreen
                            ),
                            modifier = Modifier.testTag("number_masking_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Neutral100)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Location Sharing Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Live Breakdown GPS Sharing",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Neutral900
                            )
                            Text(
                                text = "Share active pin with arriving mechanic during rescue session only.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Neutral500)
                            )
                        }

                        Switch(
                            checked = profile.isGpsSharingEnabled,
                            onCheckedChange = { profileViewModel.toggleGpsSharing(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = SafetyGreen
                            ),
                            modifier = Modifier.testTag("gps_sharing_switch")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // EMERGENCY CONTACT CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContactPhone,
                            contentDescription = null,
                            tint = EmergencyOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Emergency Family Contact (SOS)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = NavyDark
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "${profile.emergencyContactName} • ${profile.emergencyContactPhone}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Neutral800
                    )
                    Text(
                        text = "Automated SMS notification dispatched if highway emergency triggers SOS.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Neutral500)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // LANGUAGE SETTINGS (English / Hindi)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = NavyDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Language Preference / भाषा",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = NavyDark
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (profile.preferredLanguage == "en") Color(0xFFFFECE5) else Color(0xFFF1F5F9),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (profile.preferredLanguage == "en") EmergencyOrange else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { profileViewModel.setLanguage("en") }
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "English",
                                    fontWeight = FontWeight.Bold,
                                    color = if (profile.preferredLanguage == "en") EmergencyOrange else NavyDark
                                )
                                Text(text = "Default", fontSize = 10.sp, color = Neutral500)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (profile.preferredLanguage == "hi") Color(0xFFFFECE5) else Color(0xFFF1F5F9),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (profile.preferredLanguage == "hi") EmergencyOrange else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { profileViewModel.setLanguage("hi") }
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "हिंदी (Hindi)",
                                    fontWeight = FontWeight.Bold,
                                    color = if (profile.preferredLanguage == "hi") EmergencyOrange else NavyDark
                                )
                                Text(text = "स्थानीयकरण", fontSize = 10.sp, color = Neutral500)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // BACKEND INTEGRATION STATUS INSPECTOR BUTTON
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBackendDialog = true }
                    .testTag("backend_status_inspector_card"),
                shape = RoundedCornerShape(16.dp),
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
                            .background(WarningAmber),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = "Cloud Status",
                            tint = NavyDark,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cloud & Backend Integration Status",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        )
                        Text(
                            text = "Check Firebase, Firestore, FCM & REST readiness",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = PureWhite.copy(alpha = 0.75f),
                                fontSize = 11.sp
                            )
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = PureWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Logout Button
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("logout_button"),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmergencyRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out from Device", fontWeight = FontWeight.Bold)
            }
        }

        // BACKEND STATUS MODAL DIALOG
        if (showBackendDialog) {
            AlertDialog(
                onDismissRequest = { showBackendDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = WarningAmber
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Backend Integration Report",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Status: ${backendStatus.activeEnvironment}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )

                        IntegrationStatusRow(name = "Firebase Authentication", isReady = backendStatus.firebaseAuthAvailable)
                        IntegrationStatusRow(name = "Cloud Firestore Database", isReady = backendStatus.firestoreAvailable)
                        IntegrationStatusRow(name = "Firebase Cloud Messaging (FCM)", isReady = backendStatus.fcmAvailable)
                        IntegrationStatusRow(name = "Live Dispatch REST Backend", isReady = backendStatus.backendApiAvailable)

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Architecture Note:\nConnected directly to live Cloud Firestore database (ai-studio-android-fixmecha-b0e3b2e8-05f1-4bf3-b32f-7f48ef3138b9) with enforced zero-trust security rules. Authenticated via Google Sign-In with real-time listeners for emergency requests, chat messaging, and customer profiles.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Neutral500)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showBackendDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDark)
                    ) {
                        Text("Close", color = PureWhite)
                    }
                }
            )
        }
    }
}

@Composable
fun IntegrationStatusRow(name: String, isReady: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isReady) Color(0xFFECFDF5) else Color(0xFFFFFBEB)
        ) {
            Text(
                text = if (isReady) "Connected" else "Pending Config",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isReady) SafetyGreen else WarningAmber
                ),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
