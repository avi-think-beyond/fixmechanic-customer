package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.AiDiagnoserScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.JobCompletionScreen
import com.example.ui.screens.JobTrackingScreen
import com.example.ui.screens.LocationScreen
import com.example.ui.screens.MainScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.RequestSummaryScreen
import com.example.ui.screens.SearchingMechanicScreen
import com.example.ui.screens.ServiceSelectionScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.VehicleDetailsScreen
import com.example.ui.screens.VehicleSelectionScreen
import com.example.ui.viewmodel.AiDiagnosisViewModel
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.EmergencyViewModel
import com.example.ui.viewmodel.ProfileViewModel

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = viewModel(),
    emergencyViewModel: EmergencyViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel(),
    aiViewModel: AiDiagnosisViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Auth.route) {
            AuthScreen(
                authViewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            MainScreen(
                emergencyViewModel = emergencyViewModel,
                profileViewModel = profileViewModel,
                onStartEmergency = { vehicleType ->
                    emergencyViewModel.selectVehicleType(vehicleType)
                    navController.navigate(Screen.VehicleSelect.route)
                },
                onOpenActiveJob = {
                    navController.navigate(Screen.JobTracking.route)
                },
                onOpenAiDiagnoser = {
                    navController.navigate(Screen.AiDiagnoser.route)
                },
                onOpenBackendStatus = {
                    // Handled inside Profile tab dialog or direct navigation
                },
                onLogout = {
                    authViewModel.resetState()
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.VehicleSelect.route) {
            VehicleSelectionScreen(
                emergencyViewModel = emergencyViewModel,
                onBackClick = { navController.popBackStack() },
                onVehicleSelected = {
                    navController.navigate(Screen.ServiceSelect.route)
                }
            )
        }

        composable(Screen.ServiceSelect.route) {
            ServiceSelectionScreen(
                emergencyViewModel = emergencyViewModel,
                onBackClick = { navController.popBackStack() },
                onServiceConfirmed = {
                    navController.navigate(Screen.Location.route)
                },
                onOpenAiDiagnoser = {
                    navController.navigate(Screen.AiDiagnoser.route)
                }
            )
        }

        composable(Screen.Location.route) {
            LocationScreen(
                emergencyViewModel = emergencyViewModel,
                onBackClick = { navController.popBackStack() },
                onLocationConfirmed = {
                    navController.navigate(Screen.VehicleDetails.route)
                }
            )
        }

        composable(Screen.VehicleDetails.route) {
            VehicleDetailsScreen(
                emergencyViewModel = emergencyViewModel,
                aiViewModel = aiViewModel,
                onBackClick = { navController.popBackStack() },
                onProceedToSummary = {
                    navController.navigate(Screen.RequestSummary.route)
                }
            )
        }

        composable(Screen.RequestSummary.route) {
            RequestSummaryScreen(
                emergencyViewModel = emergencyViewModel,
                onBackClick = { navController.popBackStack() },
                onConfirmedDispatched = { _ ->
                    navController.navigate(Screen.SearchingMechanic.route) {
                        popUpTo(Screen.ServiceSelect.route) { inclusive = false }
                    }
                }
            )
        }

        composable(Screen.SearchingMechanic.route) {
            SearchingMechanicScreen(
                emergencyViewModel = emergencyViewModel,
                onMechanicAssigned = {
                    navController.navigate(Screen.JobTracking.route) {
                        popUpTo(Screen.SearchingMechanic.route) { inclusive = true }
                    }
                },
                onCancelled = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.SearchingMechanic.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.JobTracking.route) {
            JobTrackingScreen(
                emergencyViewModel = emergencyViewModel,
                onBackClick = { navController.popBackStack() },
                onOpenChat = { jobId ->
                    navController.navigate(Screen.Chat.createRoute(jobId))
                },
                onJobCompleted = {
                    navController.navigate(Screen.JobCompletion.route) {
                        popUpTo(Screen.JobTracking.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: "active_job"
            ChatScreen(
                jobId = jobId,
                chatViewModel = chatViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.JobCompletion.route) {
            JobCompletionScreen(
                emergencyViewModel = emergencyViewModel,
                onFinished = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.AiDiagnoser.route) {
            AiDiagnoserScreen(
                emergencyViewModel = emergencyViewModel,
                aiViewModel = aiViewModel,
                onBackClick = { navController.popBackStack() },
                onServiceSelected = {
                    navController.navigate(Screen.Location.route)
                }
            )
        }
    }
}
