package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.VehicleType
import com.example.ui.components.FixMechanicTopBar
import com.example.ui.theme.EmergencyOrange
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.NavyDark
import com.example.ui.theme.Neutral300
import com.example.ui.theme.Neutral500
import com.example.ui.theme.Neutral900
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SafetyGreen
import com.example.ui.viewmodel.AiDiagnosisViewModel
import com.example.ui.viewmodel.EmergencyViewModel

@Composable
fun VehicleDetailsScreen(
    emergencyViewModel: EmergencyViewModel,
    aiViewModel: AiDiagnosisViewModel,
    onBackClick: () -> Unit,
    onProceedToSummary: () -> Unit
) {
    val emergencyState by emergencyViewModel.uiState.collectAsState()
    val aiState by aiViewModel.uiState.collectAsState()

    var makeModelInput by remember { mutableStateOf(emergencyState.vehicleMakeModel) }
    var regNumberInput by remember { mutableStateOf(emergencyState.registrationNumber) }
    var issueDescInput by remember { mutableStateOf(emergencyState.issueDescription) }

    // Android Zero-permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        emergencyViewModel.onPhotoSelected(uri?.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .testTag("vehicle_details_screen")
    ) {
        FixMechanicTopBar(
            title = stringResource(id = R.string.vehicle_details_title),
            onBackClick = onBackClick
        )

        Column(modifier = Modifier.padding(20.dp)) {

            Text(
                text = "Vehicle & Problem Description",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavyDark
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Help the arriving technician bring the correct spare parts and tools.",
                style = MaterialTheme.typography.bodyMedium.copy(color = Neutral500)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Vehicle Make & Model Input
            Text(
                text = stringResource(id = R.string.vehicle_make_model_label),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Neutral900
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = makeModelInput,
                onValueChange = {
                    makeModelInput = it
                    emergencyViewModel.updateVehicleDetails(it, regNumberInput, issueDescInput)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vehicle_make_model_input"),
                placeholder = {
                    Text(
                        if (emergencyState.selectedVehicleType == VehicleType.BIKE)
                            "e.g. Royal Enfield Classic 350, Honda Activa 6G"
                        else
                            "e.g. Maruti Swift Dzire, Hyundai Creta"
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NavyDark,
                    unfocusedTextColor = NavyDark,
                    focusedContainerColor = PureWhite,
                    unfocusedContainerColor = PureWhite,
                    focusedBorderColor = EmergencyOrange,
                    unfocusedBorderColor = Neutral300,
                    focusedPlaceholderColor = Neutral500,
                    unfocusedPlaceholderColor = Neutral500,
                    cursorColor = EmergencyOrange
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Registration Number (Optional)
            Text(
                text = stringResource(id = R.string.vehicle_reg_number_label),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Neutral900
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = regNumberInput,
                onValueChange = {
                    regNumberInput = it
                    emergencyViewModel.updateVehicleDetails(makeModelInput, it, issueDescInput)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vehicle_reg_number_input"),
                placeholder = { Text("e.g. DL 01 AB 1234 (Optional)", color = Neutral500) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NavyDark,
                    unfocusedTextColor = NavyDark,
                    focusedContainerColor = PureWhite,
                    unfocusedContainerColor = PureWhite,
                    focusedBorderColor = EmergencyOrange,
                    unfocusedBorderColor = Neutral300,
                    focusedPlaceholderColor = Neutral500,
                    unfocusedPlaceholderColor = Neutral500,
                    cursorColor = EmergencyOrange
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Issue Description
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.issue_description_label),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Neutral900
                )

                Text(
                    text = "Optional",
                    style = MaterialTheme.typography.labelSmall.copy(color = Neutral500)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = issueDescInput,
                onValueChange = {
                    issueDescInput = it
                    aiViewModel.onQueryChanged(it)
                    emergencyViewModel.updateVehicleDetails(makeModelInput, regNumberInput, it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .testTag("issue_description_input"),
                placeholder = { Text(stringResource(id = R.string.issue_description_placeholder), color = Neutral500) },
                shape = RoundedCornerShape(12.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NavyDark,
                    unfocusedTextColor = NavyDark,
                    focusedContainerColor = PureWhite,
                    unfocusedContainerColor = PureWhite,
                    focusedBorderColor = EmergencyOrange,
                    unfocusedBorderColor = Neutral300,
                    focusedPlaceholderColor = Neutral500,
                    unfocusedPlaceholderColor = Neutral500,
                    cursorColor = EmergencyOrange
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // OPTIONAL PHOTO ATTACHMENT SECTION
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = null,
                                tint = NavyDark,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Attach Breakdown Photo (Optional)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                            )
                        }

                        if (emergencyState.selectedPhotoUri == null) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("select_photo_button")
                            ) {
                                Text("Choose Photo", fontSize = 12.sp, color = NavyDark)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Adding a photo of the flat tyre, engine, or dashboard light helps the mechanic prepare.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Neutral500)
                    )

                    // Display attached image preview
                    if (emergencyState.selectedPhotoUri != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(8.dp)
                        ) {
                            AsyncImage(
                                model = emergencyState.selectedPhotoUri,
                                contentDescription = "Breakdown photo preview",
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Photo Attached",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark
                                    )
                                )
                                Text(
                                    text = "Will be transmitted to the arriving mechanic",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Neutral500)
                                )
                            }
                            IconButton(
                                onClick = { emergencyViewModel.onPhotoSelected(null) },
                                modifier = Modifier.testTag("remove_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove photo",
                                    tint = EmergencyRed
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Problem Diagnoser Assistant Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDD6FE))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Classifier",
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI Service Suggester",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF5B21B6)
                                )
                            )
                        }

                        Button(
                            onClick = {
                                aiViewModel.onQueryChanged(issueDescInput.ifBlank { makeModelInput })
                                aiViewModel.diagnoseProblem(emergencyState.selectedVehicleType)
                            },
                            enabled = !aiState.isAnalyzing,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("ai_classify_button")
                        ) {
                            if (aiState.isAnalyzing) {
                                CircularProgressIndicator(
                                    color = PureWhite,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Classify Issue", fontSize = 11.sp, color = PureWhite)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "AI analyzes your text to suggest the most appropriate service category. You retain 100% manual control.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = Color(0xFF6D28D9)
                        )
                    )

                    // Diagnosis result display
                    AnimatedVisibility(visible = aiState.diagnosisResult != null) {
                        val diag = aiState.diagnosisResult
                        if (diag != null) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = PureWhite,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC4B5FD))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = SafetyGreen,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Suggested: ${diag.suggestedService.title}",
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = NavyDark
                                                    )
                                                )
                                            }

                                            Button(
                                                onClick = {
                                                    emergencyViewModel.selectService(diag.suggestedService)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = EmergencyOrange),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.testTag("apply_ai_suggestion_button")
                                            ) {
                                                Text("Use This Service", fontSize = 11.sp, color = PureWhite)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = diag.explanation,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                color = Neutral500
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Currently Selected Service Confirmation Chip
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFEDD5))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = EmergencyOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Assigned Request Category:",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = Neutral500)
                        )
                        Text(
                            text = "${emergencyState.selectedService?.title} (₹${emergencyState.selectedService?.estimatedPriceInr})",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    emergencyViewModel.updateVehicleDetails(
                        makeModelInput,
                        regNumberInput,
                        issueDescInput,
                        emergencyState.selectedPhotoUri
                    )
                    onProceedToSummary()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("proceed_to_summary_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyOrange)
            ) {
                Text(
                    text = "Review Request Summary",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                )
            }
        }
    }
}
