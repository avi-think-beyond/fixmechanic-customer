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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ServiceItem
import com.example.data.model.VehicleType
import com.example.ui.components.FixMechanicTopBar
import com.example.ui.theme.EmergencyOrange
import com.example.ui.theme.NavyDark
import com.example.ui.theme.Neutral100
import com.example.ui.theme.Neutral300
import com.example.ui.theme.Neutral500
import com.example.ui.theme.Neutral900
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.AiDiagnosisViewModel
import com.example.ui.viewmodel.EmergencyViewModel

@Composable
fun AiDiagnoserScreen(
    emergencyViewModel: EmergencyViewModel,
    aiViewModel: AiDiagnosisViewModel,
    onBackClick: () -> Unit,
    onServiceSelected: () -> Unit
) {
    val aiState by aiViewModel.uiState.collectAsState()
    val emergencyState by emergencyViewModel.uiState.collectAsState()
    var selectedVehicleType by remember { mutableStateOf(emergencyState.selectedVehicleType) }

    val samplePrompts = listOf(
        "White steam and smoke coming from car radiator bonnet",
        "Key locked inside my Swift Dzire on the front seat",
        "Battery completely dead, car just makes clicking sound",
        "Motorcycle clutch wire snapped while shifting gears",
        "Rear tyre flat on the highway with a metal puncture nail",
        "Ran out of petrol 2 km before the toll plaza"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .testTag("ai_diagnoser_screen")
    ) {
        FixMechanicTopBar(
            title = "AI Issue Diagnoser",
            onBackClick = onBackClick
        )

        // Vehicle Switch Tabs
        TabRow(
            selectedTabIndex = if (selectedVehicleType == VehicleType.BIKE) 0 else 1,
            containerColor = PureWhite,
            contentColor = Color(0xFF4F46E5),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[if (selectedVehicleType == VehicleType.BIKE) 0 else 1]),
                    color = Color(0xFF4F46E5)
                )
            }
        ) {
            Tab(
                selected = selectedVehicleType == VehicleType.BIKE,
                onClick = { selectedVehicleType = VehicleType.BIKE },
                text = {
                    Text(
                        text = "🏍️ Bike / 2-Wheeler",
                        fontWeight = FontWeight.Bold,
                        color = if (selectedVehicleType == VehicleType.BIKE) Color(0xFF4F46E5) else Neutral500
                    )
                }
            )
            Tab(
                selected = selectedVehicleType == VehicleType.CAR,
                onClick = { selectedVehicleType = VehicleType.CAR },
                text = {
                    Text(
                        text = "🚗 Car / 4-Wheeler",
                        fontWeight = FontWeight.Bold,
                        color = if (selectedVehicleType == VehicleType.CAR) Color(0xFF4F46E5) else Neutral500
                    )
                }
            )
        }

        Column(modifier = Modifier.padding(20.dp)) {

            // Header Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFC7D2FE))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4F46E5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Smart Symptom Classification",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF312E81)
                            )
                        )
                        Text(
                            text = "Type how your vehicle is behaving. AI suggests the right emergency service category without dispatching without your consent.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF4338CA),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sample Symptom Chips
            Text(
                text = "Tap a common symptom or type your own:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Neutral900
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(samplePrompts) { prompt ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF1F5F9),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.clickable {
                            aiViewModel.onQueryChanged(prompt)
                            aiViewModel.diagnoseProblem(selectedVehicleType)
                        }
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                color = NavyDark
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Input field
            OutlinedTextField(
                value = aiState.queryInput,
                onValueChange = { aiViewModel.onQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .testTag("ai_problem_query_input"),
                placeholder = {
                    Text(
                        "Describe noise, smell, smoke, or breakdown symptom in plain language...",
                        fontSize = 13.sp
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NavyDark,
                    unfocusedTextColor = NavyDark,
                    focusedContainerColor = PureWhite,
                    unfocusedContainerColor = PureWhite,
                    focusedBorderColor = Color(0xFF4F46E5),
                    unfocusedBorderColor = Neutral300,
                    focusedPlaceholderColor = Neutral500,
                    unfocusedPlaceholderColor = Neutral500,
                    cursorColor = Color(0xFF4F46E5)
                ),
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { aiViewModel.diagnoseProblem(selectedVehicleType) },
                enabled = !aiState.isAnalyzing && aiState.queryInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("run_ai_diagnosis_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                if (aiState.isAnalyzing) {
                    CircularProgressIndicator(
                        color = PureWhite,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Diagnose with AI", fontWeight = FontWeight.Bold, color = PureWhite)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Classification Result
            AnimatedVisibility(visible = aiState.diagnosisResult != null) {
                val diag = aiState.diagnosisResult
                if (diag != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, SafetyGreen)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
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
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "AI Recommended Service",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = NavyDark
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFECFDF5)
                                ) {
                                    Text(
                                        text = "${(diag.confidenceScore * 100).toInt()}% Match",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = SafetyGreen
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = diag.suggestedService.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmergencyOrange
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Base Inspection: ₹${diag.suggestedService.estimatedPriceInr} • ${diag.explanation}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Neutral500,
                                    lineHeight = 18.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    emergencyViewModel.startNewEmergencyFlow(selectedVehicleType)
                                    emergencyViewModel.selectService(diag.suggestedService)
                                    onServiceSelected()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("accept_ai_service_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmergencyOrange)
                            ) {
                                Text(
                                    text = "Book ${diag.suggestedService.title}",
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 24/7 FIXMECHANIC LIVE AI ASSISTANT CHAT
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7D2FE)),
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
                                    .clip(CircleShape)
                                    .background(Color(0xFF4F46E5)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Copilot",
                                    tint = PureWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "FixMechanic AI Roadside Copilot",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = NavyDark
                                )
                                Text(
                                    text = "Free 24/7 AI Mechanical Advice • Gemini Live",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = SafetyGreen,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Chat messages list
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        aiState.chatMessages.forEach { msg ->
                            val isAi = msg.sender == "ai"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(
                                        topStart = 12.dp,
                                        topEnd = 12.dp,
                                        bottomStart = if (isAi) 2.dp else 12.dp,
                                        bottomEnd = if (isAi) 12.dp else 2.dp
                                    ),
                                    color = if (isAi) PureWhite else Color(0xFF4F46E5),
                                    border = if (isAi) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)) else null,
                                    modifier = Modifier.fillMaxWidth(0.92f)
                                ) {
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                            color = if (isAi) Neutral900 else PureWhite
                                        ),
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }

                        if (aiState.isAssistantThinking) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF4F46E5)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "FixMechanic AI is analyzing your vehicle question...",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Neutral500)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Input & Ask button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = aiState.assistantQuestionInput,
                            onValueChange = { aiViewModel.onAssistantQuestionChanged(it) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_assistant_input"),
                            placeholder = { Text("Ask anything (e.g. Can I drive with coolant leak?)", fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = NavyDark,
                                unfocusedTextColor = NavyDark,
                                focusedContainerColor = PureWhite,
                                unfocusedContainerColor = PureWhite,
                                focusedPlaceholderColor = Neutral500,
                                unfocusedPlaceholderColor = Neutral500,
                                focusedBorderColor = Color(0xFF4F46E5),
                                unfocusedBorderColor = Neutral300,
                                cursorColor = Color(0xFF4F46E5)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { aiViewModel.askAssistant(selectedVehicleType) },
                            enabled = !aiState.isAssistantThinking && aiState.assistantQuestionInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            modifier = Modifier.testTag("ai_assistant_send_button")
                        ) {
                            Text("Ask", color = PureWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
