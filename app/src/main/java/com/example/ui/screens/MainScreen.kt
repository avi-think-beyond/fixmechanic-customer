package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.VehicleType
import com.example.ui.navigation.MainTab
import com.example.ui.theme.EmergencyOrange
import com.example.ui.theme.NavyDark
import com.example.ui.theme.Neutral500
import com.example.ui.theme.PureWhite
import com.example.ui.viewmodel.EmergencyViewModel
import com.example.ui.viewmodel.ProfileViewModel

@Composable
fun MainScreen(
    emergencyViewModel: EmergencyViewModel,
    profileViewModel: ProfileViewModel,
    onStartEmergency: (VehicleType) -> Unit,
    onOpenActiveJob: () -> Unit,
    onOpenAiDiagnoser: () -> Unit,
    onOpenBackendStatus: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav_bar"),
                containerColor = PureWhite,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == MainTab.HOME,
                    onClick = { selectedTab = MainTab.HOME },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == MainTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = "Home"
                        )
                    },
                    label = {
                        Text(
                            text = "Home",
                            fontWeight = if (selectedTab == MainTab.HOME) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EmergencyOrange,
                        selectedTextColor = EmergencyOrange,
                        unselectedIconColor = Neutral500,
                        unselectedTextColor = Neutral500,
                        indicatorColor = EmergencyOrange.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag(MainTab.HOME.testTag)
                )

                NavigationBarItem(
                    selected = selectedTab == MainTab.ACTIVITY,
                    onClick = { selectedTab = MainTab.ACTIVITY },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == MainTab.ACTIVITY) Icons.Filled.History else Icons.Outlined.History,
                            contentDescription = "Activity"
                        )
                    },
                    label = {
                        Text(
                            text = "Activity",
                            fontWeight = if (selectedTab == MainTab.ACTIVITY) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EmergencyOrange,
                        selectedTextColor = EmergencyOrange,
                        unselectedIconColor = Neutral500,
                        unselectedTextColor = Neutral500,
                        indicatorColor = EmergencyOrange.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag(MainTab.ACTIVITY.testTag)
                )

                NavigationBarItem(
                    selected = selectedTab == MainTab.PROFILE,
                    onClick = { selectedTab = MainTab.PROFILE },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == MainTab.PROFILE) Icons.Filled.Person else Icons.Outlined.Person,
                            contentDescription = "Profile"
                        )
                    },
                    label = {
                        Text(
                            text = "Profile",
                            fontWeight = if (selectedTab == MainTab.PROFILE) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EmergencyOrange,
                        selectedTextColor = EmergencyOrange,
                        unselectedIconColor = Neutral500,
                        unselectedTextColor = Neutral500,
                        indicatorColor = EmergencyOrange.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag(MainTab.PROFILE.testTag)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                MainTab.HOME -> HomeScreen(
                    emergencyViewModel = emergencyViewModel,
                    onStartEmergency = onStartEmergency,
                    onOpenActiveJob = onOpenActiveJob,
                    onOpenAiDiagnoser = onOpenAiDiagnoser,
                    onOpenHistory = { selectedTab = MainTab.ACTIVITY },
                    onOpenBackendStatus = onOpenBackendStatus
                )

                MainTab.ACTIVITY -> ServiceHistoryScreen(
                    emergencyViewModel = emergencyViewModel,
                    onBookServiceAgain = { vehicleType ->
                        emergencyViewModel.selectVehicleType(vehicleType)
                        onStartEmergency(vehicleType)
                    }
                )

                MainTab.PROFILE -> ProfileScreen(
                    profileViewModel = profileViewModel,
                    onLogout = onLogout
                )
            }
        }
    }
}
