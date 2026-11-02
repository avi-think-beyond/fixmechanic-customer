package com.example.ui.screens

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
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.ui.theme.NavyDark
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
fun JobCompletionScreen(
    emergencyViewModel: EmergencyViewModel,
    onFinished: () -> Unit
) {
    val activeRequest by emergencyViewModel.activeRequest.collectAsState()
    var selectedRating by remember { mutableFloatStateOf(5.0f) }
    var selectedPaymentMethod by remember { mutableStateOf("UPI") }
    var feedbackComments by remember { mutableStateOf("") }
    val feedbackTags = listOf("Very Punctual", "Transparent Pricing", "Expert Work", "Polite Behavior", "Clean Tools")
    var selectedTags by remember { mutableStateOf(setOf("Very Punctual", "Transparent Pricing")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .testTag("job_completion_screen")
    ) {
        FixMechanicTopBar(
            title = stringResource(id = R.string.completion_title)
        )

        Column(modifier = Modifier.padding(20.dp)) {

            // Success Header Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(SafetyGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = PureWhite,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Vehicle Emergency Resolved!",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46)
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Thank you for trusting FixMechanic. Your vehicle is safe to drive.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF047857))
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Itemized Invoice Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Itemized Service Invoice",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = NavyDark
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = activeRequest?.serviceItem?.title ?: "Standard Inspection",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Neutral800
                        )
                        Text(
                            text = "₹${activeRequest?.baseInspectionFee ?: 249}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = NavyDark
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Roadside Diagnostic & Safety Audit",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Neutral500
                        )
                        Text(
                            text = "FREE",
                            style = MaterialTheme.typography.bodyMedium.copy(color = SafetyGreen, fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "FixMechanic 24-Hour Assurance",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Neutral500
                        )
                        Text(
                            text = "Included",
                            style = MaterialTheme.typography.bodyMedium.copy(color = SafetyGreen, fontWeight = FontWeight.SemiBold)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Neutral100)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Payable Amount",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = NavyDark
                        )
                        Text(
                            text = "₹${activeRequest?.baseInspectionFee ?: 249}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = EmergencyOrange
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Payment Mode Selector
            Text(
                text = "Select Payment Method",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = NavyDark
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PaymentMethodOption(
                    title = "UPI / QR",
                    icon = Icons.Default.QrCode,
                    isSelected = selectedPaymentMethod == "UPI",
                    onClick = { selectedPaymentMethod = "UPI" }
                )
                PaymentMethodOption(
                    title = "Cash",
                    icon = Icons.Default.LocalAtm,
                    isSelected = selectedPaymentMethod == "Cash",
                    onClick = { selectedPaymentMethod = "Cash" }
                )
                PaymentMethodOption(
                    title = "Card",
                    icon = Icons.Default.CreditCard,
                    isSelected = selectedPaymentMethod == "Card",
                    onClick = { selectedPaymentMethod = "Card" }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5-Star Rating Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.rate_experience),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = NavyDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "How was your experience with ${activeRequest?.assignedMechanic?.name ?: "the mechanic"}?",
                        style = MaterialTheme.typography.bodySmall.copy(color = Neutral500)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 5 Stars
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (selectedRating >= star) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = "$star Stars",
                                tint = WarningAmber,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { selectedRating = star.toFloat() }
                                    .padding(2.dp)
                                    .testTag("star_rating_$star")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Feedback Tag Chips
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            feedbackTags.take(3).forEach { tag ->
                                val isSelected = selectedTags.contains(tag)
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) Color(0xFFFFECE5) else Color(0xFFF1F5F9),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) EmergencyOrange else Color(0xFFCBD5E1)
                                    ),
                                    modifier = Modifier.clickable {
                                        selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag
                                    }
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isSelected) EmergencyOrange else Neutral800,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            feedbackTags.drop(3).forEach { tag ->
                                val isSelected = selectedTags.contains(tag)
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) Color(0xFFFFECE5) else Color(0xFFF1F5F9),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) EmergencyOrange else Color(0xFFCBD5E1)
                                    ),
                                    modifier = Modifier.clickable {
                                        selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag
                                    }
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isSelected) EmergencyOrange else Neutral800,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Comment text field
                    OutlinedTextField(
                        value = feedbackComments,
                        onValueChange = { feedbackComments = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("feedback_comments_input"),
                        placeholder = { Text("Write additional feedback (optional)...", fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NavyDark,
                            unfocusedBorderColor = Neutral300
                        ),
                        maxLines = 3
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    emergencyViewModel.submitRating(
                        rating = selectedRating,
                        comment = feedbackComments.ifBlank { selectedTags.joinToString(", ") }
                    ) {
                        onFinished()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_feedback_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavyDark)
            ) {
                Text(
                    text = stringResource(id = R.string.btn_submit_feedback),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                )
            }
        }
    }
}

@Composable
fun androidx.compose.foundation.layout.RowScope.PaymentMethodOption(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .weight(1f)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFFFECE5) else PureWhite
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isSelected) EmergencyOrange else Color(0xFFE2E8F0)
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) EmergencyOrange else NavyDark,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) EmergencyOrange else NavyDark
                )
            )
        }
    }
}
