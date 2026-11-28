package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BackendIntegrationStatus
import com.example.data.repository.UserProfileData
import com.example.ui.theme.EmergencyOrange
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.NavyDark
import com.example.ui.theme.Neutral300
import com.example.ui.theme.Neutral500
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.WarningAmber

@Composable
fun NewUserWelcomeBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("new_user_profile_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4EE)),
        border = BorderStroke(1.dp, EmergencyOrange.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(EmergencyOrange),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Complete Profile",
                    tint = PureWhite,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Welcome to FixMechanic!",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Please update your contact number, vehicle, and SOS details so nearby mechanics can reach you faster during emergencies.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Neutral500,
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}

@Composable
fun ProfileDetailsEditCard(
    profile: UserProfileData,
    isNewUserSetup: Boolean,
    isSaving: Boolean,
    errorMessage: String?,
    onSave: (
        fullName: String,
        phoneNumber: String,
        primaryVehicle: String,
        emergencyContactName: String,
        emergencyContactPhone: String
    ) -> Unit,
    onCancel: () -> Unit
) {
    var fullName by remember(profile.email) { mutableStateOf(profile.fullName.takeIf { it != "Customer" } ?: "") }
    var phoneNumber by remember(profile.email) { mutableStateOf(profile.phoneNumber) }
    var primaryVehicle by remember(profile.email) { mutableStateOf(profile.primaryVehicle) }
    var emergencyContactName by remember(profile.email) {
        mutableStateOf(profile.emergencyContactName.takeIf { it != "Not set" } ?: "")
    }
    var emergencyContactPhone by remember(profile.email) { mutableStateOf(profile.emergencyContactPhone) }

    // Sync initial values if Firestore snapshot arrives while fields are still empty
    LaunchedEffect(profile) {
        if (fullName.isBlank() && profile.fullName.isNotBlank() && profile.fullName != "Customer") {
            fullName = profile.fullName
        }
        if (phoneNumber.isBlank() && profile.phoneNumber.isNotBlank()) {
            phoneNumber = profile.phoneNumber
        }
        if (primaryVehicle.isBlank() && profile.primaryVehicle.isNotBlank()) {
            primaryVehicle = profile.primaryVehicle
        }
        if (emergencyContactName.isBlank() && profile.emergencyContactName.isNotBlank() && profile.emergencyContactName != "Not set") {
            emergencyContactName = profile.emergencyContactName
        }
        if (emergencyContactPhone.isBlank() && profile.emergencyContactPhone.isNotBlank()) {
            emergencyContactPhone = profile.emergencyContactPhone
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_edit_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = if (isNewUserSetup) "Complete Your Profile Details" else "Update Profile Details",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavyDark
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Saved securely to your FixMechanic account.",
                style = MaterialTheme.typography.bodySmall.copy(color = Neutral500)
            )

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedVisibility(visible = errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(color = EmergencyRed),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_fullname_input"),
                label = { Text("Full Name *") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NavyDark,
                    unfocusedBorderColor = Neutral300
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_phone_input"),
                label = { Text("Mobile Number") },
                placeholder = { Text("+91 98765 43210") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NavyDark,
                    unfocusedBorderColor = Neutral300
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = primaryVehicle,
                onValueChange = { primaryVehicle = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_vehicle_input"),
                label = { Text("Primary Vehicle (Car / Bike)") },
                placeholder = { Text("e.g., Honda City / Activa 6G") },
                leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NavyDark,
                    unfocusedBorderColor = Neutral300
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = emergencyContactName,
                onValueChange = { emergencyContactName = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_emergency_name_input"),
                label = { Text("Emergency Contact Name (SOS)") },
                leadingIcon = { Icon(Icons.Default.ContactPhone, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NavyDark,
                    unfocusedBorderColor = Neutral300
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = emergencyContactPhone,
                onValueChange = { emergencyContactPhone = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_emergency_phone_input"),
                label = { Text("Emergency Contact Phone") },
                placeholder = { Text("+91 98765 00000") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NavyDark,
                    unfocusedBorderColor = Neutral300
                )
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    onSave(
                        fullName,
                        phoneNumber,
                        primaryVehicle,
                        emergencyContactName,
                        emergencyContactPhone
                    )
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_profile_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyOrange)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = PureWhite,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = if (isNewUserSetup) "Save & Continue to Home" else "Save Profile Details",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isNewUserSetup) {
                TextButton(
                    onClick = onCancel,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("skip_profile_setup_button")
                ) {
                    Text(
                        text = "Skip for Now & Go to Home",
                        color = Neutral500,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("cancel_edit_profile_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Neutral300)
                ) {
                    Text(text = "Cancel", color = NavyDark, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun ProfileSaveSuccessBanner(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_save_success_banner"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Saved",
                tint = SafetyGreen,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = SafetyGreen,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
fun BackendStatusReportDialog(
    backendStatus: BackendIntegrationStatus,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                    text = backendStatus.statusSummary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Neutral500)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = NavyDark)
            ) {
                Text("Close", color = PureWhite)
            }
        }
    )
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
